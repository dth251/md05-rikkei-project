$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$main = Get-Content (Join-Path $root 'tests/postman/md05-tieng-viet.postman_collection.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$vnpay = Get-Content (Join-Path $root 'tests/postman/md05-tieng-viet-vnpay.postman_collection.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$main.info.name = 'MD05 - Tất cả API - Production và VNPay SANDBOX'
$main.info.description = 'Một collection tiếng Việt cho toàn bộ API. Dùng environment production private đã chuẩn bị. P1: nghiệp vụ 01–13 rồi VNPay V00/V01; tự dừng sau khi tạo link. Mở vnpayCheckoutUrl trên trình duyệt để thanh toán bằng thẻ test sandbox. P2: chọn V02–V04 để đối chiếu/hoàn tiền; P3: xóa mềm cuối. Không chạy lại P1 khi tiếp tục P2. Không có HashSecret trong collection. Xem docs/postman-mot-file.md.'
foreach ($variable in $main.variable) {
    if ($variable.key -eq 'baseUrl') { $variable.value = 'https://md05-rikkei-project.duckdns.org' }
}
$folders = @()
foreach ($folder in $main.item[0..12]) {
    $folder.name = 'P1 - ' + $folder.name
    $folders += $folder
}
for ($index = 0; $index -lt $vnpay.item.Count; $index++) {
    $folder = $vnpay.item[$index]
    $folder.name = $(if ($index -lt 2) { 'P1 - ' } else { 'P2 - ' }) + $folder.name
    $folders += $folder
}
$cleanup = $main.item[13]
$cleanup.name = 'P3 - ' + $cleanup.name
$folders += $cleanup
$main.item = $folders
$checkout = $main.item[14].item[-1]
$checkout.request.description = 'Mở biến vnpayCheckoutUrl trên trình duyệt ngay sau HTTP 200. Runner dừng tại đây để thao tác sandbox. Sau khi quay về trang kết quả, chạy riêng các folder P2 rồi P3; không chạy lại P1.'
$hook = $checkout.event | Where-Object listen -eq 'test'
$hook.script.exec += @(
    "if (pm.response.code === 200) { pm.environment.set('allInOneAwaitingVnpay', 'true'); pm.execution.setNextRequest(null); }"
)
$path = Join-Path $root 'tests/postman/md05-tat-ca-api.postman_collection.json'
[IO.File]::WriteAllText($path, ($main | ConvertTo-Json -Depth 100) + "`n", [Text.UTF8Encoding]::new($false))
Write-Output 'Built one collection: 181 requests / 19 folders; stop after sandbox checkout.'
