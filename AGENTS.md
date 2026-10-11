# Quy ước triển khai

- Java 21, Spring Boot 3.5.6, Gradle Groovy và Gradle Wrapper riêng từng service.
- Phạm vi hiện tại: ngày 3 theo kế hoạch mới nhất — VPS HTTPS/UFW/rollback, CI/CD sáu app, metrics private, Java security/recovery tests, k6/Postman, báo cáo tải, README/sơ đồ và bằng chứng demo. Nghiệp vụ ngày 1–2 đã triển khai.
- Phát triển trên feature/...; commit từng mốc theo số tăng dần.
- Commit sau khi hoàn thành và kiểm thử một phần công việc; không giới hạn số lần commit theo bảng kế hoạch. Cấp số từ lịch sử Git thực tế.
- Commit, tiêu đề PR và ghi chú CI/CD tuân format NN-ten-service: noi dung thay doi; summary/nhật ký ghi SHA, thời gian, kết quả kiểm tra và trạng thái deploy thực tế.
- Tích hợp bằng Pull Request sau khi build và kiểm thử đạt; không force-push.
- Không tạo shared, bootstrap, Maven, Flyway, Liquibase hoặc frontend.
- Mỗi service tự sở hữu DTO, security và error handlers; định dạng code xuống dòng rõ ràng.
- Không commit secret, .env, private key hoặc .verification; không ghi secret vào logs.
- Không xóa volume, dữ liệu hoặc ứng dụng ngoài phạm vi dự án.
- Tuân theo docs/coding-conventions.md: service interface + impl, DTO request/response, mapper riêng và ApiResponse theo phong cách MD03.
- Nghiệp vụ và kiểm thử service viết bằng Java; script vận hành dùng Bash/PowerShell, không đưa Python vào source.
- Kịch bản k6 dùng JavaScript của công cụ k6 theo kế hoạch ngày 3; không thay thế unit/integration tests Java.
