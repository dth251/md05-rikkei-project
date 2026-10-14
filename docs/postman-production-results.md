# Kết quả test Postman trên production

Ngày 14/10/2026 (+07), target **https://md05-rikkei-project.duckdns.org**.
Dùng [hướng dẫn production](test-production.md), ADMIN thử riêng và hai
USER mới; thanh toán ONLINE được mô phỏng, không giao dịch ngân hàng.

| Bộ chạy | Requests đã lưu | HTTP thực gửi, gồm polling | Assertions | Lỗi | Thời gian | Exit |
|---|---:|---:|---:|---:|---|---:|
| Nghiệp vụ, đủ 14 folders | 162 | 280 | 659 | 0 | 5 phút 58,5 giây | 0 |
| VNPay provider đang tắt | 6 | 6 | 21 | 0 | 8,1 giây | 0 |
| Tổng | 168 | 286 | 680 | 0 | Hai lượt có thời gian chồng nhau | 0 |

Postman CLI: delay 1100 ms, timeout request 15000 ms, `--bail
--no-report-events`, không publish báo cáo lên cloud. Lượt nghiệp vụ có
response trung bình 75 ms, min 32/max 889 ms; đây là kiểm thử chức năng,
không phải kết quả load test. Bộ VNPay tắt trung bình 151 ms.

Các method/path quan sát trong log bao phủ **41/41 operations** OpenAPI.
Các URL provider chỉ được kiểm tra trạng thái tắt: IPN HTTP 200/RspCode 99;
return, checkout, refund, reconcile 503. Điều này không chứng minh provider
checkout/IPN có chữ ký đã hoạt động.

## Đối chiếu dữ liệu riêng của lượt thử

Đọc lại PostgreSQL trên VPS lúc **23:35:22 +07**, chỉ các ID mới của lượt
chạy, không sửa đơn khách hàng. USER A #224, sản phẩm cha #151 và biến thể
#153. Đơn/payment có kết quả:

| Luồng | Đơn | Payment | Trạng thái đơn cuối | Thanh toán / hoàn tiền |
|---|---:|---:|---|---|
| ONLINE SUCCESS rồi hoàn tất/trả toàn bộ | 146 | 83 | COMPLETED | SUCCESS / REFUNDED 250.000 |
| ONLINE FAILED | 147 | 84 | CANCELLED | FAILED |
| COD thu tiền rồi hoàn tất/trả toàn bộ | 148 | 85 | COMPLETED | SUCCESS / REFUNDED 125.000 |
| COD hủy trước giao | 149 | 86 | CANCELLED | FAILED |
| Quá hạn thật 150 giây | 150 | 87 | EXPIRED | FAILED, reservationExpired=true; SUCCESS muộn 409 |
| POS mua biến thể | 151 | 88 | CONFIRMED | SUCCESS 130.000 |

Kho cha cuối **available=20/reserved=0**, biến thể **4/0**. Hai hồ sơ trả
hàng đều REFUNDED. Hai sản phẩm được xóa mềm (`active=false`), đơn lịch sử
vẫn giữ snapshot. Không xóa volume hoặc dữ liệu cũ.

## Tự xác nhận đơn và email

`POST /api/payments/{id}/simulate` với `{"status":"SUCCESS"}` chỉ trả
JSON. Sau đó Payment phát `payment.succeeded`, Order tự chuyển CONFIRMED
và phát `order.confirmed`, Notification tự gửi email qua Gmail SMTP.
**Không cần POST xác nhận/đặt lại đơn. Chưa có link thanh toán giả lập.**

Đối chiếu sáu đơn trên có **10 email deliveries, tất cả SENT, attempts=1,
error_code=null**: 3 confirmed, 2 shipped, 2 completed, 2 cancelled và 1
expired. Đơn #151 có email xác nhận SENT lúc 23:30:53 +07. Recipient là
Gmail alias của tài khoản đăng ký mới, về hộp thư người dùng đã chọn.
SENT chứng minh SMTP chấp nhận; **chưa có xác nhận thư đến inbox** từ người
nhận. Mẫu hóa đơn HTML trước đó được gửi riêng; mapper tự động vẫn là text.

## Runtime và giới hạn

Sáu service trên VPS giữ image revision
`d2779d1595fb5541675e65604eb07ac633ccf91b`, tất cả healthy sau lượt chạy.
Preflight monitoring lúc 23:25:44 +07 đạt: sáu app/năm dependencies UP,
datasource/dashboard queries, tài nguyên và business metrics.

Không thay mã Java, cấu hình provider, CI hoặc rollout binary trong lượt
này. `VNPAY_ENABLED=false`, không dùng merchant hoặc tiền thật. Không chạy
race/load test; không thay thế Java unit/integration tests. Log private
giữ ngoài Git, bằng chứng công khai đã bỏ credentials và địa chỉ nhận:
[JSON kết quả](evidence/postman-production-2026-10-14.json).
