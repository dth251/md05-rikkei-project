param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._%+-]+@gmail\.com$')]
    [string]$Sender,

    [Parameter(Mandatory = $true)]
    [string]$SshKey,

    [Parameter(Mandatory = $true)]
    [string]$ResultFile
)

$ErrorActionPreference = 'Stop'
$secret = $null
$plain = $null
$pointer = [IntPtr]::Zero
$remote = $null
$result = [ordered]@{
    status = 'awaiting-password'
    sender = $Sender
    target = 'deploy@42.96.2.60'
    time = (Get-Date -Format o)
}
$resultPath = [IO.Path]::GetFullPath($ResultFile)
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
if (-not $resultPath.StartsWith((Join-Path $repoRoot '.verification') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'ResultFile must be inside the ignored project .verification directory'
}
$keyPath = (Resolve-Path -LiteralPath $SshKey).Path
$result | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding UTF8

try {
    Write-Host "Cau hinh Gmail SMTP tren VPS cho $Sender"
    Write-Host 'Tao mat khau ung dung tai https://myaccount.google.com/apppasswords'
    Write-Host 'Nhap MAT KHAU UNG DUNG 16 ky tu, khong nhap mat khau dang nhap Gmail.'
    $secret = Read-Host 'Mat khau ung dung (nhap kin)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) -replace '\s', ''
    if ($plain -cnotmatch '^[A-Za-z0-9]{16}$') {
        throw 'Expected a 16-character Gmail app password'
    }

    $remote = @'
set -euo pipefail
set +x
umask 077
sender='__SENDER__'
gmail_password='__PASSWORD__'
root=/opt/omnichannel-ecommerce
cd "$root/current"
exec 9>"$root/.deploy.lock"
flock -w 300 9
env_file="$root/.env"
test -f "$env_file"
project=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' omnichannel-day1-notification-service-1)
test "$project" = omnichannel-day1
image=$(docker inspect -f '{{.Config.Image}}' omnichannel-day1-notification-service-1)
tag=${image##*:}
[[ "$tag" =~ ^[0-9a-f]{40}$ ]]
test "$(docker inspect -f '{{.State.Health.Status}}' omnichannel-day1-notification-service-1)" = healthy

# AUTH is a challenge/response exchange; never pipeline the credentials.
coproc GMAIL_TLS { timeout 75 openssl s_client -starttls smtp -connect smtp.gmail.com:587 \
    -servername smtp.gmail.com -verify_return_error -crlf -quiet 2>/dev/null; }
smtp_pid=$GMAIL_TLS_PID
exec 3>&"${GMAIL_TLS[1]}" 4<&"${GMAIL_TLS[0]}"
reply_codes=()
read_reply() {
  local line
  smtp_code=''
  while IFS= read -r -t 15 line <&4; do
    if [[ "$line" =~ ^[0-9]{3}[[:space:]-] ]]; then
      smtp_code=${line:0:3}
      if [[ "${line:3:1}" = ' ' ]]; then
        reply_codes+=("$smtp_code")
        return 0
      fi
    fi
  done
  return 1
}
smtp_ok=true
printf 'EHLO md05.local\n' >&3
if ! read_reply || [[ "$smtp_code" != 250 ]]; then smtp_ok=false; fi
if "$smtp_ok"; then
  printf 'AUTH LOGIN\n' >&3
  if ! read_reply || [[ "$smtp_code" != 334 ]]; then smtp_ok=false; fi
fi
if "$smtp_ok"; then
  printf '%s\n' "$(printf '%s' "$sender" | base64 -w 0)" >&3
  if ! read_reply || [[ "$smtp_code" != 334 ]]; then smtp_ok=false; fi
fi
if "$smtp_ok"; then
  printf '%s\n' "$(printf '%s' "$gmail_password" | base64 -w 0)" >&3
  if ! read_reply || [[ "$smtp_code" != 235 ]]; then smtp_ok=false; fi
fi
printf 'QUIT\n' >&3 || true
read_reply || true
exec 3>&- 4<&-
wait "$smtp_pid" || true
if ! "$smtp_ok"; then
  unset gmail_password
  printf 'SMTP_AUTHENTICATION_FAILED reply_codes=%s: no runtime configuration changed\n' "${reply_codes[*]}" >&2
  exit 1
fi

backup="$root/.env.before-gmail-$(date -u +%Y%m%dT%H%M%SZ)-$$"
cp -p "$env_file" "$backup"
chmod 600 "$backup"
next=$(mktemp "$root/.env.gmail.XXXXXX")
trap 'rm -f -- "$next"' EXIT
awk '!/^(SMTP_SANDBOX_ENABLED|SMTP_ENABLED|SMTP_HOST|SMTP_PORT|SMTP_USERNAME|SMTP_PASSWORD|SMTP_FROM|SMTP_AUTH|SMTP_STARTTLS)=/' "$env_file" > "$next"
{
  printf '\nSMTP_SANDBOX_ENABLED=false\nSMTP_ENABLED=true\nSMTP_HOST=smtp.gmail.com\nSMTP_PORT=587\n'
  printf 'SMTP_USERNAME=%s\nSMTP_PASSWORD=%s\nSMTP_FROM=%s\n' "$sender" "$gmail_password" "$sender"
  printf 'SMTP_AUTH=true\nSMTP_STARTTLS=true\n'
} >> "$next"
unset gmail_password
chmod 600 "$next"
mv -- "$next" "$env_file"
export IMAGE_TAG="$tag" NOTIFICATION_IMAGE_TAG="$tag"
compose=(docker compose -p "$project" --project-directory "$root" --env-file "$env_file"
  -f "$root/current/docker-compose.yml" -f "$root/current/docker-compose.production.yml")
rollback() {
  cp -p "$backup" "$env_file"
  "${compose[@]}" up -d --no-deps --no-build --pull never --wait --wait-timeout 240 notification-service
  echo 'GMAIL_CONFIGURATION_ROLLED_BACK' >&2
}
if ! "${compose[@]}" config --quiet; then
  cp -p "$backup" "$env_file"
  echo 'COMPOSE_VALIDATION_FAILED: restored original environment' >&2
  exit 1
fi
if ! "${compose[@]}" up -d --no-deps --no-build --pull never --wait --wait-timeout 240 notification-service; then
  rollback
  exit 1
fi
if [[ "$(docker inspect -f '{{.Config.Image}}' omnichannel-day1-notification-service-1)" != "$image" ]] \
  || [[ "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_HOST)" != smtp.gmail.com ]] \
  || [[ "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_PORT)" != 587 ]] \
  || [[ "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_USERNAME)" != "$sender" ]] \
  || [[ "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_FROM)" != "$sender" ]] \
  || [[ "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_ENABLED)" != true ]] \
  || [[ "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_AUTH)" != true ]] \
  || [[ "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_STARTTLS)" != true ]]; then
  rollback
  exit 1
fi
printf 'GMAIL_SMTP_CONFIGURED image=%s health=healthy host=smtp.gmail.com port=587\n' "$image"
printf 'INBOX_RECEIPT_NOT_YET_VERIFIED\n'
'@
    $remote = $remote.Replace('__SENDER__', $Sender).Replace('__PASSWORD__', $plain)
    $result.status = 'configuring'
    $result.time = Get-Date -Format o
    $result | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding UTF8
    $sshOptions = @('-i', $keyPath, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10')
    try {
        $ErrorActionPreference = 'Continue'
        $output = @($remote | & ssh @sshOptions 'deploy@42.96.2.60' 'tr -d ''\r'' | bash -s' 2>&1 | ForEach-Object { $_.ToString() })
        $sshExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
    }
    if ($sshExit -ne 0 -or -not ($output -match '^GMAIL_SMTP_CONFIGURED ')) {
        throw 'SMTP configuration did not complete; inspect the sanitized terminal status'
    }
    $result.status = 'configured'
    $result.time = Get-Date -Format o
    $result.inboxVerified = $false
    $result.summary = @($output | Where-Object { $_ -match '^(GMAIL_SMTP_CONFIGURED|INBOX_RECEIPT_)' })
    $result | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding UTF8
    Write-Host 'Da bat Gmail SMTP. Can dat don thu va doi chieu hop thu that.'
} catch {
    $result.status = 'failed'
    $result.time = Get-Date -Format o
    $result.message = 'Gmail configuration failed or was cancelled. Credentials were not written to the result file.'
    if ($null -ne $output) {
        $safeStatus = @($output | Where-Object { $_ -match '^(SMTP_AUTHENTICATION_FAILED|COMPOSE_VALIDATION_FAILED|GMAIL_CONFIGURATION_ROLLED_BACK)' })
        if ($safeStatus.Count -gt 0) { $result.detail = $safeStatus }
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding UTF8
    Write-Host 'Chua bat Gmail SMTP thanh cong. Hay bao lai trang thai nay de kiem tra.'
} finally {
    $plain = $null
    $remote = $null
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    if ($null -ne $secret) { $secret.Dispose() }
}
Read-Host 'Nhan Enter de dong cua so' | Out-Null
