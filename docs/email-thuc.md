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

VPS đã bật Gmail SMTP lúc **23:02:42 +07 ngày 14/10/2026**, giữ nguyên image
Notification mốc 168. Đơn #145 được Notification gửi tự động với trạng thái
SENT ngay lần đầu. Sau khi render/kiểm tra [mẫu hóa đơn HTML](email/README.md),
đã gửi bản mẫu tới đúng địa chỉ người dùng chỉ định lúc **23:13:29 +07**,
SMTP trả 250. Chưa xác nhận inbox tại thời điểm ghi tài liệu. Local vẫn
dùng Mailpit. [Bằng chứng cấu hình và gửi mẫu](evidence/gmail-smtp-2026-10-14.json).

## Gmail trên VPS đang triển khai

Google hỗ trợ `smtp.gmail.com`, xác thực và STARTTLS trên port 587;
xem [thiết lập SMTP chính thức](https://support.google.com/mail/answer/7104828?hl=vi).
Với cách xác thực của service hiện tại, dùng mật khẩu ứng dụng sau khi bật
xác minh hai bước, theo [Google Account Help](https://support.google.com/accounts/answer/185833?hl=vi).

1. Đăng nhập đúng Gmail dùng làm người gửi.
2. Bật [xác minh hai bước](https://myaccount.google.com/signinoptions/two-step-verification).
3. Mở [Mật khẩu ứng dụng](https://myaccount.google.com/apppasswords), đặt tên `MD05 VPS`, chọn Tạo.
4. Nhập mật khẩu 16 ký tự vào prompt kín của script bên dưới. Không dùng mật khẩu đăng nhập Gmail. Một số tài khoản có chính sách không cho tạo app password; kiểm tra hướng dẫn Google nếu thiếu mục này.

[Script cấu hình VPS](infrastructure/scripts/configure-gmail-vps.ps1) dùng
SSH có kiểm tra host key, xác thực SMTP qua TLS trước khi đổi runtime,
backup `.env` quyền 600, cập nhật riêng SMTP và giữ nguyên image Notification
đang chạy. Chỉ recreate Notification; khi Compose/health/config không đạt,
khôi phục cấu hình cũ. Không truyền password qua tham số dòng lệnh hoặc lưu
vào file kết quả. File kết quả chỉ chứa trạng thái đã bỏ credentials.

```powershell
New-Item -ItemType Directory -Force .verification/smtp | Out-Null
powershell -NoProfile -ExecutionPolicy Bypass `
  -File docs/infrastructure/scripts/configure-gmail-vps.ps1 `
  -Sender '<gmail-gui>@gmail.com' `
  -SshKey '<private-key-path>' `
  -ResultFile "$PWD/.verification/smtp/status.json"
```

Chạy từ repository này. Script phục vụ VPS `deploy@42.96.2.60`, deployment
root `/opt/omnichannel-ecommerce`, project `omnichannel-day1`; không áp dụng
nguyên trạng cho VPS khác. `configured` chỉ chứng minh SMTP auth/config và
Notification healthy, chưa chứng minh khách đã nhận thư. Sau đó thực hiện
bài đặt đơn và đối chiếu inbox ở phần trên.
