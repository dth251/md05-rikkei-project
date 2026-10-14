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
$register.request.description = 'Chọn No environment. Nhập fullName, email nhận thư và password trực tiếp trong Body. Dùng email chưa đăng ký. Nếu đã có tài khoản, bỏ qua request này và bắt đầu ở Đăng nhập khách hàng.'
$register.request.body.raw = @'
{
  "fullName": "Khách hàng demo",
  "email": "",
  "password": ""
}
'@
$init = Get-Hook $register 'prerequest'
$init.script.exec = @'
let payload;
try { payload = JSON.parse(pm.request.body.raw); }
catch (_) { pm.execution.setNextRequest(null); throw new Error('Body phải là JSON hợp lệ.'); }
if (!String(payload.fullName || '').trim() || !String(payload.email || '').trim() || !String(payload.password || '').trim()) {
    pm.execution.setNextRequest(null);
    throw new Error('Nhập fullName, email và password trực tiếp trong Body của request đăng ký.');
}
for (const key of Object.keys(pm.collectionVariables.toObject())) {
    if (key !== 'baseUrl') pm.collectionVariables.unset(key);
}
pm.collectionVariables.set('runId', pm.variables.replaceIn('{{$randomUUID}}'));
pm.collectionVariables.set('email', payload.email.trim());
'@ -split '\r?\n'

function Set-ManualLogin($Request, [string]$Role) {
    $Request.request.description = "Nhập email và password của $Role trực tiếp trong Body rồi Send. Token tự lưu vào collection, không cần environment. Không tự điền mật khẩu từ request đăng ký."
    $Request.request.body.raw = @'
{
  "email": "",
  "password": ""
}
'@
    (Get-Hook $Request 'prerequest').script.exec = @'
let payload;
try { payload = JSON.parse(pm.request.body.raw); }
catch (_) { pm.execution.setNextRequest(null); throw new Error('Body phải là JSON hợp lệ.'); }
if (!String(payload.email || '').trim() || !String(payload.password || '').trim()) {
    pm.execution.setNextRequest(null);
    throw new Error('Nhập email và password trực tiếp trong Body của request đăng nhập.');
}
if (!pm.collectionVariables.get('runId')) {
    pm.collectionVariables.set('runId', pm.variables.replaceIn('{{$randomUUID}}'));
}
'@ -split '\r?\n'
    (Get-Hook $Request 'test').script.exec += "if (pm.response.code === 200 && pm.response.json().success) pm.collectionVariables.set('$($Role.ToLower())Email', pm.response.json().data.user.email);"
}
$userLogin = Copy-Request 0 5 'Đăng nhập khách hàng'
Set-ManualLogin $userLogin 'USER'
# Giữ tên biến email để đối chiếu thông báo cho khách hàng đang đăng nhập.
(Get-Hook $userLogin 'test').script.exec += "if (pm.response.code === 200 && pm.response.json().success) pm.collectionVariables.set('email', pm.response.json().data.user.email);"
$adminLogin = Copy-Request 3 0 'Đăng nhập ADMIN'
Set-ManualLogin $adminLogin 'ADMIN'

$stock = Copy-Request 4 5 'Xem tồn kho của sản phẩm'
$stock.request.auth.bearer[0].value = '{{adminToken}}'
$stock.request.description = 'ADMIN xem tồn kho bằng adminToken. Có thể thay {{productId}} trên URL bằng ID thật, ví dụ /api/inventory/159; không cần có biến productId để đọc thủ công. Giữ {{productId}} để dùng ID tự lưu từ bước Tạo sản phẩm demo.'
(Get-Hook $stock 'prerequest').script.exec = @((Get-Hook $stock 'prerequest').script.exec | ForEach-Object { $_.Replace('userToken', 'adminToken') })
(Get-Hook $stock 'test').script.exec = @'
function kiemTra(ten, ham) {
    pm.test(ten, () => {
        try { ham(); } catch (loi) { pm.execution.setNextRequest(null); throw loi; }
    });
}
kiemTra('Mã HTTP đúng 200', () => pm.expect(pm.response.code).to.eql(200));
kiemTra('Phản hồi đi qua API Gateway', () => pm.expect(pm.response.headers.get('X-Gateway')).to.eql('api-gateway'));
if (pm.response.code === 200) {
    const json = pm.response.json();
    kiemTra('Cờ success đúng kết quả', () => pm.expect(json.success).to.eql(true));
    const d = json.data;
    const id = pm.variables.replaceIn(pm.request.url.toString()).match(/\/api\/inventory\/([1-9]\d*)\/?(?:[?#].*)?$/);
    kiemTra('Tồn kho thuộc đúng sản phẩm trên URL', () => pm.expect(String(d.productId)).to.eql(id && id[1]));
    kiemTra('Kho khả dụng và giữ chỗ là số nguyên không âm', () => {
        pm.expect(Number.isInteger(d.available) && d.available >= 0).to.eql(true);
        pm.expect(Number.isInteger(d.reserved) && d.reserved >= 0).to.eql(true);
    });
    if (String(d.productId) === String(pm.collectionVariables.get('productId'))) {
        pm.collectionVariables.set('codBaseline', d.available);
    }
}
'@ -split '\r?\n'

$productDetail = Copy-Request 3 7 'Xem chi tiết sản phẩm'
$productDetail.request.description = 'Thay {{productId}} trên URL bằng ID sản phẩm muốn xem, hoặc giữ biến để đọc sản phẩm vừa tạo. Không cần environment.'
(Get-Hook $productDetail 'test').script.exec = @((Get-Hook $productDetail 'test').script.exec | ForEach-Object {
    if ($_ -match 'SKU chuẩn hóa khớp lượt chạy') {
        "const id = pm.variables.replaceIn(pm.request.url.toString()).match(/\/api\/products\/([1-9]\d*)\/?(?:[?#].*)?`$/);"
        "kiemTra('Chi tiết thuộc đúng sản phẩm trên URL', () => pm.expect(String(d.id)).to.eql(id && id[1]));"
    } else { $_ }
})

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
        $userLogin
        (Copy-Request 0 10 'Xem thông tin tài khoản')
    ) }
    @{ name = '02 - ADMIN chuẩn bị sản phẩm và kho'; item = @(
        $adminLogin
        (Copy-Request 3 3 'Tạo sản phẩm demo')
        (Copy-Request 3 2 'Xem danh sách sản phẩm')
        $productDetail
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
$source.info.description = '42 requests, 8 luồng chính; chọn No environment. URL production có sẵn. Nhập email/password trực tiếp trong Body của Đăng ký/Đăng nhập khách hàng và Đăng nhập ADMIN. Nếu đã có USER, bỏ qua đăng ký. Token và ID tự lưu vào Variables của collection; không lưu mật khẩu vào biến. Sản phẩm/kho; COD; trả/hoàn COD; chờ 150 giây; VNPay sandbox; giao/hoàn tất; trả/hoàn VNPay. Chạy 01–06 rồi mở vnpayCheckoutUrl trong collection Variables; sau thanh toán chạy riêng 07–08. Xem docs/test-luong-chinh.md.'
$source.variable = @(@{ key = 'baseUrl'; value = 'https://md05-rikkei-project.duckdns.org'; type = 'string' })
$source.event[0].script.exec = @("if (!pm.collectionVariables.get('baseUrl')) { pm.execution.setNextRequest(null); throw new Error('Điền baseUrl trong Variables của collection. URL production đã có sẵn khi import.'); }")
$source.item = $folders
$missingVariables = @'
if (thieu.length) {
    pm.execution.setNextRequest(null);
    const huongDan = thieu.includes('userToken')
        ? 'Mở 01 → Đăng nhập khách hàng trong CHÍNH collection này, nhập Body rồi Send. Đăng nhập ADMIN không tạo userToken.'
        : thieu.includes('adminToken')
            ? 'Mở 02 → Đăng nhập ADMIN trong CHÍNH collection này, nhập Body rồi Send.'
            : thieu.includes('productId')
                ? 'Nhập ID thật thay {{productId}} trên URL để xem sản phẩm/kho, hoặc chạy 02 → Tạo sản phẩm demo để tự lưu ID.'
                : 'Chạy request tạo dữ liệu trước; token và ID nằm ở collection → Variables.';
    throw new Error('Thiếu biến tiền đề: '+thieu.join(', ')+'. '+huongDan);
}
'@
foreach ($folder in $source.item) {
    foreach ($request in $folder.item) {
        $manualProductRead = $request -eq $stock -or $request -eq $productDetail
        foreach ($hook in $request.event) {
            $hook.script.exec = @($hook.script.exec | ForEach-Object {
                if ($_ -match '^if \(thieu.length\)') {
                    $missingVariables
                } else {
                    $line = $_.Replace('pm.environment.', 'pm.collectionVariables.').Replace('pm.variables.get(', 'pm.collectionVariables.get(')
                    if ($manualProductRead -and $hook.listen -eq 'prerequest' -and $line -match '^const thieu') {
                        $line = $line.Replace('const value = pm.collectionVariables.get(key);', "const value = key === 'productId' ? productIdTrongUrl : pm.collectionVariables.get(key);")
                    }
                    $line
                }
            })
            if ($manualProductRead -and $hook.listen -eq 'prerequest') {
                $hook.script.exec = @(
                    "const urlSanPham = pm.variables.replaceIn(pm.request.url.toString());"
                    "const idTrongUrl = urlSanPham.match(/\/api\/(?:inventory|products)\/([1-9]\d*)\/?(?:[?#].*)?`$/);"
                    "const productIdTrongUrl = idTrongUrl ? idTrongUrl[1] : null;"
                ) + @($hook.script.exec)
            }
        }
    }
}
$target = Join-Path $root 'tests/postman/md05-luong-chinh.postman_collection.json'
[IO.File]::WriteAllText($target, ($source | ConvertTo-Json -Depth 100) + "`n", [Text.UTF8Encoding]::new($false))
Write-Output 'Built main business flows: 42 requests / 8 folders / customer and ADMIN.'
