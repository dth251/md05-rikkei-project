# API contract

Base URL local qua Gateway: `http://127.0.0.1:8080`. Production: `https://md05-rikkei-project.duckdns.org`.

## Auth

| Method | Endpoint | Quyền | Thành công |
|---|---|---|---|
| POST | `/api/auth/register` | Public | 201 |
| POST | `/api/auth/login` | Public | 200 |
| POST | `/api/auth/refresh` | Refresh credential | 200 |
| POST | `/api/auth/logout` | Refresh credential | 204 |
| GET/POST | `/api/auth/addresses` | USER/ADMIN, chính user | 200/201 |
| GET/PUT/DELETE | `/api/auth/addresses/{id}` | USER/ADMIN, chính user | 200/200/204 |
| GET | `/api/auth/me` | USER/ADMIN | 200 |
| GET | `/api/auth/admin/users/{id}` | ADMIN | 200 |
| GET | `/actuator/health` | Loopback/administration | 200, `status=UP` |

Request đăng ký:

```json
{
  "fullName": "Demo User",
  "email": "demo@example.com",
  "password": "YOUR_PRIVATE_PASSWORD"
}
```

Đăng nhập gửi `email` và `password`. Email được chuyển sang chữ thường. Mật khẩu đăng ký tối thiểu 8 ký tự, tối đa 72 UTF-8 bytes; database chỉ lưu BCrypt hash. Register luôn tạo USER; gửi thêm `role` bị từ chối 400.

Response nghiệp vụ có dạng `ApiResponse<T>` theo MD03:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "JWT_RETURNED_BY_SERVER",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshToken": "OPAQUE_CREDENTIAL_RETURNED_BY_SERVER",
    "refreshExpiresIn": 604799,
    "user": {
      "id": 1,
      "fullName": "Demo User",
      "email": "demo@example.com",
      "role": "USER",
      "createdAt": "2026-10-11T00:00:00Z"
    }
  },
  "errors": null,
  "timestamp": "2026-10-11T00:00:00Z"
}
```

Token nằm tại `data.accessToken`. Gửi `Authorization: Bearer <token>` cho API cần xác thực.

JWT dùng HS256 với key tối thiểu 32 random bytes, được truyền qua `JWT_SECRET` dạng Base64. Contract: `sub` là user ID, `iss=omnichannel-auth`, `iat`, `exp`, `roles=["USER"]` hoặc `["ADMIN"]`. JWT mới có thời hạn mặc định 15 phút; JWT cũ giữ `exp` đã cấp. Token thiếu claim, sai issuer/signature, hết hạn hoặc role lạ trả 401.

Lỗi giữ HTTP status thực tế: 400 validation/JSON, 401 xác thực, 403 quyền, 404 tài nguyên, 409 email trùng. Body có `success=false`, `data=null`, `message` và `errors` (map lỗi từng trường nếu có). Health dùng response chuẩn Spring Actuator, không bọc ApiResponse.

ADMIN được cấp qua thao tác quản trị database trên server sau khi tài khoản đã đăng ký; không có endpoint public tự nâng quyền. Lấy token mới sau khi đổi role. Token đã cấp vẫn giữ quyền trong claim đến khi hết hạn. Refresh có rotation, phát hiện replay và logout thu hồi family; JWT đã cấp chưa bị thu hồi tức thời. Địa chỉ kiểm tra ownership, tối đa mười địa chỉ/một default. Chi tiết body và hành vi cạnh tranh: [auth-sessions-and-addresses.md](auth-sessions-and-addresses.md).

## Product

| Method | Endpoint | Quyền | Thành công |
|---|---|---|---|
| GET | `/api/products?page=0&size=20` | Public | 200, `data.content`, `page`, `size`, `totalElements`, `totalPages` |
| GET | `/api/products/{id}` | Public | 200 |
| POST | `/api/products` | ADMIN | 201 |
| PUT | `/api/products/{id}` | ADMIN | 200 |
| DELETE | `/api/products/{id}` | ADMIN | 200, soft-delete |
| GET | `/api/products/{parentId}/variants` | Public | 200 |
| POST | `/api/products/{parentId}/variants` | ADMIN | 201 |

POST/PUT nhận cùng request:

```json
{
  "sku": "DEMO-001",
  "name": "Demo Product",
  "description": "Optional description",
  "unitPrice": 125000.00,
  "category": "Demo",
  "brand": "Example"
}
```

SKU bắt buộc, tối đa 64 ký tự, gồm chữ/số/dấu chấm/gạch dưới/gạch ngang, bắt đầu bằng chữ hoặc số; lưu dạng chữ hoa và duy nhất cả với sản phẩm đã soft-delete. Tên bắt buộc, tối đa 200 ký tự. Giá tối thiểu 0.01, tối đa 12 chữ số phần nguyên và 2 chữ số thập phân. Description tối đa 2000 ký tự; category/brand tối đa 100. Page không âm, size từ 1 đến 100. Lỗi dữ liệu trả 400; SKU trùng hoặc xung đột cập nhật trả 409.

Response `data` chứa `id`, các field trên, `active`, `createdAt`, `updatedAt`, `parentId` và `attributes`. DELETE giữ dữ liệu và đánh dấu inactive; xóa parent ẩn cả children. Catalog/get public loại sản phẩm này và GET theo id trả 404. Thiếu token khi ghi trả 401, USER ghi trả 403. Product không quản lý tồn kho.

GET danh sách hỗ trợ `q`, `category`, `brand`, `minPrice`, `maxPrice`, `sort`; page tối đa 10000. Biến thể có SKU/giá riêng, attributes chuẩn hóa và bất biến. Contract, giới hạn, body và migration: [product-search-and-variants.md](product-search-and-variants.md).

## Gateway

Gateway giữ nguyên prefix API, xác minh JWT, loại header giả mạo identity, trả `X-Gateway: api-gateway` và request ID. Login giới hạn theo IP: 2 request/giây, burst 5; `/api/orders/**` theo user ID: 20 request/giây, burst 40. Vượt quota trả 429. Redis không hoạt động khi áp dụng quota trả 503; backend không truy cập được trả 503. IP header chỉ được tin từ proxy cấu hình; Nginx ghi đè IP client.

Refresh/logout dùng chung quota IP 2/s, burst 5 trên route riêng với login. Redis lỗi trả 503; địa chỉ vẫn cần JWT.

`GET /gateway-status` trên domain trả trạng thái Gateway; `/` redirect tới đó. `/actuator/**` không công khai trên domain. Redis không thuộc health dependency của Gateway: health UP không chứng minh các route phụ thuộc Redis đang phục vụ. Gateway có response deadline 10 giây; nếu upstream chưa trả trước deadline, trả 504 với API error envelope. Product disconnect thường trả 503; Order phụ thuộc snapshot Product có thể nhận 503 hoặc 504 qua Gateway khi dependency/DNS/network chậm.

Mỗi app có private `GET /actuator/prometheus` với JVM/process/HTTP metrics và tag `application`; chỉ scrape qua loopback/SSH/internal network. Endpoint `/actuator/env` vẫn được bảo vệ; domain public trả 404 cho Actuator. Chưa deploy Prometheus/Grafana server.

## Order và Inventory

| Method | Endpoint | Quyền | Thành công |
|---|---|---|---|
| POST | `/api/orders` | USER/ADMIN | 201 lần tạo đầu, 200 replay |
| GET | `/api/orders/{id}` | Chủ đơn/ADMIN | 200; người khác 404 |
| GET | `/api/inventory/{productId}` | JWT | 200 |
| POST | `/api/inventory/stocks` | ADMIN | 200, nhập thêm kho |

```json
{
  "channel": "WEB",
  "idempotencyKey": "user-web-001",
  "items": [{ "productId": 1, "quantity": 2 }]
}
```

Channel là WEB/POS. Key bắt buộc, tối đa 128 ký tự, duy nhất theo user. Items có 1–50 sản phẩm khác nhau; quantity 1–10000. User ID lấy từ JWT, giá/SKU/name lấy snapshot Product; client không truyền giá. Không đủ kho 409; không có Product 404; catalog không truy cập được 503. Tổng tiền hỗ trợ tối đa 16 chữ số phần nguyên/2 thập phân. Order, reservation và `order.created` Outbox nằm trong một transaction; thất bại rollback tất cả.

Replay cùng key và cùng channel/items/quantity trả cùng order, không giữ kho hay tạo event lần nữa. Đổi nội dung trả 409; thứ tự items không làm đổi fingerprint. Replay đơn đã có không phụ thuộc Product đang hoạt động. Request thất bại chưa lưu đơn không chiếm key. Response `data`: `id`, `userId`, `channel`, `status`, `totalAmount`, `items` (snapshot), `createdAt`.

Nhập kho nhận `{"productId":1,"quantity":10,"idempotencyKey":"receipt-001"}`; quantity 1–1000000, key bắt buộc/tối đa 128 ký tự. Retry phải giữ nguyên key: cộng kho đúng một lần và trả snapshot của phiếu nhập đầu tiên; dùng lại key với product/quantity khác trả 409. Phiếu và kho commit cùng transaction; lỗi rollback không chiếm key. Response inventory: `productId`, `available`, `reserved`. Tổng available/reserved được giới hạn để hoàn kho không tràn số nguyên. WEB/POS cùng dùng một inventory trong Order DB.

## Payment mock

| Method | Endpoint | Quyền | Thành công |
|---|---|---|---|
| GET | `/api/payments/{id}` | Chủ đơn/ADMIN | 200 |
| GET | `/api/payments/by-order/{orderId}` | Chủ đơn/ADMIN | 200, 404 khi chưa có/không được đọc |
| POST | `/api/payments/{id}/simulate` | ADMIN | 200 |

Consumer `order.created` tạo payment PENDING bất đồng bộ; client có thể nhận 404 trước khi consumer xử lý. Simulate nhận `{"status":"SUCCESS"}` hoặc `{"status":"FAILED"}`. PENDING/enum sai 400; cùng kết quả replay 200; đổi kết quả sau hoàn tất 409. Thiếu JWT 401, USER simulate 403. Response: `id`, `orderId`, `userId`, `amount`, `status`, `createdAt`, `completedAt`.

Payment result ghi cùng transaction vào Payment Outbox. Order nhận SUCCESS → CONFIRMED và trừ reserved; FAILED → CANCELLED, trừ reserved và hoàn available đúng một lần. Khóa đơn, event ID và trạng thái cuối ngăn duplicate hoặc kết quả ngược đến muộn làm đổi kho lần nữa. Đây là thanh toán mock, chưa tích hợp cổng tiền thật/refund/webhook.

## Notification và event contract

Notification không có API client hoặc Gateway route. Consumer ghi một notification log cho mỗi order final vào DB riêng; MVP chưa gửi email/SMS. Event IDs và UNIQUE order ID chống duplicate delivery.

Event JSON của mỗi service tự sở hữu gồm `eventId` (UUID), `type`, `orderId`, `userId`, `amount`, `paymentId`, `occurredAt` (ISO UTC). `paymentId=null` với `order.created`; các outcome/final events có payment ID. Exchange topic durable `commerce.events`; ba durable queues và năm bindings như kế hoạch. Routing: `order.created` → `payment.succeeded/payment.failed` → `order.confirmed/order.cancelled`.

Order/Payment có transactional Outbox, persistent messages và broker confirms/returns. Consumer ACK sau local DB commit và deduplicate. Delivery là at-least-once; không tuyên bố exactly-once transport. Payload sai bị reject, chưa có malformed-event DLQ/replay tool; sự cố DB/MQ tạm thời được thử lại. Reservation chưa có timeout/hủy tự động: Payment dừng hoặc chưa simulate thì order giữ PENDING_PAYMENT đến khi xử lý tiếp.
