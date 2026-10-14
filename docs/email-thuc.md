# Gửi thông báo đến email khách hàng đã đăng ký

Notification đã có chức năng gửi SMTP thật qua Spring Mail. Email người nhận được tra từ tài khoản Auth theo `userId` của đơn qua API nội bộ; không lấy từ email người gửi hoặc một địa chỉ cố định dùng chung.

- `order.confirmed`: thông báo đơn đã xác nhận; ONLINE cần thanh toán thành công và Order xử lý event. Chỉ tạo đơn PENDING_PAYMENT chưa phát xác nhận thành công.
- `order.cancelled`: thông báo hủy.
- `order.expired`: thông báo quá hạn thanh toán.

Môi trường local kiểm tra ngày 14/10/2026 đang dùng Mailpit (`SMTP_ENABLED=true`, host `mailpit`, port `1025`). Thư được SMTP nhận trong sandbox, chưa chuyển tới hộp thư Internet. Bộ Postman mới dùng email giả `example.com`; để kiểm chứng hộp thư thật, cần một tài khoản khách đăng ký bằng email nhận thử thực tế và một đơn mới của đúng tài khoản đó.

## Cấu hình cần có

Cần chọn môi trường local/VPS và một dịch vụ SMTP gửi thư. Điền cấu hình vào `.env` private của môi trường được chọn, không đưa credentials vào Git hoặc Postman:

```dotenv
SMTP_SANDBOX_ENABLED=false
SMTP_ENABLED=true
SMTP_HOST=<may-chu-SMTP-do-dich-vu-cung-cap>
SMTP_PORT=587
SMTP_USERNAME=<tai-khoan-gui>
SMTP_PASSWORD=<mat-khau-SMTP-hoac-mat-khau-ung-dung-private>
SMTP_FROM=<dia-chi-gui-duoc-dich-vu-cho-phep>
SMTP_AUTH=true
SMTP_STARTTLS=true
```

Mẫu trên dành cho SMTP có STARTTLS trên port 587; dùng đúng host, port và phương thức xác thực nhà cung cấp hỗ trợ. Giữ nguyên `NOTIFICATION_RECIPIENT_TOKEN` private đang hoạt động giữa Auth và Notification. Không cần đổi email người nhận trong source hoặc thêm API public nhận email tùy ý.

Không áp dụng `docker-compose.smtp-test.yml` khi chạy Notification gửi thư thật: file này ghi đè host thành Mailpit và tắt xác thực/STARTTLS, dù `.env` đã có giá trị SMTP mới. Khi recreate Notification, giữ project name, image/tag hiện hành và các overlay quan sát đang dùng; chỉ đổi cấu hình service Notification. Việc bật `.env` đơn lẻ chưa đổi container đang chạy. Không chạy lại toàn bộ stack hoặc xóa volume để đổi SMTP.

## Kiểm chứng sau khi bật

1. Xác nhận Notification healthy, SMTP bật và host hiệu lực đúng dịch vụ gửi, không còn là Mailpit. Chỉ đọc các trường không chứa mật khẩu.
2. Đăng ký/login tài khoản khách với email nhận thử thật. Tạo một đơn ONLINE mới, thanh toán theo luồng được chọn và chờ Order CONFIRMED; dùng ADMIN thử nghiệm cho simulate nếu đang demo mô phỏng.
3. Kiểm tra delivery của đúng order/stage: Notification đã tra đúng email chủ đơn, trạng thái gửi SENT. Với UNKNOWN cần đối soát, không gửi lại mù quáng.
4. Kiểm tra hộp thư và thư rác của email nhận thử, đối chiếu mã đơn và tổng tiền. SMTP chấp nhận hoặc SENT chưa tự chứng minh thư đã vào inbox; chỉ ghi nhận nhận thành công khi đã thấy thư ở hộp thư thật.
5. Replay thanh toán cùng trạng thái để kiểm tra không tạo thêm delivery cùng order/stage. SMTP có thể nhận thư rồi mất kết nối; không tuyên bố email exactly-once.

Chưa bật SMTP Internet hoặc xác minh inbox trong lượt bàn giao Postman. Cần thông tin dịch vụ gửi và cấu hình private của môi trường được chọn trước khi thực hiện thay đổi runtime.
