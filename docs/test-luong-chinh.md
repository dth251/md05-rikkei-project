# Test các luồng nghiệp vụ chính

Import **[md05-luong-chinh.postman_collection.json](../tests/postman/md05-luong-chinh.postman_collection.json)**:
42 requests trong 8 nhóm, chỉ một **khách hàng (USER)** và một **ADMIN**.
Không cần User A/User B. Các request tự lưu và dùng đúng token của hai vai.

Chọn environment production private đã chuẩn bị:

```text
C:\Users\Admin\.md05-demo\postman-production-20261014-2324\md05-production.private.postman_environment.json
```

API đích: `https://md05-rikkei-project.duckdns.org`. Environment cần
`adminEmail`, `adminPassword`, `notificationMailbox`. Collection tự đăng
ký khách hàng có Gmail alias riêng mỗi lượt và tạo sản phẩm/kho/đơn mới.
Thư về Gmail đã đặt trong `notificationMailbox`; không tạo hộp thư Gmail
mới. HashSecret VNPay được giữ ở backend, không nhập vào Postman.

| Nhóm | Người thực hiện và luồng | Kết quả cần thấy |
|---|---|---|
| 01 | Khách đăng ký → đăng nhập → xem tài khoản | USER, có userToken |
| 02 | ADMIN đăng nhập → tạo/xem sản phẩm → nhập kho | Sản phẩm 125.000 đồng, kho 20/0 |
| 03 | Khách đặt COD → ADMIN giao → thu đủ tiền → hoàn tất | CONFIRMED → SHIPPING → COMPLETED; Payment SUCCESS |
| 04 | Khách yêu cầu trả COD → ADMIN duyệt → nhận hàng → hoàn tiền | Hàng nhập lại kho; hồ sơ trả REFUNDED |
| 05 | Khách đặt ONLINE, không thanh toán, chờ thật 150 giây | Đơn EXPIRED; Payment FAILED/reservationExpired; kho được giải phóng |
| 06 | Khách tạo một đơn mới → lấy Payment → tạo link sandbox | Có vnpayCheckoutUrl; Runner dừng |
| 07 | Sau khi khách thanh toán sandbox: IPN → xác nhận → giao → hoàn tất | Payment SUCCESS, Order CONFIRMED rồi COMPLETED |
| 08 | Khách yêu cầu trả VNPay → ADMIN duyệt → nhận hàng → hoàn tiền sandbox | Payment refundStatus và hồ sơ trả đều REFUNDED |

Trong Postman, mở **Run collection**, chọn nhóm **01–06** theo thứ tự:
Iterations **1**, Delay **1100 ms**, dừng khi assertion/script lỗi.
Giữ cùng environment. Phần này có chờ thật 150 giây nên mất vài phút;
để Runner tiếp tục gửi GET chờ, không bấm thanh toán đơn của nhóm 05.

Runner dừng ở cuối nhóm 06. Mở biến **`vnpayCheckoutUrl`** trên trình duyệt,
thanh toán bằng [thẻ test VNPay](vnpay-sandbox-browser.md#tạo-link-cho-đúng-đơn).
Đơn VNPay này là đơn mới, tách khỏi đơn cố tình hết hạn ở nhóm 05.
Cũng cần thanh toán trong **150 giây từ lúc tạo đơn VNPay**; không chờ
hết 150 giây rồi mới thanh toán.

Sau khi quay về trang kết quả, chạy **riêng nhóm 07**, rồi **08** nếu muốn
test hoàn trả đơn VNPay. Không chạy lại 01 vì sẽ khởi tạo ID/tài khoản
khác. Đơn phải COMPLETED trước khi yêu cầu trả; cần duyệt và nhận hàng
trước khi ghi nhận hoàn tiền. Đơn gốc vẫn COMPLETED, hồ sơ trả là REFUNDED.

Nếu token hết hạn, chạy lại riêng request đăng nhập khách hàng/ADMIN,
giữ các ID đang thử. Nếu VNPay còn chưa xác nhận IPN thì 07 không thể
đạt chỉ bằng việc trình duyệt quay về; xem [cấu hình và trạng thái sandbox](vnpay-sandbox-browser.md).
Hoàn tiền VNPay chỉ được đánh dấu REFUNDED khi backend nhận kết quả đã
xác minh. Nếu provider trả trạng thái chưa rõ, dừng và dùng đối soát theo
[runbook](vnpay-sandbox.md); không gửi thêm lệnh hoàn tiền.

## Kiểm tra email trong các luồng chính

Backend tự gửi qua Gmail SMTP trên VPS đến email khách hàng đã đăng ký,
chính là biến `email` của lượt chạy. Không cần gọi API gửi email riêng.
Sau khi chạy, mở Gmail ở `notificationMailbox` và đối chiếu mã đơn:

| Thời điểm | Mã đơn trong environment | Email cần kiểm tra |
|---|---|---|
| Nhóm 03, sau thu COD | `codOrderId` | Đơn hàng #ID đã xác nhận |
| Nhóm 03, khi giao/hoàn tất COD | `codOrderId` | Đơn hàng #ID đang giao hàng / đã hoàn tất |
| Nhóm 05, sau 150 giây | `expiryOrderId` | Đơn hàng #ID đã hủy do quá hạn thanh toán |
| Nhóm 07, sau IPN và giao/hoàn tất VNPay | `vnpayOrderId` | Đã xác nhận / đang giao hàng / đã hoàn tất |
| Nhóm 04 và 08, trả/hoàn tiền | Mã đơn tương ứng | Hiện chưa có email trả/hoàn tiền riêng; kiểm tra Payment và hồ sơ trả REFUNDED |

Trong Gmail, có thể tìm `subject:"Đơn hàng #ID"`, thay ID bằng mã thật.
Kiểm tra recipient đúng biến `email`, mã đơn và nội dung trạng thái;
kiểm tra cả thư rác nếu chưa thấy. Các sự kiện bất đồng bộ có thể đến
khác thứ tự, nên đối chiếu theo trạng thái và mã đơn.

Assertions Postman kiểm tra API, không tự đọc inbox Gmail. Phía VPS có
thể đối chiếu `email_deliveries` theo các order ID mới: SENT, attempts và
error_code. SMTP SENT chứng minh máy chủ thư chấp nhận gửi; cần người
nhận kiểm tra inbox để xác nhận thư đã tới. Email tự động hiện là text;
mẫu hóa đơn HTML trước đó là bản gửi thử riêng, chưa gắn vào mapper.

Chỉ có các bước phục vụ luồng chính; không có bài cố tình sai JWT, truy
cập tài khoản khác, replay, tải hoặc đối soát nâng cao trong Runner này.
COD ở đây là ghi nhận thu/hoàn tiền demo, không tự chuyển tiền mặt.
Giao dịch VNPay chỉ dùng sandbox thử nghiệm.

Collection được tạo từ nguồn hiện có bằng
[build-postman-main-flows.ps1](infrastructure/scripts/build-postman-main-flows.ps1).

## Kết quả kiểm tra ngày 15/10/2026

Đã chạy nhóm 01–06 trên production: **31 request lưu sẵn**, thực thi
**136 HTTP requests / 145 assertions**, không lỗi, exit 0, **3 phút 19,5 giây**.
Số HTTP lớn hơn số request vì các bước chờ xử lý bất đồng bộ gửi GET lặp lại.
Đơn COD #161 hoàn tất, hồ sơ trả và Payment #96 REFUNDED đủ 125.000 đồng.
Đơn #162 đã EXPIRED sau ít nhất 150 giây, Payment #97 FAILED và đã giải
phóng kho. Đơn mới #163/Payment #98 tạo link VNPay sandbox thành công;
Runner dừng đúng bước checkout.

Đối chiếu VPS lúc **00:55:20 +07**: cả sáu service healthy; có **4 email
SENT, attempts=1, không error_code** cho COD #161 (xác nhận, giao, hoàn tất)
và đơn hết hạn #162. Chưa xác nhận thư trong inbox. Tại thời điểm đó đơn
VNPay #163 còn chờ thanh toán nên kho là 19 sản phẩm khả dụng, 1 giữ chỗ.
Link của lượt kiểm tra này có hạn; chạy lượt mới để tự thao tác thanh toán.

Đối chiếu tiếp lúc **00:57:32 +07**: đơn #163 cũng đã EXPIRED do không
thanh toán; kho trở lại **20 khả dụng / 0 giữ chỗ**. Có thêm email hết hạn
của #163, tổng **5 email SENT**, mỗi email một lần gửi, không lỗi.

Nhóm 07–08 đã kiểm tra cấu trúc/script, **chưa xác minh thanh toán, IPN và
hoàn tiền thực tế tại VNPay sandbox**. Xem [bằng chứng kiểm tra](evidence/postman-luong-chinh-2026-10-15.json).
