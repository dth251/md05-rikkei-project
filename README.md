# md05-rikkei-project

Backend Omnichannel WEB/POS: **Client → Nginx HTTPS → API Gateway → Auth / Product / Order / Payment**, RabbitMQ chuyển event tới Payment, Order và Notification. Sáu app Java độc lập, mỗi app có Gradle Wrapper Groovy, DTO/security/error handlers riêng theo MD03.

Java 21 · Spring Boot 3.5.6 · Spring Cloud 2025.0.3 · PostgreSQL · RabbitMQ · Redis · Docker Compose · GitHub Actions/GHCR. Không có frontend, shared module, Maven, Flyway hoặc Liquibase.

| App | Loopback port | Sở hữu |
|---|---:|---|
| api-gateway | 8080 | JWT verify, routes, request ID, Redis login/order quota |
| auth-service | 8081 | auth_db, USER registration, BCrypt, JWT, ADMIN RBAC |
| product-service | 8082 | product_db, catalog CRUD ADMIN, soft-delete |
| order-service | 8083 | order_db, WEB/POS order, atomic inventory, idempotency, Outbox/Saga |
| notification-service | 8084 | notification_db, durable final-event consumer, logs-only, deduplication |
| payment-service | 8085 | payment_db, mock PENDING/SUCCESS/FAILED, own Outbox |

Production Gateway: **https://md05-rikkei-project.duckdns.org**. `/gateway-status` là health, `/api/products` là catalog public. Notification/Actuator/DB/MQ/Redis không có public route. Một instance PostgreSQL có năm DB/user cách ly; inventory thuộc Order.

```text
api-gateway/             # WebFlux Gateway + Redis
auth-service/            # Controller → service interface/impl → repository
product-service/         # DTO request/response + mapper + own security
order-service/           # Atomic SQL inventory, order/items, Outbox/Saga
payment-service/         # Mock payment, event inbox/Outbox
notification-service/    # Idempotent event consumer and notification logs
infrastructure/          # PostgreSQL init, Rabbit topology, Nginx, Bash/PowerShell ops
tests/k6/                # Catalog/write/race/quota tool harness
tests/postman/           # API collection, no credentials
docs/                    # Contracts, runbooks, diagrams, measured evidence, Git/deploy journal
.github/workflows/ci.yml # Test all six apps, publish/deploy changed services
```

## Local build/run

Cài Java 21 và Docker Compose; dùng Wrapper từng app. Generator tạo `.env` private, không ghi đè file đang dùng:

```powershell
powershell -ExecutionPolicy Bypass -File infrastructure/scripts/generate-env.ps1
$services = 'auth-service','api-gateway','product-service','order-service','payment-service','notification-service'
foreach ($service in $services) {
    & ".\$service\gradlew.bat" -p $service test bootJar --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $service" }
}
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --wait postgres rabbitmq redis
docker compose run --rm --no-deps rabbitmq-topology
powershell -ExecutionPolicy Bypass -File infrastructure/scripts/verify-infrastructure.ps1 -AllowLoopback
docker compose build $services
docker compose up -d --no-deps --wait $services
```

Local Gateway `http://127.0.0.1:8080`; infrastructure test ports 15432/15672/15673/16379 chỉ loopback. Với volume RabbitMQ cũ, giữ hostname node đang dùng theo [CI/CD runbook](docs/ci-cd.md). Không xóa volume để xử lý lỗi hostname/topology.

## API và Saga

[API contract](docs/api-contract.md) mô tả request/response/status/roles và event. [Postman collection](tests/postman/md05.postman_collection.json) có 19 requests; nạp credential ADMIN riêng, không export token/password. Register luôn USER, không có public endpoint tự nâng ADMIN.

Order tạo đầu trả 201 `PENDING_PAYMENT`, replay cùng user/key/body trả 200 cùng đơn, đổi body trả 409. Order/atomic reservation/Outbox commit cùng transaction. `order.created → payment.succeeded/payment.failed → order.confirmed/order.cancelled`. SUCCESS tiêu thụ reserved; FAILED trả available đúng một lần. Notification ghi một log cho final order. Xem [sơ đồ Mermaid](docs/architecture.md).

Transport at-least-once, consumer deduplicate theo event/business key. Payment mock cần ADMIN simulate; reservation không tự hết hạn. Chưa có thanh toán thật/refund/SMTP, Elasticsearch, gRPC, DLQ replay hoặc tracing. Một Gateway replica chưa có HA.

## Kiểm chứng và triển khai

[Runbook ngày 2](docs/day2-operations.md) có unit/live/recovery Java; [runbook ngày 3](docs/day3-operations.md) có private Prometheus, forced image rollback, k6 resource guard và fixture. Race gọi Order private để tránh 429 che kết quả kho; quota kiểm tra riêng qua Gateway. Tải GET catalog và ghi Order đo riêng, report actual RPS/p95/p99/error/CPU/RAM/dropped iterations và queue depth. VPS nhỏ có thể dừng trước 500–1000 RPS; xem số liệu đo thật, không suy ra năng lực từ target.

Phát triển `feature/...`, commit từng phần đã kiểm thử với số nối tiếp lịch sử Git. PR CI test/build cả sáu app và integration PostgreSQL/RabbitMQ/Redis/recovery. Main publish GHCR immutable SHA cho app thay đổi, SSH deploy đúng service, health/smoke, ghi `.image-tags`; smoke lỗi thì phục hồi image trước. Không force-push, không build source trên VPS. Deploy dùng user deploy/SSH strict, secrets ở GitHub/.env private; chỉ SSH/80/443 public, UFW active. Rollback image không rollback schema Hibernate.

- [Kế hoạch mới nhất](Ke_hoach_Omnichannel_3_ngay_6_ung_dung_Co_API_Gateway.md)
- [Quy ước code](docs/coding-conventions.md)
- [Kết quả ngày 2](docs/day2-test-results.md)
- [Kết quả ngày 3](docs/day3-test-results.md)
- [Nhật ký SHA/CI/deploy](docs/commits-and-deployments.md)
- [CI/CD và VPS](docs/ci-cd.md)

Không commit `.env`, private key, JWT hoặc `.verification`. Giữ dữ liệu fixture audit và không thay đổi ứng dụng ngoài dự án.
