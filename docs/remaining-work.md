# Danh sách các phần còn cần bổ sung

Cập nhật: **12/10/2026, múi giờ +07:00**. Đây là checklist công việc còn lại,
không phải xác nhận dự án đã đáp ứng đầy đủ đề bài.

## 1. Mốc đối chiếu và trạng thái hiện tại

- Main hiện tại: `f7c5e8b0dc411482e38edb64b8141c8531240217` — mốc 125,
  đã xóa README gốc qua [PR #29](https://github.com/dth251/md05-rikkei-project/pull/29).
- Bản ứng dụng trên VPS: `b253c9528c764fc24dded222ee464dc4ba99b206` — mốc 119.
  SSH lúc **09:44:34 +07 ngày 12/10/2026** xác nhận sáu service healthy.
  Mốc 125 chỉ thay đổi tài liệu nên không cần rollout ứng dụng.
- VNPay nằm trên `feature/remediation-vnpay`, head
  `a8dcd5c3e26b8455b047d2d2c445f73a63c3ba9d` — mốc 129.
  [CI 38196864016](https://github.com/dth251/md05-rikkei-project/actions/runs/38196864016)
  đã completed success; chưa merge vào main hoặc deploy VNPay.
- VPS quan sát gần nhất lúc **09:48:28 +07**: 1 CPU, 969 MiB RAM,
  khoảng 113 MiB available, swap dùng khoảng 1.327 MiB trên 2.047 MiB.
  Đây là số đo tại checkpoint, không phải giá trị cố định.
- Sáu endpoint private `/actuator/prometheus` đã trả HTTP 200. Không có
  container Prometheus/Grafana, không có listener 9090/3000 tại lần kiểm tra.

Các phần đã triển khai không nằm trong danh sách phải xây lại: Auth JWT/
refresh/logout/địa chỉ; Gateway và Redis quota; Product CRUD/search/variants;
Order Saga/giữ-hoàn kho/expiry/Shipping/Completed; Payment mô phỏng;
Notification log; Outbox/retry/DLQ/redrive; gRPC readiness; OpenAPI;
Docker/HTTPS/firewall và CI/CD từng service.

Đối chiếu với [kế hoạch bốn nhóm](remediation-plan.md),
[kết quả ngày 3](day3-test-results.md) và
[đề bài gốc](https://github.com/hunghx/Project-MD5---Devops/blob/main/De_Tai_1_Omnichannel_ECommerce.md).
Các tài liệu cũ chứa checkpoint lịch sử; trạng thái mới cần xem cùng SHA,
CI và bằng chứng deploy trong [nhật ký](commits-and-deployments.md).

## 2. Bảng ưu tiên

P0: công việc cần chốt trong đợt bổ sung hiện tại. P1: hoàn thiện vận hành và
bằng chứng sau P0. P2: mở rộng ngoài phạm vi bốn nhóm đã thống nhất.
Các mức ưu tiên này dùng cho checklist này.

| Mã | Phần còn thiếu | Trạng thái | Ưu tiên |
|---|---|---|---|
| R01 | Hoàn tất tích hợp VNPay | Có code, unit và CI; thiếu kiểm thử chuyên biệt, PR và deploy | P0 |
| R02 | Gửi email SMTP | Notification mới ghi log; chưa có luồng gửi email | P0 |
| R03 | Prometheus server | Service có metrics; chưa có collector/lưu lịch sử | P0 |
| R04 | Grafana dashboard | Chưa có server, datasource và dashboard | P0 |
| R05 | Distributed tracing | Chưa có trace xuyên các service | P0 |
| R06 | Profiling và k6 nhiều tài khoản | Có harness/số liệu cũ; chưa hoàn thành bài đo mới | P0 |
| R07 | Demo sự cố nghiệp vụ trên VPS | Recovery local/CI đã có; demo dependency VPS còn thiếu | P0 |
| R08 | Logging tập trung | Chưa có hệ thống gom/tìm log xuyên container | P1 |
| R09 | Alert và theo dõi dependency | Chưa có alert được kiểm chứng và exporter đầy đủ | P1 |
| R10 | Đồng bộ tài liệu/bằng chứng cuối | OpenAPI hiện tại đã có; phần mới cần cập nhật tiếp | P1 |
| R11 | Các mở rộng nghiệp vụ/kiến trúc | Chưa nằm trong kế hoạch triển khai hiện tại | P2 |

## 3. R01 — VNPay sandbox

**Đã có:** checkout dùng số tiền lưu trong DB, IPN/return xác minh HMAC,
callback idempotent, lưu capture đến muộn với yêu cầu refund, API ADMIN
refund/querydr. Payment đạt **42/42 unit, zero fail/error/skip**, bootJar đạt;
refund được thử với HTTP fixture. Gateway có kiểm thử routing, quota và
client IP. CI nhánh đạt không đồng nghĩa đã giao dịch với VNPay thật.

- [ ] Chạy callback qua Gateway thật với PostgreSQL; xác nhận payment, Order,
  tồn kho và số Outbox event sau callback thành công/thất bại/trùng.
- [ ] Kiểm thử callback đồng thời trên PostgreSQL, xử lý capture tới sau expiry
  và bảo đảm Order hết hạn không bị xác nhận lại.
- [ ] Kiểm thử restart Payment giữa các bước refund; timeout không tạo lần
  gửi lệnh hoàn tiền thứ hai, query thanh toán gốc không được ghi REFUNDED.
- [ ] Bổ sung các bài này vào CI và chạy ở đúng head sẽ merge.
- [ ] Export lại OpenAPI Payment/Gateway cho checkout, IPN, return, refund,
  reconcile; cập nhật collection/runbook tương ứng.
- [ ] Tạo PR, merge sau kiểm thử đạt và deploy các service thực sự thay đổi.
- [ ] Cấu hình merchant sandbox, secret, return/IPN HTTPS và server IP trong
  môi trường private; không đưa credential vào repo hoặc báo cáo.
- [ ] Thực hiện thanh toán và hoàn tiền sandbox thật; đối chiếu portal VNPay
  với payment/refund lưu trong hệ thống, ghi lại kết quả đã loại credential.

**Hoàn tất khi:** có bằng chứng riêng cho test fixture/PG/Gateway và giao dịch
sandbox thực, CI đạt, SHA deploy đúng và luồng mua hàng sau rollout đạt.
Phần sandbox thực phụ thuộc cấu hình merchant chưa được cung cấp.

## 4. R02 — SMTP và Notification

- [ ] Xây dựng gửi email theo trạng thái đơn với service interface/impl,
  DTO và mapper thuộc Notification.
- [ ] Xác định nguồn email người nhận qua API/event hợp lệ; không truy vấn
  trực tiếp database Auth từ Notification.
- [ ] Lưu trạng thái gửi, số lần thử, lỗi đã chuẩn hóa và thời gian gửi;
  ghi dữ liệu cùng transaction thích hợp trước khi thực hiện SMTP.
- [ ] Retry có giới hạn, chống tạo tác vụ trùng khi RabbitMQ giao event lại;
  quy định cách xử lý trường hợp SMTP đã nhận thư nhưng app chưa lưu kết quả.
- [ ] Kiểm thử bằng SMTP server thử nghiệm: nội dung/người nhận, duplicate
  event, SMTP mất kết nối và restart Notification.
- [ ] Cấu hình SMTP/TLS/credential private và thử nhận email ngoài thực tế.
- [ ] Chạy CI, PR và deploy Notification cùng các service liên quan nếu có.

**Hoàn tất khi:** có thư nhận được và trạng thái gửi được đối chiếu; lỗi SMTP
không làm mất event hoặc cản trở các service nghiệp vụ độc lập. Không tuyên
bố gửi đúng một lần tuyệt đối nếu còn cửa sổ crash sau khi SMTP nhận thư.

## 5. R03 — Prometheus server

Hiện `/actuator/prometheus` chỉ xuất metrics tại thời điểm gọi. Chưa có server
thu thập định kỳ, lưu time series hoặc truy vấn lịch sử.

- [ ] Tạo cấu hình scrape cho sáu service bằng DNS/mạng private; kiểm tra
  từng target, thời gian scrape và lỗi timeout.
- [ ] Thêm Compose profile monitoring riêng, phiên bản image được cố định,
  giới hạn tài nguyên, volume và healthcheck.
- [ ] Quy định scrape interval, retention theo thời gian/dung lượng và
  ngân sách disk; kiểm tra dữ liệu còn sau restart.
- [ ] Kiểm tra cấu hình bằng công cụ Prometheus và test query trên stack thử.
- [ ] Giữ endpoint nội bộ; cách truy cập quản trị qua SSH/private network.
- [ ] Chọn môi trường có ngân sách tài nguyên đủ trước khi deploy stack.

**Hoàn tất khi:** targets cần theo dõi UP, query trả dữ liệu có nhãn service,
có lịch sử qua nhiều lần scrape và dữ liệu được lưu đúng retention.

## 6. R04 — Grafana dashboard

- [ ] Cấu hình Grafana, datasource Prometheus và provisioning dashboard từ
  file trong repository; credential admin nằm ngoài source.
- [ ] Dashboard tổng quan: availability, request rate, p95/p99 latency,
  phân loại 2xx/4xx/5xx và 429 theo service/route.
- [ ] Dashboard JVM: heap/non-heap, GC, threads; tài nguyên host/container
  chỉ hiển thị khi đã bổ sung nguồn metrics phù hợp.
- [ ] Dashboard nghiệp vụ: publish/retry Outbox, độ trễ gRPC và các trạng
  thái/kết quả thực sự có metric; không tự suy ra pending backlog từ counter.
- [ ] Đánh dấu rõ metric còn thiếu dữ liệu; kiểm tra đơn vị, nhãn và bộ lọc
  thời gian trên dữ liệu thật.
- [ ] Kiểm tra sau restart provisioning còn hoạt động; lưu ảnh và JSON
  dashboard cùng checkpoint của stack đã chạy.

**Hoàn tất khi:** dashboard hiển thị dữ liệu thực, truy được lịch sử và giúp
đối chiếu một luồng mua hàng hoặc một sự cố có thời gian xác định.

## 7. R05/R08 — Tracing và logging tập trung

- [ ] Tracing: instrument HTTP Gateway/service và gRPC Product–Order;
  truyền trace context qua RabbitMQ/Outbox khi phù hợp.
- [ ] Chọn và cấu hình OpenTelemetry cùng backend trace; quy định sampling,
  giới hạn queue/export và retry khi backend mất kết nối.
- [ ] Đối chiếu trace một đơn hàng từ Gateway đến các bước đồng bộ/bất đồng
  bộ; nghiệp vụ tiếp tục hoạt động khi exporter không sẵn sàng.
- [ ] Logging: cấu hình gom log container về hệ thống tập trung và truy vấn
  theo application, timestamp, trace ID/correlation ID.
- [ ] Cố định image/collector còn được hỗ trợ khi triển khai; cấu hình
  retention, giới hạn tài nguyên và storage cho log/trace.
- [ ] Loại token, password, signing key, email/địa chỉ và payload provider
  khỏi log/trace; kiểm tra bằng fixture và dữ liệu xuất đã redaction.
- [ ] Bổ sung CI kiểm tra config và lưu bằng chứng trace/log đã chạy thật.

**Hoàn tất khi:** tìm được request/event xuyên service và lỗi tương ứng trong
trace/log; có cấu hình được kiểm chứng, không chỉ có sơ đồ hoặc file Compose.

## 8. R06 — Profiling, tối ưu và kiểm thử tải

Đã có k6 catalog/order/race/quota và kết quả VPS cũ. Các bài cũ dùng stock/
user fixture giới hạn, chưa chứng minh mục tiêu 500–1.000 requests/s.
Catalog target 50/s đã thất bại; Order giảm 1/s vẫn có dropped iterations.
Không dùng lại kết quả local để kết luận capacity VPS hiện tại.

- [ ] Mở rộng Java fixture thành nhiều tài khoản USER thật, dữ liệu/stock
  tách biệt và idempotency key mới cho mỗi đơn cần tạo.
- [ ] Tách catalog, tạo đơn, thanh toán, quota và race; bổ sung luồng
  end-to-end với kiểm tra trạng thái Order/Payment/stock sau tải.
- [ ] Profiling JVM/GC/threads, SQL/query plan/pool và MQ/Outbox; có số đo
  trước/sau để xác định và kiểm tra cải thiện bottleneck.
- [ ] Ghi rõ warm-up, thời lượng ổn định, concurrency, target và throughput
  thành công thực; phân biệt request timeout với response thành công.
- [ ] Báo cáo p95/p99, 2xx/4xx/5xx/429, transport errors, dropped/interrupted
  iterations, CPU/RAM/swap/disk/queue/Outbox cùng cửa sổ đo.
- [ ] Chạy lại từ tải nhỏ sau thay đổi tài nguyên/tối ưu; giữ resource guard
  và threshold ban đầu, không nâng tải khi stage trước thất bại.
- [ ] Kiểm chứng race trên VPS bằng response và SQL: một đơn thắng, stock
  không âm, giữ/hoàn kho đúng; phân biệt quota với xung đột tồn kho.

**Hoàn tất khi:** có báo cáo tái lập được ở SHA cụ thể và kết luận rõ đạt/
không đạt. Chỉ xác nhận một mức RPS sau khi thực sự đo thành công mức đó.

## 9. R07 — Demo sự cố nghiệp vụ trên VPS

Đã có recovery local/CI và rollback image sau smoke failure trên VPS.
Đó chưa phải bằng chứng demo chủ động dừng Notification/Payment/broker trên
VPS rồi đối chiếu tính sẵn sàng và khôi phục nghiệp vụ.

- [ ] Chuẩn bị fixture riêng, đo trạng thái trước và chọn cửa sổ không có
  CI/CD rollout hoặc bài tải khác.
- [ ] Dừng Notification có kiểm soát, chứng minh tạo đơn và các service độc
  lập vẫn hoạt động; sau khôi phục, event được xử lý và log không trùng.
- [ ] Thử Payment/broker theo kịch bản riêng: hành vi lỗi/deferred processing,
  pending Outbox/queue và trạng thái đơn được ghi nhận trung thực.
- [ ] Bảo đảm `finally` khôi phục đúng container của dự án; không purge queue,
  xóa volume hay sửa trạng thái DB để làm kết quả đẹp hơn.
- [ ] Lưu timestamps, SHA/image, request kết quả và SQL/metrics trước/sau;
  ghi hình demo thực và kiểm thử mua hàng sau khi phục hồi.

**Hoàn tất khi:** có bằng chứng thao tác trên VPS, service độc lập còn phục
vụ và backlog/đơn hàng được xử lý đúng sau phục hồi.

## 10. R09 — Dependency metrics và alert

- [ ] Bổ sung nguồn metrics host/container, PostgreSQL và RabbitMQ; kiểm tra
  Redis nếu cần giám sát dependency rate limit.
- [ ] Theo dõi queue ready/unacked/consumer, retry/DLQ, số và tuổi Outbox
  pending bằng metric phù hợp; bổ sung metric còn thiếu trước khi vẽ panel.
- [ ] Định nghĩa alert service/scrape down, latency/error tăng, thiếu tài
  nguyên, mất consumer và backlog vượt ngưỡng.
- [ ] Kiểm thử rule và trạng thái firing/resolved bằng sự cố fixture; cấu
  hình kênh nhận cảnh báo và routing theo phạm vi vận hành đã thống nhất.

**Hoàn tất khi:** alert thực sự phát hiện/giải quyết được sự cố thử nghiệm,
có người/kênh nhận phù hợp và không chứa credential hoặc dữ liệu khách hàng.

## 11. R10 — Tài liệu và bằng chứng cuối

- [ ] Đồng bộ OpenAPI, API contract và Postman theo controller sau các PR mới.
- [ ] Thêm runbook VNPay/SMTP, provisioning monitoring và xử lý alert.
- [ ] Ghi SHA, thời gian, kết quả kiểm thử, image/health deploy từng mốc;
  giữ rõ checkpoint thất bại và giới hạn của phép đo.
- [ ] Cập nhật sơ đồ kiến trúc/runtime, ảnh dashboard/trace và video sự cố.
- [ ] Tổng hợp checklist cuối theo yêu cầu đề; phần giản lược phải có giải
  thích, không ghi toàn bộ hoàn tất từ việc chỉ có code hoặc CI xanh.

## 12. R11 — Các mở rộng chưa thuộc bốn nhóm hiện tại

Các mục sau là khác biệt/mở rộng cần ghi nhận nếu muốn hoàn thiện thêm theo
kiến trúc doanh nghiệp. Chưa tự động đưa vào phạm vi triển khai đợt này.

| Mục | Hiện trạng | Việc bổ sung nếu mở rộng phạm vi |
|---|---|---|
| Elasticsearch/MongoDB | Product hiện dùng PostgreSQL và SQL search | Thiết kế index/document ownership, đồng bộ và reindex, đo tìm kiếm nâng cao |
| gRPC latency | RPC/readiness hoạt động; chưa chứng minh latency dưới 5 ms | Đo RPC riêng trên môi trường và tải xác định, tối ưu có bằng chứng |
| SMS/Push | Notification hiện ghi log; SMTP còn trong R02 | Chọn provider, quản lý delivery/retry và cấu hình private |
| API hãng vận chuyển | ADMIN nhập carrier/tracking và chuyển trạng thái | Adapter provider, callback xác minh và đồng bộ vận đơn |
| Scale/HA | Một VPS, một instance mỗi service/dependency | Chứng minh scale đồng thời, cân bằng tải và phương án phục hồi khi host/dependency hỏng |

Không bổ sung frontend, shared module, Maven, Flyway hoặc Liquibase theo quy
ước hiện tại. Chống race bằng SQL đã có; không coi việc chưa dùng Redis Lock
là một lỗi bắt buộc sửa.

## 13. Thứ tự triển khai và cập nhật checklist

1. Chốt các bài kiểm thử VNPay chuyên biệt và SMTP fixture trước; phần giao
   dịch/email ngoài phụ thuộc credential private.
2. Chuẩn bị cấu hình Prometheus/Grafana, kiểm thử trên stack riêng và xác nhận
   ngân sách tài nguyên trước khi deploy monitoring.
3. Bổ sung dependency metrics, alert, logging và tracing.
4. Profiling, bài k6 nhiều tài khoản và demo sự cố VPS có kiểm soát.
5. Đồng bộ tài liệu/bằng chứng, đánh dấu từng mục dựa trên kết quả thực tế.

Mỗi phần phát triển trên `feature/...`, commit mốc tăng dần, kiểm thử rồi tích
hợp qua PR. Script vận hành dùng Bash/PowerShell; nghiệp vụ và tests service
dùng Java. Không ghi secret vào Git/logs, không xóa dữ liệu hoặc volume.
Tick một mục chỉ khi đã có bằng chứng tương ứng; thay đổi môi trường/SHA thì
cần đánh giá lại kết quả bị ảnh hưởng.
