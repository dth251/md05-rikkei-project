param(
    [Parameter(Mandatory = $true)][string]$DataFile,
    [Parameter(Mandatory = $true)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$template = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot '../../email/invoice-template.html')
$data = Get-Content -Raw -Encoding UTF8 $DataFile | ConvertFrom-Json
$culture = [Globalization.CultureInfo]::GetCultureInfo('vi-VN')
function Escape([string]$Value) { return [Net.WebUtility]::HtmlEncode($Value) }
function Money([decimal]$Value) {
    $format = if ($Value -eq [decimal]::Truncate($Value)) { 'N0' } else { 'N2' }
    return $Value.ToString($format, $culture) + [char]0x20AB
}
$rows = ''
[decimal]$total = 0
foreach ($item in $data.items) {
    [decimal]$quantity = $item.quantity
    [decimal]$price = $item.unitPrice
    if ($quantity -lt 1 -or $quantity -ne [decimal]::Truncate($quantity) -or $price -lt 0.01) {
        throw 'Expected a positive integer quantity and a positive unit price'
    }
    [decimal]$lineTotal = $quantity * $price
    $total += $lineTotal
    $name = Escape $item.name
    $unitPrice = Escape (Money $price)
    $amount = Escape (Money $lineTotal)
    $rows += @"
<tr>
  <td style="padding:20px 10px;border-bottom:1px solid #e8edea;font-size:13px;line-height:1.6;color:#2b4636;word-break:break-word;">$name</td>
  <td align="center" style="padding:20px 0;border-bottom:1px solid #e8edea;font-size:13px;color:#65796b;">$quantity</td>
  <td class="item-price" align="right" style="padding:20px 4px;border-bottom:1px solid #e8edea;font-size:13px;color:#65796b;white-space:nowrap;">$unitPrice</td>
  <td class="item-price" align="right" style="padding:20px 10px 20px 4px;border-bottom:1px solid #e8edea;font-size:13px;font-weight:bold;color:#2b4636;white-space:nowrap;">$amount</td>
</tr>
"@
}
if (@($data.items).Count -eq 0 -or $total -ne [decimal]$data.totalAmount) {
    throw 'Invoice total must match the sum of item quantities and unit prices'
}
foreach ($field in @('orderNumber','orderDate','customerName','customerEmail','paymentMethod','paymentStatus','note')) {
    if ([string]::IsNullOrWhiteSpace([string]$data.$field)) { throw "Missing invoice field: $field" }
    $template = $template.Replace('{{' + $field + '}}', (Escape $data.$field))
}
$template = $template.Replace('{{totalAmount}}', (Escape (Money $total))).Replace('{{itemRows}}', $rows)
if ($template -match '\{\{[A-Za-z]+\}\}') { throw 'Unresolved invoice placeholder' }
$output = [IO.Path]::GetFullPath($OutputFile)
[IO.File]::WriteAllText($output, $template, (New-Object Text.UTF8Encoding($false)))
Write-Output 'Invoice rendered; item sum and total match; all customer text is HTML-escaped.'
