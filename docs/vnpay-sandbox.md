# VNPay sandbox: checkpoint phát triển

Luồng checkout, IPN, return và hoàn tiền đang ở nhánh
`feature/remediation-vnpay`. Chưa merge/deploy VNPay hoặc thực hiện giao dịch
với merchant sandbox thật. VPS vẫn chạy image main 119; cấu hình private
VNPay/SMTP chưa được cung cấp trong phiên làm việc.

## API và trạng thái

- `POST /api/payments/{id}/vnpay/checkout`: chủ payment, số tiền lấy từ database;
  reference và URL ổn định khi gọi lại, TTL 15 phút. Chặn simulate sau checkout.
- `GET /api/payments/vnpay/ipn`: public, HMAC-SHA512 hợp lệ trước khi ghi dữ liệu;
  kiểm tra merchant, amount, capture number và thời gian. Trả JSON `RspCode`/
  `Message` theo protocol VNPay. Callback trùng trả `02`, capture lần đầu `00`.
- `GET /api/payments/vnpay/return`: xác minh chữ ký, hiển thị kết quả provider;
  không cập nhật payment. IPN mới quyết định trạng thái lưu trữ.
- `POST /api/payments/{id}/vnpay/refund`: ADMIN, chỉ hoàn toàn phần đối với capture
  thật đã được xác minh và có `refundStatus=REQUIRED`.
- `POST /api/payments/{id}/vnpay/reconcile`: ADMIN, truy vấn `querydr` cho yêu cầu
  hoàn tiền đã tồn tại. Không tự gửi thêm lệnh refund.

Capture đến sau khi thất bại/hết hạn vẫn được lưu, đánh dấu cần hoàn tiền;
không tạo thêm `payment.succeeded` hay làm sống lại order đã hết hạn. Payment
và refund có khóa database, unique reference/transaction và một request hoàn
toàn phần trên mỗi payment.

Claim refund được commit **trước** HTTP call, ngoài transaction giữ khóa.
Gọi lại cùng payment chỉ đọc trạng thái, kể cả sau timeout hoặc process crash.
`UNKNOWN` là chưa biết provider đã nhận/chuyển tiền hay chưa; `PROCESSING` là
đang xử lý, không coi response code `00` là bằng chứng tiền đã hoàn. Chỉ reply
đã xác minh với type `02`, status `00`, merchant/reference/amount khớp và refund
transaction number hợp lệ mới ghi `COMPLETED` / `REFUNDED`. Query trả type `01`
(thanh toán gốc) không thể hoàn tất refund. Trạng thái không rõ cần đối soát
provider; không có thao tác tự reset claim hoặc tự thử lại lệnh chuyển tiền.

Client ledger cố định tới sandbox HTTPS, không redirect, tối đa bốn request
đồng thời, connect 3 giây, deadline 6 giây gồm response body, tối đa 16 KiB.
JSON trùng key và chữ ký không hợp lệ giữ kết quả chưa rõ. Fixture HTTP chỉ
cho phép loopback khi explicitly bật property thử nghiệm; Compose production
không expose property đổi URL hoặc bật fixture.

## Cấu hình private

Đặt trong `.env` private: `VNPAY_ENABLED`, `VNPAY_TMN_CODE`,
`VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL`, `VNPAY_SERVER_IP`. Enabled mặc định
`false`, không sinh merchant/key giả vào môi trường deploy. Return URL cần
HTTPS, path `/api/payments/vnpay/return`; server IP là địa chỉ số của máy chủ
merchant gọi refund/querydr. Cấu hình IPN HTTPS Gateway trong portal VNPay.
Không đổi merchant/secret khi vẫn còn checkout hoặc refund chưa đối soát.

Gateway chỉ public hai callback GET, có Redis quota riêng; checkout dùng quota
theo user. Gateway ghi đè header IP client bằng địa chỉ đã qua trusted proxy;
backend đặt trên mạng private/loopback. Redis mất kết nối trả 503 cho callback
và checkout. Không ghi secret hay request/response provider vào logs.

## Kiểm thử và việc còn lại

2026-10-12 09:38 +07: Payment đạt **42/42 unit, zero fail/error/skip**, bootJar
đạt. Bảy test refund chạy HTTP fixture ký thật, gồm 20 request đồng thời chỉ
một refund POST, mất response không resend, query payment gốc không hoàn tất
refund, processing/final, chữ ký sai/JSON trùng/amount sai/body quá lớn và
quyền ADMIN. Lần full trước phát hiện expiry DTO khác precision sau reload;
đã chuẩn hóa checkout timestamp theo giây và chạy lại toàn bộ thành công.
Gateway baseline 20 unit và ba test VNPay routing/quota/IP đã đạt riêng.

Còn kiểm thử callback với PostgreSQL và Gateway thật, cập nhật bản OpenAPI
export, CI/PR và rollout service. Các fixture hiện tại không chứng minh đã
thanh toán/hoàn tiền tại hệ thống VNPay. Sandbox thật và SMTP ngoài cần cấu
hình private; không đánh dấu bốn nhóm remediation đã hoàn tất.

Protocol đối chiếu với tài liệu chính thức:
[PAY/IPN](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html) và
[QueryDr/Refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).
