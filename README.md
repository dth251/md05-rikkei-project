# Omnichannel E-Commerce

Backend API Java 21, Spring Boot 3.5.6, Gradle Groovy, PostgreSQL và RabbitMQ. Phạm vi hiện tại theo yêu cầu: hạ tầng và Auth Service. Product, Order/Inventory, Payment và Notification sẽ được triển khai lần lượt ở các mốc tiếp theo.

Java được tổ chức theo phong cách [RikkeiProject_MD03](https://github.com/dth251/RikkeiProject_MD03): service interface/implementation, DTO request/response, mapper, ApiResponse và exception handler riêng mỗi service. Xem [quy ước Java](docs/coding-conventions.md), [API](docs/api-contract.md), [CI/CD](docs/ci-cd.md) và [kết quả kiểm thử](docs/test-results.md).

## Chạy local trên Windows

Cần Java 21, Git, Docker Desktop đang chạy và PowerShell. Mỗi lệnh chạy riêng từ thư mục dự án.

Tạo `.env` một lần nếu chưa có; không ghi đè `.env` đang dùng:

```powershell
powershell -ExecutionPolicy Bypass -File infrastructure/scripts/generate-env.ps1
```

Build/test Java:

```powershell
cd auth-service
.\gradlew.bat clean test bootJar --no-daemon
cd ..
```

Khởi chạy hạ tầng và provision durable queue trước khi chạy consumer trong các ngày tiếp theo:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --wait postgres rabbitmq
docker compose run --rm --no-deps rabbitmq-topology
powershell -ExecutionPolicy Bypass -File infrastructure/scripts/verify-infrastructure.ps1 -AllowLoopback
```

Chạy Auth và kiểm thử Java với PostgreSQL thực:

```powershell
docker compose build auth-service
docker compose up -d --no-deps --wait auth-service
powershell -ExecutionPolicy Bypass -File infrastructure/scripts/test-auth-live.ps1
```

Auth: `http://127.0.0.1:8081`; health: `/actuator/health`. PostgreSQL local ở `127.0.0.1:15432`, RabbitMQ Management ở `127.0.0.1:15672`. Chỉ file override local mở các cổng này và chỉ trên loopback. Các mật khẩu nằm trong `.env`, không ghi vào Git.

## Linux và CI

```bash
bash infrastructure/scripts/generate-env.sh
cd auth-service
./gradlew clean test bootJar --no-daemon
cd ..
docker compose up -d --wait postgres rabbitmq
docker compose run --rm --no-deps rabbitmq-topology
bash infrastructure/scripts/verify-infrastructure.sh
docker compose build auth-service
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --wait postgres
docker compose up -d --no-deps --wait auth-service
bash infrastructure/scripts/test-auth-live.sh
```

`Dockerfile.runtime` đóng gói JAR đã được Gradle kiểm thử; `Dockerfile` là tùy chọn build toàn bộ source trong container. CI dùng artifact đã kiểm thử và Dockerfile.runtime.

## Chuẩn bị VPS

VPS: `42.96.2.60`; domain: `md05-rikkei-project.duckdns.org`; thư mục: `/opt/omnichannel-ecommerce`.

Phải cấp quyền SSH cho máy vận hành trước. Sau đó chuyển public key CI riêng và `infrastructure/scripts/prepare-vps.sh` lên VPS, chạy script bằng root với đường dẫn public key. Script chuẩn bị Docker Compose, Nginx/Certbot và user `deploy`; không xóa dữ liệu/volume có sẵn.

Đăng nhập user deploy, clone repository public vào đường dẫn triển khai nếu chưa có repo. Tạo `.env` bằng `bash infrastructure/scripts/generate-env.sh` và giữ quyền 600. Không clone đè thư mục đã có dữ liệu; repository đã clone được cập nhật bằng `git pull --ff-only`.

Kiểm tra SSH user deploy, `.env` và Actions secrets/variables trong [CI/CD](docs/ci-cd.md), rồi bật `DEPLOY_ENABLED=true`. Workflow chỉ deploy Auth từ main sau khi test/publish đạt. Health VPS là `http://127.0.0.1:8081/actuator/health` qua SSH; không mở port Auth, PostgreSQL hoặc RabbitMQ ra Internet.

Public routing Nginx và HTTPS phải được cấu hình/kiểm chứng trước khi gửi thông tin đăng nhập qua domain. Không coi DNS trỏ đúng hoặc file workflow tồn tại là bằng chứng deploy thành công; kết quả thực tế được ghi trong docs/test-results.md.
