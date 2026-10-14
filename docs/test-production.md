# Hướng dẫn test toàn bộ API trên production

Yêu cầu mới: thanh toán qua **VNPay sandbox trên trình duyệt** rồi quay về
dự án, xem [hướng dẫn riêng](vnpay-sandbox-browser.md). Luồng này cần
merchant sandbox private và bản Payment có trang kết quả đã triển khai.
Hiện đã cấu hình sandbox và triển khai trang kết quả; dùng
**[một file Postman gộp](postman-mot-file.md)** cho bài đầy đủ.
Các kết quả 680 assertions bên dưới là checkpoint mô phỏng trước đó;
không phải bằng chứng đã giao dịch trên VNPay sandbox.

Môi trường của hướng dẫn này là **https://md05-rikkei-project.duckdns.org**.
Thanh toán dùng API mô phỏng, không giao dịch ngân hàng thật và không cần
merchant VNPay. Các bước vẫn tạo dữ liệu thật trên production, riêng cho
tài khoản thử: hai USER mới, sản phẩm/biến thể và các đơn mới mỗi phiên.

## 1. Import và chọn đúng environment

Import vào Postman:

1. [Collection tiếng Việt — 162 requests](../tests/postman/md05-tieng-viet.postman_collection.json).
2. [Environment production](../tests/postman/md05-tieng-viet.production.postman_environment.json).

Chọn **MD05 - Production HTTPS - Tiếng Việt**, giữ `baseUrl` như file mẫu.
Điền giá trị private/local của `adminEmail`, `adminPassword`,
`notificationMailbox` (Gmail của bạn để nhận thư thử).
`requireRealMailbox=true` giúp dừng trước đăng ký nếu thiếu Gmail nhận thử.
Đăng ký public chỉ tạo USER; dùng ADMIN thử nghiệm đã cấp sẵn.

Đã chuẩn bị bản environment có ADMIN thử nghiệm và Gmail nhận thư của bạn,
nằm ngoài Git, chỉ Windows user hiện tại đọc:

```text
C:\Users\Admin\.md05-demo\postman-production-20261014-2324\md05-production.private.postman_environment.json
```

Bạn có thể import thẳng file private này, không cần nhập lại ADMIN. Không
chia sẻ/export bản chứa passwords và tokens. Các file trong Git luôn để
credentials trống.

## 2. Chạy toàn bộ 14 folders

Trong Collection Runner, chọn cả **01–14**, theo thứ tự có sẵn:

- **Iterations = 1**.
- **Delay = 1100 ms**.
- Dừng khi có lỗi request/script/assertion.
- Giữ cùng một environment và một phiên Runner; không chạy song song.

| Folder | Nội dung kiểm tra trên production | Kết quả cuối cần thấy |
|---|---|---|
| 01 | Đăng ký A/B, login, JWT, phân quyền | 201/200; các lỗi 400/401/403 đúng tình huống |
| 02 | Refresh rotation, replay, logout | Thu hồi family, logout 204; login lại cuối folder |
| 03 | CRUD địa chỉ, default và ownership | Một default; B không đọc/sửa/xóa địa chỉ A |
| 04 | ADMIN, sản phẩm, tìm kiếm, biến thể | Giá parent 125.000; variant 130.000 |
| 05 | Nhập kho và idempotency | Parent 20/0, variant 5/0; replay không tăng thêm |
| 06 | ONLINE mô phỏng SUCCESS, giao/hoàn tất | COMPLETED, parent 18/0, email xác nhận |
| 07 | ONLINE mô phỏng FAILED và bù trừ | CANCELLED, parent trở lại 18/0, email hủy |
| 08 | COD giao, thu đủ tiền, hoàn tất | COMPLETED, có biên nhận/codPaidAt, parent 17/0 |
| 09 | Hủy một đơn COD khác trước giao | CANCELLED/FAILED; parent vẫn 17/0 |
| 10 | Trả toàn bộ COD, nhập lại, hoàn tiền | Return REFUNDED; parent 18/0 |
| 11 | Chờ thật 150 giây, thanh toán muộn | EXPIRED; muộn 409; parent 18/0, email quá hạn |
| 12 | POS mua variant, đặt vượt tồn | Variant 4/0; parent 18/0; vượt tồn 409 |
| 13 | Trả ONLINE và hoàn tiền mô phỏng | Return REFUNDED; parent 20/0 |
| 14 | Xóa mềm parent/variant vừa tạo | Public GET 404; đơn cũ giữ snapshot |

401/403/404/409 trong request kiểm tra lỗi là kết quả mong đợi, không phải
API hỏng. Đọc tên assertion để phân biệt lỗi dự kiến với một request thất
bại ngoài kế hoạch. Khi một bước tiền đề sai, dừng và xử lý nguyên nhân;
không chạy tiếp những bước dùng ID/token chưa có.

Folder 01 sinh UUID cho lượt thử và địa chỉ Gmail có nhãn riêng
`+md05-a-...`/`+md05-b-...`; các thư vẫn về hộp thư đã đặt trong
`notificationMailbox`. Mật khẩu USER được sinh riêng hoặc dùng giá trị
private đã chuẩn bị. Không tạo globals/data iteration trùng tên biến.

Folder 11 cần để Runner chờ quá hạn thật; không sửa thời gian đơn hay TTL.
Polling có giới hạn và assertion cuối. Bấm Send thủ công không tự lặp;
phải gửi lại request chờ cho đến khi đạt kết quả hoặc hết giới hạn.

Khi demo chậm và JWT hết hạn, chạy lại riêng các request login A/B/ADMIN,
giữ ID đang thử. Muốn phiên mới, bắt đầu lại 01; không chạy lại 01 giữa
phiên đang kiểm tra vì nó khởi tạo người dùng/SKU/keys mới.

## 3. Mô phỏng nghiệp vụ và VNPay sandbox

`/simulate` là bài nghiệp vụ riêng, trả JSON. Với sandbox thử nghiệm,
`POST /api/payments/{id}/vnpay/checkout` hiện trả link sang VNPay sau khi
merchant private đã được bật; xem hướng dẫn riêng ở đầu tài liệu.
Sau khi mô phỏng SUCCESS đúng hạn, Payment phát
`payment.succeeded` qua Outbox/RabbitMQ; Order tự chuyển CONFIRMED và phát
`order.confirmed`; Notification tự gửi email. Không gọi thêm POST đặt hàng
hoặc POST xác nhận đơn. Đơn được tạo trước khi thanh toán. Việc xử lý là
bất đồng bộ nên cần chờ GET Order thấy CONFIRMED.

Theo yêu cầu, dùng ba kịch bản nghiệp vụ ONLINE:

- **06:** ADMIN `POST /api/payments/{id}/simulate`, body `{"status":"SUCCESS"}`;
  chờ Order CONFIRMED, kiểm tra chốt kho và email.
- **07:** cùng API với `{"status":"FAILED"}`; chờ CANCELLED và hoàn kho.
- **11:** không simulate trước deadline, chờ EXPIRED rồi simulate SUCCESS
  muộn phải 409.

Đây là mô phỏng kết quả thanh toán, không kiểm chứng chữ ký/IPN/checkout
của nhà cung cấp VNPay. Không cần điền TmnCode/HashSecret hoặc mở trang
thanh toán ngân hàng để chạy bộ này. Đường dẫn checkout VNPay thật chỉ có
khi merchant sandbox đã cấu hình; không dùng link giả để kết luận đã tích
hợp nhà cung cấp.

Ở checkpoint 680 assertions ngày 14/10, VPS có `VNPAY_ENABLED=false`.
Hiện provider sandbox đã bật. Bộ dưới chỉ dành cho một môi trường được
xác nhận đang tắt provider, không chạy trong bài sandbox hiện tại:
[collection VNPay đang tắt — 6 requests](../tests/postman/md05-production-vnpay-disabled.postman_collection.json)
với cùng environment của môi trường provider tắt:

1. IPN trả HTTP 200 với `RspCode=99` xác nhận provider đang tắt.
2. Return, checkout, refund, reconcile trả 503 theo feature guard.
3. `probePaymentId` lấy từ `onlinePaymentId` của folder 06, là payment
   mô phỏng của chính lượt thử. Không chọn một payment khách hàng khác.

Collection kiểm tra IPN trước; nếu provider đã bật hoặc kết quả khác dự
kiến, nó dừng. Thành công ở bộ này chỉ chứng minh xử lý đúng khi feature
tắt, không phải thanh toán VNPay thành công. Collection sandbox 19 requests
khác trong repository dành cho bài có merchant, không thuộc phạm vi giả
lập hiện tại.

## 4. Email và monitoring đều ở VPS

Notification production đã dùng **Gmail SMTP**. Sau các luồng xác nhận,
hủy và hết hạn, kiểm tra Gmail nhận thử, đối chiếu order ID và tổng tiền.
SENT trong Notification nghĩa là SMTP đã nhận; inbox thật cần kiểm tra cả
thư rác/cập nhật. HTTP Order thành công chưa chứng minh email đã tới inbox.

Mẫu hóa đơn HTML đã gửi thử riêng từ VPS, xem [mẫu và trạng thái](email/README.md).
Thông báo tự động hiện vẫn dùng EmailMapper text; không dùng file demo cố
định làm hóa đơn tự động cho mọi khách hàng.

Grafana/Prometheus xem trên VPS qua tunnel:

```powershell
ssh -i '<private-key-path>' -o StrictHostKeyChecking=yes -N `
  -L 13001:127.0.0.1:13000 -L 19091:127.0.0.1:19090 deploy@42.96.2.60
```

Grafana `http://127.0.0.1:13001`, Prometheus `http://127.0.0.1:19091`.
Địa chỉ localhost ở đây là đầu tunnel tới **VPS**, không phải ứng dụng
local. Chọn đúng cửa sổ demo và chờ vài lần scrape. Kết quả hiện tại và
giới hạn xác minh nằm trong [báo cáo production](postman-production-results.md).

## 5. CLI và chuẩn bị một lượt private mới

Environment private hiện có dùng được với Postman desktop. Để tạo bản mới
với mật khẩu USER riêng trước một lượt CLI:

```powershell
& docs/infrastructure/scripts/prepare-production-postman.ps1 `
  -CredentialEnvironment '<environment-private-co-admin.json>' `
  -PrivateDirectory '<thu-muc-private-moi-ngoai-Git>' `
  -NotificationMailbox '<gmail-nhan-thu>@gmail.com'

postman collection run tests/postman/md05-tieng-viet.postman_collection.json `
  -e '<thu-muc-private-moi-ngoai-Git>/md05-production.private.postman_environment.json' `
  --delay-request 1100 --timeout-request 15000 --timeout 1200000 `
  --bail --no-report-events
```

Sau CLI, môi trường đầu vào không tự lưu các ID/token do Runner sinh;
Postman desktop giữ các biến trong environment của phiên. Với collection
VNPay tắt chạy CLI riêng, điền `probePaymentId` từ payment của folder 06.
Không đưa password vào command line và không publish kết quả có credentials.

Các bài k6 tải lớn hoặc dừng dependency là kịch bản vận hành riêng; chúng
không được kích hoạt khi Run 162 request chức năng này. Không có thay đổi
binary, rate limit, TTL hoặc reset dữ liệu để làm kết quả PASS.
