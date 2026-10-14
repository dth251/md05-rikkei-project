param(
    [string]$EvidenceFile = '.verification/observability-live.json',
    [switch]$SkipRestart
)
$ErrorActionPreference = 'Stop'
[Net.WebRequest]::DefaultWebProxy = $null # All endpoints below are fixed loopback addresses.
if ([string]::IsNullOrEmpty($env:GRAFANA_ADMIN_PASSWORD)) {throw 'Load private GRAFANA_ADMIN_PASSWORD into the process'}
$project = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $project
$basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('ops:' + $env:GRAFANA_ADMIN_PASSWORD))
$headers = @{Authorization = 'Basic ' + $basic}
for ($retry=0; $retry -lt 60; $retry++) {
    try {
        if ((Invoke-RestMethod http://127.0.0.1:13000/api/health -TimeoutSec 5).database -eq 'ok') {break}
    } catch { }
    if ($retry -eq 59) {throw 'Grafana did not become ready'}
    Start-Sleep -Seconds 2
}
function Read-Query([string]$Expression) {
    return (Invoke-RestMethod ('http://127.0.0.1:19090/api/v1/query?query=' + [Uri]::EscapeDataString($Expression)) -TimeoutSec 5).data.result
}
function Wait-Targets {
    for ($retry = 0; $retry -lt 30; $retry++) {
        $targets = Read-Query 'up{job="services"}'
        if (@($targets).Count -eq 6 -and @($targets | Where-Object {$_.value[1] -ne '1'}).Count -eq 0) {return $targets}
        Start-Sleep -Seconds 2
    }
    throw 'Six service scrape targets are not UP'
}
$targets = Wait-Targets
for ($retry=0; $retry -lt 30; $retry++) {
    $dependencies = Read-Query 'up{job=~"host|postgres|redis|rabbitmq|rabbitmq-queues"}'
    if (@($dependencies).Count -eq 5 -and @($dependencies | Where-Object {$_.value[1] -ne '1'}).Count -eq 0) {break}
    if ($retry -eq 29) {throw 'Dependency scrape targets are not UP'}
    Start-Sleep -Seconds 2
}
$backlog = Read-Query 'commerce_backlog_snapshot_valid'
if (@($backlog).Count -ne 2 -or @($backlog | Where-Object {$_.value[1] -ne '1'}).Count) {throw 'Outbox snapshot is not valid for Order and Payment'}
$sources = Invoke-RestMethod http://127.0.0.1:13000/api/datasources -Headers $headers -TimeoutSec 15
foreach ($uid in @('prometheus','tempo','loki')) {
    if (@($sources | Where-Object {$_.uid -eq $uid}).Count -ne 1) {throw "Missing Grafana datasource $uid"}
}
$dashboard = Invoke-RestMethod http://127.0.0.1:13000/api/dashboards/uid/commerce-overview -Headers $headers -TimeoutSec 15
if (@($dashboard.dashboard.panels).Count -lt 21) {throw 'Dashboard provisioning is incomplete'}
# A fresh CI TSDB needs two more scrape intervals after its first UP sample.
# Advance the query end while waiting; keep the passing window fixed across restart.
for ($retry=0; $retry -lt 30; $retry++) {
    $start = [DateTimeOffset]::UtcNow.AddMinutes(-2).ToUnixTimeSeconds()
    $end = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $url = 'http://127.0.0.1:19090/api/v1/query_range?query=' + [Uri]::EscapeDataString('up{job="services"}') + "&start=$start&end=$end&step=15"
    $history = (Invoke-RestMethod $url -TimeoutSec 5).data.result
    if (@($history).Count -eq 6 -and @($history | Where-Object {@($_.values).Count -lt 3}).Count -eq 0) {break}
    if ($retry -eq 29) {throw 'Insufficient scrape history'}
    Start-Sleep -Seconds 2
}
if (-not $SkipRestart) {
    # Windows PowerShell treats Docker progress on stderr as a terminating error.
    try {
        $ErrorActionPreference = 'Continue'
        docker compose -f docker-compose.yml -f docker-compose.monitoring.yml --profile monitoring restart prometheus grafana *> $null
        $restartExit = $LASTEXITCODE
    } finally {$ErrorActionPreference = 'Stop'}
    if ($restartExit) {throw 'Monitoring restart failed'}
    $ready = $false
    for ($retry = 0; $retry -lt 60; $retry++) {
        try {
            $health = Invoke-RestMethod http://127.0.0.1:13000/api/health -TimeoutSec 5
            $retained = (Invoke-RestMethod $url -TimeoutSec 5).data.result
            if ($health.database -eq 'ok' -and @($retained).Count -eq 6 -and
                @($retained | Where-Object {@($_.values).Count -lt 3}).Count -eq 0) {$ready=$true;break}
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {throw 'Monitoring history/database did not recover after restart'}
    $dashboard = Invoke-RestMethod http://127.0.0.1:13000/api/dashboards/uid/commerce-overview -Headers $headers -TimeoutSec 5
    if (@($dashboard.dashboard.panels).Count -lt 21) {throw 'Dashboard missing after restart'}
}
$report = @{timeUtc=[DateTime]::UtcNow.ToString('o'); services=@($targets | ForEach-Object {$_.metric.service});
    dependencyJobs=@($dependencies | ForEach-Object {$_.metric.job}); historySeries=@($history).Count;
    dashboardPanels=@($dashboard.dashboard.panels).Count; datasources=@('prometheus','tempo','loki');
    restartVerified=(-not $SkipRestart); note='Docker Desktop host exporter measures Linux VM; no capacity claim'}
$path = [IO.Path]::GetFullPath((Join-Path $project $EvidenceFile))
if (-not $path.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'Evidence must stay inside project'}
[IO.File]::WriteAllText($path,($report | ConvertTo-Json -Depth 10),(New-Object Text.UTF8Encoding $false))
Write-Output 'PASS: six service targets, dependency exporters, measured backlog, Grafana provisioning and history'
