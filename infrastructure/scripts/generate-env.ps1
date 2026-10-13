param(
    [string]$Target = '.env'
)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $Target) {
    throw 'Refusing to overwrite an existing secret file'
}
function New-RandomBytes {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($bytes)
    $generator.Dispose()
    return ,$bytes
}
$lines = @('POSTGRES_USER=postgres')
foreach ($variable in @(
    'POSTGRES_PASSWORD',
    'AUTH_DB_PASSWORD',
    'PRODUCT_DB_PASSWORD',
    'ORDER_DB_PASSWORD',
    'PAYMENT_DB_PASSWORD',
    'NOTIFICATION_DB_PASSWORD',
    'RABBITMQ_DEFAULT_PASS',
    'PRODUCT_RPC_TOKEN',
    'NOTIFICATION_RECIPIENT_TOKEN',
    'MONITOR_DB_PASSWORD',
    'GRAFANA_ADMIN_PASSWORD'
)) {
    $value = [BitConverter]::ToString((New-RandomBytes)).Replace('-', '').ToLowerInvariant()
    $lines += "$variable=$value"
}
$lines += 'RABBITMQ_DEFAULT_USER=commerce_app'
$lines += 'JWT_SECRET=' + [Convert]::ToBase64String((New-RandomBytes))
$lines += 'DOMAIN=md05-rikkei-project.duckdns.org'
$lines += 'AUTH_IMAGE=ghcr.io/dth251/md05-rikkei-project/auth-service'
$lines += 'VNPAY_ENABLED=false'
$lines += 'SMTP_ENABLED=false'
$lines += 'SMTP_HOST=localhost'
$lines += 'SMTP_PORT=587'
$lines += 'SMTP_USERNAME='
$lines += 'SMTP_PASSWORD='
$lines += 'SMTP_FROM='
$lines += 'SMTP_AUTH=true'
$lines += 'SMTP_STARTTLS=true'
$lines += 'VNPAY_TMN_CODE='
$lines += 'VNPAY_HASH_SECRET='
$lines += 'VNPAY_SERVER_IP='
$lines += 'VNPAY_RETURN_URL=https://md05-rikkei-project.duckdns.org/api/payments/vnpay/return'
[System.IO.File]::WriteAllText(
    [System.IO.Path]::GetFullPath($Target),
    ($lines -join "`n") + "`n",
    (New-Object System.Text.UTF8Encoding $false)
)
Write-Output "Created private environment file: $Target"
