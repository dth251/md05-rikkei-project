# Một file Postman cho toàn bộ API

Import **[md05-tat-ca-api.postman_collection.json](../tests/postman/md05-tat-ca-api.postman_collection.json)**:
181 requests, 19 folders, tiếng Việt, bao phủ toàn bộ method/path API hiện có.
Chọn environment production private đã chuẩn bị theo
[hướng dẫn production](test-production.md#1-import-và-chọn-đúng-environment).
File collection không chứa mật khẩu ADMIN, JWT hoặc HashSecret; không cần
import riêng collection nghiệp vụ và collection VNPay nữa.

## User A, User B và ADMIN là ai?

Đây là ba vai trò trong kịch bản kiểm thử, không phải ba service:

| Tài khoản | Role trong hệ thống | Dùng để làm gì? | Biến Postman |
|---|---|---|---|
| User A | USER | Khách mua hàng chính: quản lý địa chỉ, tạo đơn, thanh toán, yêu cầu trả hàng | `email`, `password`, `userId`, `userToken` |
| User B | USER | Khách khác, dùng để kiểm tra không đọc/sửa được dữ liệu của A | `emailB`, `passwordB`, `userBId`, `userBToken` |
| ADMIN | ADMIN | Quản lý sản phẩm, nhập kho, giao/hoàn tất đơn, thu COD, duyệt trả/hoàn tiền; simulate thuộc bài kiểm thử riêng | `adminEmail`, `adminPassword`, `adminToken` |

Folder **P1 - 01** tự đăng ký A/B và đăng nhập, lưu ID/token vào
environment. Cả A và B đều là khách hàng USER; B không có quyền thấp hơn A.
ADMIN là tài khoản thử được cấp sẵn, không được tự cấp quyền bằng API
đăng ký công khai. Các request đã chọn đúng token cho từng người, không
cần tự đổi Authorization giữa mỗi bước.

Ví dụ A tạo đơn #100: A GET đơn #100 được 200; B dùng token của B GET
đúng đơn #100 phải nhận 404. B không đọc/sửa/xóa địa chỉ của A, không đọc
payment của A. Mã 404 ở đây giúp không tiết lộ dữ liệu của người khác.
USER thử thao tác quản trị phải bị 403; không có/sai JWT thì 401.

Không cần tạo hai hộp thư Gmail mới. Khi dùng `notificationMailbox`, bộ
test tạo hai địa chỉ nhãn `+md05-a-...` và `+md05-b-...` thuộc cùng Gmail
nhận thử. Đó là hai tài khoản khác nhau trong database Auth, có ID và JWT
riêng; thư cùng về inbox đã cấu hình. A/B được tạo mới mỗi lượt P1-01.

## Chuẩn bị trong Postman

1. Import collection gộp và chọn environment production private đã chuẩn
   bị. Nếu chưa import environment, dùng file ở đường dẫn:

   ```text
   C:\Users\Admin\.md05-demo\postman-production-20261014-2324\md05-production.private.postman_environment.json
   ```

2. Kiểm tra `baseUrl=https://md05-rikkei-project.duckdns.org`, có
   `adminEmail`, `adminPassword` và Gmail nhận thử `notificationMailbox`.
   Không điền HashSecret VNPay vào Postman; backend VPS đã giữ khóa private.
3. Mở **Run collection**, đặt Iterations **1**, Delay **1100 ms**; dừng nếu
   có assertion/script lỗi. Dùng một environment trong suốt các giai đoạn.
   Các ID/token/key do request tạo và lưu tự động.

## Thứ tự và mục đích các luồng

| Nhóm trong collection | Luồng API | Kết quả cần đối chiếu |
|---|---|---|
| P1 - 01–03 | Đăng ký/login A/B, refresh/logout, địa chỉ, ownership | JWT đúng role; B không truy cập dữ liệu A |
| P1 - 04–05 | ADMIN tạo sản phẩm/biến thể, tìm kiếm, nhập kho | Đúng giá/tồn kho; gửi lại cùng key không nhập thêm |
| P1 - 06–07 | A đặt ONLINE; ADMIN simulate SUCCESS hoặc FAILED trên các đơn riêng | SUCCESS → CONFIRMED, chốt kho; FAILED → CANCELLED, hoàn kho |
| P1 - 08–10 | A đặt COD; ADMIN giao, thu tiền, hoàn tất; hủy/trả COD trên các đơn riêng | Đúng biên nhận; chưa thu đủ không hoàn tất; trả/hoàn tiền đúng một lần |
| P1 - 11 | Tạo ONLINE rồi chờ hết hạn thật 150 giây | EXPIRED, giải phóng giữ chỗ; simulate SUCCESS muộn 409 |
| P1 - 12–13 | Đơn POS mua biến thể và trả/hoàn ONLINE mô phỏng | Kho cha/biến thể độc lập; nhập lại/hoàn tiền đúng |
| P1 - V00/V01 | Từ chối callback không chữ ký; tạo đơn mới và URL VNPay sandbox | HTTP 200 có `vnpayCheckoutUrl`; Runner dừng để mở trình duyệt |
| P2 - V02 | Sau thao tác thanh toán sandbox, đọc Payment/Order/kho | Payment SUCCESS, Order CONFIRMED; còn chờ thì polling có giới hạn |
| P2 - V03/V04 | Giao/hoàn tất/trả và hoàn tiền sandbox, đối soát | Chỉ chạy nếu muốn demo hoàn tiền; kết quả chưa rõ phải đối soát |
| P3 - 14 | Xóa mềm sản phẩm mới, đọc lại đơn lịch sử | Product public 404, snapshot đơn vẫn giữ nguyên |

Luồng thành công chính:

```text
A tạo đơn → giữ hàng → tạo Payment → tạo checkoutUrl
→ khách thao tác trên VNPay sandbox
→ VNPay gửi IPN đã ký → Payment SUCCESS
→ sự kiện xác nhận Order → Order CONFIRMED → Notification gửi email
```

Trình duyệt quay về trang kết quả có thể sớm hơn IPN; khi đó hiển thị
đang chờ xác nhận. Không gọi POST tạo lại đơn sau thanh toán. SMTP SENT
không tự chứng minh thư đã tới inbox; mở Gmail đối chiếu đúng order ID.

## Cách chạy theo ba giai đoạn

1. **P1:** chọn tất cả folders mang tiền tố P1, chạy theo thứ tự, Iterations
   1, Delay 1100 ms, dừng khi lỗi. Bộ tạo dữ liệu mới, chạy các tình huống
   nghiệp vụ rồi tạo một đơn ONLINE riêng để thanh toán trên VNPay sandbox.
   Sản phẩm chưa bị xóa mềm ở giai đoạn này.
2. Runner **tự dừng sau khi tạo link sandbox**. Mở biến environment
   `vnpayCheckoutUrl` trên trình duyệt ngay, thanh toán bằng thẻ test theo
   [hướng dẫn sandbox](vnpay-sandbox-browser.md). Đơn hết hạn sau 150 giây
   từ lúc tạo, cần hoàn tất thao tác trong thời gian đó.
3. Sau khi trình duyệt quay về, chọn **riêng P2** để kiểm tra Payment
   SUCCESS, Order CONFIRMED và kho, sau đó giao/hoàn tất/trả/hoàn tiền
   sandbox. Nếu chỉ cần demo thanh toán, chạy P2-V02; các P2-V03/V04 là
   bài hoàn tiền riêng. Không chạy lại P1 vì nó khởi tạo ID/tài khoản mới.
4. Chạy **P3** cuối cùng để xóa mềm đúng sản phẩm vừa tạo và kiểm tra
   snapshot đơn lịch sử. Giữ nguyên environment trong mọi giai đoạn.

Các requests simulate trong P1 là bài kiểm thử nghiệp vụ riêng. Đơn VNPay
được tạo mới sau đó; không dùng simulate trên payment đã tạo checkout
VNPay. Toàn bộ URL thanh toán provider được chốt vào sandbox, không có
giao dịch tiền thật. Provider cần merchant sandbox/IPN đã cấu hình đúng.
Không kết luận đã đạt toàn bộ từ việc P1 dừng đúng tại checkout; P2 cần
kết quả từ giao dịch trên trình duyệt và IPN thực tế.

Gửi request thủ công theo thứ tự cũng được; lệnh dừng/điều hướng chỉ có
tác dụng trong Runner. Token hết hạn thì chạy lại riêng request login,
giữ ID/key đang thử. Không export bản chứa credentials/tokens vào Git.

File được tạo từ hai collection nguồn bằng
[build-postman-all.ps1](infrastructure/scripts/build-postman-all.ps1).
Khi sửa nguồn, chạy lại script và kiểm tra cả scripts/requests trước khi
chia sẻ bản gộp.

Kiểm chứng 15/10 trên HTTPS production: P1 đạt **669 assertions, 270 HTTP
executions, zero failures**, 6m59.2s, dừng đúng sau checkout. Link tạo cho
đơn thử mở được trang chọn phương thức thanh toán Test của VNPay.
P2/P3 của phiên này chưa chạy; chưa có bằng chứng giao dịch/IPN thực từ
VNPay. Trang kết quả mới đã triển khai, smoke sau triển khai 12 assertions
đạt. Xem [runtime và bằng chứng](vnpay-sandbox-browser.md#trạng-thái-triển-khai).
