# Bổ sung theo remaining-work

Nhánh triển khai: `feature/remaining-work-implementation`. Ngày bắt đầu: 13/10/2026 (+07).
Đối chiếu từng tiêu chí với [remaining-work](remaining-work.md); không coi file cấu hình
hoặc test fixture là bằng chứng đã deploy hay đã giao dịch với nhà cung cấp thật.

## Các phần đã xây dựng

- VNPay: tích hợp nhánh mốc 129; thêm callback qua Gateway/PostgreSQL, callback đồng
  thời, capture sau expiry, refund trên PostgreSQL và restart Payment sau durable claim.
- SMTP: Notification sở hữu delivery table, DTO/mapper/interface/impl; enqueue cùng
  transaction xử lý event, lấy email qua API nội bộ Auth với credential riêng. Không đọc
  database Auth từ Notification. Unique `(order_id,event_type)` chặn tác vụ trùng.
- Retry SMTP: claim bằng `FOR UPDATE SKIP LOCKED`, lease, backoff tối đa 300 giây,
  tối đa 5 lần mặc định. Lỗi kết nối chắc chắn chưa nhận thư được retry; timeout mơ hồ
  hoặc lease hết sau crash chuyển `UNKNOWN`, không tự gửi lại. Message-ID giữ ổn định.
- Monitoring: Prometheus 15 giây/lần, retention 7 ngày/tối đa 1 GB; Grafana provisioning;
  exporter host/PostgreSQL/Redis và RabbitMQ Prometheus plugin. Outbox pending/tuổi
  event được đọc từ DB, có metric validity khi truy vấn thất bại.
- Tracing: Java agent cho HTTP/gRPC/RabbitMQ; traceparent được lưu cùng Outbox để
  nối request với publisher ở tick sau. Collector lọc theo danh sách thuộc tính cho phép,
  loại events/status message và tên span không kiểm soát trước khi gửi Tempo. Span có
  links bị loại toàn bộ để không chuyển thuộc tính link riêng tư; có thể thiếu span
  batch/consumer dùng links, cần đối chiếu trace nghiệp vụ riêng.
- Logging: Alloy chỉ chuyển audit có action/outcome/trace ID/span ID sang Loki. Toàn bộ
  dòng được viết lại; không chuyển nguyên log ứng dụng chứa payload hoặc exception.
- Alerts: scrape, RAM/disk, 5xx, latency, Outbox, queue/consumer/DLQ và email cần đối soát.
  Alertmanager mặc định giữ alert, chưa gửi tới người khác. Kênh gửi dùng config private.
- Tải: Java fixture tạo 2–50 tài khoản Auth thật, stock riêng; k6 tách catalog/orders/e2e,
  idempotency key mới và counter số đơn tạo/xác nhận. Java đối chiếu quyền sở hữu trên
  PostgreSQL, Payment SUCCESS, Order CONFIRMED và tồn kho chính xác sau tải.
- Vận hành: snapshot JVM/pool/query plan/MQ/Outbox, JFR 120 giây/tối đa 32 MB và
  harness dependency demo có preflight, deploy lock, khôi phục đúng container trong trap.

## Kiểm thử và sử dụng local

Mỗi service dùng wrapper riêng: `gradlew test bootJar --no-daemon`. Các bài `liveTest`
chạy trên stack local/CI, không tự chạy khi app khởi động. CI có stage riêng cho VNPay
và SMTP; fixture signing key là chuỗi công khai chỉ dùng test, không phải merchant secret.

```bash
bash docs/infrastructure/scripts/generate-env.sh # chỉ khi chưa có .env
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --wait
bash docs/infrastructure/scripts/apply-schema.sh notification-service
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.smtp-test.yml --profile smtp-test up -d --wait mailpit
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.smtp-test.yml --profile smtp-test up -d --no-deps --wait notification-service
```

SMTP test tắt TLS/auth riêng cho Mailpit private. Production giữ STARTTLS bắt buộc,
kiểm tra hostname và timeout hữu hạn. Không dùng SMTP test override trên VPS.
Các token/password phải truyền bằng môi trường private; không thêm vào tham số CLI,
collection hoặc báo cáo. Xem biến môi trường trong `.env.example` và stage CI.

Monitoring:

```bash
bash docs/infrastructure/scripts/verify-observability-config.sh
bash docs/infrastructure/scripts/observability-preflight.sh
# MONITOR_DB_PASSWORD lấy từ .env private, không in ra terminal.
bash docs/infrastructure/scripts/provision-monitoring.sh
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml --profile monitoring up -d --wait prometheus grafana node-exporter postgres-exporter redis-exporter
```

Grafana `127.0.0.1:13000`, Prometheus `127.0.0.1:19090`; dùng SSH tunnel để truy cập
VPS. Loki `13100`, Tempo `13200`, Alertmanager `19093` cũng chỉ bind loopback.
Profile tracing tăng giới hạn mỗi JVM lên 480 MiB; cần tính ngân sách cho cả sáu JVM
và backend, bật từng phần sau preflight. Profile logging chỉ gom audit có schema cố định;
thiếu log trong Loki không có nghĩa app không phát sinh exception.

Dashboard ghi rõ “No data” khi chưa có samples. Node exporter trên Docker Desktop
đo VM Linux Docker, không đo toàn bộ Windows host. JFR và fixture chứa token nằm
trong thư mục private `.verification`, không commit/upload.

Tải trên Windows:

```powershell
./docs/infrastructure/scripts/run-multi-user-load.ps1 -K6Path 'C:/private/tools/k6.exe' -Accounts 10 -Rate 1 -Seconds 20
```

Harness dừng các pha sau nếu threshold hoặc reconciliation thất bại; giữ nguyên dữ liệu
để điều tra. Không dùng bước finalize để biến stage k6 thất bại thành đạt. Phân biệt
response 201 với replay 200, payment poll 404 với lỗi, dropped/interrupted iterations
với request thành công. Chưa thể khẳng định 500–1.000 RPS nếu chưa đo đạt.

## CI và tích hợp ngày 13/10

Head `175095963f15aa23846dcce0673384e796758d90` (mốc 140):
[PR CI 38379527058](https://github.com/dth251/md05-rikkei-project/actions/runs/38379527058)
và [push CI 38379521378](https://github.com/dth251/md05-rikkei-project/actions/runs/38379521378)
đều success. Java/build, live integration/recovery, VNPay, SMTP, tải nhỏ nhiều tài khoản,
monitoring/history qua restart, persisted trace privacy, trace nghiệp vụ/exporter outage
và Docker audit/Loki privacy đều đạt. Reports/evidence có trong artifact `java-test-reports`;
bản trích đã loại credential nằm ở [evidence CI](evidence/remaining-ci-140/README.md).

[PR #31](https://github.com/dth251/md05-rikkei-project/pull/31) merge lúc **17:11 +07**,
main mốc 141 `19d9fc6d797154dd15a296590a4c773b7eca08e1`.
[Main CI/CD 38381634257](https://github.com/dth251/md05-rikkei-project/actions/runs/38381634257)
đang chạy tại checkpoint; chưa ghi deploy mới từ việc PR đã merge.

CI Linux, hai tài khoản, target 1 iteration/s, mỗi phase 10 giây, 5 VU ban đầu/tối đa 20:

| Phase | HTTP | p95 / p99 HTTP (ms) | Đơn tạo / xác nhận |
|---|---:|---:|---:|
| Warm-up catalog | 11 | 20,34 / 26,03 | 0 / 0 |
| Catalog | 10 | 13,07 / 14,43 | 0 / 0 |
| Orders | 10 | 61,02 / 73,13 | 10 / finalize riêng |
| E2E | 74 | 26,56 / 101,91 | 11 / 11 |

HTTP failed và dropped iterations đều 0; không 429/transport error, mọi threshold đạt.
Đối chiếu cuối: 21 đơn CONFIRMED, stock từ 50 còn 39/40 theo từng tài khoản, reserved 0.
Số HTTP bao gồm polling; target là iteration/s, không phải cam kết HTTP RPS hay capacity VPS.
Trace fixture nối đủ sáu service, Order/Payment/email có 10/5/5 durable context rows.
Khi collector dừng, bốn đơn mới vẫn được tạo/xác nhận và đối chiếu tồn kho đạt.

## Điều kiện còn cần trước production

Kiểm tra VPS ngày 13/10 sau nâng cấp: root disk 40 GB, còn khoảng 19 GB (50%);
RAM khoảng 4.926 MiB, available khoảng 2.830 MiB. Sáu service mốc 119 healthy,
HTTPS catalog trả 200. Đồng hồ lệch +25.200 giây; đã sửa UTC lúc 09:21:48 UTC
bằng Date header HTTPS Google/Cloudflare, hai nguồn chênh 1 giây. Đã cấu hình
timesyncd thử Cloudflare/Google và pool Việt Nam; các truy vấn NTP vẫn timeout, trạng thái
`NTPSynchronized=no`. RTC lệch +7 giờ cũng được ghi lại UTC lúc 09:58:32 UTC.
Sửa giờ một lần chưa thay thế đồng bộ liên tục.
Chưa deploy thêm collector hoặc demo dừng dependency trên VPS. Preflight yêu cầu
tối thiểu 4 GiB disk trống, 1 GiB RAM available và NTP sync;
đó là guard tối thiểu, không phải cam kết capacity. Không tự xóa volume/data/image cache.

Merchant sandbox VNPay, SMTP thật và kênh alert private chưa được cung cấp. Vì vậy
giao dịch/hoàn tiền sandbox thật, nhận thư ngoài thực tế, alert gửi qua kênh thật, deploy
và video demo VPS vẫn cần bằng chứng riêng. Harness demo nhận action script private
có các bước `prepare`, `unavailable`, `restored`: tạo fixture riêng, ghi response/SQL trước
và sau, chờ service healthy và kiểm thử mua hàng sau khôi phục. Không purge queue,
không xóa volume hay sửa trạng thái đơn để làm đẹp kết quả.

R11 (Elasticsearch/Mongo/SMS/HA) giữ ngoài phạm vi bốn nhóm đã thống nhất.

## Bằng chứng local ngày 13/10

- 171 unit tests: Auth 23, Gateway 23, Product 22, Order 45, Payment 42,
  Notification 16; zero fail/error. BootJar sáu service đạt.
- VNPay PostgreSQL/Gateway/refund restart: 12 tests đạt. SMTP PostgreSQL/Mailpit,
  recipient Auth, replay, SMTP outage và Notification restart: 3 tests đạt,
  đã chạy lại sau bổ sung trace cho email.
- Prometheus: sáu service và năm dependency targets UP; hai backlog snapshots valid.
  Grafana có ba datasource/21 panels; sáu history series và provisioning còn sau restart.
- Collector privacy fixture được lưu rồi đọc từ Tempo: giữ service/route, loại marker
  trong resource/scope/authorization/span name/status/events/links/tracestate.
- Alloy/Loki đã nhận Docker audit fixture, tìm được theo trace ID; chỉ giữ dòng
  action/outcome/trace/span, không nhận dòng raw hoặc marker email giả kèm audit.
  Targets được relabel trước khi tail và giới hạn trong sáu app/service fixture.
- Promtool: 11 rule definitions hợp lệ, hai fixture firing/resolved đạt. Không coi đó
  là bằng chứng cảnh báo đã gửi tới một kênh bên ngoài.
- Lần tải local đầu: 11 HTTP response thành công, không 429/transport/drop, nhưng
  warm-up p95 8.304 ms, p99 8.509 ms vượt threshold 2.000 ms. Các pha sau đã dừng.
  Không xác nhận capacity. Windows thiếu RAM khi nhiều JVM/backend cùng chạy;
  harness hiện chặn pha tải khi available RAM dưới 512 MiB, ngắt pha dưới 256 MiB.
- CI đã thêm kiểm tra trace nghiệp vụ sáu service, trace context bền vững trong
  Order/Payment/email, nghiệp vụ khi collector dừng và Docker audit privacy ở Loki.
  Các bài mới đã đạt tại head 140 nêu trên; chưa xác nhận deploy từ kết quả CI.
- CI head `f457e4279175edba5722ba4eb895dbdd7dedfc39`, push run 38377774329:
  unit/build, docs/infrastructure/live, VNPay, SMTP và multi-account load/reconciliation đạt.
  Monitoring thất bại vì fresh TSDB chưa có đủ ba samples. Verifier đã thêm bounded wait
  qua scrape intervals và giữ cùng cửa sổ query để kiểm chứng retention sau restart.
  Tracing/logging stages chưa chạy trong lần CI này; cần CI head sửa.
- Local chạy lại khi đã dừng backend quan sát: warm-up 11 requests/p95 357 ms,
  catalog 10/p95 136 ms, orders tạo 10 đơn/p95 1.139 ms đều qua threshold. Bước
  finalize gặp HTTP 504 khi thêm JVM fixture, nên không ghi load tổng thể đạt hoặc
  E2E đã chạy. Đã bổ sung guard trước cả Java fixture, giữ dữ liệu để đối chiếu.

Kết quả kiểm thử, SHA và deploy thực tế được cập nhật trong [nhật ký](commits-and-deployments.md).
