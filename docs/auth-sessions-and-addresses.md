# Refresh/logout và địa chỉ của Auth

Login trả thêm `refreshToken` và `refreshExpiresIn`. Access JWT mới có TTL
15 phút; token đã cấp trước nâng cấp giữ thời hạn cũ của nó. Refresh credential
là 32 byte ngẫu nhiên, Base64 URL không padding, dài 43 ký tự; DB chỉ lưu
SHA-256, không lưu credential plaintext.

| API | Quyền / kết quả |
|---|---|
| POST `/api/auth/refresh` | Body `refreshToken`; không cần access JWT; trả cặp access/refresh mới |
| POST `/api/auth/logout` | Body `refreshToken`; thu hồi family refresh, trả 204; gọi lại idempotent |
| GET `/api/auth/addresses` | JWT; danh sách địa chỉ của chính user |
| GET `/api/auth/addresses/{id}` | JWT; địa chỉ không thuộc user trả 404 |
| POST `/api/auth/addresses` | JWT; tạo địa chỉ, trả 201 |
| PUT `/api/auth/addresses/{id}` | JWT; thay nội dung địa chỉ thuộc user |
| DELETE `/api/auth/addresses/{id}` | JWT; xóa địa chỉ thuộc user, trả 204 |

Body địa chỉ: `recipientName`, `phone`, `street`, `ward`, `district`, `province`,
`postalCode` tùy chọn và boolean `defaultAddress`. Không nhận `userId`; chủ sở
hữu lấy từ JWT. Tối đa mười địa chỉ, địa chỉ đầu tiên tự làm mặc định. Khi bỏ
mặc định hoặc xóa địa chỉ mặc định, một địa chỉ còn lại được chọn thay thế.

Mọi thay đổi địa chỉ khóa row User trước khi đọc/ghi. UNIQUE trên
`default_user_key` (user ID hoặc NULL) cùng CHECK khớp `user_id` bảo đảm một
default/user ở DB. Bỏ default cũ và flush trước khi gán default mới; toàn bộ
thao tác trong cùng transaction.

## Rotation và thu hồi phiên

Family có thời hạn tuyệt đối bảy ngày từ login, không kéo dài khi refresh.
Mọi rotation/replay/logout khóa cùng family row; không khóa riêng các token
theo thứ tự khác nhau. Token cũ được giữ để nhận biết replay. Nếu dùng lại
token đã xoay, family bị thu hồi và HTTP 401 chỉ được tạo sau khi transaction
thu hồi commit. Family từ login khác vẫn hoạt động.

Client phải tuần tự hóa refresh của cùng phiên. Hai request dùng cùng token
đồng thời có một winner, request còn lại bị nhận là replay và thu hồi cả
successor. Nếu mất response sau khi server đã xoay token, đăng nhập lại.
Hành vi rotation/replay tham khảo [RFC 9700 §4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14);
API đăng nhập riêng của dự án không được mô tả là một OAuth authorization server.

Logout/replay không vô hiệu hóa ngay JWT stateless đã cấp: JWT đó còn hợp lệ
tới `exp` (tối đa 15 phút cho JWT mới, có thể lâu hơn với JWT cũ). Không tuyên
bố logout đã thu hồi JWT ở các service khác. Login/refresh trả `Cache-Control:
no-store`. Không log body credential, bearer header hoặc response token.

Cleanup mỗi giờ xóa tối đa 100 family đã hết hạn hơn một ngày cùng lịch sử
token. Đó là giới hạn công việc mỗi tick, không phải cam kết giữ đúng một ngày
khi có backlog. `auth.refresh-ttl` phải trong khoảng một phút đến ba mươi ngày.

Gateway cho phép POST refresh/logout không có access JWT, nhưng kiểm tra
credential vẫn ở Auth. Hai API dùng chung quota IP 2/s, burst 5, route riêng
với login; Redis lỗi trả 503 và không bỏ qua quota. Địa chỉ luôn yêu cầu JWT.

## Kiểm chứng

20 unit/API tests Auth và 18 Gateway tests đạt tại checkpoint local. Có kiểm tra
rotation, reuse revocation đã commit, một winner khi refresh đồng thời, token
hết hạn, logout bằng token cũ, hash-only storage, chống sửa ownership, CRUD và
20 lần tạo địa chỉ đồng thời chỉ có mười địa chỉ/một default. Live Auth mở rộng
đã đạt trên PostgreSQL local: rotation đồng thời, hash-only storage, địa chỉ
và restart giữ dữ liệu. Trạng thái
CI/deploy thực tế ghi trong PR và `commits-and-deployments.md`.
