# Monitoring và phép đo VPS ngày 14/10/2026

Prometheus và Grafana đã chạy trên VPS; dashboard `commerce-overview`
có 22 panel. Sáu service, năm target hạ tầng và target Prometheus tự thu
metrics đều UP. Grafana database và datasource Prometheus trả kết quả OK.
Dashboard có HTTP/Gateway throughput, p95/p99, lỗi/429, JVM/GC, CPU/RAM,
disk, Outbox, RabbitMQ, email và số lần scheduler hết hạn.

Bản ứng dụng đang chạy là
`069ad87d0cdfd768f847dbd214d85095a693947e` (mốc 157), rollout hoàn tất
01:30:20 +07:00. Kiểm tra sau tải lúc 01:42 +07:00 xác nhận sáu
image/OCI revision cùng SHA, healthy, restart=0; HTTPS `/gateway-status`
trả UP. Outbox Order/Payment và toàn bộ queue ready/unacked/DLQ đều 0.
Queue `notification.expiry.q` có một consumer.

## Điều kiện phép đo

- VPS: 1 CPU, RAM 4.926 MiB; sáu JVM, PostgreSQL, RabbitMQ, Redis,
  SMTP sandbox và monitoring cùng chạy.
- Load generator: k6 2.3.0 ở máy Windows client, gọi domain HTTPS qua
  Nginx/Gateway, một `GET /api/products?page=0&size=20` mỗi iteration.
- 50 tài khoản đăng ký/login qua Auth thật; fixture tạo xong lúc
  01:40:33 +07:00. Trước đo xác nhận cả 50 token còn hạn ít nhất 846
  giây và một request catalog trả 200 với `X-Gateway: api-gateway`.
- Constant arrival rate 500 rồi 1.000 iterations/s; 10 giây/mức,
  100 VUs ban đầu, tối đa 200, request timeout 3 giây, graceful stop
  5 giây. Không đổi quota hoặc giả IP để bỏ giới hạn.
- Trước target 500, baseline CPU 40,40%, RAM khả dụng 2.179,23 MiB;
  trước target 1.000, CPU 12,06%, RAM khả dụng 2.142,14 MiB. Có cooldown
  và kiểm tra CPU/RAM/swap trước khi tăng tải.

## Kết quả hợp lệ

| Số liệu | Target 500 requests/s | Target 1.000 requests/s |
|---|---:|---:|
| Requests thực sự được phát | 800 | 800 |
| Request rate theo summary k6 | 63,03/s | 64,54/s |
| HTTP 200 | 9 | 0 |
| Successful throughput theo summary k6 | 0,71/s | 0/s |
| Transport errors / timeout | 791 | 800 |
| Tỷ lệ `http_req_failed` | 98,875% | 100% |
| HTTP 429 / HTTP 5xx nhận được | 0 / 0 | 0 / 0 |
| Dropped iterations | 4.201 | 9.200 |
| `http_req_duration` p95 | 2.917,93 ms | 2.760,32 ms |
| `http_req_duration` p99 | 3.000,08 ms | 2.938,34 ms |
| TLS handshake p95 | 1.234,19 ms | 1.949,46 ms |
| CPU lớn nhất, mẫu 2 giây | 100% | 100% |
| RAM khả dụng thấp nhất | 2.150,81 MiB | 2.082,71 MiB |
| Swap đã dùng lớn nhất | 19,70 MiB | 56,66 MiB |
| Exit code k6 | 99, threshold fail | 99, threshold fail |

Cả hai mức tải đều không đạt threshold. Target yêu cầu không đồng nghĩa
với số request phát được hoặc throughput thành công. RPS trong summary
chia theo thời gian chạy gồm graceful stop, khoảng 12,69 và 12,39 giây;
không lấy số request chia cho 10 giây để đổi kết luận. Giới hạn 200 VUs
và timeout là một phần của kịch bản; kết quả này không xác định capacity
tối đa của VPS cho mọi workload.

p95/p99 trong bảng là `http_req_duration` của toàn bộ request, phần lớn
là lỗi, và không bao gồm thời gian blocked/connect/TLS. Đây không phải
p95/p99 của các giao dịch catalog thành công. Không có HTTP 5xx nhận
được trong k6 không có nghĩa là không có vấn đề: hầu hết request hết
timeout trước khi client nhận được response.

CPU host bão hòa trong lúc thử; TLS handshake và thời gian chờ response
cũng tăng. Chưa profile để xác định thành phần gây nghẽn, nên chưa kết
luận một service cụ thể là nguyên nhân. Không thay resource limits,
timeout, quota hoặc threshold để làm kết quả đạt.

## Kiểm chứng dữ liệu monitoring

Prometheus `query_range` từ 01:40:30 đến 01:42:30 +07:00, step 15 giây,
trả chín điểm cho mỗi query throughput Gateway, CPU host và RAM khả
dụng. CPU theo `rate(node_cpu_seconds_total[1m])` đạt 92,02%; RAM khả
dụng trong scrape thấp nhất 2.088,99 MiB. Cửa sổ rate và scrape 15 giây
làm số liệu khác CSV 2 giây. Dashboard throughput/latency dùng cửa sổ
2–5 phút, không kỳ vọng bằng summary k6 10 giây.

Sau tải, script `verify-vps-monitoring.sh` vẫn đạt: 6 service + 5 target
hạ tầng UP, dashboard 22 panel, datasource OK, metric HTTP/resource và
hai snapshot Outbox hợp lệ. Không restart container để lấy kết quả này.

Snapshot nghiệp vụ lúc 01:55:34 +07:00: Prometheus ghi
`commerce_reservation_expired_total=1`, email `SENT=3`, các state email
còn lại bằng 0 và `commerce_email_snapshot_valid=1`. Các panel expiry và
email có dữ liệu từ ba ca SMTP thực tế.

## Fixture và lần đo bị loại

Một lượt trước lúc 01:37 dùng fixture đã tạo lúc 01:01. Access token của
Auth chỉ có thời hạn 15 phút, đã hết hạn trước khi chạy; kiểm tra riêng
token cũ trả HTTP 401. Lượt này không
dùng để kết luận throughput catalog. Các summary/CSV ban đầu được giữ
riêng để đối soát. Hai cột kết quả ở trên dùng fixture mới và preflight
xác thực thành công.

Khi demo lại, chạy `multiUserLoadFixture -PfixtureAction=prepare` sát thời
điểm đo, kiểm tra token còn hạn cho toàn bộ cửa sổ thử và xác nhận một
request HTTPS có token trả 200 qua Gateway. Credentials nằm trong file
private được tool Java giới hạn quyền truy cập; không đưa file fixture
hoặc token vào Git, dashboard hay báo cáo.

Raw JSON/CSV và XML bằng chứng nằm trong `.verification`, bị Git ignore.
Không xóa các tài khoản/sản phẩm fixture hoặc dữ liệu/volume trên VPS.
Hướng dẫn tunnel Grafana/Prometheus/Mailpit nằm trong
[runbook](expiry-email-monitoring.md). Nhật ký SHA, CI và deploy nằm trong
[commits-and-deployments](commits-and-deployments.md).
