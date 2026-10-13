param(
    [Parameter(Mandatory = $true)][string]$K6Path,
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [ValidateRange(2, 50)][int]$Accounts = 10,
    [ValidateRange(1, 10)][int]$Rate = 1,
    [ValidateRange(10, 120)][int]$Seconds = 20
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $projectRoot
if (-not (Test-Path -LiteralPath $K6Path -PathType Leaf)) {throw 'Missing k6 executable'}
$drive = [IO.DriveInfo]::new([IO.Path]::GetPathRoot($projectRoot))
if ($drive.AvailableFreeSpace -lt 4GB) {throw 'At least 4 GiB free disk required'}
$privateRun = Join-Path $projectRoot ('.verification/multi-user-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $privateRun | Out-Null
$values = @{}
Get-Content -LiteralPath .env | ForEach-Object {
    if ($_ -match '^([A-Z0-9_]+)=(.*)$') {$values[$matches[1]] = $matches[2]}
}
$env:JWT_SECRET = $values['JWT_SECRET']
$env:ORDER_LIVE_DB_PASSWORD = $values['ORDER_DB_PASSWORD']
$env:ORDER_LIVE_DB_URL = 'jdbc:postgresql://localhost:15432/order_db'
$env:LOAD_BASE_URL = $BaseUrl
$env:LOAD_ACCOUNTS = [string]$Accounts
$env:LOAD_STOCK = [string]($Rate * $Seconds * 3)
$env:LOAD_FIXTURE_FILE = Join-Path $privateRun 'fixture.json'
$env:FIXTURE_FILE = $env:LOAD_FIXTURE_FILE
$env:BASE_URL = $BaseUrl
$env:RATE = [string]$Rate
$env:DURATION = "${Seconds}s"
function Invoke-Fixture([string]$Action) {
    & ./order-service/gradlew.bat -p order-service multiUserLoadFixture "-PfixtureAction=$Action" --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx256m -XX:MaxMetaspaceSize=256m' *> (Join-Path $privateRun "$Action.log")
    if ($LASTEXITCODE) {throw "Fixture $Action failed; see private log"}
}
function Save-Snapshot([string]$Name) {
    $report = Join-Path $privateRun "$Name.txt"
    [DateTime]::UtcNow.ToString('o') | Out-File -LiteralPath $report
    docker stats --no-stream --format '{{.Name}} CPU={{.CPUPerc}} RAM={{.MemUsage}}' | Out-File -LiteralPath $report -Append
    docker compose exec -T rabbitmq rabbitmqctl -q list_queues name messages_ready messages_unacknowledged consumers | Out-File -LiteralPath $report -Append
    foreach ($database in @('order_db', 'payment_db')) {
        docker compose exec -T postgres psql -U postgres -d $database -c 'SELECT count(*) AS pending,extract(epoch FROM CURRENT_TIMESTAMP-min(created_at)) AS oldest_seconds FROM outbox_events WHERE published_at IS NULL' | Out-File -LiteralPath $report -Append
    }
}
function Assert-Resources {
    $memory = Get-CimInstance Win32_OperatingSystem
    if ($memory.FreePhysicalMemory -lt 512MB / 1KB -or $drive.AvailableFreeSpace -lt 4GB) {
        throw 'Resource guard requires 512 MiB available Windows RAM and 4 GiB free disk before a load phase'
    }
}
Assert-Resources
Invoke-Fixture prepare
$sampler = Start-Job -ArgumentList $projectRoot, $privateRun -ScriptBlock {
    param($project, $output)
    Set-Location -LiteralPath $project
    while ($true) {
        [DateTime]::UtcNow.ToString('o') | Out-File -LiteralPath (Join-Path $output 'resources.txt') -Append
        docker stats --no-stream --format '{{.Name}} CPU={{.CPUPerc}} RAM={{.MemUsage}}' | Out-File -LiteralPath (Join-Path $output 'resources.txt') -Append
        Start-Sleep -Seconds 2
    }
}
try {
    Save-Snapshot before
    foreach ($phase in @('warmup', 'catalog', 'orders', 'e2e')) {
        Assert-Resources
        $env:PHASE = if ($phase -eq 'warmup') {'catalog'} else {$phase}
        $env:DURATION = if ($phase -eq 'warmup') {'10s'} else {"${Seconds}s"}
        $env:RATE = if ($phase -eq 'warmup') {'1'} else {[string]$Rate}
        $env:SUMMARY_FILE = Join-Path $privateRun "$phase-summary.json"
        $loadProcess = Start-Process -FilePath $K6Path -ArgumentList @('run','tests/k6/multi-user-load.js') -WindowStyle Hidden -PassThru -WorkingDirectory $projectRoot -RedirectStandardOutput (Join-Path $privateRun "$phase.log") -RedirectStandardError (Join-Path $privateRun "$phase-error.log")
        while (-not $loadProcess.HasExited) {
            $memory = Get-CimInstance Win32_OperatingSystem
            if ($memory.FreePhysicalMemory -lt 256MB / 1KB -or $drive.AvailableFreeSpace -lt 4GB) {
                Stop-Process -Id $loadProcess.Id -Force
                throw "$phase interrupted by resource guard; summary may be incomplete, data retained"
            }
            Start-Sleep -Seconds 2
            $loadProcess.Refresh()
        }
        $loadProcess.WaitForExit()
        $loadExit = $loadProcess.ExitCode
        Save-Snapshot "$phase-after"
        if ($loadExit) {throw "$phase load failed (exit $loadExit); later phases cancelled"}
        $env:LOAD_RESULT_FILE = Join-Path $privateRun "$phase-reconciliation.json"
        if ($phase -eq 'orders') {Invoke-Fixture finish}
        if ($phase -eq 'e2e') {Invoke-Fixture verify}
        $memory = Get-CimInstance Win32_OperatingSystem
        if ($memory.FreePhysicalMemory -lt 512MB / 1KB -or $drive.AvailableFreeSpace -lt 4GB) {
            throw 'Resource guard stopped later load phases'
        }
    }
} finally {
    Stop-Job -Job $sampler
    Remove-Job -Job $sampler
    Save-Snapshot final
}
Write-Output "PASS: all load phases and PostgreSQL reconciliations; private evidence in $privateRun"
