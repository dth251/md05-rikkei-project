# Ngày 2: kiểm thử và triển khai

Kế hoạch hiện hành: [kế hoạch 6 ứng dụng có Gateway](../Ke_hoach_Omnichannel_3_ngay_6_ung_dung_Co_API_Gateway.md). Số commit tiếp tục từ lịch sử thực tế sau mốc 23; các số trong bảng kế hoạch là tham khảo.

## Order API: kiểm chứng local 2026-10-11, UTC+07:00

- Java 21/Spring Boot 3.5.6/Gradle Groovy: `test bootJar` đạt, 10 unit tests, 0 failures/errors.
- 4 Java live tests đạt với Docker Gateway/Product/Order và PostgreSQL thật. Bộ race gọi Order loopback nội bộ để tránh 429 che kết quả giữ kho; kiểm tra API/quyền và replay cũng đi qua Gateway.
- 100 request khác key tranh 1 stock: đúng 1 response 201 và 99 response 409; database có đúng 1 order item, available=0/reserved=1.
- 12 request cùng key: đúng 1 response 201, còn lại 200 với cùng order ID; stock chỉ reserve một lần. Đổi WEB/POS với cùng key trả 409.
- Transaction nhiều mặt hàng thiếu kho rollback cả order và reservation trước đó. Client không truyền giá; Order lấy snapshot Product qua HTTP có timeout. Chủ đơn/ADMIN đọc được đơn; người khác 404; stock chỉ ADMIN nhập.
- Biên PostgreSQL: available=2147483646/reserved=1 từ chối nhập thêm 1 bằng 409; giữ capacity để hoàn kho không tràn số.
- Trạng thái ở mốc API local: chưa có Payment/Notification hoặc event publisher; VPS vẫn giữ release ngày 1. Không coi local healthy là bằng chứng deploy.

Reports XML/HTML nằm trong `order-service/build/test-results/` và `build/reports/tests/`, không commit build output. SHA, thời gian CI và rollout thực tế được cập nhật theo PR và nhật ký triển khai.

## Order Outbox: local 2026-10-11 khoảng 19:54–19:59 +07:00

- Mốc API `24-order-service`: SHA `fb3b6fb14d1624446a4e7f1a65991edf0d763415`, commit 19:52:52 +07:00. Push feature đã thực hiện; chưa merge/deploy VPS tại thời điểm này.
- `order.created` được ghi vào `order_db.outbox_events` trong transaction tạo đơn/giữ kho. Relay gửi JSON persistent, mandatory routing, chờ correlated publisher confirm và kiểm tra returned message; chỉ sau ACK và không bị return mới ghi `published_at`.
- 5 Java live tests đạt, bao gồm 100 request race, replay, giới hạn nhập kho và recovery. Tắt RabbitMQ local, tạo đơn vẫn 201/giữ kho đúng; Outbox còn pending. Khởi động lại broker healthy, event được confirm và đánh dấu published, đơn vẫn đọc được.
- Recovery test chỉ chạy khi `ORDER_OUTBOX_FAILURE_TEST=true`; CI bật rõ ràng cho môi trường Docker runner. Không chạy test tắt container local như bằng chứng failure trên VPS.
- Unit tests kiểm tra ACK, NACK và unroutable return; replay chỉ tạo một event. Health Order không phụ thuộc RabbitMQ để broker gián đoạn không chặn tạo đơn có Outbox.
- Cú pháp Bash, actionlint và production Compose đạt. Delivery là at-least-once; crash sau ACK trước DB update có thể gửi lại, consumer cần deduplicate `eventId`.

Tham chiếu hành vi publisher: [Spring AMQP 3.2 confirms/returns](https://docs.spring.io/spring-amqp/reference/3.2/amqp/template.html). Payment/Notification và consumer compensation chưa có ở mốc này; không ghi nhận E2E Saga đã hoàn tất.
