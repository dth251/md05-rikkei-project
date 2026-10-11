$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Get-Content -LiteralPath (Join-Path $root '.env') | ForEach-Object {
    if ($_ -match '^AUTH_DB_PASSWORD=(.*)$') { $env:AUTH_SMOKE_DB_PASSWORD = $matches[1] }
}
$env:AUTH_SMOKE_DB_URL = 'jdbc:postgresql://localhost:15432/auth_db'
$env:AUTH_SMOKE_URL = 'http://127.0.0.1:8081'
$env:AUTH_SMOKE_RESTART = 'true'
Push-Location (Join-Path $root 'auth-service')
try {
    & .\gradlew.bat liveTest --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Java live tests failed' }
} finally {
    Pop-Location
    Remove-Item Env:\AUTH_SMOKE_DB_PASSWORD -ErrorAction SilentlyContinue
}
