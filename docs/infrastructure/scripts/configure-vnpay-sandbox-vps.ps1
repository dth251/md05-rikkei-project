param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9]{8}$')]
    [string]$TmnCode,
    [string]$SshKey = "$env:USERPROFILE/.ssh/md05-rikkei-project/day1-deploy-key",
    [Security.SecureString]$HashSecret
)

$ErrorActionPreference = 'Stop'
$keyPath = (Resolve-Path -LiteralPath $SshKey).Path
$secret = $null
$plain = $null
$pointer = [IntPtr]::Zero
$remote = $null
try {
    $secret = $HashSecret
    if ($null -eq $secret) {
        $secret = Read-Host 'VNPay SANDBOX HashSecret (nhap kin, khong phai mat khau portal)' -AsSecureString
    }
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    # VNPay sandbox issues an alphanumeric signing key. Reject shell/dotenv syntax.
    if ($plain -cnotmatch '^[A-Za-z0-9]{16,256}$') {
        throw 'Expected the alphanumeric HashSecret issued for the sandbox merchant'
    }
    $remote = @'
set -euo pipefail
set +x
umask 077
terminal='__TERMINAL__'
signing_key='__SIGNING_KEY__'
root=/opt/omnichannel-ecommerce
env_file="$root/.env"
cd "$root/current"
exec 9>"$root/.deploy.lock"
flock -w 300 9
test -f "$env_file"
image=$(docker inspect -f '{{.Config.Image}}' omnichannel-day1-payment-service-1)
tag=${image##*:}
[[ "$tag" =~ ^[0-9a-f]{40}$ ]]
test "$(docker inspect -f '{{.State.Health.Status}}' omnichannel-day1-payment-service-1)" = healthy
unsettled=$(docker exec omnichannel-day1-postgres-1 psql -U postgres -d payment_db -Atc "SELECT count(*) FROM vnpay_payments v JOIN payments p ON p.id=v.payment_id WHERE p.status='PENDING' OR p.refund_status='REQUIRED'")
if [[ "$unsettled" != 0 ]]; then
  echo 'SANDBOX_SETUP_REFUSED: existing checkout/refund requires reconciliation' >&2
  exit 1
fi
backup="$root/.env.before-vnpay-sandbox-$(date -u +%Y%m%dT%H%M%SZ)-$$"
cp -p "$env_file" "$backup"
chmod 600 "$backup"
next=$(mktemp "$root/.env.vnpay-sandbox.XXXXXX")
trap 'rm -f -- "$next"' EXIT
awk '!/^(VNPAY_ENABLED|VNPAY_TMN_CODE|VNPAY_HASH_SECRET|VNPAY_SERVER_IP|VNPAY_RETURN_URL)=/' "$env_file" > "$next"
{
  printf '\nVNPAY_ENABLED=true\nVNPAY_TMN_CODE=%s\nVNPAY_HASH_SECRET=%s\n' "$terminal" "$signing_key"
  printf 'VNPAY_SERVER_IP=42.96.2.60\nVNPAY_RETURN_URL=https://md05-rikkei-project.duckdns.org/api/payments/vnpay/return\n'
} >> "$next"
unset signing_key
chmod 600 "$next"
mv -- "$next" "$env_file"
export IMAGE_TAG="$tag" PAYMENT_IMAGE_TAG="$tag"
compose=(docker compose -p omnichannel-day1 --project-directory "$root" --env-file "$env_file"
  -f "$root/current/docker-compose.yml" -f "$root/current/docker-compose.production.yml")
rollback() {
  cp -p "$backup" "$env_file"
  "${compose[@]}" up -d --no-deps --no-build --pull never --wait --wait-timeout 240 payment-service >/dev/null 2>&1
  echo 'SANDBOX_CONFIGURATION_ROLLED_BACK' >&2
}
if ! "${compose[@]}" config --quiet >/dev/null 2>&1; then
  cp -p "$backup" "$env_file"
  echo 'SANDBOX_COMPOSE_VALIDATION_FAILED' >&2
  exit 1
fi
if ! "${compose[@]}" up -d --no-deps --no-build --pull never --wait --wait-timeout 240 payment-service >/dev/null 2>&1; then
  rollback
  exit 1
fi
if [[ "$(docker inspect -f '{{.Config.Image}}' omnichannel-day1-payment-service-1)" != "$image" ]] \
  || [[ "$(docker exec omnichannel-day1-payment-service-1 printenv VNPAY_ENABLED)" != true ]]; then
  rollback
  exit 1
fi
printf 'VNPAY_SANDBOX_CONFIGURED image=%s health=healthy\n' "$image"
echo 'MERCHANT_CREDENTIALS_AND_BROWSER_TRANSACTION_NOT_YET_VERIFIED'
'@
    $remote = $remote.Replace('__TERMINAL__', $TmnCode).Replace('__SIGNING_KEY__', $plain)
    $options = @('-i', $keyPath, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10')
    $remote | & ssh @options deploy@42.96.2.60 'tr -d ''\r'' | bash -s'
    if ($LASTEXITCODE -ne 0) { throw 'VNPay sandbox configuration failed; credentials were not printed' }
} finally {
    $plain = $null
    $remote = $null
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    if ($null -ne $secret) { $secret.Dispose() }
}
