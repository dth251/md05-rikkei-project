param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{32}$')][string]$TraceId,
    [string]$EvidenceFile = '.verification/tracing-live.json'
)
$ErrorActionPreference = 'Stop'
[Net.WebRequest]::DefaultWebProxy = $null
$project = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $project
$expected = @('api-gateway','auth-service','product-service','order-service','payment-service','notification-service')
$complete = $false
for ($attempt=0; $attempt -lt 60; $attempt++) {
    try {
        $document = Invoke-RestMethod "http://127.0.0.1:13200/api/traces/$TraceId" -Headers @{Accept='application/json'} -TimeoutSec 5
        $names = @($document.batches | ForEach-Object {
            $_.resource.attributes | Where-Object {$_.key -eq 'service.name'} | ForEach-Object {$_.value.stringValue}
        } | Sort-Object -Unique)
        if (@($expected | Where-Object {$_ -notin $names}).Count -eq 0) {$complete=$true;break}
    } catch { }
    Start-Sleep -Seconds 2
}
if (-not $complete) {throw 'Persisted business trace does not include all six services'}
$counts = @{}
foreach ($entry in @(@('order_db','outbox_events'),@('payment_db','outbox_events'),@('notification_db','email_deliveries'))) {
    $database=$entry[0];$table=$entry[1]
    $sql = "SELECT count(*) FROM $table WHERE trace_parent LIKE '00-$TraceId-%'"
    try {
        $ErrorActionPreference='Continue'
        $result = & docker compose exec -T postgres psql -U postgres -d $database -At -c $sql 2>$null
        $code=$LASTEXITCODE
    } finally {$ErrorActionPreference='Stop'}
    if ($code -or $result -notmatch '^\d+$' -or [int]$result -lt 1) {throw "Missing persisted trace context in $database.$table"}
    $counts[$database]=[int]$result
}
$target=[IO.Path]::GetFullPath((Join-Path $project $EvidenceFile))
if (-not $target.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'Evidence must stay inside project'}
$report=@{timeUtc=[DateTime]::UtcNow.ToString('o');traceId=$TraceId;services=$names;durableTraceRows=$counts;
    note='Agent-backed business fixture, including Outbox and delayed SMTP; no credential or raw span exported to evidence'}
[IO.File]::WriteAllText($target,($report | ConvertTo-Json -Depth 10),(New-Object Text.UTF8Encoding $false))
Write-Output 'PASS: six-service business trace and durable Order/Payment/email trace context'
