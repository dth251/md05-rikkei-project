param(
    [string]$Target = 'docs/openapi'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$targetPath = [IO.Path]::GetFullPath((Join-Path $projectRoot $Target))
if (-not $targetPath.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OpenAPI export must remain inside the project'
}
$contracts = @()
foreach ($service in @('auth-service', 'product-service', 'order-service', 'payment-service')) {
    $source = Join-Path $projectRoot "$service/build/openapi/$service.json"
    if (-not (Test-Path -LiteralPath $source)) { throw "Run tested OpenAPI export first: $service" }
    $contract = Get-Content -LiteralPath $source -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($contract.info.title -ne $service -or $contract.openapi -notmatch '^3\.') {
        throw "Invalid tested OpenAPI contract: $service"
    }
    $references = [regex]::Matches([IO.File]::ReadAllText($source), '"\$ref"\s*:\s*"#/components/schemas/([^"]+)"')
    if ($references.Count -eq 0) { throw "No controller schema references found: $service" }
    foreach ($reference in $references) {
        if ($null -eq $contract.components.schemas.PSObject.Properties[$reference.Groups[1].Value]) {
            throw "Missing schema reference in $service"
        }
    }
    $contracts += @{ Service = $service; Document = $contract; Source = $source }
}
$paths = [ordered]@{}
$schemas = [ordered]@{}
foreach ($entry in $contracts) {
    foreach ($property in $entry.Document.paths.PSObject.Properties) {
        if ($paths.Contains($property.Name)) { throw "Conflicting API path: $($property.Name)" }
        foreach ($operation in $property.Value.PSObject.Properties) {
            if ($operation.Name -in @('get', 'put', 'post', 'delete', 'patch', 'options', 'head', 'trace')) {
                if ($operation.Value.operationId) {
                    $operation.Value.operationId = $entry.Service + '_' + $operation.Value.operationId
                }
                $operation.Value | Add-Member -NotePropertyName 'x-service' -NotePropertyValue $entry.Service -Force
            }
        }
        $paths[$property.Name] = $property.Value
    }
    foreach ($property in $entry.Document.components.schemas.PSObject.Properties) {
        if ($schemas.Contains($property.Name)) {
            $existing = $schemas[$property.Name] | ConvertTo-Json -Depth 100 -Compress
            $incoming = $property.Value | ConvertTo-Json -Depth 100 -Compress
            if ($existing -ne $incoming) { throw "Conflicting schema: $($property.Name)" }
        } else {
            $schemas[$property.Name] = $property.Value
        }
    }
}
New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
$utf8 = New-Object Text.UTF8Encoding $false
foreach ($entry in $contracts) {
    [IO.File]::WriteAllText((Join-Path $targetPath ($entry.Service + '.json')),
        ([IO.File]::ReadAllText($entry.Source).TrimEnd() + "`n"), $utf8)
}
$gateway = [ordered]@{
    openapi = $contracts[0].Document.openapi
    info = @{ title = 'Omnichannel API Gateway'; version = '1.0' }
    servers = @(@{ url = 'https://md05-rikkei-project.duckdns.org' })
    security = @(@{ bearerAuth = @() })
    paths = $paths
    components = @{ schemas = $schemas; securitySchemes = $contracts[0].Document.components.securitySchemes }
}
[IO.File]::WriteAllText((Join-Path $targetPath 'api-gateway.json'),
    (($gateway | ConvertTo-Json -Depth 100) + "`n"), $utf8)
Write-Output 'Exported four tested service contracts and the Gateway aggregate'
