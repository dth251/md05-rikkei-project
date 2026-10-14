# Kết quả kiểm chứng bộ Postman tiếng Việt

Ngày 14/10/2026 (+07:00). Nguồn đối chiếu: controller/DTO hiện tại và `docs/openapi/api-gateway.json`; HEAD trước thay đổi `2118079` (mốc 173). Bộ mới gồm 162 requests nghiệp vụ và 19 requests VNPay tùy chọn, với environment local mẫu không chứa credentials.

## Kiểm tra tĩnh

- Hai collection và environment parse JSON hợp lệ.
- 364 script hooks biên dịch JavaScript hợp lệ; 80 raw JSON bodies hợp lệ sau thay biến mẫu.
- Đối chiếu method/path bao phủ **41/41 HTTP operations nghiệp vụ** của OpenAPI Gateway; chỉ xác nhận có request, không có nghĩa mọi case của operation đã được test hoặc mọi API đã chạy live.
- Request/assertion/hướng dẫn bằng tiếng Việt. Biến token/ID giữ trong environment; kiểm tra tiền đề trước khi gửi; polling có giới hạn và assertion cuối, fail thì dừng luồng.
- Không thay Java nghiệp vụ; Postman không thay thế Java unit/integration tests.

## Chạy live local

Target `http://127.0.0.1:8080`, qua API Gateway, sáu container ứng dụng local healthy. Order runtime `RESERVATION_TTL=PT150S`. Các image `omnichannel-*:local` không có nhãn Git revision, nên không gán runtime SHA từ HEAD tài liệu.

Đã chạy riêng 01–03: **33 requests, 121 assertions, zero failures, exit 0**, 51,8 giây. Chạy bằng Postman CLI với delay 1.100 ms, timeout mỗi request 10 giây, `--bail --no-report-events`.

Lượt đầy đủ cuối hoàn tất khoảng **22:46 +07**, cả 14 folders / 162 requests đã lưu: **281 lần gửi HTTP** (gồm polling), **659 assertions, zero failures, exit 0**, tổng **5 phút 52,8 giây**. Có chờ thật 150 giây, EXPIRED, acknowledgement hết hạn và SUCCESS muộn 409; hoàn tất cả POS/variant, trả ONLINE và xóa mềm sau đó. Sáu container ứng dụng vẫn healthy. [Bằng chứng đã bỏ thông tin riêng tư](evidence/postman-tieng-viet-2026-10-14.json) ghi hash artifact, số kiểm tra và giới hạn xác minh.

Các lượt chuẩn bị dừng đúng khi gặp hai lỗi của bộ test: kỳ vọng SKU chữ thường trong khi contract chuẩn hóa chữ hoa; và số lượng 100.000 vượt giới hạn DTO nên trả 400 trước bước kiểm tra tồn kho. Đã dùng SKU chuẩn hóa và số lượng 1.000 hợp lệ nhưng vượt kho để kiểm tra 409, rồi chạy lại toàn bộ đạt. Không sửa API để ép PASS.

Kiểm tra thiếu credentials ADMIN: Runner exit 1 đúng mong đợi, **0 HTTP requests**, thông báo rõ `adminEmail`, `adminPassword`. Kiểm tra tĩnh còn xác nhận assertion thất bại sẽ dừng luồng và 18 liên kết nội bộ hợp lệ; `git diff --check` đạt.

Environment thực nằm trong `.verification` bị Git ignore. Đã tạo một tài khoản quản trị **mới riêng trên local**, cập nhật role có điều kiện đúng ID/email/tên vừa tạo, rồi login qua API. Không thay quyền tài khoản sẵn có, không dùng credential production và không thay/xóa volume.

SMTP local đang bật với `mailpit:1025`; chưa cấu hình gửi Internet. Gửi tới email khách đã đăng ký được mã nguồn hỗ trợ qua recipient API của Auth; cần cấu hình SMTP thật theo [hướng dẫn](email-thuc.md).

## Phần chưa được xác minh trong lượt này

- Chưa chạy collection mới trên VPS; không rollout hoặc xác nhận CI/CD/deploy mới.
- VNPay mới kiểm tra tĩnh và đối chiếu contract; chưa có kết quả merchant sandbox/ngân hàng thật của bộ mới.
- Bài Postman không tự assert Mailpit/Grafana. Chưa ghi PASS email/monitoring từ việc HTTP nghiệp vụ thành công; demo có bước đối chiếu riêng.
- Không chạy tải k6 hoặc kiểm thử race/concurrency trong lượt này.

Hướng dẫn chạy: [README Postman](../tests/postman/README.md). Trình bày và đối chiếu kho: [kịch bản demo](demo-luong-hoat-dong.md).
