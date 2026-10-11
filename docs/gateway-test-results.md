# Kiểm thử API Gateway

## 12-api-gateway: local JWT, routing and Redis verification

- Kiểm chứng ngày 2026-10-11, Asia/Bangkok; Gateway skeleton `eb33bcd`, JWT/rate limit `0d2222a`.
- `gradlew test bootJar`: 13 Java tests đạt. Token HS256 theo Auth; sai issuer/signature/expiry/subject/role và thiếu expiry trả 401. Giữ nguyên URL prefix, loại header giả mạo identity, request ID, backend không hoạt động trả 503.
- `gradlew liveTest`: 1 Java integration test đạt với Auth/PostgreSQL/Redis thật qua Docker Gateway; register/login/me, 401/403, Redis quota 429, Auth vẫn phục vụ sau burst login.
- Tắt Redis local: login trả 503 thay vì bỏ qua rate limit; Gateway health vẫn UP. Redis đã được khởi động lại và healthy.
- Hai lỗi script đã tái hiện và sửa: generator từ chối khi OpenSSL lỗi trước khi tạo `.env`; kiểm chứng topology từ chối binding sai exchange/queue.
- `actionlint`, cú pháp Bash và production Compose đạt. Nginx/domain còn dùng Auth cho đến khi Gateway được CI/CD deploy và kiểm tra trên VPS.

Gateway không truy cập PostgreSQL/RabbitMQ. Redis chỉ phục vụ rate limit. IP chỉ được tin khi peer nằm trong danh sách proxy cấu hình; Nginx ghi đè header địa chỉ client.
