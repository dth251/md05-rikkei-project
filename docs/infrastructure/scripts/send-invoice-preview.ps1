param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$')][string]$Recipient,
    [Parameter(Mandatory = $true)][string]$HtmlFile,
    [Parameter(Mandatory = $true)][string]$SshKey,
    [Parameter(Mandatory = $true)][string]$ResultFile,
    [string]$Subject = 'MD05 - Hoa don mua hang mau'
)
$ErrorActionPreference = 'Stop'
$htmlBytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $HtmlFile).Path)
if ($htmlBytes.Length -gt 102400) { throw 'Email preview exceeds 100 KiB' }
$html = [Text.Encoding]::UTF8.GetString($htmlBytes)
if ($html -match '\{\{[A-Za-z]+\}\}') { throw 'Render all template variables before sending' }
$htmlBase64 = [Convert]::ToBase64String($htmlBytes)
$subjectBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Subject))
$plainBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('Email hoa don mau MD05. Vui long xem phien ban HTML de doc chi tiet san pham, tong tien va phuong thuc thanh toan. Day la du lieu demo, khong phat sinh chuyen tien.'))
$messageId = [guid]::NewGuid().ToString()
$remote = @'
set -euo pipefail
set +x
umask 077
root=/opt/omnichannel-ecommerce
test "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_HOST)" = smtp.gmail.com
sender=$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_USERNAME)
password=$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_PASSWORD)
[[ "$sender" =~ ^[A-Za-z0-9._%+-]+@gmail\.com$ ]]
[[ "$password" =~ ^[A-Za-z0-9]{16}$ ]]
message=$(mktemp "$root/.invoice-preview.XXXXXX")
trap 'rm -f -- "$message"' EXIT
boundary=md05-__MESSAGE_ID__
{
  printf 'From: MD05 Commerce <%s>\r\nTo: __RECIPIENT__\r\n' "$sender"
  printf 'Subject: =?UTF-8?B?__SUBJECT__?=\r\n'
  printf 'Date: %s\r\n' "$(LC_ALL=C date -R)"
  printf 'Message-ID: <__MESSAGE_ID__@notification.omnichannel>\r\nMIME-Version: 1.0\r\n'
  printf 'Content-Type: multipart/alternative; boundary="%s"\r\n\r\n' "$boundary"
  printf -- '--%s\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n' "$boundary"
  printf '%s' '__PLAIN__' | fold -w 76 | sed 's/$/\r/'
  printf '\r\n--%s\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n' "$boundary"
  printf '%s' '__HTML__' | fold -w 76 | sed 's/$/\r/'
  printf '\r\n--%s--\r\n' "$boundary"
} > "$message"
# Read authentication from stdin config, never from command arguments or logs.
printf 'user = "%s:%s"\n' "$sender" "$password" | curl --disable --config - \
  --url smtp://smtp.gmail.com:587 --ssl-reqd --mail-from "$sender" \
  --mail-rcpt '__RECIPIENT__' --upload-file "$message" --connect-timeout 10 \
  --max-time 45 --silent --show-error --output /dev/null --write-out 'SMTP_RESPONSE=%{response_code}\n'
unset password
printf 'INVOICE_EMAIL_ACCEPTED message_id=__MESSAGE_ID__\n'
'@
$remote = $remote.Replace('__RECIPIENT__', $Recipient).Replace('__SUBJECT__', $subjectBase64).Replace('__MESSAGE_ID__', $messageId).Replace('__PLAIN__', $plainBase64).Replace('__HTML__', $htmlBase64)
$key = (Resolve-Path -LiteralPath $SshKey).Path
$sshArgs = @('-i', $key, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10')
try {
    $ErrorActionPreference = 'Continue'
    $output = @($remote | & ssh @sshArgs 'deploy@42.96.2.60' 'tr -d ''\r'' | bash -s' 2>&1 | ForEach-Object { $_.ToString() })
    $sendExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = 'Stop'
}
$accepted = $sendExit -eq 0 -and @($output | Where-Object { $_ -match '^INVOICE_EMAIL_ACCEPTED ' }).Count -eq 1
$result = [ordered]@{status=$(if($accepted){'smtp-accepted'}else{'unconfirmed'});time=(Get-Date -Format o);messageId=$messageId;inboxVerified=$false;response=@($output | Where-Object { $_ -match '^(SMTP_RESPONSE=|INVOICE_EMAIL_ACCEPTED )' })}
$result | ConvertTo-Json | Set-Content -LiteralPath $ResultFile -Encoding UTF8
if (-not $accepted) { throw 'Email send result is unconfirmed. Check the server before retrying to avoid duplicates.' }
$result | ConvertTo-Json
