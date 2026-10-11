param(
    [string]$BaseUrl = 'https://md05-rikkei-project.duckdns.org',
    [Parameter(Mandatory = $true)][string]$K6Path,
    [Parameter(Mandatory = $true)][string]$SshKey,
    [string]$VpsHost = '42.96.2.60',
    [string]$VpsUser = 'deploy',
    [string]$OutputDirectory = '.verification/day3/catalog',
    [int[]]$Rates = @(50, 100, 250, 500, 1000),
    [ValidateRange(5, 120)][int]$DurationSeconds = 20
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($BaseUrl -notmatch '^https://[a-zA-Z0-9.-]+$' -or $VpsHost -notmatch '^[a-zA-Z0-9.-]+$' -or $VpsUser -notmatch '^[a-zA-Z0-9_-]+$') {
    throw 'Invalid HTTPS/SSH target'
}
$output = [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sampler = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sample-resources.sh'))
try {
    $env:BASE_URL = $BaseUrl
    $env:DURATION = "${DurationSeconds}s"
    foreach ($rate in $Rates) {
        if ($rate -lt 1 -or $rate -gt 1000) { throw 'Rate must be 1-1000' }
        $baseline = @($sampler | ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -i $SshKey "$VpsUser@$VpsHost" "tr -d '\r' | bash -s -- 2")
        if ($LASTEXITCODE -ne 0) { throw 'Resource observation failed' }
        $sample = ($baseline | ConvertFrom-Csv | Select-Object -Last 1)
        $baseline | Set-Content -Encoding UTF8 (Join-Path $output "baseline-$rate.csv")
        if ([double]$sample.ram_available_mib -lt 64 -or [double]$sample.swap_used_mib -gt 1792 -or [double]$sample.cpu_percent -gt 85) {
            Write-Output "STOP before $rate RPS: resource guard (RAM/CPU/swap); higher targets unmeasured"
            break
        }
        $env:RATE = [string]$rate
        $env:SUMMARY_FILE = Join-Path $output "catalog-$rate.json"
        # Capture resource samples while k6 runs. Read-only SSH; no service/volume mutation.
        $monitor = Start-Job -ScriptBlock {
            param($Script, $Key, $User, $HostName, $Seconds)
            $Script | ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -i $Key "$User@$HostName" "tr -d '\r' | bash -s -- $Seconds"
            if ($LASTEXITCODE -ne 0) { throw 'Resource sampling failed' }
        } -ArgumentList $sampler, $SshKey, $VpsUser, $VpsHost, $DurationSeconds
        & $K6Path run (Join-Path $root 'tests/k6/load-test.js')
        $result = $LASTEXITCODE
        $samples = @($monitor | Wait-Job | Receive-Job -ErrorAction Stop)
        Remove-Job $monitor
        $samples | Set-Content -Encoding UTF8 (Join-Path $output "resources-$rate.csv")
        $data = Get-Content -Raw $env:SUMMARY_FILE | ConvertFrom-Json
        $resources = @($samples | ConvertFrom-Csv)
        $minRam = ($resources | ForEach-Object { [double]$_.ram_available_mib } | Measure-Object -Minimum).Minimum
        $maxCpu = ($resources | ForEach-Object { [double]$_.cpu_percent } | Measure-Object -Maximum).Maximum
        Write-Output "target=$rate actual=$($data.metrics.http_reqs.values.rate) p95=$($data.metrics.http_req_duration.values.'p(95)')ms minRAM=${minRam}MiB maxCPU=$maxCpu% exit=$result"
        if ($result -ne 0 -or $minRam -lt 64 -or $maxCpu -gt 85 -or $data.metrics.dropped_iterations.values.count -gt 0) {
            Write-Output 'STOP increasing load: failed threshold/resource guard/dropped iterations; remaining targets unmeasured'
            break
        }
        Start-Sleep -Seconds 5
    }
} finally {
    if ($monitor -and $monitor.State -eq 'Running') { Stop-Job $monitor; Remove-Job $monitor }
    Remove-Item Env:BASE_URL, Env:DURATION, Env:RATE, Env:SUMMARY_FILE -ErrorAction SilentlyContinue
}
