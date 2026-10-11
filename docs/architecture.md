# Kiến trúc backend sáu app

```mermaid
flowchart LR
    Client[Client / Postman / k6] -->|HTTPS 443| Nginx[Nginx TLS]
    Nginx -->|loopback 8080| Gateway[API Gateway: JWT / Redis quotas]
    Gateway --> Auth[Auth 8081]
    Gateway --> Product[Product 8082]
    Gateway --> Order[Order / Inventory 8083]
    Gateway --> Payment[Payment mock 8085]
    Gateway --> Redis[(Private Redis)]
    Order -->|Product snapshot HTTP| Product
    Auth --> AuthDB[(auth_db)]
    Product --> ProductDB[(product_db)]
    Order --> OrderDB[(order_db + Outbox)]
    Payment --> PaymentDB[(payment_db + Outbox)]
    Notification[Notification 8084] --> NotificationDB[(notification_db)]
    Order -->|order.created / final event| Rabbit[(Durable RabbitMQ)]
    Rabbit -->|order.created| Payment
    Payment -->|payment.succeeded / failed| Rabbit
    Rabbit -->|payment outcome| Order
    Rabbit -->|order.confirmed / cancelled| Notification
```

Năm database nằm trong một PostgreSQL instance, mỗi service có user riêng không CONNECT sang DB khác. Inventory thuộc Order, Product chỉ quản lý catalog. Notification không có public route. Chỉ SSH/HTTP/HTTPS public; business/Actuator ports bind loopback, DB/MQ/Redis không publish trên VPS.

```mermaid
sequenceDiagram
    participant C as Client WEB/POS
    participant G as Gateway
    participant O as Order
    participant P as Product
    participant R as RabbitMQ
    participant M as Payment mock
    participant N as Notification
    C->>G: POST /api/orders + JWT + idempotencyKey
    G->>O: Verify JWT + apply user quota
    O->>P: Fetch current product snapshot
    O->>O: TX: order + atomic reserve + Outbox
    O-->>C: 201 PENDING_PAYMENT (200 on replay)
    O->>R: Persistent order.created + confirm
    R->>M: Consume, deduplicate, store PENDING
    C->>G: ADMIN simulate SUCCESS / FAILED
    G->>M: Update payment + own Outbox in TX
    M->>R: payment.succeeded / payment.failed
    R->>O: Claim event, lock order, finalize once
    alt SUCCESS
        O->>O: CONFIRMED, reserved -= quantity
    else FAILED
        O->>O: CANCELLED, reserved -= quantity, available += quantity
    end
    O->>R: order.confirmed / order.cancelled
    R->>N: Commit one notification log per order, ACK
```

Transport at-least-once; Outbox retry và consumer deduplicate giữ nghiệp vụ idempotent. Không tuyên bố exactly-once transport. Payment chưa simulate giữ reservation; chưa có timeout/hủy tự động. Rollback image không rollback schema Hibernate. Một Gateway replica là single point of failure; HA cần nhiều replica/load balancer và hạ tầng DB/MQ phù hợp.

Prometheus scrape qua SSH/private network tại `/actuator/prometheus` mỗi app; Nginx không route `/actuator/**`. JVM/process/HTTP metrics đi cùng tag `application`; health không thay thế kiểm tra pending Outbox và queue/consumer. Chưa cài Prometheus/Grafana/Loki/tracing stack trên VPS 1 GiB RAM.
