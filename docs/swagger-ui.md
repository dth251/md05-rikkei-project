# Swagger UI và Postman API chính

Swagger UI: **https://md05-rikkei-project.duckdns.org/swagger-ui/**.

Trang liệt kê 35 thao tác nghiệp vụ thuộc tám nhóm: tài khoản, địa chỉ,
sản phẩm, kho, đơn hàng, thanh toán, trả hàng và hoàn tiền. Mỗi method/path
xuất hiện một lần; không có polling, test âm, endpoint simulate, callback
IPN/return hoặc API tra người dùng dành cho vận hành.

## Gọi thử bằng Swagger UI

1. Mở nhóm tài khoản, chọn `POST /api/auth/login`, bấm **Try it out**.
2. Điền email/mật khẩu USER hoặc ADMIN được cấp riêng rồi **Execute**.
3. Sao chép `data.accessToken`, bấm **Authorize**, dán token (không thêm
   `Bearer`) rồi xác nhận. Trang không lưu token qua lần tải lại.
4. Chọn API cần dùng, nhập ID/body và gửi. Quyền PUBLIC/USER/ADMIN được ghi
   ở phần mô tả; service vẫn kiểm tra quyền và chủ sở hữu dữ liệu.
5. Khi đổi vai trò, đăng nhập tài khoản tương ứng và thay token trong
   Authorize. Các bước ADMIN phải dùng ADMIN; USER đặt đơn bằng tài khoản
   sẽ theo dõi/thanh toán đơn đó.

Request ghi có tác động thật tới dữ liệu demo. Tạo đơn ONLINE bắt đầu giữ
hàng 200 giây; tra Payment theo đơn rồi tạo link VNPay ngay và mở link
trên trình duyệt. Hồ sơ REFUND_PENDING chưa chứng minh tiền đã hoàn.

## File Postman mới

Import [md05-api-chinh.postman_collection.json](../tests/postman/md05-api-chinh.postman_collection.json),
chọn **No environment**, nhập tài khoản trực tiếp trong request đăng nhập.
Token tự lưu tại collection Variables. ID sản phẩm, đơn, Payment, địa chỉ
và amount được nối sau các request tạo/tra thành công; có thể chỉnh lại
trong Variables để thao tác với dữ liệu đang có.

File này là danh mục thao tác thủ công, không Run toàn bộ: các request
xóa, hủy, giao hàng và hoàn tiền thuộc các nhánh khác nhau. Khi tạo lại
một request, `{{$guid}}` sinh key mới; muốn thử replay thì tự nhập lại
cùng key. Hai loại thanh toán dùng chung request tạo đơn: đổi
`paymentMethod` giữa ONLINE và COD; COD phải có địa chỉ đầy đủ.

File `md05-luong-chinh.postman_collection.json` được giữ nguyên.

## Nguồn và cập nhật

Hợp đồng gốc: [api-gateway.json](openapi/api-gateway.json), được export từ
controller/service tests. Chạy script sau để tái tạo bản gọn và Postman:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File docs/infrastructure/scripts/build-main-api-docs.ps1
```

Swagger UI sử dụng bản chính thức 5.33.2, commit
`e89a91f13cdee432d97ca9d3d1ac1969ff797036`; script
`install-api-docs.sh` tải bundle/CSS và kiểm tra SHA-256 trong
`assets.sha256` trước khi kích hoạt release tài liệu. Nginx phục vụ file
tĩnh tại `/swagger-ui/`; API dùng cùng origin nên không cần mở thêm CORS.
Không thay đổi service hoặc mở public `/v3/api-docs`.

```bash
DOCS_ROOT=/var/www/md05-api-docs DOCS_REVISION=<full-tested-git-sha> \
  bash docs/infrastructure/scripts/install-api-docs.sh
```

Lần đầu root tạo thư mục tài liệu riêng, sau đó tài khoản deploy cập nhật:

```bash
install -d -o deploy -g deploy -m 755 /var/www/md05-api-docs
```

Thư mục web này chỉ chứa tài liệu và Swagger assets. Không nới quyền thư
mục deployment có cấu hình private để Nginx đọc. Cần thêm các location
Swagger trong template Nginx vào site dự án,
kiểm tra `nginx -t` rồi reload. Chỉ cập nhật tài liệu ở các lần sau thì
không cần restart ứng dụng. Chứng chỉ và cấu hình API hiện có được giữ.

Nguồn dependency: [Swagger UI installation](https://swagger.io/docs/open-source-tools/swagger-ui/usage/installation/),
[release 5.33.2](https://github.com/swagger-api/swagger-ui/releases/tag/v5.33.2).
