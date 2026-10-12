# Catalog gRPC Product–Order

Product và Order có Gradle Wrapper, proto và Java generated DTO riêng.
Wire package `commerce.catalog.v1` dùng unary `Catalog/GetProducts`.
Script `verify-rpc-contract.sh` so sánh hai proto, chỉ bỏ qua `java_package`;
không tạo shared module. Generated code nằm trong `build/`, không commit.
Gradle Eclipse synchronization chạy `generateProto` trước khi IDE import.

## Hợp đồng và lỗi

Mỗi request chứa 1–50 product IDs dương, không trùng. Product truy vấn
active products theo batch và giữ đúng thứ tự request. Thiếu hoặc retired
bất kỳ SKU nào trả NOT_FOUND, không trả batch một phần. Response gồm ID,
SKU, name, giá dạng chuỗi thập phân và active; không dùng float cho tiền.
Order kiểm tra đầy đủ IDs, duplicate, SKU, name, giá dương và active trước
khi lưu snapshot. Create Order gửi một RPC cho toàn bộ items; replay
idempotency đã có vẫn trả đơn cũ trước khi gọi catalog.

Product trả INVALID_ARGUMENT cho batch sai, NOT_FOUND cho thiếu sản phẩm,
INTERNAL cho lỗi nội bộ với description cố định. Order giữ NOT_FOUND thành
HTTP 404; các lỗi khác, deadline và reply sai thành 503. Deadline mặc định
2500 ms, cấu hình hợp lệ 100–5000 ms; không tự retry và không fallback
sang HTTP khi RPC lỗi. HTTP chỉ dùng khi chủ động tắt RPC trong cấu hình.

## Mạng và cấu hình private

Compose bật RPC mặc định; Product listen 9002 trong Docker network.
Production không publish cổng 9002. Override local publish
`127.0.0.1:19002` để kiểm thử; không mở UFW/public proxy cho RPC.
Metadata `x-product-rpc-key` phải khớp `PRODUCT_RPC_TOKEN`, 32 random bytes
biểu diễn bằng 64 chữ hex thường. So sánh constant time; thiếu/sai trả
UNAUTHENTICATED trước service. Token được sinh bởi hai generate-env scripts,
chỉ lưu private `.env`; không dùng JWT người dùng làm khóa RPC.

Transport plaintext chỉ trong mạng riêng Docker, không có TLS end-to-end.
Nếu tách sang nhiều máy phải bổ sung TLS/mTLS trước khi mở mạng RPC.
Khóa không thay thế phân quyền ADMIN/USER trên API Gateway/Order HTTP.
Không log metadata/request credential. Thêm token vào private env hiện
có trước rollout, không overwrite hay rotate DB/JWT tự động. Deploy
Product trước Order; Product cũ không có RPC sẽ khiến Order mới trả 503.
Rollback cả hai về image trước RPC hoặc chủ động tắt RPC trong private env.

Server giới hạn inbound 16 KiB, metadata 8 KiB và 16 concurrent calls mỗi
connection; executor 4 threads, Netty boss 1/worker 2. Client dùng một
channel, executor 2 threads và event loop 1, reply tối đa 128 KiB; đóng
resources khi Spring shutdown. Health Product gồm lifecycle RPC server.
Metrics `commerce.product.rpc.duration` và
`commerce.order.product.rpc.duration` có status hữu hạn, không label ID/token.

## Kiểm chứng

Product 21 unit tests/bootJar đạt: batch 50, thứ tự/decimal, batch sai,
retired và authentication. Order 39 unit tests/bootJar đạt: wire Netty
loopback thật, một RPC cho 50 items, NOT_FOUND, sai khóa, deadline,
server stop, partial/duplicate/inactive/invalid price reply; HTTP fallback
trỏ vào cổng đóng để phát hiện nếu thực thi ngoài ý muốn.
Product 4/4 live PostgreSQL/Gateway/RPC đạt, gồm RPC thực ở 19002 và
luồng mua variant qua Order/Payment. Order 11/11 live đạt với RPC bật,
gồm stock race, expiry, broker outage và Payment recovery; zero
fail/error/skip. CI/VPS phát sinh sau checkpoint ghi bằng trạng thái thực
tế trong nhật ký/PR.

Versions: gRPC 1.84.1, protobuf Gradle plugin 0.9.5, protoc/runtime 3.25.9.
Theo [gRPC Java](https://github.com/grpc/grpc-java),
[version catalog chính thức](https://github.com/grpc/grpc-java/blob/v1.84.0/gradle/libs.versions.toml)
và [protobuf Gradle plugin](https://github.com/google/protobuf-gradle-plugin).
Chạy Gradle dependencyInsight kiểm tra runtime thực tế; không suy ra từ
chỉ version protoc. [Gradle Eclipse synchronizationTasks](https://docs.gradle.org/current/javadoc/org/gradle/plugins/ide/eclipse/model/EclipseModel.html)
đảm bảo generated source có trước project synchronization.
