# Auth API

Base URL local: `http://127.0.0.1:8081`.

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
