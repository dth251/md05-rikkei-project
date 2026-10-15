# Bằng chứng ngày 3

Các ảnh/sơ đồ/video trong bảng dưới là checkpoint ngày 3, chưa phản ánh SMTP/VNPay/
observability bổ sung ngày 13/10. Sơ đồ nguồn hiện tại nằm ở `docs/architecture.md`.
Bằng chứng mới: [CI head 140](remaining-ci-140/README.md); không thay thế số đo VPS cũ.

Đọc [báo cáo đầy đủ](https://github.com/dth251/md05-rikkei-project/blob/8a909c99a3cf246d26da9148f5502b02f37bf8a0/docs/day3-test-results.md) trước khi diễn giải các số đo. Timestamp UTC; commit/deploy dùng SHA thực, không suy ra image từ HEAD.

| File | Nguồn / phạm vi |
|---|---|
| [architecture.svg](architecture.svg), [architecture.png](architecture.png) | Sơ đồ sáu app, DB riêng và event/Saga; Mermaid source ở architecture.md |
| [backend-demo.webm](backend-demo.webm) | 63.24s, 1440×900, không audio. Phát lại response VPS đã ghi nhận trong trace 17:08–17:09 UTC, caption nêu rõ không gọi API live/không có frontend. Hiển thị cả load không đạt |
| [saga-demo.png](saga-demo.png), [load-report.png](load-report.png) | Ảnh từ cùng bản trình bày evidence, đã xem kiểm tra khả năng đọc |
| [vps-api-trace.json](vps-api-trace.json) | Newman thực: 14 requests/17 assertions/0 failures, gồm polling. Chỉ response nghiệp vụ/status/path; bỏ toàn bộ request headers/credential/token |
| [vps-catalog-50-summary.json](vps-catalog-50-summary.json), [resources](vps-catalog-resources.csv) | Stage target 50/s abort; 90 timeout/0 thành công; 111 dropped/99 interrupted. Không đo stages cao hơn |
| [vps-order-2-summary.json](vps-order-2-summary.json), [resources](vps-order-2-resources.csv) | 26×201/32 HTTP, 6 lỗi, dropped 8. Sampler bắt đầu muộn ~10s; chỉ một phần load |
| [vps-order-status-metrics.txt](vps-order-status-metrics.txt) | Private Order counter tổng hợp 17:16:26 UTC, trước Order redeploy; đối chiếu sáu lỗi 503, không phải per-request trace |
| [vps-order-1-summary.json](vps-order-1-summary.json), [resources](vps-order-1-resources.csv) | Giảm 1/s: 19×201, không HTTP lỗi nhưng dropped 2/p95 ~6.9s; không coi target đạt |
| [vps-race-summary.json](vps-race-summary.json), [resources](vps-race-resources.csv) | 100 request direct Order SSH: 1×201/63×409/36 khác. Harness cũ chưa phân loại 36 status; strict threshold FAIL |
| [local-race-summary.json](local-race-summary.json) | Harness mốc 55 có đầy đủ status counters: 1×201/99×409, zero 5xx/429/transport failures |
| [vps-metrics-audit.txt](vps-metrics-audit.txt) | Sáu app SHA 49/private JVM metrics, sau metrics rollout |
| [vps-fixture-finish.json](vps-fixture-finish.json) | Java API/SQL: 46 orders CONFIRMED, hai product reserved=0, 17:40:41 UTC; rows giữ audit |
| [vps-final-audit.txt](vps-final-audit.txt) | 17:41:02–17:41:28 UTC: image tags + actual containers, sáu UP, private metrics/public 404, queues/Outbox=0, 46 notification logs/46 orders/mỗi order một log |

k6 summary chỉ có metric, không JWT/request headers. Raw Newman/private fixture nằm ngoài Git và được dọn token sau kiểm chứng. Không có `.env`/SSH key/secret trong các artifact. Video được kiểm tra duration/dimensions và seek/decode ở giây 50; PNG đã xem trực tiếp. Các dữ liệu order/product IDs là fixture kiểm chứng, không dữ liệu khách hàng.

**Giới hạn:** VPS 969 MiB RAM nhiều swap chưa đạt catalog 50 RPS hoặc strict 99 conflict responses ở race VPS. SQL chống oversell đạt; CI chức năng/recovery đạt. Không thay thế kết quả lỗi bằng target hoặc số liệu local.
