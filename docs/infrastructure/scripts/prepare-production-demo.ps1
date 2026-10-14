param(
    [Parameter(Mandatory = $true)][string]$PrivateDirectory,
    [Parameter(Mandatory = $true)][string]$SshKey,
    [ValidateSet('prepare', 'verify', 'capacity')][string]$Action = 'prepare'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$privatePath = [IO.Path]::GetFullPath($PrivateDirectory)
if ($privatePath.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $privatePath -eq $projectRoot) {
    throw 'Credential directory must be outside the Git workspace'
}
$sshOptions = @('-i', (Resolve-Path -LiteralPath $SshKey).Path, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10')
$target = 'deploy@42.96.2.60'
$savedBase = $env:DEMO_BASE_URL
$savedDirectory = $env:DEMO_PRIVATE_DIR
try {
    $preflight = @'
set -euo pipefail
for service in auth-service api-gateway product-service order-service payment-service notification-service; do
  state=$(docker inspect -f '{{.State.Health.Status}}' "omnichannel-day1-${service}-1")
  test "$state" = healthy || { echo "Unhealthy project service: $service" >&2; exit 1; }
done
test "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_HOST)" = mailpit
test "$(docker exec omnichannel-day1-notification-service-1 printenv SMTP_ENABLED)" = true
echo 'PASS: six project services healthy; SMTP is the Mailpit sandbox'
'@
    $preflight | ssh @sshOptions $target 'tr -d ''\r'' | bash -s'
    if ($LASTEXITCODE -ne 0) { throw 'Production demo preflight failed' }
    $env:DEMO_BASE_URL = 'https://md05-rikkei-project.duckdns.org'
    $env:DEMO_PRIVATE_DIR = $privatePath
    $wrapper = Join-Path $projectRoot 'order-service/gradlew.bat'
    $gradleArguments = @('-p', (Join-Path $projectRoot 'order-service'), 'productionDemoFixture', '--no-daemon', '--max-workers=1', '-Dorg.gradle.jvmargs=-Xmx256m -XX:MaxMetaspaceSize=256m')
    if ($Action -eq 'prepare') {
        & $wrapper @gradleArguments '-PfixtureAction=accounts'
        if ($LASTEXITCODE -ne 0) { throw 'Fixture account preparation failed; private progress retained' }
        $fixturePath = Join-Path $privatePath 'fixture.private.json'
        $fixture = Get-Content -LiteralPath $fixturePath -Raw -Encoding UTF8 | ConvertFrom-Json
        $adminId = [long]$fixture.accounts.admin.id
        $adminEmail = [string]$fixture.accounts.admin.email
        if ($adminId -le 0 -or $adminEmail -cnotmatch '^demo-[a-f0-9-]{36}-admin@example\.com$') {
            throw 'Expected the isolated admin created by this fixture'
        }
        $grant = @'
set -euo pipefail
docker exec -i omnichannel-day1-postgres-1 psql -U postgres -d auth_db -v ON_ERROR_STOP=1 <<'SQL'
DO $$
DECLARE affected integer;
BEGIN
  UPDATE users SET role='ADMIN'
  WHERE id=__ID__ AND email='__EMAIL__' AND full_name='MD05 Demo admin' AND role IN ('USER','ADMIN');
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 1 THEN RAISE EXCEPTION 'Isolated demo ADMIN grant did not match one account'; END IF;
END $$;
SQL
'@
        $grant = $grant.Replace('__ID__', [string]$adminId).Replace('__EMAIL__', $adminEmail)
        $grant | ssh @sshOptions $target 'tr -d ''\r'' | bash -s'
        if ($LASTEXITCODE -ne 0) { throw 'Scoped demo ADMIN grant failed' }
        $fixture = $null
        & $wrapper @gradleArguments '-PfixtureAction=seed'
    } else {
        & $wrapper @gradleArguments "-PfixtureAction=$Action"
    }
    if ($LASTEXITCODE -ne 0) { throw 'Fixture API verification failed; data and private progress retained' }
    if ($Action -eq 'capacity') {
        Write-Output "PASS: private catalog fixture $(Join-Path $privatePath 'capacity-50.private.json')"
    } else {
        Write-Output "PASS: import $(Join-Path $privatePath 'production-demo.postman_environment.json') into Postman"
    }
} finally {
    $env:DEMO_BASE_URL = $savedBase
    $env:DEMO_PRIVATE_DIR = $savedDirectory
}
