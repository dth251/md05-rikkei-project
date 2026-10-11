# API contract

Base URL local qua Gateway: `http://127.0.0.1:8080`. Production: `https://md05-rikkei-project.duckdns.org`.

## Auth

| Method | Endpoint | Quyền | Thành công |
|---|---|---|---|
| POST | `/api/auth/register` | Public | 201 |
| POST | `/api/auth/login` | Public | 200 |
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
    "expiresIn": 3600,
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

JWT dùng HS256 với key tối thiểu 32 random bytes, được truyền qua `JWT_SECRET` dạng Base64. Contract: `sub` là user ID, `iss=omnichannel-auth`, `iat`, `exp`, `roles=["USER"]` hoặc `["ADMIN"]`. Thời hạn mặc định 1 giờ. Token thiếu claim, sai issuer/signature, hết hạn hoặc role lạ trả 401.

Lỗi giữ HTTP status thực tế: 400 validation/JSON, 401 xác thực, 403 quyền, 404 tài nguyên, 409 email trùng. Body có `success=false`, `data=null`, `message` và `errors` (map lỗi từng trường nếu có). Health dùng response chuẩn Spring Actuator, không bọc ApiResponse.

ADMIN được cấp qua thao tác quản trị database trên server sau khi tài khoản đã đăng ký; không có endpoint public tự nâng quyền. Lấy token mới sau khi đổi role. Token đã cấp vẫn giữ quyền trong claim đến khi hết hạn; MVP chưa có refresh token, blacklist hoặc thu hồi token tức thời.

## Product

| Method | Endpoint | Quyền | Thành công |
|---|---|---|---|
| GET | `/api/products?page=0&size=20` | Public | 200, `data.content`, `page`, `size`, `totalElements`, `totalPages` |
| GET | `/api/products/{id}` | Public | 200 |
| POST | `/api/products` | ADMIN | 201 |
| PUT | `/api/products/{id}` | ADMIN | 200 |
| DELETE | `/api/products/{id}` | ADMIN | 200, soft-delete |

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

Response `data` chứa `id`, các field trên, `active`, `createdAt`, `updatedAt`. DELETE giữ dữ liệu và đánh dấu inactive; catalog/get public loại sản phẩm này và GET theo id trả 404. Thiếu token khi ghi trả 401, USER ghi trả 403. Product không quản lý tồn kho.

## Gateway và Order skeleton

Gateway giữ nguyên prefix API, xác minh JWT, loại header giả mạo identity, trả `X-Gateway: api-gateway` và request ID. Login giới hạn theo IP: 2 request/giây, burst 5; `/api/orders/**` theo user ID: 20 request/giây, burst 40. Vượt quota trả 429. Redis không hoạt động khi áp dụng quota trả 503; backend không truy cập được trả 503. IP header chỉ được tin từ proxy cấu hình; Nginx ghi đè IP client.

`GET /gateway-status` trên domain trả trạng thái Gateway; `/` redirect tới đó. `/actuator/**` không công khai trên domain. Redis không thuộc health dependency của Gateway: health UP không chứng minh các route phụ thuộc Redis đang phục vụ.

Order hiện chỉ có security, health và schema `orders`, `order_items`, `inventory` trong DB riêng. `/api/orders` thiếu JWT trả 401; JWT hợp lệ tới skeleton trả 404. Route `/api/inventory/**` cũng đi tới Order. API đặt hàng WEB/POS, atomic reservation, idempotency xử lý request và Saga sẽ triển khai ngày 2. Route Payment đã được khai báo cho ngày 2 nhưng service chưa chạy.
