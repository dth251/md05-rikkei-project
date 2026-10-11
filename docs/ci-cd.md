# Services CI/CD

Workflow: [.github/workflows/ci.yml](../.github/workflows/ci.yml), tên `Services CI/CD`. Bốn ứng dụng hiện có: Auth, Gateway, Product và Order skeleton. Mỗi ứng dụng Java 21/Spring Boot 3.5.6 có Gradle Wrapper riêng.

## Quy tắc push và ghi chú

- Commit và tiêu đề PR giữ format `NN-ten-service: noi dung thay doi`, số tăng dần từ lịch sử Git. CI/CD thuộc `devops`, tài liệu thuộc `docs`. Không giới hạn số lần commit: hoàn thành và kiểm thử phần công việc thì commit.
- Phát triển trên `feature/...`, tích hợp bằng PR sau khi checks của head cuối đạt; không force-push. Số mốc trong kế hoạch là tham khảo, không đổi số commit đã có.
- Run title và Job Summary theo mốc push/PR, ghi SHA thực sự kiểm thử, thời gian UTC, kết quả và image digest. PR chỉ kiểm thử, không publish/deploy.
- [Nhật ký](commits-and-deployments.md) ghi rõ trạng thái tại từng mốc. `skipped`, queued hoặc file workflow có sẵn không phải bằng chứng deployed.
- Không đưa mật khẩu, JWT, private key hoặc `.env` vào Git, PR, summary hay log.

## Kiểm thử và publish

1. Build tất cả module hiện có bằng `clean test bootJar`: 39 Java unit tests (Auth 11, Gateway 15, Product 8, Order 5).
2. Kiểm tra cú pháp Bash; provision PostgreSQL/RabbitMQ và xác minh 5 database/user đúng owner, đủ 20 hướng CONNECT bị từ chối giữa các DB, 3 durable queues và 5 bindings chính xác.
3. Build Docker từ JAR đã kiểm thử. Chạy Redis và các app; override PostgreSQL loopback chỉ dành cho live tests trên runner. App startup dùng `--no-deps` để giữ port PostgreSQL của override.
4. Chạy 4 Java live tests: Auth/PostgreSQL/restart, Gateway/JWT/Redis quota, Product CRUD/RBAC/SKU race, Order schema/constraint/JWT routing.
5. Upload `java-test-reports` và `tested-jars`. Chỉ publish sau verify đạt, bằng `Dockerfile.runtime` và artifact đã kiểm thử.

Image: `ghcr.io/dth251/md05-rikkei-project/<service>:<40-character-SHA>`, không dùng `latest`. Thay đổi một module chỉ publish/deploy module đó. Thay đổi Compose, hạ tầng hoặc workflow chọn tất cả module hiện có; docs không kích hoạt push workflow. PR docs vẫn chạy verify.

## Cấu hình GitHub Actions

| Loại | Tên | Nội dung |
|---|---|---|
| Variable | VPS_HOST | 42.96.2.60 |
| Variable | VPS_USER | deploy |
| Variable | DEPLOY_PATH | /opt/omnichannel-ecommerce |
| Variable | DOMAIN | md05-rikkei-project.duckdns.org |
| Variable | DEPLOY_ENABLED | `true` sau khi xác minh SSH và `.env` VPS |
| Secret | VPS_SSH_KEY | Khóa SSH riêng cho CI |
| Secret | VPS_KNOWN_HOSTS | Host key VPS đã kiểm chứng |

Publish/pull dùng `GITHUB_TOKEN`. Docker login trên VPS dùng credential directory tạm, dọn khi job kết thúc. SSH kiểm tra host key nghiêm ngặt. `.env` VPS nằm ngoài release, owner `deploy`, mode 600; chứa secret riêng của môi trường VPS.

## Deploy và rollback

Chỉ `main` được deploy khi bật `DEPLOY_ENABLED`. CI chuyển cấu hình vào `/opt/omnichannel-ecommerce/releases/<SHA>` rồi gọi `deploy.sh` lần lượt cho service được chọn. Bind mount hạ tầng dùng đường dẫn cố định; project Compose giữ tên hiện có để giữ volume.

`.image-tags` lưu riêng `AUTH_IMAGE_TAG`, `GATEWAY_IMAGE_TAG`, `PRODUCT_IMAGE_TAG`, `ORDER_IMAGE_TAG`. Rollout khóa bằng `flock`, pull target image, xác minh hạ tầng, recreate target bằng `--no-deps --no-build`, đợi health và smoke phù hợp. Chỉ khi thành công mới cập nhật SHA và symlink `current`. Deploy thất bại phục hồi image trước của target nếu có; vẫn báo CI thất bại. Rollback image chưa được thử bằng lỗi cưỡng bức trên VPS và không tự rollback schema Hibernate.

Không xóa volume hoặc dùng `down -v`. PostgreSQL init chỉ provision khi volume mới; mỗi business service sở hữu DB riêng. Gateway không truy cập PostgreSQL/RabbitMQ. Redis chỉ phục vụ rate limit, có AOF. Gateway/Product/Order có giới hạn bộ nhớ để chạy trên VPS khoảng 1 GB RAM và 2 GB swap; chưa kiểm thử tải sản xuất.

## HTTPS và bằng chứng triển khai

Nginx dùng chứng chỉ hiện có và proxy `/api/` tới Gateway loopback 8080. Root redirect `/gateway-status`; `/actuator/**` không công khai. Service và DB/MQ/Redis không bind cổng Internet. `switch-gateway-domain.sh` kiểm tra Gateway và smoke trước/sau chuyển, giữ khả năng phục hồi site cũ khi lỗi.

Kết quả từng rollout, SHA riêng của service, UTC timestamp và kiểm thử domain được ghi trong [nhật ký triển khai](commits-and-deployments.md) và [kết quả kiểm thử](test-results.md). Order hiện chỉ là skeleton; publish/consume RabbitMQ, Payment và Notification chưa nằm trong release ngày 1.
