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

## RabbitMQ node identity: kiểm chứng 2026-10-11 khoảng 20:10–20:13 +07:00

Payment integration phát hiện lỗi vận hành trước khi commit service: recreate RabbitMQ để thêm port loopback làm hostname tự sinh thay đổi; node mới không đọc lại topology của node cũ trong volume. Ba live tests Payment ban đầu không nhận được payment mới và thất bại; event chưa gửi vẫn ở Order Outbox.

Sửa Compose giữ `hostname: ${RABBITMQ_HOSTNAME:-rabbitmq}`. Local và VPS đã ghi hostname node đang chạy vào `.env` trước thay đổi để giữ dữ liệu hiện tại; `.env` VPS vẫn mode 600/owner deploy. Local được provision lại topology và consumer restart, không xóa volume/dữ liệu. Queue thử nghiệm riêng có durable=true và message delivery_mode=2 được xác nhận giữ nguyên payload qua force-recreate broker; chỉ queue thử nghiệm được xóa sau kiểm tra. Các thư mục node cũ được giữ nguyên. Bộ kiểm thử Payment chạy lại sau khi sửa; chưa ghi đạt cho lần thất bại ban đầu.

## Payment: local 2026-10-11 khoảng 20:04–20:16 +07:00

- 7 Java unit tests và 3 Java live tests đạt sau sửa hostname và chọn HTTP/1.1 cho RabbitMQ Management test client. Có `payment_db`/`payment_user` riêng, không truy vấn DB Order trong nghiệp vụ.
- `order.created` tạo Payment PENDING. Claim event ID và insert payment UNIQUE order ID cùng transaction PostgreSQL; hai lần cùng event và một event ID khác cho cùng đơn vẫn một payment.
- Chủ đơn/ADMIN đọc payment; người khác 404. Simulate chỉ ADMIN, chọn SUCCESS/FAILED; PENDING hoặc field/enum sai 400. Replay cùng outcome 200; đổi outcome sau hoàn tất 409. Hai outcome đồng thời chỉ một commit và một Outbox event.
- Kết quả thanh toán được ghi cùng transaction vào Payment Outbox; broker confirm/mandatory/return trước published. Live tests SUCCESS/FAILED xác nhận có đúng một outcome được publish persistent. Order compensation/Notification chưa được ghi đạt ở mốc Payment này.
- Mốc 27 hostname: `a06da8a21f56dc5757857e921ecedd639c14be52`, 2026-10-11 20:13:55 +07:00. CI/Compose/deploy được mở rộng cho Payment với SHA riêng.
- Order [PR #8](https://github.com/dth251/md05-rikkei-project/pull/8) merge mốc 26 tại `805d791e01c678eb47d9e047617cc3e01d73c3c7`, 20:06:20 +07:00. [Run main](https://github.com/dth251/md05-rikkei-project/actions/runs/38141855301) verify/publish/deploy success, kết thúc 13:15:54 UTC; rollout chọn bốn service do thay đổi Compose/workflow. Payment vẫn chưa deploy VPS ở thời điểm kiểm chứng local này.
