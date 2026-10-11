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
