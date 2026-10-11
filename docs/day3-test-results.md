# Ngày 3: kiểm chứng VPS, tải và bài nộp

Kế hoạch: [6 app có API Gateway](../Ke_hoach_Omnichannel_3_ngay_6_ung_dung_Co_API_Gateway.md). Mốc đầu ngày 3 là main `7ff0c5e62db219ba66c38738c30d78fef78702c4` (38). Số commit tiếp tục từ lịch sử thật, không dùng lại số tham khảo trong kế hoạch.

## Audit ban đầu, 2026-10-11 16:14–16:16 UTC

VPS 969 MiB RAM, 2047 MiB swap; available RAM khoảng 114 MiB, swap đã dùng 1220 MiB. Sáu app/DB/MQ/Redis đang chạy. Chỉ 22/80/443 public; app 8080–8085 loopback, DB/MQ/Redis không publish. Nginx và certbot.timer active, TLS còn hạn tới 2027-01-09. UFW **inactive**, chưa ghi đạt firewall.

`secure-vps-network.sh` thêm allow SSH/HTTP/HTTPS trước khi bật default deny incoming, giữ các rule hiện có và từ chối khi thấy listener TCP public ngoài ba cổng. Docker có thể đi ngoài UFW; điều kiện bắt buộc vẫn là Compose loopback/private, không dựa riêng vào firewall.

`verify-rollback.sh` chỉ tái tạo Notification bằng một candidate known-good đã cache, ép smoke check đầu thất bại rồi kiểm tra deployer khôi phục image trước. Harness không kiểm thử lỗi registry hoặc rollback schema; kiểm tra manifest/current không thay đổi. Kết quả thực thi sẽ được ghi sau khi chạy, không coi có script là đã đạt.

Review deployer phát hiện `verify_service` được gọi trong điều kiện `if ... && ...`: Bash bỏ hiệu lực errexit trong function này. Pipeline curl/jq health thất bại có thể bị câu `if` sau đó che mất exit status, dẫn tới ghi nhận candidate sai. Đã thêm `|| return 1` tại health/smoke/catalog probes; rollback test phải ép đúng lỗi này và xác minh candidate bị từ chối. Các app ngày 2 đã được kiểm tra UP trực tiếp bằng SSH/E2E; phát hiện này không thay thế các bằng chứng health thực tế trước đó.

Load GET catalog và load ghi Order được đo riêng; 401/403/429 security test tách khỏi race kho. K6 dùng JavaScript của công cụ, unit/integration/recovery service dùng Java. Không tuyên bố 500–1000 RPS khi chưa đo; dừng tăng tải khi RAM/CPU/latency/error cho thấy VPS thiếu tài nguyên.

## Firewall và rollback đã thực thi

UFW được bật với default deny incoming, allow outgoing; chỉ allow 22/80/443 cả IPv4/IPv6. SSH mới kết nối được; audit root 2026-10-11 16:26:59 UTC xác nhận active. Các health 8080–8085 đều UP lúc 16:26:36 UTC, DB/Redis/MQ tiếp tục private. HTTPS dùng domain `md05-rikkei-project.duckdns.org`.

Rollback probe trên immutable staged release có deployer đã sửa chạy 16:21:20–16:22:51 UTC. Candidate Notification `2f8bb9446582ea3788222b3112c8b655cea331c2` đã được recreate/healthy, smoke failure có chủ đích khiến deployer trả lỗi và khôi phục `7ff0c5e62db219ba66c38738c30d78fef78702c4` healthy/UP. SHA256 `.image-tags` và symlink `current` không đổi. Harness PASS; không xóa dữ liệu/volume. Đây là rollback image sau smoke failure, không phải rollback schema hoặc kiểm thử registry.
