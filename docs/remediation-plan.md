# Kế hoạch xử lý bốn nhóm sau rà soát

Phạm vi được yêu cầu ngày 2026-10-12. Mỗi phần phát triển trên feature, kiểm thử Java và CI, tích hợp bằng PR rồi triển khai riêng service. Chỉ đánh dấu hoàn tất khi có bằng chứng; cấu hình sẵn không đồng nghĩa đã chạy trên VPS.

1. Tải và pipeline event: Outbox drain có giới hạn, backoff từng event, metrics; profiling và k6 nhiều người dùng, báo cáo cả lỗi/dropped iterations.
2. Độ tin cậy: nhập kho idempotent; hết hạn reservation, xử lý thanh toán đến muộn; retry có giới hạn, DLQ và công cụ redrive có kiểm soát.
3. Tính năng: refresh/logout và địa chỉ; tìm kiếm/biến thể Product; Shipping/Completed; VNPay sandbox và SMTP (mặc định, có thể đổi theo lựa chọn người dùng).
4. Phần nộp: gRPC Product–Order, OpenAPI, dashboard/tracing, demo sự cố thật và bằng chứng khôi phục trên VPS.

## Giới hạn tài nguyên và cấu hình ngoài

VPS tại checkpoint đầu: 1 CPU, 969 MiB RAM, swap 2 GiB đang dùng khoảng 1.3 GiB. Không bật Elasticsearch/Grafana/tracing stack hoặc tải lớn trên cấu hình này. Các thành phần bổ sung phải có profile tách biệt, kiểm thử độc lập và điều kiện tài nguyên trước khi bật. Chưa có chứng cứ nâng cấp máy.

Chưa có cấu hình VNPay sandbox/SMTP riêng. Tích hợp sẽ được kiểm thử chữ ký, callback, giao dịch và SMTP bằng môi trường thử; giao dịch thật trên sandbox và email ngoài cần cấu hình private. Không ghi credential vào repository hay báo cáo.

## Bằng chứng

SHA, thời gian, kiểm thử và deploy từng mốc ghi trong `commits-and-deployments.md` và PR tương ứng. Giữ nguyên dữ liệu/volume hiện có; thay đổi schema phải tương thích dữ liệu cũ, không dùng Flyway/Liquibase.

Checkpoint 2026-10-12: Outbox, nhận kho idempotent, retry/DLQ/redrive và
reservation expiry đã qua CI và deploy. Auth refresh/logout/địa chỉ cùng
Gateway quota đã qua CI và rollout main 77; sáu service healthy. Lỗi nhập
Java IDE được sửa và kiểm chứng lại sau main 77: đủ sáu project, zero error
markers. Product search/variants đã đạt 18 unit và 3 live local, ở feature
chờ CI/PR. Shipping/Completed, VNPay/SMTP, gRPC, OpenAPI, monitoring/tracing
và profiling/k6 nhiều tài khoản trong đợt này vẫn còn công việc; không
đánh dấu bốn nhóm hoàn tất từ các checkpoint trên.

Checkpoint Shipping tiếp theo: Product được merge main 81 sau CI PR #23
đạt, main deploy đang chạy. Shipping/Completed và notification stage đã
đạt local Order 33 unit + 11 live, Notification 10 unit + 5 live và schema
apply/replay; feature chờ CI/PR, chưa ghi thành deploy VPS. Phần này là
ADMIN ghi nhận vận đơn, chưa tích hợp hãng vận chuyển hoặc gửi SMTP thật.

Checkpoint tiếp theo: Product main 81 đã qua CI 38172436422 và SSH
2026-10-11 20:47:45 UTC: sáu image 81 healthy. Shipping PR #24 head 85
CI 38173359619 đạt toàn bộ Java/live/recovery, merge main 86; rollout
main 38187261497 đang chạy tại checkpoint. gRPC Product–Order đã có
21/39 unit và 4 Product live local đạt; chưa merge/deploy RPC tại checkpoint.
VNPay/SMTP, OpenAPI, monitoring/tracing, profiling/k6 nhiều tài khoản và
demo sự cố VPS vẫn còn công việc trong bốn nhóm.

Checkpoint RPC: Shipping main 86 đã deploy đủ sáu image healthy; main
103 đã deploy riêng Product/Order. Health UP từng chưa phản ánh RPC
sẵn sàng (hai lần lỗi nhận kho trên VPS); probe SSH private và lần mua
tiếp theo đạt. Feature readiness có 42 unit Order và 4 Product live
local đạt, chưa deploy tại checkpoint. VNPay/SMTP, monitoring/tracing,
profiling/k6 và demo sự cố VPS vẫn còn; OpenAPI đang ở feature riêng.
