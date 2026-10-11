# Omnichannel E-Commerce

Backend API Java 21, Spring Boot 3.5.6 và Gradle Groovy. Phạm vi đã triển khai theo ngày 1 của kế hoạch mới: hạ tầng PostgreSQL/RabbitMQ/Redis, Auth, API Gateway, Product CRUD và Order/Inventory skeleton. Order API, reservation, Payment, Notification và Saga thuộc ngày 2.

Java được tổ chức theo phong cách [RikkeiProject_MD03](https://github.com/dth251/RikkeiProject_MD03): service interface/implementation, DTO request/response, mapper, ApiResponse và exception handler riêng mỗi service. Xem [quy ước Java](docs/coding-conventions.md), [API](docs/api-contract.md), [CI/CD](docs/ci-cd.md) và [kết quả kiểm thử](docs/test-results.md).

API đã deploy tại [md05-rikkei-project.duckdns.org](https://md05-rikkei-project.duckdns.org/). Trang gốc chuyển tới `/gateway-status` và trả JSON `{"status":"UP"}`. Nginx HTTPS chỉ proxy API qua Gateway; register/login/me và catalog phục vụ qua domain. PostgreSQL/RabbitMQ/Redis giữ trong mạng Docker riêng. Xem [nhật ký deploy thực tế](docs/commits-and-deployments.md) để đối chiếu SHA và run CI.

## Chạy local trên Windows

Cần Java 21, Git, Docker Desktop đang chạy và PowerShell. Mỗi lệnh chạy riêng từ thư mục dự án.

Tạo `.env` một lần nếu chưa có; không ghi đè `.env` đang dùng:

```powershell
powershell -ExecutionPolicy Bypass -File infrastructure/scripts/generate-env.ps1
```

Build/test Java:

```powershell
foreach ($service in 'auth-service', 'api-gateway', 'product-service', 'order-service') {
    & ".\$service\gradlew.bat" -p $service clean test bootJar --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $service" }
}
```

Khởi chạy hạ tầng và provision durable queue trước khi chạy consumer trong các ngày tiếp theo:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --wait postgres rabbitmq redis
docker compose run --rm --no-deps rabbitmq-topology
powershell -ExecutionPolicy Bypass -File infrastructure/scripts/verify-infrastructure.ps1 -AllowLoopback
```

Chạy bốn ứng dụng và kiểm thử Java với PostgreSQL thực:

```powershell
docker compose build auth-service api-gateway product-service order-service
docker compose up -d --no-deps --wait auth-service api-gateway product-service order-service
powershell -ExecutionPolicy Bypass -File infrastructure/scripts/test-auth-live.ps1
.\api-gateway\gradlew.bat -p api-gateway liveTest --no-daemon
```

Gateway: `http://127.0.0.1:8080`; Auth/Product/Order lần lượt ở loopback 8081/8082/8083; health nội bộ là `/actuator/health`. PostgreSQL local ở `127.0.0.1:15432`, RabbitMQ Management ở `127.0.0.1:15672`, Redis ở `127.0.0.1:16379`. Chỉ file override local mở các cổng hạ tầng và chỉ trên loopback. Các mật khẩu nằm trong `.env`, không ghi vào Git.

Live tests Product và Order cần fixture JWT của môi trường local; Order cần thêm mật khẩu DB riêng. Đọc vào biến môi trường rồi xóa khỏi process sau khi chạy:

```powershell
try {
    foreach ($line in Get-Content .env) {
        if ($line -match '^JWT_SECRET=(.+)$') { $env:JWT_SECRET = $Matches[1] }
        if ($line -match '^ORDER_DB_PASSWORD=(.+)$') { $env:ORDER_LIVE_DB_PASSWORD = $Matches[1] }
    }
    .\product-service\gradlew.bat -p product-service liveTest --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Product live test failed' }
    .\order-service\gradlew.bat -p order-service liveTest --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Order live test failed' }
} finally {
    Remove-Item Env:JWT_SECRET, Env:ORDER_LIVE_DB_PASSWORD -ErrorAction SilentlyContinue
}
```

## Linux và CI

```bash
bash infrastructure/scripts/generate-env.sh
for service in auth-service api-gateway product-service order-service; do
    "$service/gradlew" -p "$service" clean test bootJar --no-daemon || exit 1
done
docker compose up -d --wait postgres rabbitmq
docker compose run --rm --no-deps rabbitmq-topology
bash infrastructure/scripts/verify-infrastructure.sh
docker compose build auth-service api-gateway product-service order-service
docker compose up -d --wait redis
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --wait postgres
docker compose up -d --no-deps --wait auth-service api-gateway product-service order-service
bash infrastructure/scripts/test-auth-live.sh
api-gateway/gradlew -p api-gateway liveTest --no-daemon
JWT_SECRET="$(sed -n 's/^JWT_SECRET=//p' .env)" product-service/gradlew -p product-service liveTest --no-daemon
JWT_SECRET="$(sed -n 's/^JWT_SECRET=//p' .env)" ORDER_LIVE_DB_PASSWORD="$(sed -n 's/^ORDER_DB_PASSWORD=//p' .env)" order-service/gradlew -p order-service liveTest --no-daemon
```

`Dockerfile.runtime` đóng gói JAR đã được Gradle kiểm thử; `Dockerfile` là tùy chọn build toàn bộ source trong container. CI dùng artifact đã kiểm thử và Dockerfile.runtime.

## Chuẩn bị VPS

VPS: `42.96.2.60`; domain: `md05-rikkei-project.duckdns.org`; thư mục: `/opt/omnichannel-ecommerce`.

Phải cấp quyền SSH cho máy vận hành trước. Sau đó chuyển public key CI riêng và `infrastructure/scripts/prepare-vps.sh` lên VPS, chạy script bằng root với đường dẫn public key. Script chuẩn bị Docker Compose, Nginx/Certbot và user `deploy`; không xóa dữ liệu/volume có sẵn.

Đăng nhập user deploy, clone repository public vào đường dẫn triển khai nếu chưa có repo. Tạo `.env` bằng `bash infrastructure/scripts/generate-env.sh` và giữ quyền 600. Không clone đè thư mục đã có dữ liệu; repository đã clone được cập nhật bằng `git pull --ff-only`.

Kiểm tra SSH user deploy, `.env` và Actions secrets/variables trong [CI/CD](docs/ci-cd.md), rồi bật `DEPLOY_ENABLED=true`. Workflow deploy từng service thay đổi từ main sau khi test/publish đạt, giữ SHA riêng của các service còn lại. Gateway cần `TRUSTED_PROXY_IPS` là địa chỉ bridge Docker mà Nginx trên host dùng để kết nối; xác minh địa chỉ mạng thực tế trước khi cấu hình. Health VPS kiểm tra qua SSH tại các port loopback; không mở port ứng dụng hoặc hạ tầng ra Internet.

Với VPS mới chưa có site/chứng chỉ, provision HTTPS Auth bằng script hiện có sau khi Auth healthy:

```bash
TLS_EMAIL=YOUR_CERTIFICATE_CONTACT_EMAIL DOMAIN=md05-rikkei-project.duckdns.org bash infrastructure/scripts/setup-domain.sh
```

Sau khi Gateway đã deploy healthy, chuyển site HTTPS hiện có sang Gateway bằng root:

```bash
DOMAIN=md05-rikkei-project.duckdns.org bash infrastructure/scripts/switch-gateway-domain.sh /opt/omnichannel-ecommerce/current
```

VPS hiện tại đã hoàn tất bước chuyển này. Script dùng chứng chỉ sẵn có, chạy `nginx -t`, reload và smoke HTTPS; phục hồi site cũ khi kiểm tra thất bại. `/gateway-status` công khai chỉ trạng thái Gateway; `/actuator/**` qua domain trả 404. Các health của service vẫn kiểm tra qua SSH. Dự án hiện là backend API.

Chỉ gửi thông tin đăng nhập qua domain sau khi HTTPS được kiểm chứng. Không coi DNS trỏ đúng hoặc file workflow tồn tại là bằng chứng deploy thành công; kết quả thực tế được ghi trong docs/test-results.md.
