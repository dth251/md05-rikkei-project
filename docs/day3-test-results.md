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

## Metrics/security/recovery và harness local

Sáu app có registry Prometheus, private `/actuator/prometheus`, tag application; test xác minh JVM memory metrics và `/actuator/env` được bảo vệ. Local 69 unit tests + sáu bootJars đạt; Gateway ba live tests đạt với `GATEWAY_FAILURE_TEST=true`. Product stop trả catalog 503, Auth vẫn 200; Order qua Gateway có thể trả controlled 504 khi upstream response deadline hết. Redis stop chặn login/Order bằng 503, catalog/Auth vẫn 200; restart phục hồi. Các recovery flags chỉ dùng local/CI.

[PR #14 CI](https://github.com/dth251/md05-rikkei-project/actions/runs/38156696424) success 2026-10-11 16:51:22 UTC ở head `10d7eadb78c61b9500a364022f1da71acce99fa7`, 69 unit/20 live với đầy đủ Product/Redis/Payment/Notification/broker flags. Merge `8ece1e0a5d2b0da16e83369facad1815803746f6` (49); chưa suy ra metrics đã deployed tại thời điểm merge.

Local k6 v2.3.0: 100 VUs đồng thời/1 stock → một 201, 99×409; SQL available=0/reserved=1/orders=1. p95 16126ms, p99 16141ms ở bài consistency này, không coi là throughput production. Order smoke riêng 2/s ×5s tạo 11 đơn (arrival boundary); Java fixture finish xác nhận cả 12 orders load/race qua Saga và reserved=0. Login burst riêng có 429. Newman Product/Inventory/Saga chạy lại 12 requests/16 assertions/0 failures sau bounded async polling.

Ghi nhận các lần thử trước khi đạt: Product metrics ban đầu 401 vì matcher public catalog chưa gồm Prometheus; sửa matcher rồi 9 unit đạt. Gateway scrape vượt default timeout 5s trên máy Windows chạy nhiều JVM; nâng timeout **test** lên 30s rồi 16 unit đạt, không đổi timeout production. Product outage test quan sát Order 504 nên contract ghi rõ deadline Gateway, không ghi sai thành 503. Fixture prepare local bị 503 khi chạy trùng lúc recovery test stop Product; chạy tuần tự sau phục hồi đạt. Postman lúc đầu đọc Order còn PENDING trước khi Saga xử lý; thêm polling tối đa 30 lần và rerun đạt. Bash sampler được sửa awk ternary/CSV trước khi dùng bản đã kiểm chứng.

## Calibration trước rollout metrics (không dùng làm capacity cuối)

Guard từ chối stage khi VPS đang rollout có CPU=100%. Sau rollout hạ tầng (44), calibration 1 RPS ×5s lúc 16:51 UTC thực hiện 6 HTTP 200, actual 1.1523 RPS, p95 2419ms; threshold latency fail và harness dừng tăng tải đúng thiết kế. RAM min 68.55 MiB, CPU max 78.43%. Đây là calibration cùng hoạt động quan sát/health, không phải đo capacity steady-state. [Main 44](https://github.com/dth251/md05-rikkei-project/actions/runs/38156063749) verify/publish/deploy success; SSH 16:51:15–16:51:25 UTC xác nhận cả sáu image `6bca54e8d915a786eb5aa6c49b00fd57f90389fe`, UP, queue ready/unacked=0/consumer=1 và hai pending Outbox=0.
