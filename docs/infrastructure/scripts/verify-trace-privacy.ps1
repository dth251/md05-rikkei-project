param([string]$EvidenceFile = '.verification/trace-privacy.json')
$ErrorActionPreference = 'Stop'
[Net.WebRequest]::DefaultWebProxy = $null # Tempo is queried over loopback only.
$project = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $project
New-Item -ItemType Directory -Path .verification -Force | Out-Null
$trace = [Guid]::NewGuid().ToString('N')
$marker = 'synthetic-private-' + [Guid]::NewGuid().ToString('N')
$span = [Guid]::NewGuid().ToString('N').Substring(0,16)
$start = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * [long]1000000
$end = $start + [long]1000000
$payload = @{resourceSpans=@(@{resource=@{attributes=@(
    @{key='service.name';value=@{stringValue='privacy-fixture'}},
    @{key='customer.email';value=@{stringValue=$marker}})};
    scopeSpans=@(@{scope=@{name=$marker;version=$marker;attributes=@(@{key='email';value=@{stringValue=$marker}})};spans=@(@{traceId=$trace;spanId=$span;traceState=$marker;
        name="GET /api/fixture?token=$marker";kind=2;startTimeUnixNano=[string]$start;endTimeUnixNano=[string]$end;
        attributes=@(@{key='http.route';value=@{stringValue='/api/fixture'}},@{key='authorization';value=@{stringValue=$marker}});
        status=@{code=2;message=$marker};
        events=@(@{timeUnixNano=[string]$end;name=$marker})},
        @{traceId=$trace;spanId=[Guid]::NewGuid().ToString('N').Substring(0,16);
        name=$marker;kind=2;startTimeUnixNano=[string]$start;endTimeUnixNano=[string]$end;
        links=@(@{traceId=$trace;spanId=$span;attributes=@(@{key='email';value=@{stringValue=$marker}})})})})})}
$payloadPath = Join-Path $project ".verification/privacy-$trace.json"
[IO.File]::WriteAllText($payloadPath,($payload | ConvertTo-Json -Depth 30 -Compress),(New-Object Text.UTF8Encoding $false))
$arguments = @('run','--rm','--network','omnichannel-day1_default','-v',"${payloadPath}:/fixture.json:ro",
    'curlimages/curl:8.16.0','--fail','--silent','--show-error','--max-time','15','-H','Content-Type: application/json',
    '--retry','10','--retry-delay','2','--retry-connrefused','--retry-max-time','45',
    '--data-binary','@/fixture.json','http://otel-collector:4318/v1/traces')
try {
    $ErrorActionPreference = 'Continue'
    & docker @arguments *> $null
    $submitExit = $LASTEXITCODE
} finally {$ErrorActionPreference = 'Stop'}
if ($submitExit) {throw 'OTLP fixture submission failed'}
$document = $null
for ($retry=0; $retry -lt 30; $retry++) {
    try {
        $document = Invoke-RestMethod "http://127.0.0.1:13200/api/traces/$trace" -Headers @{Accept='application/json'} -TimeoutSec 5
        break
    } catch {Start-Sleep -Seconds 2}
}
if ($null -eq $document) {throw 'Trace not queryable from Tempo'}
$exported = $document | ConvertTo-Json -Depth 100 -Compress
if ($exported.Contains($marker) -or -not $exported.Contains('privacy-fixture') -or -not $exported.Contains('/api/fixture') -or -not $exported.Contains('commerce.operation')) {
    throw 'Trace privacy allow-list failed or useful routing/service attributes lost'
}
$target = [IO.Path]::GetFullPath((Join-Path $project $EvidenceFile))
if (-not $target.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'Evidence must stay inside project'}
[IO.File]::WriteAllText($target,(@{timeUtc=[DateTime]::UtcNow.ToString('o');traceId=$trace;
    markerExcluded=$true;routeAndServiceRetained=$true;note='Synthetic fixture; no real credentials used'} | ConvertTo-Json),(New-Object Text.UTF8Encoding $false))
Write-Output 'PASS: persisted Tempo trace excludes synthetic credential/email, event, link and error text'
