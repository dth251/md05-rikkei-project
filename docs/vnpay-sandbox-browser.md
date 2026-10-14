# Thanh toán trên VNPay sandbox rồi quay về dự án

API của dự án dùng VPS `https://md05-rikkei-project.duckdns.org`; giao dịch
thử nghiệm chỉ đi tới **sandbox.vnpayment.vn**, dùng thẻ test của VNPay.
Không dùng tài khoản merchant production hoặc thẻ ngân hàng thật.

## Cấu hình merchant thử nghiệm

VNPay yêu cầu **TmnCode và HashSecret sandbox** để tạo URL hợp lệ. Nếu chưa
có, [đăng ký merchant môi trường test](https://sandbox.vnpayment.vn/devreg/):
tên website MD05 Omnichannel, URL domain VPS ở trên và email của bạn. Tự
nhập mật khẩu portal và CAPTCHA; VNPay gửi thông tin kết nối về email đăng
ký. HashSecret là khóa ký do VNPay cấp, không phải mật khẩu portal/Gmail.

Đăng ký IPN HTTPS với VNPay/portal merchant theo hướng dẫn tài khoản:

```text
https://md05-rikkei-project.duckdns.org/api/payments/vnpay/ipn
```

Return URL do backend đưa vào URL thanh toán:

```text
https://md05-rikkei-project.duckdns.org/api/payments/vnpay/return
```

Chạy helper sau khi đã có merchant sandbox, nhập HashSecret qua prompt kín:

```powershell
powershell -NoProfile -File docs/infrastructure/scripts/configure-vnpay-sandbox-vps.ps1 `
  -TmnCode '<ma-8-ky-tu-do-VNPay-cap>'
```

Helper chỉ đổi cấu hình private VPS và recreate riêng Payment, giữ image
đang chạy, không deploy binary mới. Có backup/rollback, không ghi khóa vào
Git, arguments hoặc báo cáo. Refuse nếu còn checkout/refund chưa đối soát;
không dùng helper để xoay merchant khi có giao dịch tồn đọng. Kết quả
CONFIGURED chưa chứng minh thông tin merchant hợp lệ hoặc IPN đã được
VNPay cấu hình. Trang kết quả mới cần được triển khai qua PR/CI trước.

## Tạo link cho đúng đơn

1. Login USER sở hữu đơn, tạo **đơn ONLINE mới**; không dùng đơn đã simulate,
   hủy, hết hạn hoặc hoàn tất. Chờ `GET /api/payments/by-order/{orderId}`
   trả payment; lưu `data.id`.
2. Gửi request sau với JWT của chính USER đó, body trống:

   ```http
   POST https://md05-rikkei-project.duckdns.org/api/payments/{paymentId}/vnpay/checkout
   Authorization: Bearer <JWT-cua-chu-don>
   ```

3. Response HTTP 200 có `data.checkoutUrl` bắt đầu bằng
   `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...`. Mở URL này trên
   trình duyệt. Số tiền lấy từ đơn trong database; không gửi số tiền từ client.
4. Chọn thẻ nội địa/ngân hàng NCB và dùng thông tin test bên dưới. Hoàn tất
   thao tác xác thực, VNPay chuyển trình duyệt về Return URL của dự án.
5. Trang tiếng Việt hiển thị đơn, payment, tổng tiền và trạng thái đã lưu.
   Nếu IPN chưa tới: “Đang chờ xác nhận thanh toán”, tự kiểm tra tối đa 24
   lượt, cách 2,5 giây, có nút kiểm tra lại. Sau IPN thành công: “Thanh toán
   thành công”. Payment phát sự kiện để Order xác nhận đơn và Notification
   gửi email đã đăng ký. Không POST tạo hoặc xác nhận đơn lần nữa.

**Deadline đơn ONLINE hiện là 150 giây từ khi tạo đơn**, dù link VNPay có
hạn tối đa 15 phút. Cần thao tác ngay sau tạo đơn. Nếu đơn đã hết hạn,
capture đến muộn không làm sống lại đơn; trang thông báo đơn không còn
hiệu lực và khoản tiền cần được xử lý hoàn. Không kéo dài TTL hoặc ép
SUCCESS để vượt chính sách giữ hàng.

Thẻ thử thành công do [VNPay công bố](https://sandbox.vnpayment.vn/apis/vnpay-demo/):

| Trường | Giá trị thử nghiệm |
|---|---|
| Ngân hàng | NCB |
| Số thẻ test | `9704198526191432198` |
| Tên | `NGUYEN VAN A` |
| Ngày phát hành | `07/15` |
| OTP test | `123456` |

## Postman và cách đối chiếu

Import [collection sandbox](../tests/postman/md05-tieng-viet-vnpay.postman_collection.json)
và environment production private theo [hướng dẫn](test-production.md).
Chuẩn bị fixture mới bằng folders 01–05 của bộ chính; không chạy 14 xóa sản
phẩm. Chạy sandbox **V00 → V01**, mở biến `vnpayCheckoutUrl` trên trình
duyệt và hoàn tất thanh toán thử, rồi chạy **V02** để đối chiếu Payment
SUCCESS, Order CONFIRMED và kho. V03/V04 là bài trả/hoàn tiền riêng.

Postman nhận JSON từ Return URL với `Accept: application/json` hoặc không
chỉ định Accept. Trình duyệt gửi `Accept: text/html` nhận trang kết quả.
JSON giữ reference/providerOutcome/message, bổ sung paymentId/orderId,
amount/paymentStatus/reservationExpired/refundStatus. `providerOutcome`
chỉ là kết quả trên return có chữ ký; `paymentStatus` mới là trạng thái lưu.
Trang không khẳng định Order đã CONFIRMED chỉ từ Payment SUCCESS: Order
xử lý bất đồng bộ, xác nhận bằng GET Order trong V02. Email SENT chỉ xác
nhận SMTP chấp nhận, cần đối chiếu inbox để xác nhận thư đã tới.

Return chỉ đọc, không cập nhật Payment. Mọi lần refresh đều kiểm tra chữ
ký, merchant, reference, amount và capture đã lưu. IPN có chữ ký hợp lệ
mới cập nhật thanh toán. Thiếu/sai chữ ký không được xác nhận đơn. Không
đưa HashSecret vào Postman hoặc script trình duyệt.

## Trạng thái triển khai

Trang kết quả đang được kiểm thử trong nhánh feature. VPS tại checkpoint
180 vẫn provider tắt/image main 168. Merchant sandbox đã được người dùng
cung cấp sau checkpoint này; khóa chỉ cấu hình private. Chưa hoàn thành
giao dịch trên hệ thống VNPay trong lúc đang chuẩn bị triển khai. Kết
quả unit/fixture không thay thế bằng chứng giao dịch sandbox thực tế.

Nguồn protocol: [VNPay PAY/IPN/Return](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
Discovery Orbit không tìm thấy endpoint VNPay phù hợp trong kết quả trả
về; dùng tài liệu gốc VNPay và các contract/requests hiện có của dự án.
