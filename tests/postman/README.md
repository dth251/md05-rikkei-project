# Postman và Swagger UI

Xem API trên trình duyệt: **https://md05-rikkei-project.duckdns.org/swagger-ui/**.

| Mục đích | File | Số request |
|---|---|---:|
| Xem và gọi từng API nghiệp vụ, mỗi thao tác một lần | [md05-api-chinh.postman_collection.json](md05-api-chinh.postman_collection.json) | 35 |
| Demo các luồng chính theo thứ tự | [md05-luong-chinh.postman_collection.json](md05-luong-chinh.postman_collection.json) | 42 |
| Bộ kiểm thử đầy đủ, gồm ca lỗi và phân quyền | [md05-tat-ca-api.postman_collection.json](md05-tat-ca-api.postman_collection.json) | 181 |

## Bản API chính

Import file 35 request, chọn **No environment**. Nhập email/mật khẩu trực
tiếp trong Body đăng nhập. Token được lưu trong collection Variables;
đăng nhập lại khi đổi USER/ADMIN. Các request tạo/tra dữ liệu tự lưu ID
sản phẩm, đơn, Payment và địa chỉ; có thể chỉnh Variables để dùng ID khác.

Gửi từng API cần dùng, không Run toàn bộ: các bước hủy, giao hàng, trả hàng
và hoàn tiền thuộc những nhánh nghiệp vụ khác nhau. Xem
[hướng dẫn Swagger và API chính](../../docs/swagger-ui.md).

## Bản luồng chính đang dùng để demo

File 42 request được giữ nguyên. Chọn **No environment**, nhập tài khoản
trực tiếp trong request đăng nhập USER và ADMIN. Token riêng của hai vai
và ID được lưu trong Variables của chính collection đó.

Xem [thứ tự và điều kiện demo](../../docs/test-luong-chinh.md). Đơn ONLINE
giữ hàng 200 giây; mở link VNPay ngay sau khi tạo checkout. COD có quy
trình giao/thu tiền riêng. Không chạy song song các lượt dùng chung biến.

## Bộ đầy đủ

Bản 181 request cần environment private tương ứng. Chỉ dùng khi cần các
ca kỹ thuật, ownership, replay và lỗi; xem
[hướng dẫn bộ đầy đủ](../../docs/postman-mot-file.md) và
[hướng dẫn production](../../docs/test-production.md).

Thông tin đăng nhập và token chỉ dùng trong môi trường riêng, không
commit/export vào repository. Giữ lại ID để đối chiếu khi gặp lỗi.
