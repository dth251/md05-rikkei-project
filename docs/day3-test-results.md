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

## Kết quả VPS cuối: chức năng đạt, tải chưa đạt mục tiêu

[Main 49](https://github.com/dth251/md05-rikkei-project/actions/runs/38157321547) hoàn tất deploy sáu app lúc 2026-10-11 17:08:44 UTC. [Audit metrics](evidence/vps-metrics-audit.txt) 17:09:10–17:09:27 xác nhận sáu image SHA 49, private JVM metrics/application tag; public Actuator trả 404. Catalog, Order 2/s và race dưới đây chạy với sáu app SHA 49, không có rollout ứng dụng trùng cửa sổ đo. Order 1/s chạy sau selective rollout Order SHA 53; năm app còn lại vẫn SHA 49. Các thay đổi 53/54 là harness/test, không đổi nghiệp vụ hay quota production.

Máy phát tải Windows truy cập HTTPS domain từ xa; race truy cập Order qua foreground SSH tunnel loopback, vẫn xác thực JWT nhưng không đi qua quota Gateway. k6 v2.3.0; catalog target 50/s ×20s, max 100 VUs, timeout 3s, abort threshold sau 5s. Order target 2/s rồi giảm 1/s ×20s, max 20 VUs. Race 100 VUs ×1 iteration, 100 key khác nhau, stock=1. Không trộn security/quota với tính đúng đắn inventory.

| Bài đo | HTTP hoàn tất / actual tổng RPS | Kết quả response | p95 / p99 HTTP (ms) | Dropped / kết luận |
|---|---|---|---|---|
| Catalog target 50/s | 90 / 15.0018 | 0×2xx, 0×4xx/5xx/429; 90 timeout, 100% failure | 2947.17 / 2948.29 | 111 dropped, 99 interrupted; abort sau ~6s, **FAIL** |
| Order target 2/s | 32 / 1.4256 | 26×201, 6 lỗi, 0×429; failure 18.75% | 8362.99 / 8784.35 | 8 dropped; threshold **FAIL** |
| Order giảm 1/s | 19 / 0.9338 | 19×201, 0×4xx/5xx/429/transport failure | 6898.42 / 8012.24 | 2 dropped; HTTP checks đạt, **chưa đạt toàn bộ arrival target** |
| Race VPS | 100 / 6.0188 | 1×201, 63×409, 36 response khác | 16439.75 / 16519.19 | Strict kỳ vọng 1/99 **FAIL**; SQL một order/không âm kho đạt |
| Race local chạy lại với counter mốc 55 | 100 / 18.9192 | 1×201, 99×409, 0×5xx/429/transport failure | 5260.57 / 5270.07 | Strict 1/99 và SQL đạt; không suy ra capacity VPS |

**Catalog successful throughput = 0 RPS**, không phải 15 RPS. 15.0018 là tổng request hoàn tất ở client, gồm timeout; p95/p99 là thời gian timeout, không phải latency phản hồi thành công. Không chạy các stage 100/250/500/1000 sau stage 50 lỗi và resource guard. Không khẳng định đạt 50, 500 hoặc 1000 RPS.

Order 2/s và race VPS dùng harness trước mốc 55, chưa có counter phân loại mọi status. [Private Order metrics](evidence/vps-order-status-metrics.txt) lúc 17:16:26 có POST `/api/orders`: 6×503, 27×201, 1×200. Sau khi đối chiếu 26 đơn load + một đơn Newman và replay, số 503 khớp sáu lỗi load; đây là đối chiếu metric tổng hợp, không phải log per-request. Không suy đoán mã HTTP của 36 response race khác. Mốc 55 bổ sung counter 2xx/4xx/5xx/429/status=0 cho các lần đo sau; đã kiểm chứng race local và Order VPS giảm 1/s. Không chạy lại burst 100 requests VPS chỉ để thay số liệu lỗi bằng kết quả đẹp hơn.

409 là kết quả mong đợi trong race; `http_req_failed=99%` của local race phản ánh cách k6 mặc định phân loại 409, không phải 99% lỗi nghiệp vụ. Kết luận consistency dùng `orders_created`, `stock_conflicts`, checks và SQL. Order load giảm 1/s không đặt latency SLO nên exit 0 chỉ chứng minh HTTP checks đạt; p95 ~6.9s và dropped=2 vẫn là giới hạn rõ ràng.

| Resource CSV UTC | CPU max | RAM available min (MiB) | Swap used max (MiB) | Phạm vi |
|---|---:|---:|---:|---|
| Catalog 17:10:21–17:10:40 | 100% | 54.30 | 1195.30 | Bao gồm abort và recovery |
| Order 2/s 17:12:19–17:12:48 | 100% | 50.54 | 1200.08 | Sampler khởi động muộn ~10s; **chỉ một phần cửa sổ load**, không đại diện toàn bài |
| Race 17:17:04–17:18:33 | 100% | 49.62 | 1342.23 | Bao gồm recovery sau burst |
| Order 1/s 17:23:36–17:24:05 | 100% | 46.77 | 1209.17 | Sampler chạy đồng thời load và sau load |

Sáu JVM/DB/MQ trên VPS 969 MiB RAM đang dùng nhiều swap. Các mẫu CPU/RAM hỗ trợ quyết định dừng/throttle; chưa có profiler nên không gán chắc chắn bottleneck cho một service. Muốn đánh giá mục tiêu cao hơn cần tăng tài nguyên hoặc đo/tối ưu JVM/dependency latency rồi chạy lại từ target thấp. Không nới threshold hoặc quota để ghi nhận PASS.

## HTTPS nghiệp vụ, quota và kết thúc fixture

Newman VPS HTTPS thực thi 17:08:31–17:09:20 UTC: **14 requests, 17 assertions, 0 failures**, gồm polling Payment 404→200 và Order PENDING→CANCELLED. Kiểm chứng catalog, ADMIN Product/stock, tạo WEB order, replay cùng key, USER simulate 403, ADMIN FAILED/replay và stock hoàn đúng một lần. Collection có 19 requests nhưng không chạy toàn bộ 19: không có credential ADMIN thật cho login; USER token cấp qua Auth register/login, ADMIN fixture JWT ký trong process kiểm thử. [Trace đã loại credential/header](evidence/vps-api-trace.json) giữ response thật và thứ tự polling.

Gateway live quota ban đầu gửi 20 login tuần tự qua HTTPS nên token refill và không chắc có 429. Mốc 52 đổi fixture sang 20 request concurrent; Java local/VPS kiểm tra 401/403/429 đạt, không thay quota production. [PR #16 CI](https://github.com/dth251/md05-rikkei-project/actions/runs/38158364265) đạt 69 unit/20 live; [main 54](https://github.com/dth251/md05-rikkei-project/actions/runs/38158991947) chỉ deploy **Gateway**, thành công 17:31:10 UTC. Product/Redis/Payment/Notification/broker stop/recovery tests chạy local/CI; không cố ý stop dependencies VPS để ghi hình.

Java SQL race VPS xác nhận available=0/reserved=1/order count=1 trước khi hoàn tất. Lần `finish` đầu lỗi HTTP 503 tại GET Order trong thời điểm Gateway rollout/áp lực tài nguyên; không coi là đạt và không xác định chắc nguyên nhân chỉ từ status. Chạy lại idempotent sau khi stack ổn định, không đổi code: **46 orders CONFIRMED** (26 + 19 load + 1 race), load stock available=955/reserved=0, race stock available=0/reserved=0, hoàn tất **17:40:41 UTC**. [Bằng chứng fixture](evidence/vps-fixture-finish.json) chứa IDs/stock, không token. Rerun SUCCESS không tạo lần trừ kho mới. Fixture rows giữ nguyên để audit; tunnel đã đóng, token files private được dọn sau khi kiểm chứng.

## Deploy thực tế và audit sau tải

[Main 53](https://github.com/dth251/md05-rikkei-project/actions/runs/38158509490) success 17:19:47 UTC, chỉ publish/deploy Order. Main 54 chỉ publish/deploy Gateway. [Audit cuối](evidence/vps-final-audit.txt) **17:41:02–17:41:28 UTC** xác nhận manifest và container image thực:

| App | SHA image |
|---|---|
| Gateway | `06340098eae04131a8e8da856f746f27109916d2` (54) |
| Order | `fb348bd1bc12d208d8ddef5d3373fb235d7a2567` (53) |
| Auth, Product, Payment, Notification | `8ece1e0a5d2b0da16e83369facad1815803746f6` (49) |

Sáu health UP, public gateway-status UP, public Prometheus 404, private JVM metrics/tag sáu app đạt. Ba queue ready=0/unacked=0/consumer=1; Order/Payment pending Outbox=0. Notification SQL: **46 logs/46 fixture orders, min=max=1 log/order**. Inventory SQL xác nhận 46 CONFIRMED và reserved=0. Không có volume hoặc dữ liệu bị xóa. Checkpoint này không tự cập nhật theo SHA main tương lai; head/merge/CI cuối được ghi trong PR hoàn tất.

## Bài nộp và giới hạn

- [Kiến trúc Mermaid](architecture.md), [PNG](evidence/architecture.png), [API contract](api-contract.md), [runbook](day3-operations.md), [nhật ký](commits-and-deployments.md).
- [Ảnh Saga](evidence/saga-demo.png), [ảnh tải](evidence/load-report.png), [video WebM 63.24s](evidence/backend-demo.webm), [danh mục bằng chứng](evidence/README.md).
- Video phát lại response VPS thực trong trace đã redaction, gồm tạo/replay/403/FAILED/CANCELLED/hoàn kho và kết quả tải không đạt. Đây là trình bày bằng chứng backend, không có frontend hoặc video giả gọi API live. Không audio; caption/timestamp trong hình. Đã xem PNG và xác minh video 1440×900, seek/decode ở giây 50.

Phần chức năng và CI/recovery đạt; **capacity và strict response race VPS chưa đạt kỳ vọng**. Chưa có payment thật/refund/SMTP, reservation expiry, Elasticsearch/gRPC, DLQ replay, distributed tracing hoặc HA Gateway. Metrics private có sẵn; chưa chạy thêm Prometheus/Grafana server trên VPS hạn chế RAM. Không tuyên bố exactly-once transport.
