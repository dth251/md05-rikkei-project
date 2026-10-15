# Đơn hết hạn 150 giây, email và monitoring

Cập nhật cuối ngày 14/10/2026: VPS đã chuyển Notification sang Gmail SMTP
lúc 23:02:42 +07; binary vẫn mốc 168, local giữ Mailpit. Phần sandbox bên
dưới mô tả checkpoint trước thay đổi. Xem [cấu hình SMTP thật](https://github.com/dth251/md05-rikkei-project/blob/8a909c99a3cf246d26da9148f5502b02f37bf8a0/docs/email-thuc.md)
và [bằng chứng mới](evidence/gmail-smtp-2026-10-14.json).

Ngày 14/10/2026, nhánh `feature/expiry-150s-email-monitoring` tiếp nối
worktree đêm trước và đổi thời hạn 5 phút thành 150 giây. Hạ tầng runtime
nằm trong `docs/infrastructure/`; Compose, CI, release và scripts đều dùng
đường dẫn này. SHA/deploy theo từng checkpoint ở `commits-and-deployments.md`.

Checkpoint **14/10/2026, 10:50 +07**: [PR #36](https://github.com/dth251/md05-rikkei-project/pull/36)
đã merge main mốc 168 `d2779d1595fb5541675e65604eb07ac633ccf91b`;
[CI/CD main](https://github.com/dth251/md05-rikkei-project/actions/runs/38505256214)
success, rollout hoàn tất 10:41:31 +07. Sáu app đúng revision/healthy,
restart=0; Order runtime PT150S, Notification migrations 001–005.
Monitoring/history/query/unit và 3/3 SMTP VPS tests đạt. Snapshot
[metrics sau rollout](evidence/monitoring-2026-10-14.json) không chứa
credential/định danh khách hàng và không phải kết quả capacity.

## Thời hạn thanh toán

Order ONLINE ở PENDING_PAYMENT có thời hạn 150 giây kể từ createdAt.
Đúng hạn hoặc quá hạn, đơn chuyển EXPIRED (hủy vì chưa thanh toán), hoàn
reserved về available và ghi order.expired trong cùng transaction.
Scheduler quét mỗi giây, tối đa 50 đơn/lượt; backlog hoặc service downtime
có thể làm việc quét chậm hơn. Khi service phục hồi, các đơn quá hạn tiếp
tục được xử lý. COD không thuộc timeout thanh toán ONLINE.

Scheduler và payment consumer dùng cùng pessimistic row lock. Consumer
kiểm tra deadline sau khi khóa và dùng cùng policy/expiry service; dù
scheduler chưa quét, payment đến sau hạn cũng không xác nhận đơn. Đơn đã
CONFIRMED không bị expiry hoàn kho. Khi kho không nhất quán, toàn bộ
transaction rollback; không đổi trạng thái hoặc ACK event trước commit.

Nếu tiền đã được capture sau khi đơn hết hạn, Payment giữ chứng cứ nhận
tiền, refundStatus=REQUIRED và xử lý đối soát/hoàn tiền theo luồng hiện có.
Không phục hồi đơn hoặc giữ lại kho, không coi REQUIRED là tiền đã hoàn.
Inbox và trạng thái terminal chống xử lý lại event.

## Email SMTP sandbox

Notification dùng Spring Mail, tra email chủ đơn qua API private của Auth
và lưu email_deliveries trước khi worker gửi SMTP. order.confirmed gửi
xác nhận; order.cancelled gửi thông báo hủy; order.expired gửi thông báo
hủy vì quá hạn thanh toán. Kho/thanh toán không phụ thuộc SMTP hoạt động.
Retry có giới hạn; kết quả không chắc chắn giữ UNKNOWN để đối soát,
không gửi lại mù quáng.

order.expired có queue riêng notification.expiry.q, DLQ và policy riêng.
Trong rollout từng service, consumer cũ không đọc queue mới; event chờ
bền vững đến khi Notification mới nhận. Một email/order/stage theo unique
constraint; replay event không tạo thêm delivery. SMTP vẫn có khả năng
gửi trùng nếu máy chủ đã nhận trước khi connection mất; không tuyên bố
exactly-once email.

Profile docker-compose.smtp-test.yml chạy Mailpit, nhận thư SMTP thật
nhưng giữ trong sandbox. Inbox chỉ bind 127.0.0.1:18025, không gửi đến
hộp thư Internet. Dùng SSH tunnel để xem demo. SMTP thật ngoài sandbox
cần cấu hình private riêng, không đưa credential vào Git/log.

## Prometheus và Grafana trên VPS

Profile monitoring gồm Prometheus, Grafana, node/postgres/redis exporters;
RabbitMQ dùng metrics plugin hiện có. Dashboard commerce-overview có
throughput, HTTP p95/p99, lỗi/429, JVM/GC, CPU/RAM/disk, Outbox, queue,
email và số lần scheduler hết hạn. Không có dữ liệu không có nghĩa là 0.
Prometheus/Grafana có volume riêng giữ history qua recreate; retention
Prometheus 7 ngày hoặc 1 GB. Không public Actuator hoặc cổng giám sát.

Cấu hình private: MONITORING_ENABLED=true; MONITOR_DB_PASSWORD và
GRAFANA_ADMIN_PASSWORD ngẫu nhiên 64 hex; SMTP_SANDBOX_ENABLED=true,
SMTP_ENABLED=true, SMTP_HOST=mailpit, SMTP_PORT=1025, SMTP_AUTH=false,
SMTP_STARTTLS=false, SMTP_FROM=orders@example.com; recipient token riêng
cho Auth/Notification. provision-demo-profiles.sh tạo sandbox/monitoring
trước rollout ứng dụng. Không thay credential đang dùng ngoài sandbox.

Preflight yêu cầu tối thiểu 1 GiB RAM khả dụng, 4 GiB đĩa trống và đồng hồ
UTC đúng. Khi NTP chưa đồng bộ, đối chiếu hai HTTPS Date độc lập với sai
lệch tối đa 5 giây; không ghi rằng NTP đã đồng bộ. verify-vps-monitoring.sh
kiểm tra sáu service/five dependency targets, dashboard và datasource
Prometheus thực tế. Metric rate/quantile cần nhiều scrape.

Verifier đối chiếu từng panel theo id/title/query/unit với file provisioning,
đọc history hai phút với step 15 giây và ít nhất ba mẫu cho mỗi service.
CI cũng so sánh cùng projection trước/sau restart Grafana/Prometheus;
không chỉ kiểm tra số panel. Không có dữ liệu không được thay bằng số 0.

~~~~powershell
ssh -i <private-key-path> -o StrictHostKeyChecking=yes -N -L 13001:127.0.0.1:13000 -L 19091:127.0.0.1:19090 -L 18825:127.0.0.1:18025 deploy@42.96.2.60
~~~~

Grafana http://127.0.0.1:13001 (tài khoản ops, mật khẩu private);
Prometheus http://127.0.0.1:19091; Mailpit http://127.0.0.1:18825.
Cổng local chọn khác stack Docker local.

## Demo 500 và 1.000 requests/giây

catalog-capacity.js dùng 50 tài khoản Auth thật, một GET /api/products mỗi
iteration và chia đều theo iteration. Không đổi quota hoặc giả header để
bỏ rate limit. Java multiUserLoadFixture của Order tạo tài khoản/sản phẩm
riêng; token giữ trong file private, summary chỉ lưu số liệu.
Chạy k6 ở máy client để không chiếm CPU VPS làm load generator.

run-capacity-demo.ps1 chạy target 500 và 1.000 trong 10 giây/mức, giới hạn
VUs, request timeout và graceful stop. Thu CSV tài nguyên đồng thời; chỉ
tăng mức tải sau khi baseline CPU/RAM/swap phục hồi. Threshold không đạt
vẫn lưu thành kết quả, không đổi ngưỡng sau đo. Báo cáo actual requests/s,
successful throughput, p95/p99, lỗi, 429, transport errors, dropped
iterations, CPU/RAM/swap và health sau tải. Target chưa chạy không coi là
đạt. Có thể điều chỉnh duration 5–30 giây khi cần kiểm chứng lại.

VPS hiện có 1 CPU và khoảng 4,9 GiB RAM. Kịch bản đo không phải cam kết
capacity 1.000 successful requests/s. So sánh k6/Grafana trên cùng cửa
sổ; panel rate dùng cửa sổ 2–150 giây nên khác summary k6 10 giây.

Access token Auth có hạn 1150 giây: chuẩn bị fixture sát lúc đo, xác minh
token còn hạn đủ cho cả hai mức tải và thử một request HTTPS trả 200 qua
Gateway trước khi chạy. Kết quả thực tế, gồm threshold fail và giới hạn
diễn giải, trong [báo cáo monitoring/tải VPS](capacity-monitoring-report.md).

## Kiểm thử

Java Order kiểm tra lock race, thanh toán trước/sau hạn, scan đến muộn,
hoàn kho một lần và rollback khi reserved không khớp. Notification kiểm
tra envelope/schema và SMTP UTF-8 thật. Live tests dùng Gateway,
RabbitMQ/PostgreSQL và Mailpit: recipient Auth thật, replay, SMTP outage,
restart Notification, email hủy và email hết hạn. CI rút ngắn tuổi fixture
qua SQL; SMTP_EXPIRY_REAL_WAIT=true chờ đủ 150 giây, không sửa timestamp khi
xác minh VPS. Xem kết quả deploy thực tế trong commits-and-deployments.md.
