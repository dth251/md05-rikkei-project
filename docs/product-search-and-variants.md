# Tìm kiếm và biến thể Product

`GET /api/products` giữ response phân trang hiện có, bổ sung các query:

| Query | Hành vi |
|---|---|
| `q` | Tìm chuỗi con trong name/SKU/description, không phân biệt hoa thường; `%`, `_`, `\` là ký tự thường |
| `category`, `brand` | Khớp toàn bộ, không phân biệt hoa thường |
| `minPrice`, `maxPrice` | Bao gồm hai đầu, 0.01–999999999999.99, tối đa hai chữ số thập phân; min lớn hơn max trả 400 |
| `sort` | `id` mặc định, `name`, `price-asc`, `price-desc`, `newest`; thêm id làm thứ tự phụ |
| `page`, `size` | Mặc định 0/20; page 0–10000, size 1–100 |

Text query tối đa 100 ký tự. Chỉ trả sản phẩm active. Đây là tìm kiếm
PostgreSQL/JPA theo chuỗi con; chưa có Elasticsearch, relevance/fuzzy search
hay bằng chứng capacity lớn. Sắp xếp chỉ nhận danh sách trường cho phép.

## API biến thể

`GET /api/products/{parentId}/variants` public trả danh sách biến thể active.
`POST` cùng URL cần ADMIN và nhận:

```json
{
  "product": {
    "sku": "SHIRT-BLUE-M",
    "name": "Blue medium shirt",
    "unitPrice": 125000.00,
    "category": "Clothing",
    "brand": "Example"
  },
  "attributes": {"color": "Blue", "size": "M"}
}
```

Product response bổ sung `parentId` và `attributes`; sản phẩm gốc có
parentId null và attributes rỗng. Mỗi biến thể là Product riêng với SKU,
giá và tồn kho riêng trong Order service. Sản phẩm gốc vẫn có thể bán bằng
SKU riêng; chưa có lựa chọn “chỉ dùng làm nhóm, không bán”.

Attributes có 1–10 cặp; key chữ thường theo `[a-z][a-z0-9_-]{0,31}`, value
1–64 ký tự sau strip/NFKC/chuyển chữ thường. Thứ tự key và khác biệt hoa/thường
không tạo biến thể mới. Hash SHA-256 lưu bộ attributes chuẩn hóa; prefix
độ dài tránh nhập nhằng dấu phân cách. Unique constraint và khóa parent
ngăn hai request đồng thời tạo cùng tổ hợp. Tối đa 50 biến thể tính cả
inactive; không tạo biến thể lồng nhau. Parent không tồn tại/inactive trả 404,
tổ hợp trùng, lồng nhau hoặc vượt giới hạn trả 409.

SKU/attributes của biến thể không thay đổi sau tạo. PUT hiện có vẫn cập nhật
tên/giá/metadata, đổi SKU biến thể trả 409; muốn đổi attributes phải tạo SKU
mới. DELETE một biến thể chỉ ẩn nó; DELETE parent ẩn toàn bộ children trong
cùng transaction. Dữ liệu, tồn kho và lịch sử đơn không bị xóa. Order giữ
snapshot SKU/name/price đã chốt; request mua dùng productId của biến thể.

## Schema và kiểm chứng

Migration `docs/infrastructure/postgres/migrations/product-service/001-product-variants.sql`
thêm parent/key, self foreign key, unique/check và index vào bảng hiện có.
Chạy bằng `bash docs/infrastructure/scripts/apply-schema.sh product-service` trước
image mới. Runner ghi checksum/version trong DB của Product, khóa transaction
và không áp dụng lại migration đã ghi. Bảng `product_attributes` được Hibernate
tạo khi khởi động. Dữ liệu root cũ giữ parent/key null.

Local PostgreSQL: áp dụng và chạy lại migration đạt, số sản phẩm cũ giữ
65/65 trước khi tạo fixture. Java unit có tìm kiếm, escape SQL LIKE, validation,
authorization, 20 request tạo cùng tổ hợp, parent retirement và giới hạn
chuỗi sau Unicode normalization. Live Java qua Gateway kiểm tra cùng tổ hợp
trên PostgreSQL, tìm ký tự `%` và vòng đời mua biến thể qua Order/Payment.
Kết quả chạy và deploy thực tế ghi trong nhật ký/PR; code kiểm thử chưa tự
chứng minh đã chạy trên VPS.
