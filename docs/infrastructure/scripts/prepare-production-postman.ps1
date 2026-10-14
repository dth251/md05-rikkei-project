param(
    [Parameter(Mandatory = $true)][string]$CredentialEnvironment,
    [Parameter(Mandatory = $true)][string]$PrivateDirectory,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._%+-]+@gmail\.com$')]
    [string]$NotificationMailbox
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$privatePath = [IO.Path]::GetFullPath($PrivateDirectory)
if ($privatePath -eq $projectRoot -or $privatePath.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'PrivateDirectory must be outside the Git workspace'
}
if (Test-Path -LiteralPath $privatePath) { throw 'Use a new private directory for each production run' }
$values = @{}
(Get-Content -LiteralPath $CredentialEnvironment -Raw -Encoding UTF8 | ConvertFrom-Json).values | ForEach-Object { $values[$_.key] = $_.value }
if ([string]::IsNullOrWhiteSpace($values.adminEmail) -or [string]::IsNullOrWhiteSpace($values.adminPassword)) {
    throw 'The private credential environment must contain adminEmail and adminPassword'
}
$templatePath = Join-Path $projectRoot 'tests/postman/md05-tieng-viet.production.postman_environment.json'
$environment = Get-Content -LiteralPath $templatePath -Raw -Encoding UTF8 | ConvertFrom-Json
$newValues = @{
    adminEmail = $values.adminEmail
    adminPassword = $values.adminPassword
    notificationMailbox = $NotificationMailbox.ToLowerInvariant()
    password = 'Prod!' + [guid]::NewGuid().ToString()
    passwordB = 'Prod!' + [guid]::NewGuid().ToString()
}
foreach ($entry in $environment.values) {
    if ($newValues.ContainsKey($entry.key)) { $entry.value = $newValues[$entry.key] }
}
New-Item -ItemType Directory -Path $privatePath | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $privatePath /inheritance:r /grant:r ('{0}:(OI)(CI)F' -f $identity) | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Failed to restrict access to the private directory' }
$outputPath = Join-Path $privatePath 'md05-production.private.postman_environment.json'
$json = $environment | ConvertTo-Json -Depth 6
[IO.File]::WriteAllText($outputPath, $json + [Environment]::NewLine, (New-Object Text.UTF8Encoding($false)))
Write-Output "Private production environment prepared: $outputPath"
Write-Output 'Import it with the Vietnamese collection. Credentials were not printed.'
