param(
    [Parameter(Mandatory = $true)][string]$K6Path,
    [Parameter(Mandatory = $true)][string]$FixtureFile,
    [Parameter(Mandatory = $true)][string]$SshKey,
    [string]$BaseUrl = 'https://md05-rikkei-project.duckdns.org',
    [string]$VpsHost = '42.96.2.60',
    [string]$VpsUser = 'deploy',
    [string]$OutputDirectory = '.verification/capacity',
    [ValidateRange(5, 30)][int]$DurationSeconds = 10
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
if ($BaseUrl -notmatch '^https://[a-zA-Z0-9.-]+$' -or $VpsHost -notmatch '^[a-zA-Z0-9.-]+$' -or $VpsUser -notmatch '^[a-zA-Z0-9_-]+$') {
    throw 'Invalid HTTPS/SSH target'
}
$output = [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sampler = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sample-resources.sh'))
$monitor = $null
try {
    $env:BASE_URL = $BaseUrl
    $env:FIXTURE_FILE = (Resolve-Path -LiteralPath $FixtureFile).Path
    $env:DURATION = "${DurationSeconds}s"
    foreach ($rate in @(500, 1000)) {
        $baseline = @($sampler | ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -i $SshKey "$VpsUser@$VpsHost" "tr -d '\r' | bash -s -- 2")
        if ($LASTEXITCODE -ne 0) { throw 'Cannot sample VPS resources' }
        $sample = $baseline | ConvertFrom-Csv | Select-Object -Last 1
        $baseline | Set-Content -Encoding UTF8 (Join-Path $output "baseline-$rate.csv")
        if ([double]$sample.ram_available_mib -lt 256 -or [double]$sample.swap_used_mib -gt 1024 -or [double]$sample.cpu_percent -gt 85) {
            Write-Output "STOP before $rate RPS: VPS has insufficient headroom; this target is unmeasured"
            break
        }
        $env:RATE = [string]$rate
        $env:SUMMARY_FILE = Join-Path $output "capacity-$rate.json"
        $monitor = Start-Job -ScriptBlock {
            param($Script, $Key, $User, $Target, $Seconds)
            $Script | ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -i $Key "$User@$Target" "tr -d '\r' | bash -s -- $Seconds"
            if ($LASTEXITCODE -ne 0) { throw 'Resource sampling failed' }
        } -ArgumentList $sampler, $SshKey, $VpsUser, $VpsHost, $DurationSeconds
        & $K6Path run (Join-Path $root 'tests/k6/catalog-capacity.js')
        $result = $LASTEXITCODE
        $samples = @($monitor | Wait-Job | Receive-Job -ErrorAction Stop)
        Remove-Job $monitor
        $monitor = $null
        $samples | Set-Content -Encoding UTF8 (Join-Path $output "resources-$rate.csv")
        $data = Get-Content -LiteralPath $env:SUMMARY_FILE -Raw | ConvertFrom-Json
        Write-Output "target=$rate actual=$($data.metrics.http_reqs.values.rate) successful=$($data.metrics.successful_requests.values.count) p95=$($data.metrics.http_req_duration.values.'p(95)')ms dropped=$($data.metrics.dropped_iterations.values.count) exit=$result"
        # Preserve threshold failures as measurements. Require recovery before a higher target.
        Start-Sleep -Seconds 10
    }
} finally {
    if ($monitor) {
        if ($monitor.State -eq 'Running') { Stop-Job $monitor }
        Remove-Job $monitor
    }
    Remove-Item Env:BASE_URL, Env:FIXTURE_FILE, Env:DURATION, Env:RATE, Env:SUMMARY_FILE -ErrorAction SilentlyContinue
}
