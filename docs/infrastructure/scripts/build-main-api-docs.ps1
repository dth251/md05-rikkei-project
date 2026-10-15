$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$contract = Get-Content (Join-Path $root 'docs/openapi/api-gateway.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$source = Get-Content (Join-Path $root 'tests/postman/md05-tat-ca-api.postman_collection.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$requests = [Collections.Generic.List[object]]::new()
function Read-Requests($Items) {
    foreach ($item in $Items) {
        if ($item.request) { $requests.Add($item.request) }
        if ($item.item) { Read-Requests $item.item }
    }
}
Read-Requests $source.item
function Normalize-Path([string]$Value) {
    $value = ($Value -split '\?', 2)[0] -replace '^\{\{baseUrl\}\}', '' -replace '^https?://[^/]+', ''
    $value = $value -replace '\{\{[^}]+\}\}', '{id}' -replace '\{[^}]+\}', '{id}'
    return $value.TrimEnd('/')
}
$definitions = @'
01 - Tài khoản|POST|/api/auth/register|Đăng ký tài khoản
01 - Tài khoản|POST|/api/auth/login|Đăng nhập USER hoặc ADMIN
01 - Tài khoản|GET|/api/auth/me|Xem tài khoản hiện tại
01 - Tài khoản|POST|/api/auth/refresh|Làm mới token
01 - Tài khoản|POST|/api/auth/logout|Đăng xuất phiên
02 - Địa chỉ|GET|/api/auth/addresses|Danh sách địa chỉ
02 - Địa chỉ|POST|/api/auth/addresses|Thêm địa chỉ
02 - Địa chỉ|GET|/api/auth/addresses/{id}|Chi tiết địa chỉ
02 - Địa chỉ|PUT|/api/auth/addresses/{id}|Cập nhật địa chỉ
02 - Địa chỉ|DELETE|/api/auth/addresses/{id}|Xóa địa chỉ
03 - Sản phẩm|GET|/api/products|Danh sách và tìm kiếm sản phẩm
03 - Sản phẩm|POST|/api/products|Tạo sản phẩm
03 - Sản phẩm|GET|/api/products/{id}|Chi tiết sản phẩm
03 - Sản phẩm|PUT|/api/products/{id}|Cập nhật sản phẩm
03 - Sản phẩm|DELETE|/api/products/{id}|Ngừng bán sản phẩm
03 - Sản phẩm|GET|/api/products/{parentId}/variants|Danh sách biến thể
03 - Sản phẩm|POST|/api/products/{parentId}/variants|Tạo biến thể
04 - Kho|POST|/api/inventory/stocks|Nhập kho
04 - Kho|GET|/api/inventory/{productId}|Xem tồn kho
05 - Đơn hàng|POST|/api/orders|Tạo đơn ONLINE hoặc COD - WEB hoặc POS
05 - Đơn hàng|GET|/api/orders/{id}|Chi tiết đơn hàng
05 - Đơn hàng|POST|/api/orders/{id}/ship|Giao hàng
05 - Đơn hàng|POST|/api/orders/{id}/complete|Hoàn tất đơn
05 - Đơn hàng|POST|/api/orders/{id}/cod/cancel|Hủy COD trước giao
06 - Thanh toán|GET|/api/payments/by-order/{orderId}|Tra thanh toán theo đơn
06 - Thanh toán|POST|/api/payments/{id}/vnpay/checkout|Tạo link VNPay sandbox
06 - Thanh toán|POST|/api/payments/{id}/cod/collect|Ghi nhận đã thu tiền COD
07 - Trả hàng|POST|/api/orders/{id}/return|Yêu cầu trả toàn bộ đơn
07 - Trả hàng|GET|/api/orders/{id}/return|Xem hồ sơ trả hàng
07 - Trả hàng|POST|/api/orders/{id}/return/decision|Duyệt hoặc từ chối trả hàng
07 - Trả hàng|POST|/api/orders/{id}/return/receive|Nhận hàng trả và nhập lại kho
08 - Hoàn tiền|POST|/api/payments/{id}/cod/refund|Ghi nhận hoàn tiền COD
08 - Hoàn tiền|GET|/api/payments/{id}/cod/refund|Xem biên nhận hoàn COD
08 - Hoàn tiền|POST|/api/payments/{id}/vnpay/refund|Yêu cầu hoàn VNPay
08 - Hoàn tiền|POST|/api/payments/{id}/vnpay/reconcile|Đối soát kết quả hoàn VNPay
'@ -split '\r?\n'
$folders = [ordered]@{}
$paths = [ordered]@{}
foreach ($definition in $definitions) {
    $group, $method, $path, $name = $definition -split '\|'
    $operation = $contract.paths.PSObject.Properties[$path].Value.PSObject.Properties[$method.ToLowerInvariant()].Value
    if (-not $operation) { throw "Operation missing: $method $path" }
    $candidate = $requests | Where-Object {
        $url = if ($_.url -is [string]) { $_.url } else { $_.url.raw }
        $_.method -eq $method -and (Normalize-Path $url) -eq (Normalize-Path $path)
    } | Select-Object -First 1
    if (-not $candidate) { throw "Request example missing: $method $path" }
    $request = $candidate | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $variablePath = $path
    foreach ($parameter in [regex]::Matches($path, '\{([^}]+)\}')) {
        $key = $parameter.Groups[1].Value
        if ($key -eq 'id') {
            $key = if ($path.StartsWith('/api/auth/addresses')) { 'addressId' }
                elseif ($path.StartsWith('/api/products')) { 'productId' }
                elseif ($path.StartsWith('/api/orders')) { 'orderId' } else { 'paymentId' }
        }
        $variablePath = $variablePath.Replace($parameter.Value, '{{' + $key + '}}')
    }
    $request.url = '{{baseUrl}}' + $variablePath
    $request.auth = if ($operation.'x-required-role' -eq 'PUBLIC') { @{ type = 'noauth' } }
        else { @{ type = 'bearer'; bearer = @(@{ key = 'token'; value = '{{token}}'; type = 'string' }) } }
    $description = "Quyền: $($operation.'x-required-role'). Nhập ID thực tế ở collection Variables. Đăng nhập bằng tài khoản phù hợp trước khi Send; token của collection này được thay sau mỗi lần đăng nhập."
    if ($path -eq '/api/auth/login') {
        $request.body.raw = '{"email":"","password":""}'
    } elseif ($path -eq '/api/auth/register') {
        $request.body.raw = '{"fullName":"Khách hàng demo","email":"","password":""}'
    } elseif ($request.body -and $request.body.mode -eq 'raw') {
        $numericVariables = @([regex]::Matches($request.body.raw, ':\s*(\{\{[^}]+\}\})') | ForEach-Object { $_.Groups[1].Value })
        $parseable = [regex]::Replace($request.body.raw, '(:\s*)(\{\{[^}]+\}\})', '$1"$2"')
        $body = $parseable | ConvertFrom-Json
        if ($body.PSObject.Properties['idempotencyKey']) { $body.idempotencyKey = '{{$guid}}' }
        if ($body.PSObject.Properties['receiptNumber']) { $body.receiptNumber = 'DEMO-{{$guid}}' }
        if ($body.PSObject.Properties['warehouseReceipt']) { $body.warehouseReceipt = 'RETURN-{{$guid}}' }
        if ($path -eq '/api/orders') { $body.paymentMethod = 'ONLINE'; $body.items[0].quantity = 1 }
        if ($path -eq '/api/products' -and $method -eq 'POST') { $body.sku = 'SKU-{{$guid}}' }
        if ($path -eq '/api/products/{id}' -and $method -eq 'PUT') { $body.sku = '{{productSku}}' }
        if ($path.EndsWith('/variants') -and $method -eq 'POST') { $body.product.sku = 'VARIANT-{{$guid}}' }
        if ($body.PSObject.Properties['amount']) { $body.amount = '{{amount}}' }
        $request.body.raw = $body | ConvertTo-Json -Depth 30
        foreach ($variable in $numericVariables) { $request.body.raw = $request.body.raw.Replace('"' + $variable + '"', $variable) }
        $request.body.raw = $request.body.raw.Replace('"{{amount}}"', '{{amount}}').Replace('{{onlineOrderId}}', '{{orderId}}')
    }
    if ($path -eq '/api/orders' -and $method -eq 'POST') {
        $description += ' ONLINE giữ hàng 200 giây. Đổi paymentMethod thành COD để thử COD; COD cần địa chỉ giao hàng đầy đủ.'
    }
    if ($path.EndsWith('/vnpay/refund')) {
        $description += ' Chỉ gọi khi đủ điều kiện hoàn. Nếu kết quả chưa rõ, dùng reconcile; không liên tục gửi lại yêu cầu refund.'
    }
    $request.description = $description
    if (-not $folders.Contains($group)) { $folders[$group] = [Collections.Generic.List[object]]::new() }
    $folders[$group].Add(@{ name = "$method - $name"; request = $request; response = @() })
    $operation | Add-Member -NotePropertyName summary -NotePropertyValue $name -Force
    $operation | Add-Member -NotePropertyName tags -NotePropertyValue @($group) -Force
    $existingDescription = [string]$operation.description
    $operation | Add-Member -NotePropertyName description -NotePropertyValue ($description + "`n" + $existingDescription) -Force
    if ($request.body -and $request.body.mode -eq 'raw' -and $operation.requestBody.content.'application/json') {
        $example = $request.body.raw -replace '\{\{\$guid\}\}', 'demo-unique-key' -replace '"\{\{[^}]+\}\}"', '"example"' -replace '\{\{[^}]+\}\}', '1'
        $operation.requestBody.content.'application/json' | Add-Member -NotePropertyName example -NotePropertyValue ($example | ConvertFrom-Json) -Force
    }
    if (-not $paths.Contains($path)) { $paths[$path] = [ordered]@{} }
    $paths[$path][$method.ToLowerInvariant()] = $operation
}
$hook = @'
if (pm.response.code >= 200 && pm.response.code < 300 && pm.response.code !== 204) {
  const data = pm.response.json().data;
  const url = pm.request.url.toString().split('?')[0];
  if (data) {
    if (data.accessToken) pm.collectionVariables.set('token', data.accessToken);
    if (data.refreshToken) pm.collectionVariables.set('refreshToken', data.refreshToken);
    if (data.id && pm.request.method === 'POST') {
      if (/\/api\/products$/.test(url)) {
        pm.collectionVariables.set('productId', data.id);
        pm.collectionVariables.set('parentId', data.id);
        pm.collectionVariables.set('productSku', data.sku);
      }
      if (/\/api\/orders$/.test(url)) pm.collectionVariables.set('orderId', data.id);
      if (/\/api\/auth\/addresses$/.test(url)) pm.collectionVariables.set('addressId', data.id);
    }
    if (data.id && /\/api\/payments\/by-order\//.test(url)) {
      pm.collectionVariables.set('paymentId', data.id);
      pm.collectionVariables.set('amount', data.amount);
    }
    if (data.checkoutUrl) pm.collectionVariables.set('checkoutUrl', data.checkoutUrl);
  }
}
'@ -split '\r?\n'
$collection = [ordered]@{
    info = @{ _postman_id = '69f408aa-a11e-4df6-b6e6-a4a1b7c75035'; name = 'MD05 - API chính - mỗi thao tác một request'; schema = 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'; description = '35 API nghiệp vụ, không có test âm, polling hoặc callback kỹ thuật. Chọn No environment. Nhập tài khoản trực tiếp trong Body đăng nhập; đăng nhập lại khi đổi USER/ADMIN. Đây là danh mục thao tác thủ công, không Run toàn bộ vì có các bước xóa/hủy/hoàn tiền và nhiều nhánh nghiệp vụ.' }
    variable = @(@{key='baseUrl';value='https://md05-rikkei-project.duckdns.org'}) + @('token','refreshToken','productId','parentId','productSku','orderId','paymentId','addressId','amount' | ForEach-Object { @{key=$_;value=''} })
    event = @(@{listen='test';script=@{type='text/javascript';exec=$hook}})
    item = @($folders.Keys | ForEach-Object { @{name=$_;item=@($folders[$_].ToArray())} })
}
$contract.info.title = 'MD05 - API nghiệp vụ chính'
$contract.info | Add-Member -NotePropertyName description -NotePropertyValue '35 thao tác chính. Dùng POST /api/auth/login để lấy data.accessToken, sau đó bấm Authorize và dán token. Đăng nhập lại khi chuyển USER/ADMIN. Đơn ONLINE giữ hàng 200 giây. Các thao tác ghi có tác động thật đến dữ liệu demo; gửi từng bước theo nghiệp vụ.' -Force
$contract.paths = [pscustomobject]$paths
$contract.servers = @(@{url='/';description='Cùng máy chủ Swagger UI'})
$contract | Add-Member -NotePropertyName tags -NotePropertyValue @($folders.Keys | ForEach-Object { @{name=$_} }) -Force
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $root 'tests/postman/md05-api-chinh.postman_collection.json'), ($collection | ConvertTo-Json -Depth 100) + "`n", $utf8)
[IO.File]::WriteAllText((Join-Path $root 'docs/openapi/api-chinh.json'), ($contract | ConvertTo-Json -Depth 100) + "`n", $utf8)
Write-Output "Generated $($definitions.Count) main API requests and Swagger contract; existing collections unchanged."
