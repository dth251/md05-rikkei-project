# Dữ liệu mẫu trên production để kiểm thử

Đã tạo qua HTTPS trên `https://md05-rikkei-project.duckdns.org`, ngày
14/10/2026. Dataset `demo-c5c9d77e-8a2e-4b6b-9a68-6dc486ea6461` dùng dữ
liệu giả riêng cho demo. Runtime sáu service vẫn ở main mốc 168,
`d2779d1595fb5541675e65604eb07ac633ccf91b`; không rollout code mới khi seed.

## 1. Import vào Postman và test ngay

1. Import [collection production fixtures](../tests/postman/md05-production-fixtures.postman_collection.json).
2. Import file private
   `C:\Users\Admin\.md05-demo\production-20261014-182534\production-demo.postman_environment.json`,
   chọn environment **MD05 production demo demo-c5c9d77e-…**.
   File có email, password và token của ADMIN/A/B; password/token dùng kiểu
   secret. File chỉ cho Windows user hiện tại đọc, nằm ngoài Git.
3. Run folder **01 Login accounts** để lấy token mới; làm lại khi token hết
   hạn. Dùng credentials đã có trong environment, không cần đăng ký lại.
4. Run folders **02–04** để đọc và đối chiếu dữ liệu ban đầu. Có assert HTTP,
   inventory, Order/Payment/refund/return status và ownership 401/404.
5. Run **từng folder** 05, 06 hoặc 07 trong Runner: Iterations=1,
   Delay=1000 ms; bật dừng khi lỗi script/network. Mỗi lượt tạo key/đơn mới,
   poll có giới hạn, không coi 404/PENDING kéo dài là PASS.
6. Folder **08 Optional prepared-state actions**: chọn từng request Send
   thủ công. Nó thay đổi đơn mẫu. Ship và Cancel COD cùng nhắm đơn 134,
   chỉ chọn một nhánh. **Không Run All collection**.

Collection có **99 requests / 8 folders**. Các folders 05–07 dùng Product
139 riêng: ban đầu 10/0. SUCCESS quantity=2 → 8/0; FAILED quantity=1 → trả
lại baseline; expiry quantity=1 → trả lại baseline. Dữ liệu lịch sử dùng
Product 140 nên không làm lệch baseline của ba workflow mới.

Sau khi thao tác thay đổi dữ liệu, các assert trạng thái/kho ban đầu của
folders 02–04 có thể fail đúng vì snapshot đã đổi. Đọc trạng thái hiện tại
và đối chiếu thao tác vừa chạy; không sửa DB/reset kho để khôi phục ảnh chụp.

## 2. Tài khoản demo

| Biến / quyền | User ID | Email |
|---|---|---|
| adminEmail / ADMIN | 170 | demo-c5c9d77e-8a2e-4b6b-9a68-6dc486ea6461-admin@example.com |
| email / USER A | 171 | demo-c5c9d77e-8a2e-4b6b-9a68-6dc486ea6461-usera@example.com |
| emailB / USER B | 172 | demo-c5c9d77e-8a2e-4b6b-9a68-6dc486ea6461-userb@example.com |

Password ở environment private: adminPassword/password/passwordB tương
ứng. ADMIN là tài khoản đăng ký mới, cấp role bằng UPDATE giới hạn đúng
id/email/fullName của fixture, rồi login lại. USER A/B là tài khoản thật
đăng ký public. USER B đọc Order/Payment của A trả 404 và ngược lại;
USER tạo Product trả 403, thiếu token đọc Order trả 401.

## 3. Địa chỉ đã lưu

| Key | Address ID | Chủ sở hữu | Street | Default |
|---|---|---|---|---|
| homeA | 2 | USER A | 123 Demo Street | true |
| workA | 3 | USER A | 456 Demo Office | false |
| homeB | 4 | USER B | 789 Demo Street | true |

Cùng recipientName=`MD05 Synthetic Demo`, phone=`0901234567`,
ward=`Demo Ward`, district=`Demo District`, province=`Demo Province`,
postalCode=`70000`. Đây là dữ liệu giả. GET `/api/auth/addresses` theo token
chỉ trả địa chỉ của tài khoản tương ứng. Shipping snapshot trong các đơn
mẫu dùng địa chỉ giả 123 Demo Street.

## 4. Sản phẩm, biến thể và tồn kho đã kiểm chứng

Đơn vị giá là giá trị tiền theo API. Brand chung `MD05Demo`; SKU và toàn bộ
response mẫu nằm trong [manifest dữ liệu](evidence/production-demo-data-2026-10-14.json).

| Key / biến ID | Product ID | Sản phẩm | Giá | Available | Reserved | Mục đích |
|---|---|---|---:|---:|---:|---|
| demoProductId; productId | 139 | Demo Shirt for interactive tests | 125000 | 10 | 0 | Tạo đơn ONLINE/COD mới |
| historyProductId | 140 | Demo history fixture | 125000 | 89 | 1 | Kho các đơn lịch sử; 1 đang giữ bởi COD 134 |
| parentProductId | 141 | Demo Shirt variants | 125000 | 20 | 0 | Parent, stock tách khỏi variant |
| hoodieProductId | 142 | Demo Hoodie | 350000 | 50 | 0 | Search Clothing/price |
| mugProductId | 143 | Demo Mug | 79000 | 30 | 0 | Search Home/price |
| bagProductId | 144 | Demo Backpack | 499000 | 20 | 0 | Search Accessories/price |
| zeroStockProductId | 145 | Demo Sold Out | 99000 | 0 | 0 | Tạo đơn mới quantity=1 phải trả 409 |
| deletedProductId | 146 | Demo inactive product | 59000 | 5 | 0 | Soft-delete; GET Product / tạo đơn mới trả 404 |
| blueMId | 147 | Demo Shirt Blue M | 130000 | 20 | 0 | Variant parent=141, color=Blue/size=M |
| redLId | 148 | Demo Shirt Red L | 130000 | 15 | 0 | Variant parent=141, color=Red/size=L |
| blackSId | 149 | Demo Shirt Black S | 130000 | 10 | 0 | Variant parent=141, color=Black/size=S |

GET `/api/products?brand=MD05Demo&page=0&size=100` tìm các sản phẩm demo;
có thể có thêm lượt seed khác về sau, đối chiếu đúng ID/SKU/runId.
GET `/api/products/141/variants` có ba biến thể. Muốn mua Blue M, gửi
productId=147; kho parent 141 không bị trừ. Inventory của inactive 146
vẫn lưu để audit; soft-delete không xóa kho.

## 5. 17 đơn và payment mẫu

Mọi đơn quantity=1. Tổng tiền 125000, riêng Order 128 là 99000. Trạng thái
Order giữ lịch sử giao hàng; tiến trình trả hàng đọc qua `/return`.
Các biến `KEYOrderId` và `KEYPaymentId` đã có trong environment.

| Key | Order ID | Payment ID | Chủ | Method/channel | Order status | Payment status | Return/refund |
|---|---:|---:|---|---|---|---|---|
| zeroStockConsumed | 128 | 65 | A | ONLINE/WEB | CONFIRMED | SUCCESS | NONE; tiêu thụ hết Product 145 |
| onlineConfirmed | 129 | 66 | A | ONLINE/WEB | CONFIRMED | SUCCESS | NONE; có thể ship |
| onlineShipping | 130 | 67 | A | ONLINE/WEB | SHIPPING | SUCCESS | NONE; có thể complete |
| onlineCompleted | 131 | 68 | A | ONLINE/WEB | COMPLETED | SUCCESS | NONE; có thể request return |
| posCompleted | 132 | 69 | A | ONLINE/POS | COMPLETED | SUCCESS | NONE; kênh POS cùng kho |
| onlineCancelled | 133 | 70 | A | ONLINE/WEB | CANCELLED | FAILED | NONE; SUCCESS sau FAILED bị từ chối |
| codConfirmed | 134 | 71 | A | COD/WEB | CONFIRMED | PENDING | NONE; giữ 1 kho, chưa giao |
| codShipping | 135 | 72 | A | COD/WEB | SHIPPING | PENDING | NONE; chưa thu tiền |
| codShippingPaid | 136 | 73 | A | COD/WEB | SHIPPING | SUCCESS | NONE; đã nhận codPaidAt, có thể complete |
| codCompleted | 137 | 74 | A | COD/WEB | COMPLETED | SUCCESS | NONE; có thể request return |
| codCancelled | 138 | 75 | A | COD/WEB | CANCELLED | FAILED | NONE; đã trả reserved |
| returnRequested | 139 | 76 | A | COD/WEB | COMPLETED | SUCCESS | REQUESTED / NONE |
| returnApproved | 140 | 77 | A | COD/WEB | COMPLETED | SUCCESS | APPROVED / NONE |
| returnRefundPending | 141 | 78 | A | COD/WEB | COMPLETED | SUCCESS | REFUND_PENDING / REQUIRED; đã restock |
| returnRefunded | 142 | 79 | A | COD/WEB | COMPLETED | SUCCESS | REFUNDED / REFUNDED; đã restock |
| foreignOwnedOrder | 143 | 80 | B | ONLINE/WEB | CONFIRMED | SUCCESS | NONE; A đọc trả 404 |
| onlineExpired | 144 | 81 | A | ONLINE/WEB | EXPIRED | FAILED | reservationExpired=true; SUCCESS muộn trả 409 |

Order ID và Product ID có thể cùng số vì thuộc hai bảng/service khác nhau;
không dùng Order 139 thay cho Product 139. GET `/api/orders/{orderId}` và
`/api/payments/{paymentId}` theo đúng chủ/ADMIN.

Đơn ONLINE EXPIRED đã chờ deadline thật 150 giây. Muốn xem PENDING_PAYMENT,
chạy folder 05–07 để tạo **đơn mới lúc test**; không thể giữ sẵn trạng thái
này lâu dài vì scheduler sẽ hủy. COD 134 không thuộc timeout ONLINE.
Các khoản SUCCESS/thu/hoàn tiền của dataset là mô phỏng và biên nhận demo;
không có giao dịch ngân hàng thật hoặc thanh toán VNPay thật trong lượt seed.

## 6. Email và monitoring

32 thư thật được Mailpit sandbox nhận cho hai email USER A/B:

| Stage | Thư SENT |
|---|---:|
| order.confirmed | 12 |
| order.cancelled | 2 |
| order.shipped | 10 |
| order.completed | 7 |
| order.expired | 1 |

Đối chiếu orderId trong Subject, recipient và stage. Order 144 có thư
“đã hủy do quá hạn thanh toán”; Order 133/138 có thư hủy. Email không gửi
ra Internet. Dùng SSH tunnel theo [kịch bản demo](demo-test-guide.md) để mở
Mailpit/Grafana/Prometheus. Grafana có dữ liệu mới do phiên seed; chọn cửa
sổ khoảng **18:28–18:35 +07 ngày 14/10/2026**. Verifier xác nhận sáu service,
năm dependency UP, history, datasource và 22 panel/query/unit khớp nguồn.

## 7. Fixture riêng cho tải catalog 500/1.000 RPS

File private `capacity-50.private.json` cùng thư mục chứa 50 USER Auth thật
và token; không cần ADMIN để chạy GET catalog. Password của các tài khoản
này ở fixture.private.json để tool login lại. Token có hạn, luôn refresh
fixture sát lúc chạy tải:

Đã chuẩn bị và login kiểm chứng lúc **18:38:18 +07**: USER IDs **173–222**,
email theo mẫu `demo-c5c9d77e-8a2e-4b6b-9a68-6dc486ea6461-load0@example.com`
đến `-load49@example.com`. Tổng bộ dữ liệu có **53 tài khoản**: ba tài khoản
demo nghiệp vụ và 50 tài khoản tải. Catalog smoke qua HTTPS trả 200.

```powershell
powershell -ExecutionPolicy Bypass -File docs/infrastructure/scripts/prepare-production-demo.ps1 `
  -PrivateDirectory 'C:/Users/Admin/.md05-demo/production-20261014-182534' `
  -SshKey '<private-key-path>' -Action capacity

powershell -ExecutionPolicy Bypass -File docs/infrastructure/scripts/run-capacity-demo.ps1 `
  -K6Path '<private-tools>/k6.exe' `
  -FixtureFile 'C:/Users/Admin/.md05-demo/production-20261014-182534/capacity-50.private.json' `
  -SshKey '<private-key-path>' -DurationSeconds 10
```

Chưa chạy tải lớn trong lượt chuẩn bị dữ liệu. Giữ target/actual throughput,
successful requests, p95/p99, errors/429/dropped và CPU/RAM/swap; target bị
resource guard chặn là chưa đo. Không thay quota để ép đạt.

## 8. Payload thử nhanh bằng dữ liệu đã có

USER A tạo ONLINE mới trên Product 139 (đổi key UUID mỗi đơn mới):

```json
{
  "channel": "WEB",
  "paymentMethod": "ONLINE",
  "idempotencyKey": "{{runId}}-manual-{{$randomUUID}}",
  "items": [{"productId": 139, "quantity": 1}],
  "shippingAddress": {
    "recipientName": "MD05 Synthetic Demo",
    "phone": "0901234567",
    "street": "123 Demo Street",
    "ward": "Demo Ward",
    "district": "Demo District",
    "province": "Demo Province",
    "postalCode": "70000"
  }
}
```

POST `/api/orders`: 201/PENDING_PAYMENT; lưu orderId mới. Poll Payment
by-order rồi ADMIN simulate SUCCESS/FAILED; hoặc không simulate và chờ
150 giây. Replay phải giữ nguyên **key đã resolve**, không dùng lại
`{{$randomUUID}}` để sinh key khác khi muốn kiểm tra replay.
Đổi productId=145: 409; đổi 146: 404; dùng variant 147 giá server=130000.
Đổi paymentMethod=COD để thử nhánh mới, vẫn phải có shippingAddress.

## 9. Kiểm chứng và bảo quản

Seed/verify dùng Java CLI test tool của Order, chỉ chạy khi operator gọi
explicit task. API/ownership/stock/payment/return được đối chiếu thật trên
HTTPS; Order regression 61/61 tests và bootJar đạt. Collection JSON/scripts
được kiểm tra trước bàn giao; chưa coi cả 99 requests là đã chạy trong
Postman Runner nếu chưa có kết quả Runner.

Các thư mục/file private không đưa vào Git. `manifest.json` và
`capacity-manifest.json` private có bảng ID; báo cáo đã bỏ password/token
nằm trong docs/evidence. Sau khi đổi trạng thái mẫu, action verify sẽ báo
lệch snapshot ban đầu; muốn dataset mới, dùng PrivateDirectory mới với
action prepare, không reset dữ liệu cũ. Không xóa volume/dữ liệu để seed.
VNPay cần merchant sandbox riêng; không cung cấp merchant secret trong
bộ này. [Kịch bản demo đầy đủ](demo-test-guide.md) mô tả các bước còn lại.
