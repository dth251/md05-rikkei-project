$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$source = Get-Content (Join-Path $root 'tests/postman/md05-tat-ca-api.postman_collection.json') -Raw -Encoding UTF8 | ConvertFrom-Json

function Copy-Request([int]$Folder, [int]$Index, [string]$Name) {
    $request = $source.item[$Folder].item[$Index] | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $request.name = $Name
    if ($request.request.body) {
        $request.request.body.raw = $request.request.body.raw.Replace('Khách hàng demo A', 'Khách hàng demo')
    }
    return $request
}
function Get-Hook($Request, [string]$Listen) {
    return $Request.event | Where-Object listen -eq $Listen
}

$register = Copy-Request 0 0 'Đăng ký khách hàng và bắt đầu lượt demo'
$register.request.description = 'Tạo một khách hàng mới cho lượt demo; không tạo User B. ID và JWT được lưu tự động.'
$init = Get-Hook $register 'prerequest'
$init.script.exec = @($init.script.exec | Where-Object { $_ -notmatch "environment.set\('emailB'|environment.get\('passwordB'" } | ForEach-Object {
    $_.Replace("'+md05-a-'", "'+md05-main-'").Replace('postman-vi-a-', 'postman-main-')
})

$stock = Copy-Request 4 5 'Xem tồn kho của sản phẩm'
(Get-Hook $stock 'test').script.exec += "if (pm.response.code === 200) pm.environment.set('codBaseline', pm.response.json().data.available);"

$cod = Copy-Request 7 1 'Khách hàng đặt đơn COD'
$codInit = Get-Hook $cod 'prerequest'
$codInit.script.exec = @("pm.environment.set('collectKey', 'main-'+pm.variables.replaceIn('{{`$randomUUID}}'));") + @($codInit.script.exec)
$return = Copy-Request 9 0 'Khách hàng yêu cầu trả toàn bộ đơn COD'
$returnInit = Get-Hook $return 'prerequest'
$returnInit.script.exec = @("pm.environment.set('refundKey', 'main-'+pm.variables.replaceIn('{{`$randomUUID}}'));") + @($returnInit.script.exec)

$checkout = Copy-Request 14 3 'Tạo link thanh toán VNPay sandbox và dừng Runner'
$checkout.request.description = 'Mở biến vnpayCheckoutUrl trên trình duyệt, thanh toán bằng thẻ test. Sau khi quay về, chọn riêng nhóm 07 và 08; không chạy lại nhóm 01 khởi tạo lượt mới.'
(Get-Hook $checkout 'test').script.exec = @((Get-Hook $checkout 'test').script.exec | ForEach-Object { $_.Replace('allInOneAwaitingVnpay', 'mainAwaitingVnpay') })

$folders = @(
    @{ name = '01 - Tài khoản khách hàng'; item = @(
        $register
        (Copy-Request 0 5 'Đăng nhập khách hàng')
        (Copy-Request 0 10 'Xem thông tin tài khoản')
    ) }
    @{ name = '02 - ADMIN chuẩn bị sản phẩm và kho'; item = @(
        (Copy-Request 3 0 'Đăng nhập ADMIN')
        (Copy-Request 3 3 'Tạo sản phẩm demo')
        (Copy-Request 3 2 'Xem danh sách sản phẩm')
        (Copy-Request 3 7 'Xem chi tiết sản phẩm')
        (Copy-Request 4 0 'Nhập 20 sản phẩm vào kho')
        $stock
    ) }
    @{ name = '03 - COD đặt hàng giao hàng và thu tiền'; item = @(
        $cod
        (Copy-Request 7 3 'Chờ tạo thanh toán COD')
        (Copy-Request 7 5 'ADMIN giao đơn COD')
        (Copy-Request 7 9 'ADMIN ghi nhận thu đủ tiền COD')
        (Copy-Request 7 12 'Chờ đơn ghi nhận đã thu tiền')
        (Copy-Request 7 13 'Hoàn tất đơn COD')
    ) }
    @{ name = '04 - Trả hàng và hoàn tiền COD'; item = @(
        $return
        (Copy-Request 9 5 'ADMIN duyệt trả hàng COD')
        (Copy-Request 9 6 'ADMIN nhận hàng trả và nhập lại kho')
        (Copy-Request 9 9 'Chờ thanh toán đủ điều kiện hoàn tiền')
        (Copy-Request 9 12 'ADMIN ghi nhận hoàn tiền COD')
        (Copy-Request 9 15 'Chờ hồ sơ trả hàng REFUNDED')
        (Copy-Request 9 18 'Kiểm tra kho sau trả hàng COD')
    ) }
    @{ name = '05 - Chờ thanh toán 150 giây rồi hết hạn'; item = @(
        (Copy-Request 10 0 'Đọc tồn kho trước khi đặt đơn chờ thanh toán')
        (Copy-Request 10 1 'Tạo đơn ONLINE để chờ hết hạn')
        (Copy-Request 10 2 'Chờ tạo thanh toán của đơn quá hạn')
        (Copy-Request 10 4 'Chờ đủ 150 giây và kiểm tra đơn EXPIRED')
        (Copy-Request 10 5 'Chờ Payment ghi nhận hết hạn')
        (Copy-Request 10 7 'Kiểm tra đã giải phóng hàng giữ chỗ')
    ) }
    @{ name = '06 - Đặt đơn mới và tạo link VNPay sandbox'; item = @(
        (Copy-Request 14 1 'Khách hàng đặt đơn thanh toán VNPay')
        (Copy-Request 14 2 'Lấy Payment của đúng đơn VNPay')
        $checkout
    ) }
    @{ name = '07 - VNPay thành công giao hàng và hoàn tất'; item = @(
        (Copy-Request 15 0 'Chờ IPN cập nhật thanh toán SUCCESS')
        (Copy-Request 15 1 'Chờ đơn tự xác nhận CONFIRMED')
        (Copy-Request 16 0 'ADMIN giao đơn VNPay')
        (Copy-Request 16 1 'Hoàn tất đơn VNPay')
    ) }
    @{ name = '08 - Trả hàng và hoàn tiền VNPay sandbox'; item = @(
        (Copy-Request 16 2 'Khách hàng yêu cầu trả đơn VNPay')
        (Copy-Request 16 3 'ADMIN duyệt trả đơn VNPay')
        (Copy-Request 16 4 'ADMIN nhận lại hàng VNPay và nhập kho')
        (Copy-Request 16 5 'Chờ thanh toán đủ điều kiện hoàn VNPay')
        (Copy-Request 16 6 'Yêu cầu hoàn tiền trên VNPay sandbox')
        (Copy-Request 17 1 'Chờ Payment được hoàn tiền')
        (Copy-Request 17 2 'Chờ hồ sơ trả hàng VNPay REFUNDED')
    ) }
)
$source.info.name = 'MD05 - Các luồng nghiệp vụ chính - Tiếng Việt'
$source.info.description = '42 requests, 8 luồng chính: một khách hàng và ADMIN; sản phẩm/kho; COD; trả/hoàn COD; chờ thanh toán 150 giây; VNPay sandbox; giao/hoàn tất; trả/hoàn VNPay. Dùng environment production private. Chạy 01–06 rồi mở vnpayCheckoutUrl; sau thanh toán chạy riêng 07–08. Không tạo User B, không có bài JWT sai/phân quyền/replay. Xem docs/test-luong-chinh.md.'
$source.item = $folders
$target = Join-Path $root 'tests/postman/md05-luong-chinh.postman_collection.json'
[IO.File]::WriteAllText($target, ($source | ConvertTo-Json -Depth 100) + "`n", [Text.UTF8Encoding]::new($false))
Write-Output 'Built main business flows: 42 requests / 8 folders / customer and ADMIN.'
