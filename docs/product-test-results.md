# Kiểm thử Product

Kiểm chứng local ngày 2026-10-11 (Asia/Bangkok):

- 8 Java tests với H2: public catalog, ADMIN CRUD, USER 403, anonymous 401, SKU chuẩn hóa và trùng 409, giá/field/pagination không hợp lệ 400, JWT sai 401, soft-delete giữ bản ghi nhưng public GET trả 404.
- 1 Java live test qua Docker Gateway/Auth-compatible JWT/real PostgreSQL: CRUD, RBAC, validation, duplicate SKU và 6 request đồng thời cùng SKU — đúng 1 response 201, 5 response 409.
- Live ADMIN/USER token là fixture ký bằng JWT secret của môi trường test; luồng phát token Auth được kiểm thử riêng bởi Gateway live test.
- Product có `product_db`/`product_user` riêng. Không truy vấn DB Auth/Order; mỗi module sở hữu security, DTO, mapper và error handler riêng.
- CI/VPS deployment chỉ được ghi đạt sau khi hoàn tất PR và xác minh image SHA trên VPS.
