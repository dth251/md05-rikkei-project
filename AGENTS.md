# Quy ước triển khai

- Java 21, Spring Boot 3.5.6, Gradle Groovy và Gradle Wrapper riêng từng service.
- Phạm vi hiện tại: service 0 (hạ tầng) và service 1 (Auth), CI/CD và deploy VPS theo yêu cầu người dùng.
- Phát triển trên feature/...; commit từng mốc theo số tăng dần.
- Tích hợp bằng Pull Request sau khi build và kiểm thử đạt; không force-push.
- Không tạo shared, bootstrap, Maven, Flyway, Liquibase hoặc frontend.
- Mỗi service tự sở hữu DTO, security và error handlers; định dạng code xuống dòng rõ ràng.
- Không commit secret, .env, private key hoặc .verification; không ghi secret vào logs.
- Không xóa volume, dữ liệu hoặc ứng dụng ngoài phạm vi dự án.
- Tuân theo docs/coding-conventions.md: service interface + impl, DTO request/response, mapper riêng và ApiResponse theo phong cách MD03.
- Nghiệp vụ và kiểm thử service viết bằng Java; script vận hành dùng Bash/PowerShell, không đưa Python vào source.
