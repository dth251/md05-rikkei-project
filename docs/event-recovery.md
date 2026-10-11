# Retry, DLQ và nhập kho idempotent

Nhập kho yêu cầu `idempotencyKey` trong JSON. Client tạo key một lần cho mỗi phiếu và giữ nguyên khi retry. Cùng key/nội dung trả snapshot phiếu đầu; đổi nội dung trả 409. Phiếu và cập nhật kho cùng transaction. Cần cập nhật client cũ trước khi chuyển image Order mới; Postman và fixture Java đã dùng key.

Ba consumer retry lỗi tạm thời tối đa 5 lần, backoff 1/2/4/8 giây. Payload sai bị reject ngay, kể cả khi exception được bọc bởi listener. Sau khi hết retry, message được chuyển sang `<queue>.dlq` qua exchange `commerce.dead-letter`. Cấu hình bằng policy trên queue cũ, không thay arguments hoặc xóa queue. Giữ persistent message, eventId và body để điều tra.

Classic queue hiện có không đảm bảo dead-letter at-least-once khi broker/đích lỗi; đây vẫn là một broker trên một VPS. Không coi DLQ là HA. Chuyển quorum/cluster cần kế hoạch di chuyển riêng; không xóa dữ liệu hiện tại.

## Kiểm tra và redrive

Sau khi sửa nguyên nhân (database, dữ liệu tham chiếu, deployment), cấu hình private `RABBITMQ_LIVE_HOST`, `RABBITMQ_LIVE_AMQP_PORT`, `RABBITMQ_LIVE_USER`, `RABBITMQ_LIVE_PASSWORD` trong môi trường shell. Không đưa password lên command line, Git hay output.

```powershell
cd order-service
.\gradlew.bat deadLetter -PdlqAction=inspect -PdlqQueue=payment.order-created.q --no-daemon
# Mặc định một message; DLQ_LIMIT cho phép 1–100.
.\gradlew.bat deadLetter -PdlqAction=redrive -PdlqQueue=payment.order-created.q --no-daemon
```

Queue hợp lệ: `payment.order-created.q`, `order.payment-result.q`, `notification.order.q`. Inspect chỉ in UUID hợp lệ/kích thước, không body hoặc credential; message được requeue khi đóng channel. Redrive không sửa payload; chỉ ack DLQ sau publisher confirm và kiểm tra mandatory return. Lỗi/chết tiến trình giữ message trong DLQ, hoặc có thể tạo bản gửi trùng sau confirm: consumer phải tiếp tục dedup eventId. Payload hỏng giữ lại và cần quy trình sửa dữ liệu có kiểm soát, không tự sửa hoặc purge.

Nguồn thiết kế: [RabbitMQ 4.1 DLX policies](https://www.rabbitmq.com/docs/4.1/dlx), [Spring AMQP retry/recovery](https://docs.spring.io/spring-amqp/reference/3.2/amqp/resilience-recovering-from-errors-and-broker-failures.html).

## Outbox

Order và Payment mặc định lấy 50 event/batch, drain tối đa 200 event/vòng và ngân sách 2 giây, delay giữa vòng 1 giây. Một publish đang chờ confirm có thể vượt ngân sách đến timeout 5 giây; ngân sách giới hạn lúc bắt đầu event kế tiếp. Các thuộc tính `commerce.outbox.batch-size`, `max-events-per-tick`, `budget-ms`, `delay-ms` điều chỉnh riêng từng app.

Lỗi publish rollback trước, metadata retry commit riêng: delay 2/4/8/... tối đa 300 giây. Event lỗi không chặn các event khác; không tự xóa event pending. Metrics `commerce_outbox_publish_total{result="failed|completed"}` đếm lần gọi, completed có thể gồm row đã được worker khác xử lý; không coi đó là số giao dịch. `commerce_outbox_relay_seconds` theo dõi thời gian vòng. `published_at` chỉ ghi sau ACK và không có unroutable return.
