# Test các luồng nghiệp vụ chính

Import **[md05-luong-chinh.postman_collection.json](../tests/postman/md05-luong-chinh.postman_collection.json)**:
42 requests trong 8 nhóm, chỉ một **khách hàng (USER)** và một **ADMIN**.
Không cần User A/User B. Các request tự lưu và dùng đúng token của hai vai.

## Nhập trực tiếp, không cần environment

1. Import lại file collection đã cập nhật và chọn **No environment**.
2. Nếu chưa có USER: mở **01 → Đăng ký khách hàng**, tab **Body → raw → JSON**,
   điền `fullName`, `email`, `password`, rồi **Send**. Dùng email chưa đăng ký;
   email này nhận thông báo đơn hàng. Nếu đã có USER, bỏ qua đăng ký.
3. Mở **Đăng nhập khách hàng**, nhập trực tiếp `email` và `password` trong
   Body rồi **Send**. Khi đăng ký mới, nhập lại đúng tài khoản vừa đăng ký.
4. Mở **02 → Đăng nhập ADMIN**, nhập trực tiếp email/mật khẩu ADMIN trong
   Body rồi **Send**. Các ô được để trống trong file để bạn tự điền.
5. Tiếp tục các request theo thứ tự. Token, ID sản phẩm, ID đơn và link
   thanh toán được tự lưu tại **collection → Variables**; không cần chép
   token hoặc nhập environment. Khi token hết hạn, gửi lại request đăng nhập.

API đích đã có sẵn: `https://md05-rikkei-project.duckdns.org`.
Body đăng nhập chỉ cần hai trường:

```json
{
  "email": "",
  "password": ""
}
```

Bạn điền trực tiếp bên trong dấu nháy; không cần dùng `{{email}}` hoặc
`{{password}}`. Request đăng ký/đăng nhập không tự sinh hay ghi đè tài khoản
bạn nhập. Các Body nghiệp vụ còn lại cũng có thể chỉnh trực tiếp; giữ các
biến ID nếu muốn tự nối dữ liệu từ request trước. Tổng tiền và một số
assertion hiện dựa trên sản phẩm demo giá 125.000 đồng, số lượng 1.

Mật khẩu không được sao chép vào Variables; bạn nhập trong Body của từng
request đăng ký/đăng nhập. Bản collection trong Git để trống credentials.
HashSecret VNPay được giữ ở backend, không nhập vào Postman.

### Khi báo thiếu userToken hoặc adminToken

`userToken` chỉ được lưu sau khi gửi **01 → Đăng nhập khách hàng** thành
công; `adminToken` được lưu ở **02 → Đăng nhập ADMIN**. Phải gửi request
đăng nhập nằm trong **chính collection đang chạy**, vì đăng nhập ở tab
request rời hoặc collection khác không lưu token cho collection này.
Nếu dùng Runner, bật **Keep variable values** trước khi chạy riêng từng
nhóm để giữ các token khi Runner kết thúc.

Nhóm **02 → Xem tồn kho** dùng `adminToken`, có thể chạy nhóm chuẩn bị kho
chỉ với ADMIN. Các bước xem kho trong nhóm trả hàng/hết hạn vẫn dùng
`userToken` của khách đang kiểm tra đơn hàng. Nếu thiếu token ở đây, gửi
lại request đăng nhập khách hàng rồi gửi lại request lỗi, không cần tạo
lại sản phẩm hay nhập kho lần nữa.

### Khi báo thiếu productId

Để **xem kho của sản phẩm có sẵn**, sau khi đăng nhập ADMIN, mở
**02 → Xem tồn kho**, thay `{{productId}}` trên thanh URL bằng ID thật:

```text
GET https://md05-rikkei-project.duckdns.org/api/inventory/159
```

Request này đọc ID trực tiếp từ URL, không cần nhập biến `productId`.
Tương tự, xem chi tiết sản phẩm bằng `/api/products/159`. Chỉ nhập ID
thật của sản phẩm muốn xem; request đọc kho không tự tạo hay nhập thêm hàng.
Đọc một ID thủ công không ghi đè `productId` đang dùng trong luồng đặt đơn.

Để chạy **trọn luồng demo**, giữ `{{productId}}` trên URL và chạy
**02 → Tạo sản phẩm demo → Nhập 20 sản phẩm vào kho → Xem tồn kho**.
Request tạo sản phẩm trả HTTP 201 sẽ tự lưu `data.id` thành `productId`
trong chính collection. Đăng nhập chỉ lưu token, không tạo ID sản phẩm.

Nếu đang dùng bản cũ và muốn tiếp tục ngay, mở **collection → Variables**,
thêm `productId` bằng ID thật đang thử (ví dụ `159`), lưu rồi gửi lại
request. Đây là biến của collection, không cần tạo/chọn environment.

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
Điền Body của đăng ký/đăng nhập trước khi chạy. Nếu dùng USER đã có, bỏ
chọn request đăng ký và bắt đầu bằng đăng nhập. Khi tiếp tục Runner,
bật [**Keep variable values**](https://learning.postman.com/docs/tests-and-scripts/running-collections/intro-to-collection-runs)
để giữ token/ID trong collection Variables.
Phần này có chờ thật 150 giây nên mất vài phút;
để Runner tiếp tục gửi GET chờ, không bấm thanh toán đơn của nhóm 05.

Runner dừng ở cuối nhóm 06. Mở collection → **Variables**, lấy giá trị
**`vnpayCheckoutUrl`** rồi mở trên trình duyệt,
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
chính là email bạn nhập khi đăng ký/đăng nhập, được lưu thành biến `email`
trong collection. Không cần gọi API gửi email riêng.
Sau khi chạy, mở hộp thư của tài khoản khách hàng đó và đối chiếu mã đơn:

| Thời điểm | Mã đơn trong collection Variables | Email cần kiểm tra |
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

**Bản nhập Body trực tiếp, không environment:** nhóm 01–06 đạt
**140 HTTP requests / 145 assertions**, không lỗi, exit 0, **3 phút 10,9 giây**.
CLI chạy bản private chỉ điền các Body đăng ký/đăng nhập, không dùng `-e`.
Chạy thêm riêng đăng nhập USER đã có → xem tài khoản → đăng nhập ADMIN
đạt **3 requests / 16 assertions**, exit 0, không environment.
85 hooks biên dịch được, 18 Body JSON hợp lệ; Body trống/JSON sai được
báo rõ trước khi chạy, không sao chép mật khẩu vào Variables.
Đơn COD #164 hoàn trả REFUNDED, đơn #165 EXPIRED, đơn #166/Payment #101
tạo link sandbox. Đối chiếu 01:15:55 +07 có 4 email SENT/attempts=1 cho
#164/#165; chưa xác nhận inbox. [Bằng chứng bản không environment](evidence/postman-luong-chinh-khong-environment-2026-10-15.json).

Sau khi sửa quyền đọc kho ở nhóm 02, chạy riêng **Đăng nhập ADMIN → Xem
tồn kho** trên production không environment và không có `userToken`:
**2 requests / 12 assertions**, exit 0, **2,9 giây**. Không tạo sản phẩm,
nhập thêm hàng hoặc đặt đơn trong lượt kiểm tra sửa lỗi này.

Bản hỗ trợ ID trực tiếp trên URL đã chạy production **6 requests /
31 assertions**, exit 0, **9,7 giây**: đăng nhập ADMIN; đọc kho/chi tiết
#159 khi không có biến `productId`; đọc #158 bằng biến; đọc #159 khi biến
vẫn là #158. ID và baseline của luồng #158 không bị ghi đè. Chỉ đăng nhập
và GET, không tạo đơn hay thay đổi kho. Kiểm tra nhập kho 20 và các bài
trả hàng/hết hạn vẫn giữ assertions riêng của luồng.

Lượt kiểm tra trước khi chuyển sang nhập Body trực tiếp đã chạy nhóm 01–06
trên production: **31 request lưu sẵn**, thực thi
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
