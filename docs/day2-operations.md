# Chạy và kiểm chứng phạm vi ngày 2

Sáu ứng dụng độc lập: Gateway 8080, Auth 8081, Product 8082, Order/Inventory 8083, Notification 8084, Payment 8085. Java 21, Spring Boot 3.5.6, Wrapper Gradle Groovy riêng. Dự án là backend API; Payment là mock và Notification ghi log trong DB.

## Local Windows

Nếu chưa có `.env`, chạy generator hiện có. Không ghi đè `.env` đang dùng. Nếu volume RabbitMQ đã tồn tại trước khi cố định hostname, giữ hostname node hiện tại như [runbook CI/CD](ci-cd.md).

```powershell
$services = 'auth-service', 'api-gateway', 'product-service', 'order-service', 'payment-service', 'notification-service'
foreach ($service in $services) {
    & ".\$service\gradlew.bat" -p $service clean test bootJar --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $service" }
}
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --wait postgres rabbitmq redis
docker compose run --rm --no-deps rabbitmq-topology
powershell -ExecutionPolicy Bypass -File infrastructure/scripts/verify-infrastructure.ps1 -AllowLoopback
docker compose build $services
docker compose up -d --no-deps --wait $services
```

Local infrastructure ports chỉ loopback: PostgreSQL 15432, RabbitMQ Management 15672/AMQP 15673, Redis 16379. Test API qua Gateway 8080; business ports dùng chẩn đoán/race test trong môi trường local.

Live tests đọc secret vào process rồi xóa. Các recovery flags dưới đây **stop/start container dự án local**; chỉ chạy trên môi trường test, không dùng như một bài kiểm tra VPS từ máy local:

```powershell
try {
    foreach ($line in Get-Content .env) {
        if ($line -match '^JWT_SECRET=(.+)$') { $env:JWT_SECRET = $Matches[1] }
        if ($line -match '^ORDER_DB_PASSWORD=(.+)$') { $env:ORDER_LIVE_DB_PASSWORD = $Matches[1] }
        if ($line -match '^PAYMENT_DB_PASSWORD=(.+)$') { $env:PAYMENT_LIVE_DB_PASSWORD = $Matches[1] }
        if ($line -match '^NOTIFICATION_DB_PASSWORD=(.+)$') { $env:NOTIFICATION_LIVE_DB_PASSWORD = $Matches[1] }
        if ($line -match '^RABBITMQ_DEFAULT_USER=(.+)$') { $env:RABBITMQ_LIVE_USER = $Matches[1] }
        if ($line -match '^RABBITMQ_DEFAULT_PASS=(.+)$') { $env:RABBITMQ_LIVE_PASSWORD = $Matches[1] }
    }
    $env:ORDER_OUTBOX_FAILURE_TEST = 'true'
    $env:ORDER_SAGA_LIVE_TEST = 'true'
    $env:SAGA_RECOVERY_TEST = 'true'
    $env:NOTIFICATION_RECOVERY_TEST = 'true'
    powershell -ExecutionPolicy Bypass -File infrastructure/scripts/test-auth-live.ps1
    foreach ($service in 'api-gateway', 'product-service', 'order-service', 'payment-service', 'notification-service') {
        & ".\$service\gradlew.bat" -p $service liveTest --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Live test failed: $service" }
    }
} finally {
    Remove-Item Env:JWT_SECRET, Env:ORDER_LIVE_DB_PASSWORD, Env:PAYMENT_LIVE_DB_PASSWORD,
        Env:NOTIFICATION_LIVE_DB_PASSWORD, Env:RABBITMQ_LIVE_USER, Env:RABBITMQ_LIVE_PASSWORD,
        Env:ORDER_OUTBOX_FAILURE_TEST, Env:ORDER_SAGA_LIVE_TEST, Env:SAGA_RECOVERY_TEST,
        Env:NOTIFICATION_RECOVERY_TEST, Env:AUTH_SMOKE_DB_URL, Env:AUTH_SMOKE_URL,
        Env:AUTH_SMOKE_RESTART -ErrorAction SilentlyContinue
}
```

Race test 100 requests gọi Order loopback, tránh Redis 429 che tính đúng đắn kho. Saga/E2E gọi Gateway; USER token E2E Notification do Auth phát thật, ADMIN fixture chỉ trong process kiểm thử. Không dùng JWT fixture cố định ngoài môi trường test.

## VPS và quan sát event

Tích hợp bằng PR sau CI đạt. CI chuyển immutable release/JAR image SHA và deploy từng service, giữ `.image-tags` riêng; không build source hoặc mở DB/MQ/Redis ra Internet. Nginx HTTPS tới Gateway; Notification không có public route. `.env` ngoài release, owner deploy/mode 600, hostname RabbitMQ cố định.

Health UP chỉ xác nhận app/DB đang phục vụ, không chứng minh event đã xử lý. Qua SSH, kiểm tra số row `outbox_events.published_at IS NULL` trong Order/Payment, tuổi event pending và queue backlog/consumer count. Query bằng đúng database service; không truyền password vào command/log. Không tự xóa pending event, queue, volume hoặc sửa trạng thái order để làm dashboard xanh.

Khi Payment tắt/chưa simulate, Order giữ PENDING_PAYMENT và reserved. Khi Payment phục hồi, durable `order.created` được nhận; ADMIN simulate SUCCESS/FAILED để demo. Khi Notification tắt, Saga vẫn hoàn tất và final-order event chờ trong durable queue; consumer phục hồi ghi log một lần. Chưa có expiration/cancel timeout, thanh toán thật/refund, email/SMS, DLQ/replay malformed event hoặc kiểm thử tải sản xuất. Outbox/consumer dùng at-least-once transport và xử lý nghiệp vụ idempotent.

Rollback image không rollback schema Hibernate. Kết quả rollout và giới hạn thực tế: [nhật ký](commits-and-deployments.md), [tests ngày 2](day2-test-results.md), [API contract](api-contract.md).
