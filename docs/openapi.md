# OpenAPI của các API nghiệp vụ

Hợp đồng đã export từ controller thực và kiểm thử nằm trong `docs/openapi/`:
Auth, Product, Order, Payment và bản gộp API Gateway. Bản gộp hiện có
27 paths, 34 operations, operation IDs duy nhất và `x-service` chỉ service
chịu trách nhiệm. Notification nhận event, hợp đồng event được mô tả trong
`api-contract.md` và `order-fulfillment.md`; không tạo REST nghiệp vụ giả cho nó.

Mỗi service tự sở hữu cấu hình/schema; không tạo shared module. Dùng
[springdoc 2.8.17](https://github.com/springdoc/springdoc-openapi/releases/tag/v2.8.17)
với Spring Boot 3.5.6, theo [compatibility matrix](https://springdoc.org/v2/).
Chỉ dùng starter API, không tạo frontend hay Swagger UI.

## Truy cập và xác thực

`/v3/api-docs` và `/v3/api-docs.yaml` chỉ nhận JWT ADMIN tại từng service.
Anonymous nhận 401, USER nhận 403; ADMIN nhận JSON 200. Endpoint chỉ ở
loopback HTTP riêng (8081, 8082, 8083, 8085), không thêm route public cho
tài liệu qua Gateway/Nginx. Có thể lấy bằng SSH tunnel; tránh đưa JWT
lên command line hoặc ghi response headers vào log.

Import `docs/openapi/api-gateway.json` vào công cụ API rồi đặt JWT cá nhân.
Server của bản gộp trỏ đến domain HTTPS của dự án. Đăng ký/login/refresh/
logout và Product GET công khai được đánh dấu security rỗng; các API khác
dùng bearer JWT với `x-required-role` PUBLIC/USER/ADMIN. Metadata OpenAPI
mô tả API, phân quyền thực thi vẫn ở security của từng service/Gateway.

Schema gồm validation DTO, pagination/filter, item limit 50, stock
idempotency, shipping address/shipment, paymentMethod COD/ONLINE, biên nhận COD và các HTTP status. Password và
refresh credential của request là writeOnly. Response lỗi tham chiếu
schema ApiError thực sự tồn tại; actuator không lọt vào hợp đồng.

## Sinh lại và kiểm chứng

Chạy `test` của bốn service bằng Wrapper riêng. OpenApiTests kiểm tra
security, controller/schema, toàn bộ references và export file
`<service>/build/openapi/<service>.json`. Sau đó chạy:

```powershell
pwsh -File infrastructure/scripts/export-openapi.ps1
```

Script chỉ ghi trong repository, từ chối hợp đồng/schema/path xung đột
hoặc thiếu references, thêm tiền tố service cho operation ID bản gộp.
CI chạy cùng export sau Java tests và lưu JSON cùng reports làm artifact.
Sau khi thay controller/DTO phải sinh lại tài liệu và review diff.

OpenApiLiveTests của Auth kiểm tra trực tiếp cả bốn container với
`OPENAPI_LIVE_TEST=true`: anonymous/USER/ADMIN, đúng title, ApiError,
không actuator. Dùng JWT fixture private qua environment, không ghi
business data. Đây là kiểm chứng khác với MockMvc export.

## Tài nguyên và trạng thái triển khai

Local container Auth/Product/Order/Payment dùng giới hạn 320 MiB,
heap 112 MiB, metaspace 128 MiB, code cache 32 MiB, CPU count 2 và
thread stack 256 KiB. Sau đọc tài liệu, đo local tương ứng khoảng
290/292/292/266 MiB; đây không phải kết quả tải lớn hoặc đo VPS.
Giới hạn 320 MiB là trần, không cấp sẵn RAM. VPS hiện 969 MiB RAM còn
phụ thuộc swap; chưa có bằng chứng nâng cấp máy hoặc sức tải 500/1000 users.

Checkpoint feature: bốn OpenApiTests/bootJar và live docs trên bốn container
đạt. Trước đó live bắt được references ApiError bị springdoc loại bỏ;
đã sửa customizer và kiểm tra references lại. Feature chưa merge/deploy
VPS tại lúc ghi tài liệu; CI và image/health phải ghi riêng trong PR/nhật ký.
