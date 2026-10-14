param([string]$EvidenceFile = '.verification/logging-live.json')
$ErrorActionPreference = 'Stop'
[Net.WebRequest]::DefaultWebProxy = $null
$project = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $project
$trace = [Guid]::NewGuid().ToString('N')
$span = [Guid]::NewGuid().ToString('N').Substring(0,16)
$marker = 'synthetic-private-' + [Guid]::NewGuid().ToString('N')
$container = 'md05-audit-fixture-' + $trace
$command = "sleep 65; echo private_email=$marker; echo COMMERCE_AUDIT action=fixture_check outcome=completed trace_id=$trace span_id=$span private_email=$marker; sleep 120"
function Invoke-Docker([string[]]$Arguments) {
    try {
        $ErrorActionPreference = 'Continue'
        & docker @Arguments 2>&1 | Out-Null
        $code = $LASTEXITCODE
    } finally {$ErrorActionPreference = 'Stop'}
    return $code
}
$code = Invoke-Docker @('run','-d','--rm','--name',$container,'--memory','32m',
    '--label','com.docker.compose.project=omnichannel-day1',
    '--label','com.docker.compose.service=audit-fixture','alpine:3.22','sh','-c',$command)
if ($code) {throw 'Logging fixture container could not start'}
try {
    $query = '{application="audit-fixture"} |= "' + $trace + '"'
    $url = 'http://127.0.0.1:13100/loki/api/v1/query_range?query=' + [Uri]::EscapeDataString($query) + '&limit=20'
    $found = $false
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        try {
            $result = Invoke-RestMethod $url -TimeoutSec 5
            foreach ($stream in $result.data.result) {
                foreach ($value in $stream.values) {
                    if ($value[1] -eq "action=fixture_check outcome=completed trace_id=$trace span_id=$span") {$found=$true}
                    if ($value[1].Contains($marker)) {throw 'Private marker leaked to Loki'}
                }
            }
        } catch {
            if ($_.Exception.Message -eq 'Private marker leaked to Loki') {throw}
        }
        if ($found) {break}
        Start-Sleep -Seconds 2
    }
    if (-not $found) {throw 'Sanitized Docker audit log was not queryable in Loki'}
    $rawQuery = '{application="audit-fixture"} |= "' + $marker + '"'
    $raw = Invoke-RestMethod ('http://127.0.0.1:13100/loki/api/v1/query_range?query=' + [Uri]::EscapeDataString($rawQuery)) -TimeoutSec 5
    if (@($raw.data.result).Count) {throw 'Private raw Docker log leaked to Loki'}
    $target = [IO.Path]::GetFullPath((Join-Path $project $EvidenceFile))
    if (-not $target.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'Evidence must stay inside project'}
    $report = @{timeUtc=[DateTime]::UtcNow.ToString('o');traceId=$trace;sanitizedAuditQueryable=$true;privateMarkerExcluded=$true}
    [IO.File]::WriteAllText($target,($report | ConvertTo-Json),(New-Object Text.UTF8Encoding $false))
    Write-Output 'PASS: Docker audit queried by trace ID; raw line and private marker excluded'
} finally {
    # Only the uniquely named container created by this invocation is stopped.
    $null = Invoke-Docker @('stop','--time','2',$container)
}
