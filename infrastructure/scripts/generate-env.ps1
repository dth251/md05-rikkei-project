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
    'RABBITMQ_DEFAULT_PASS'
)) {
    $value = [BitConverter]::ToString((New-RandomBytes)).Replace('-', '').ToLowerInvariant()
    $lines += "$variable=$value"
}
$lines += 'RABBITMQ_DEFAULT_USER=commerce_app'
$lines += 'JWT_SECRET=' + [Convert]::ToBase64String((New-RandomBytes))
$lines += 'DOMAIN=md05-rikkei-project.duckdns.org'
$lines += 'AUTH_IMAGE=ghcr.io/dth251/md05-rikkei-project/auth-service'
[System.IO.File]::WriteAllText(
    [System.IO.Path]::GetFullPath($Target),
    ($lines -join "`n") + "`n",
    (New-Object System.Text.UTF8Encoding $false)
)
Write-Output "Created private environment file: $Target"
