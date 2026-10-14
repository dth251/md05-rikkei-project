# Hướng dẫn kiểm thử API bằng Postman và đối chiếu yêu cầu dự án

Theo yêu cầu hiện tại, dùng **[hướng dẫn test production](test-production.md)**
và [kết quả live production](postman-production-results.md). Thanh toán dùng
API simulate, chưa có link giả lập; không cần merchant VNPay. Hướng dẫn
bên dưới giữ các chi tiết đối chiếu contract và môi trường lịch sử.

Bộ mới có tên request/assertion bằng tiếng Việt, tự tạo fixture và chạy theo
luồng: [hướng dẫn import và chạy](../tests/postman/README.md),
[kịch bản demo có lời dẫn](demo-luong-hoat-dong.md),
[kết quả kiểm chứng](postman-tieng-viet-results.md).

Cập nhật **14/10/2026 (+07:00)**. Hướng dẫn này mô tả request và kết quả cần
kiểm tra; không tự coi các bước chưa chạy là kết quả PASS.

VPS đã rollout main mốc 168 ngày 14/10/2026: deadline ONLINE 150 giây,
Prometheus/Grafana đã được kiểm chứng. Gmail SMTP thay Mailpit trên VPS từ
23:02 cùng ngày. VNPay provider đang tắt, test hiện tại dùng simulate.
Trạng thái CI/CD
và từng nhóm nằm trong [nhật ký triển khai](commits-and-deployments.md).
Để trình bày và tự test theo thứ tự, dùng [kịch bản demo](demo-test-guide.md).

## 1. Chuẩn bị môi trường

| Môi trường | `baseUrl` | Cách sử dụng |
|---|---|---|
| Local qua Gateway | `http://127.0.0.1:8080` | Kiểm thử chức năng, dữ liệu fixture và lỗi dependency |
| VPS qua HTTPS | `https://md05-rikkei-project.duckdns.org` | Kiểm chứng API đã deploy với tài khoản/sản phẩm thử riêng |

Cần một USER, một USER khác để kiểm tra ownership và một ADMIN thử nghiệm.
Đăng ký public luôn tạo USER. ADMIN phải được người vận hành cấp role trong
Auth DB cho đúng tài khoản thử, rồi **login lại** để nhận JWT mới; không sửa
claim JWT hoặc truyền `role=ADMIN` trong request đăng ký. Không có API public
nâng quyền. Nếu chưa có ADMIN, vẫn chạy được nhóm Auth/public/ownership;
các bước tạo Product, nhập kho, simulate và giao hàng chưa đủ điều kiện chạy.

Trong Postman, request POST/PUT có **Body → raw → JSON** và
`Content-Type: application/json`. API protected dùng **Authorization → Bearer
Token**, ô Token đặt `{{userToken}}` hoặc `{{adminToken}}`; không thêm chữ
`Bearer` lần nữa vào ô Token. Với test anonymous, chọn No Auth và bỏ cả header
Authorization thủ công nếu có. Các API dùng cùng prefix `/api/...` qua Gateway.

Response nghiệp vụ thường có `success`, `message`, `data`, `errors`,
`timestamp`. Token/ID nằm trong `data`, không ở root. Logout/xóa địa chỉ trả
204 không có JSON; health và IPN VNPay có định dạng riêng. Kiểm tra thêm
`X-Gateway: api-gateway` trên response qua Gateway.

JWT mới có TTL mặc định 15 phút. Khi hết hạn, refresh hoặc login lại; giữ ID
fixture đang kiểm tra. Dùng credential/token private trong Postman, không
ghi chúng vào Git, Console, ảnh, environment export hoặc báo cáo chia sẻ.

Thao tác cơ bản của công cụ tham khảo tài liệu chính thức:
[Bearer auth](https://learning.postman.com/v11/docs/use/send-requests/authorization/authorization-types),
[post-response scripts](https://learning.postman.com/docs/tests-and-scripts/write-scripts/test-examples/)
và [environment variables](https://learning.postman.com/docs/use/send-requests/variables/environment-variables).

## 2. Chạy nhanh ba folder đầu: 19 requests

Import [md05.postman_collection.json](../tests/postman/md05.postman_collection.json).
Collection có 38 requests trong 7 folders. Ba folder đầu kiểm tra
Auth/security, Product/Inventory và Saga **FAILED → CANCELLED → hoàn kho**.
Các folder VNPay, COD, cancellation và return cần tiền đề riêng; chọn đúng
folder để chạy, không Run toàn bộ collection trong lượt kiểm tra nhanh.

1. Chọn environment; đặt `baseUrl`, `adminEmail`, `adminPassword` private.
2. Collection tự sinh `email`, `password`, `sku`, `orderKey`, rồi lưu
   `userToken`, `adminToken`, `productId`, `orderId`, `paymentId` vào collection
   variables. **Không tạo biến environment trùng các tên này cho lần chạy
   nhanh**, vì chúng có thể che giá trị collection script vừa sinh.
3. Run cả ba folder theo thứ tự: **Auth and security → Product and inventory
   → Order and failed payment compensation**.
4. Collection Runner: **Iterations = 1**, **Delay = 1000 ms**, bật dừng khi
   gặp script/network error. Nếu request tiền đề login/create/stock thất bại,
   dừng và sửa nguyên nhân trước khi tiếp tục, kể cả Runner chưa tự dừng.
5. Script lặp request chờ Payment/Order chỉ hoạt động trong Runner; bấm Send
   thủ công phải tự gửi lại. Collection có giới hạn retry và assertion cuối,
   không chấp nhận 404/PENDING kéo dài là PASS.
6. Kết thúc cần thấy Order CANCELLED, stock `available=1`, `reserved=0`;
   gọi lại simulate FAILED vẫn không cộng kho thêm lần nữa.

Hai request tạo/replay Order đã được sửa ở commit
`0baaa6984d048b620fb42bfdb23257f45249eac9` — mốc 131,
**10:32:11 +07 ngày 12/10/2026**: item chỉ có `productId`, `quantity`.
`idempotencyKey` của đơn ở root; key của phiếu nhập kho ở body `/stocks`.
JSON và cú pháp 21 scripts đã kiểm tra, không đồng nghĩa đã chạy lại 19 API.

Runner và điều khiển thứ tự:
[Collection Runner](https://learning.postman.com/docs/tests-and-scripts/running-collections/intro-to-collection-runs),
[pm.execution](https://learning.postman.com/latest-v-12/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-execution).

## 3. Tạo bộ kiểm thử mở rộng

Import [OpenAPI Gateway](openapi/api-gateway.json) thành một collection mới,
hoặc tạo request thủ công theo phần dưới. Kiểm tra URL sau import và đặt
`{{baseUrl}}` đúng môi trường; thay body mẫu bằng dữ liệu fixture trong hướng
dẫn. OpenAPI hiện tại có các endpoint VNPay đã tích hợp; vẫn cần cấu hình
merchant private để chạy sandbox.
[Cách import specification](https://learning.postman.com/docs/design-apis/specifications/import-a-specification).

Dùng environment riêng cho bộ mở rộng; scripts dưới đây dùng
`pm.environment`, không trộn với bộ 19 requests dùng collection variables.

| Biến | Giá trị/cách lấy |
|---|---|
| `baseUrl` | Theo bảng môi trường |
| `email`, `password` | Email mới và mật khẩu private cho USER A |
| `emailB`, `passwordB` | Tài khoản USER B khác A |
| `adminEmail`, `adminPassword` | ADMIN đã được cấp quyền |
| `userToken`, `userBToken`, `adminToken` | `data.accessToken` sau login tương ứng |
| `refreshToken`, `previousRefreshToken` | Credential refresh của USER A; chỉ giữ private |
| `userId`, `addressId` | ID trả bởi Auth |
| `runId`, `productSku`, `variantSku` | Sinh một lần cho mỗi đợt thử |
| `productId`, `variantId` | ID trả khi tạo Product/variant |
| `receiptKey`, `orderKey` | Key mới cho phiếu/đơn mới; giữ nguyên khi test replay |
| `orderId`, `paymentId` | ID do API trả, không đoán hoặc dùng lẫn nhau |
| `expectedOrderStatus` | CONFIRMED hoặc CANCELLED khi chạy request polling |

Chạy đoạn sau **một lần trước lượt thử mới**, ví dụ Pre-request của Register
USER A. Không gắn ở mức collection vì sẽ làm đổi key/SKU ở mỗi request:

```javascript
const runId = pm.variables.replaceIn('{{$randomUUID}}');
pm.environment.set('runId', runId);
pm.environment.set('email', `postman-a-${runId}@example.com`);
pm.environment.set('emailB', `postman-b-${runId}@example.com`);
pm.environment.set('productSku', `PM-${runId}`);
pm.environment.set('variantSku', `PM-V-${runId}`);
pm.environment.set('receiptKey', `${runId}-receipt`);
pm.environment.set('orderKey', `${runId}-success`);
pm.environment.unset('orderId');
pm.environment.unset('paymentId');
```

Mật khẩu đặt private, tối thiểu 8 ký tự và không quá 72 UTF-8 bytes.
Request không cần body thì chọn Body → none.

## 4. Auth, phân quyền, refresh và logout

### 4.1. Đăng ký và đăng nhập

**POST `{{baseUrl}}/api/auth/register` — No Auth — 201**

```json
{
  "fullName": "Postman User A",
  "email": "{{email}}",
  "password": "{{password}}"
}
```

Gửi lại đúng email: 409. Email sai hoặc password ngắn: 400. Gửi thêm
`role`, `userId` hay field không thuộc DTO: 400. Khi test email trùng, tắt
Pre-request sinh email để giữ nguyên body.

**POST `{{baseUrl}}/api/auth/login` — No Auth — 200**

```json
{
  "email": "{{email}}",
  "password": "{{password}}"
}
```

Scripts → Post-response cho USER A:

```javascript
pm.test('Login HTTP 200', () => pm.response.to.have.status(200));
if (pm.response.code === 200) {
  const auth = pm.response.json().data;
  pm.environment.set('userToken', auth.accessToken);
  pm.environment.set('refreshToken', auth.refreshToken);
  pm.environment.set('userId', auth.user.id);
  pm.test('Role USER', () => pm.expect(auth.user.role).to.eql('USER'));
  pm.test('Credentials returned', () => {
    pm.expect(typeof auth.accessToken === 'string' && auth.accessToken.length > 0).to.eql(true);
    pm.expect(typeof auth.refreshToken === 'string' && auth.refreshToken.length > 0).to.eql(true);
  });
}
```

USER B đăng ký/login với `emailB`, `passwordB`, lưu token vào `userBToken`.
ADMIN login cùng endpoint với `adminEmail`, `adminPassword`, lưu token vào
`adminToken` và kiểm tra `data.user.role=ADMIN`.

| Kiểm tra | Request/cách thực hiện | Kỳ vọng |
|---|---|---|
| Sai password | POST login với password sai, body vẫn hợp lệ | 401 |
| Thiếu JWT | GET `/api/auth/me`, No Auth | 401 |
| JWT không hợp lệ | GET me, Bearer `invalid` | 401 |
| JWT hết hạn | GET me với token đã hết hạn | 401 |
| JWT hợp lệ | GET me, USER A | 200, đúng `userId` |
| USER vào API ADMIN | GET `/api/auth/admin/users/{{userId}}`, USER A | 403 |
| ADMIN đọc user | Cùng URL, ADMIN | 200 |

### 4.2. Refresh rotation và logout

1. Lưu `refreshToken` hiện tại vào `previousRefreshToken` trong environment.
2. **POST `/api/auth/refresh`**, No Auth, body dưới đây: 200; lưu cặp token
   mới bằng script tương tự login vào `userToken`, `refreshToken`.

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

3. Dùng **refresh cũ** `previousRefreshToken` gọi refresh: 401. Đó là replay;
   cả family bị thu hồi. Refresh token mới của family đó cũng phải 401.
4. Login lại tạo family mới. **POST `/api/auth/logout`**, No Auth, cùng body
   refresh hiện tại: 204, không parse JSON. Gọi logout lại: 204.
5. Refresh credential đã logout: 401. Login lại trước các luồng mua hàng.

Logout không thu hồi tức thì access JWT stateless đã cấp; access JWT cũ có
thể còn dùng được tới `exp`. Không ghi FAIL chỉ vì GET me vẫn 200 trong
khoảng đó. Refresh cùng phiên cần tuần tự; test cạnh tranh dùng Java tests.
Chi tiết: [Auth sessions](auth-sessions-and-addresses.md).

## 5. Địa chỉ giao hàng và ownership

**POST `{{baseUrl}}/api/auth/addresses` — USER A — 201**

```json
{
  "recipientName": "Postman User A",
  "phone": "0901234567",
  "street": "123 Test Street",
  "ward": "Test Ward",
  "district": "Test District",
  "province": "Test Province",
  "postalCode": "70000",
  "defaultAddress": true
}
```

Lưu `data.id` vào `addressId`. GET danh sách `/api/auth/addresses`: 200,
`data` là array. GET `/api/auth/addresses/{{addressId}}`: 200. PUT cùng URL
với body đầy đủ và street khác: 200.

| Trường hợp | Kỳ vọng |
|---|---|
| USER B GET/PUT/DELETE địa chỉ của A | 404; kể cả biết đúng ID |
| Không JWT | 401 |
| Thiếu recipient/street hoặc phone sai | 400 |
| Tạo địa chỉ đầu tiên với defaultAddress=false | Vẫn có một địa chỉ mặc định |
| Tạo/thay một default mới | Danh sách có đúng một default |
| Tạo địa chỉ thứ 11 của cùng user | 409 |
| DELETE địa chỉ của mình | 204; GET ID đã xóa 404 |
| Xóa địa chỉ mặc định còn địa chỉ khác | Một địa chỉ còn lại trở thành default |

Chỉ xóa fixture của mình; hoàn tất test default trước khi lấy snapshot địa
chỉ cho Order. Order nhận các field địa chỉ, không nhận `addressId`, `userId`
hoặc `defaultAddress` trong `shippingAddress`.

## 6. Product, tìm kiếm và biến thể

### 6.1. CRUD Product

**POST `{{baseUrl}}/api/products` — ADMIN — 201**

```json
{
  "sku": "{{productSku}}",
  "name": "Postman Shirt",
  "description": "Isolated API test product",
  "unitPrice": 125000.00,
  "category": "Clothing",
  "brand": "PostmanFixture"
}
```

Post-response:

```javascript
pm.test('Product created', () => pm.response.to.have.status(201));
if (pm.response.code === 201) {
  pm.environment.set('productId', pm.response.json().data.id);
}
```

| API/case | Quyền | Kỳ vọng |
|---|---|---|
| GET `/api/products?page=0&size=20` | No Auth | 200, `data.content` array |
| GET `/api/products/{{productId}}` | No Auth | 200, đúng SKU/giá |
| PUT `/api/products/{{productId}}`, body Product đầy đủ | ADMIN | 200 |
| POST lại SKU đã dùng, không đổi SKU trước request | ADMIN | 409 |
| POST/PUT/DELETE thiếu JWT | No Auth | 401 |
| POST/PUT/DELETE dùng USER | USER | 403 |
| Giá 0, quá hai số thập phân, SKU sai định dạng | ADMIN | 400 |
| DELETE `/api/products/{{productId}}` | ADMIN | 200 soft-delete; GET public ID đó 404 |

**Để DELETE cuối luồng**, sau khi đã thử đặt hàng/biến thể. Soft-delete không
xóa tồn kho hoặc snapshot đơn cũ. Không dùng lại SKU đã soft-delete.

### 6.2. Search và pagination

GET public qua Params, ví dụ:

```text
{{baseUrl}}/api/products?q=shirt&category=Clothing&brand=PostmanFixture&minPrice=100000&maxPrice=200000&sort=price-asc&page=0&size=20
```

Kiểm tra các filter được kết hợp, hai đầu min/max bao gồm, SKU/name/
description tìm không phân biệt hoa thường, sorting có thứ tự ổn định.
`sort`: id/name/price-asc/price-desc/newest. Page 0–10000, size 1–100.
Page âm, size=101, min>max, sort không hợp lệ: 400.

Test `q=%` hoặc `_` qua tab Params, để Postman encode query; đó là ký tự
literal, không phải wildcard trả mọi sản phẩm. Cần sản phẩm fixture có ký tự
đó trong name/description để kiểm tra kết quả rõ ràng. Đây là SQL search,
không phải test Elasticsearch/fuzzy search.

### 6.3. Variants

**POST `{{baseUrl}}/api/products/{{productId}}/variants` — ADMIN — 201**

```json
{
  "product": {
    "sku": "{{variantSku}}",
    "name": "Postman Shirt Blue M",
    "unitPrice": 130000.00,
    "category": "Clothing",
    "brand": "PostmanFixture"
  },
  "attributes": {
    "color": "Blue",
    "size": "M"
  }
}
```

Lưu `data.id` vào `variantId`; GET cùng URL: 200, có variant active. Tạo SKU
mới nhưng cùng bộ attributes khác hoa/thường/thứ tự: 409. Tạo variant dưới
một variant: 409. USER tạo: 403; parent không tồn tại/inactive: 404.
Đổi SKU variant bằng PUT: 409; đổi attributes không thuộc Product PUT DTO.

Nhập kho và đặt hàng bằng **variantId**, không phải parent ID. Kiểm tra
inventory parent không bị trừ khi mua variant. Xóa parent sau các test sẽ
ẩn cả variants; GET Order đã tạo vẫn giữ snapshot SKU/name/price.
Chi tiết giới hạn: [Product search/variants](product-search-and-variants.md).

## 7. Inventory: nhập kho và idempotency

**POST `{{baseUrl}}/api/inventory/stocks` — ADMIN — 200**

```json
{
  "productId": {{productId}},
  "quantity": 10,
  "idempotencyKey": "{{receiptKey}}"
}
```

Với Product mới chưa có inventory, kết quả đầu `available=10`, `reserved=0`.
GET `/api/inventory/{{productId}}`, USER/ADMIN: 200. Trước khi nhập lần đầu,
inventory chưa có có thể trả 404.

- Gửi lại nguyên body/key: 200, snapshot phiếu đầu; stock **không tăng lên 20**.
- Giữ key nhưng đổi quantity hoặc productId: 409.
- Phiếu mới phải có key mới; quantity 1–1.000.000. Thiếu key/quantity sai: 400.
- USER nhập kho: 403; thiếu JWT: 401; Product chưa tồn tại/inactive: 404.
- Nhập variant dùng variantId và key phiếu khác. GET inventory đọc trạng thái
  hiện tại; response replay phiếu là snapshot cũ, không phải live stock.

## 8. Order SUCCESS và Payment bất đồng bộ

### 8.1. Tạo đơn WEB

**POST `{{baseUrl}}/api/orders` — USER A — 201**

```json
{
  "channel": "WEB",
  "idempotencyKey": "{{orderKey}}",
  "items": [
    {
      "productId": {{productId}},
      "quantity": 2
    }
  ],
  "shippingAddress": {
    "recipientName": "Postman User A",
    "phone": "0901234567",
    "street": "123 Test Street",
    "ward": "Test Ward",
    "district": "Test District",
    "province": "Test Province",
    "postalCode": "70000"
  }
}
```

Post-response:

```javascript
pm.test('New order HTTP 201', () => pm.response.to.have.status(201));
if (pm.response.code === 201) {
  const order = pm.response.json().data;
  pm.environment.set('orderId', order.id);
  pm.environment.unset('paymentId');
  pm.test('Pending and server price', () => {
    pm.expect(order.status).to.eql('PENDING_PAYMENT');
    pm.expect(order.totalAmount).to.eql(250000);
    pm.expect(order.items[0].unitPrice).to.eql(125000);
  });
}
```

Giá/stock kỳ vọng trên giả định chưa đổi giá hoặc dùng stock cho test khác.
Nếu đã PUT Product, dùng giá server hiện tại để tính expected, không sửa SLO
hoặc chấp nhận tổng sai. Stock lúc này `available=8`, `reserved=2`.

Gửi lại nguyên body/key: **200**, cùng `data.id`, stock không đổi. Thay
channel/items/quantity/address nhưng giữ key: **409**. Muốn đơn mới phải đổi
key. Item chỉ nhận `productId`, `quantity`; không gửi price, SKU, userId hoặc
receipt idempotencyKey trong item.

GET `/api/orders/{{orderId}}`, USER A/ADMIN: 200; USER B: 404; anonymous: 401.
Đổi Product name/price sau tạo rồi GET đơn: snapshot giữ giá/name đã chốt.
Replay cùng key vẫn dùng snapshot cũ. Tạo đơn mới mới lấy Product mới.

### 8.2. Chờ Payment được consumer tạo

GET **`{{baseUrl}}/api/payments/by-order/{{orderId}}` — USER A**.
Ban đầu có thể 404; poll đến 200 và lưu `data.id` thành `paymentId`.
Kiểm tra `data.orderId`, `amount=250000`, `status=PENDING`.
GET `/api/payments/{{paymentId}}` cùng ownership; USER B trả 404.

Post-response dưới đây dùng trong Runner, Delay 1000 ms, tối đa 30 lần gọi:

```javascript
const attempt = Number(pm.environment.get('paymentPollAttempts') || 0) + 1;
if (pm.response.code === 404 && attempt < 30) {
  pm.environment.set('paymentPollAttempts', attempt);
  pm.execution.setNextRequest(pm.info.requestId);
} else {
  pm.environment.unset('paymentPollAttempts');
  pm.test('Payment available HTTP 200', () => pm.response.to.have.status(200));
  if (pm.response.code === 200) {
    pm.environment.set('paymentId', pm.response.json().data.id);
  } else {
    pm.execution.setNextRequest(null);
  }
}
```

Không tự đổi 404/timeout kéo dài thành PASS. Kiểm tra ownership, consumers,
queue và Outbox khi polling hết giới hạn.

### 8.3. Simulate thành công và chờ CONFIRMED

**POST `{{baseUrl}}/api/payments/{{paymentId}}/simulate` — ADMIN — 200**

```json
{
  "status": "SUCCESS"
}
```

USER gọi simulate: 403. Thiếu JWT: 401. PENDING/enum lạ: 400.
Replay SUCCESS: 200, không trừ stock nữa. Đổi thành FAILED sau SUCCESS: 409.
Đây là mô phỏng, không có tiền chuyển qua cổng thanh toán thật.

Đặt `expectedOrderStatus=CONFIRMED`; GET `/api/orders/{{orderId}}` và dùng
Post-response sau trong Runner, Delay 1000 ms, tối đa 30 lần:

```javascript
pm.test('Order HTTP 200', () => pm.response.to.have.status(200));
if (pm.response.code === 200) {
  const status = pm.response.json().data.status;
  const target = pm.environment.get('expectedOrderStatus');
  const attempt = Number(pm.environment.get('orderPollAttempts') || 0) + 1;
  if (status === 'PENDING_PAYMENT' && attempt < 30) {
    pm.environment.set('orderPollAttempts', attempt);
    pm.execution.setNextRequest(pm.info.requestId);
  } else {
    pm.environment.unset('orderPollAttempts');
    pm.test('Expected terminal status', () => pm.expect(status).to.eql(target));
    if (status !== target) pm.execution.setNextRequest(null);
  }
} else {
  pm.execution.setNextRequest(null);
}
```

Sau CONFIRMED: stock `available=8`, `reserved=0`. Gọi GET Inventory để kiểm
tra riêng; response Payment SUCCESS không tự chứng minh Order đã CONFIRMED.

## 9. Shipping/Completed

Dùng đơn CONFIRMED ở phần 8; ADMIN thực hiện:

**POST `{{baseUrl}}/api/orders/{{orderId}}/ship` — 200**

```json
{
  "carrier": "PostmanTestCarrier",
  "trackingNumber": "TRACK-{{runId}}"
}
```

Order trên đã có shippingAddress nên có thể bỏ address trong ship. Nếu đơn
không có address, thêm `shippingAddress` với cấu trúc như phần 8; thiếu trả
400. Address đã chốt gửi khác trả 409.

Kiểm tra status SHIPPING và `shipment`. Replay cùng thông tin: 200, giữ
timestamp và không trừ kho. Đổi carrier/tracking của shipment đã lưu: 409.
USER gọi ship/complete: 403. Pending/cancelled/expired không được giao.

**POST `{{baseUrl}}/api/orders/{{orderId}}/complete` — Body none — 200**:
status COMPLETED. Replay: 200, completedAt không đổi, inventory không đổi.
Một đơn CONFIRMED mới gọi complete khi chưa ship: 409.
Chi tiết: [Order fulfillment](order-fulfillment.md).

## 10. FAILED, POS, thiếu kho và expiry

### 10.1. Thanh toán thất bại phải hoàn kho một lần

1. Dùng stock sau phần 8 là available 8/reserved 0; đổi `orderKey` sang
   `{{runId}}-failed`, tạo Order quantity 2. Lưu orderId mới, xóa paymentId cũ.
2. Stock 6/2; chờ Payment của **đơn mới** bằng GET by-order.
3. ADMIN simulate `{"status":"FAILED"}`: 200.
4. Đặt `expectedOrderStatus=CANCELLED`, poll GET Order đến CANCELLED.
5. Inventory trở về 8/0. Replay FAILED: 200, vẫn 8/0.
6. Đổi FAILED sang SUCCESS: 409; không biến đơn thành CONFIRMED.

### 10.2. POS và validation

Tạo đơn mới với `channel=POS`, key `{{runId}}-pos`, quantity 1; cùng kho
với WEB. Tạo và simulate SUCCESS hoặc FAILED, rồi đối chiếu stock theo kết
quả. Không để đơn fixture giữ kho nếu đã kết thúc bài thử.

| Case | Kỳ vọng và bằng chứng |
|---|---|
| Quantity lớn hơn available, key mới | 409; stock và Order không tăng/giữ thêm |
| Quantity 0 hoặc >10000 | 400 |
| Channel ngoài WEB/POS | 400 |
| Items rỗng, >50 hoặc productId trùng | 400 |
| Product ID không tồn tại/inactive | 404 |
| Gửi price/userId/field không được hỗ trợ | 400 |
| Key đã thành công nhưng thay payload | 409; stock giữ nguyên |
| USER B đọc Order/Payment của A | 404 |
| Thiếu JWT khi tạo đơn/đọc kho | 401 |

### 10.3. Hết hạn reservation

Tạo đơn ONLINE riêng, không simulate; chờ TTL mặc định **150 giây** tính từ
createdAt, cộng thời gian scan/queue. Login lại nếu JWT hết hạn. Kỳ vọng
Order EXPIRED, stock được trả đúng một lần; khi event expiry tới Payment,
`reservationExpired=true`, Payment pending đổi FAILED, simulate SUCCESS bị
từ chối. Lặp GET/retry không hoàn kho thêm.

Local/CI có thể dùng TTL ngắn hợp lệ (tối thiểu 1 phút) trong cấu hình stack
thử riêng. Không sửa TTL toàn VPS hay sửa timestamps của đơn thực để ép PASS.
Kiểm thử race payment/expiry và event đảo thứ tự dùng Java integration tests;
Postman gửi tuần tự không chứng minh cạnh tranh. Capture đến muộn và refund
provider theo phần VNPay, không dùng simulate vượt quy tắc expiry.
Chi tiết: [Event recovery](event-recovery.md).

## 11. Các yêu cầu cần kiểm thử ngoài Postman

| Yêu cầu dự án | Cách kiểm tra đề xuất | Kết quả/bằng chứng cần có |
|---|---|---|
| Microservice độc lập | Docker health, DB ownership, CI và runbook | Image/SHA và DB riêng; không business query chéo DB |
| gRPC Product–Order | Đặt đơn qua Gateway + Java RPC tests/probe private | Snapshot đúng, authentication/deadline/error; HTTP response không tự chứng minh transport |
| 100 request tranh stock=1 | Java live/k6 race, keys khác nhau | Một order thắng; SQL stock không âm; response phân loại đúng |
| Tải 500–1000 requests/s | k6 + resource sampler | Target và throughput thành công thực, latency/errors/dropped và stock cuối |
| Saga/Outbox/RabbitMQ | Java live + quan sát queue/DB qua operator | Commit/outbox, consumer xử lý, stock và dedup đúng sau recovery |
| Retry/DLQ/redrive | Java tests và tool inspect/redrive | Retry hữu hạn, payload sai vào DLQ, redrive sau fix không nhân tác động |
| Tính sẵn sàng khi service dừng | Kịch bản local/CI, sau đó demo VPS có kiểm soát | Các service độc lập còn phục vụ và xử lý backlog sau phục hồi |
| HTTPS/firewall | HTTPS URL + SSH audit listener/UFW | 22/80/443 public; app/DB/MQ/Redis private |
| Monitoring/tracing | Endpoint metrics, sau đó stack monitoring/trace | Dữ liệu thực trên dashboard và trace xuyên service |
| SMTP/VNPay thật | Môi trường provider thử và đối chiếu ngoài | Email nhận được/giao dịch sandbox, không chỉ response mock |

Đối chiếu [đề bài gốc](https://github.com/hunghx/Project-MD5---Devops/blob/main/De_Tai_1_Omnichannel_ECommerce.md)
và [kế hoạch hiện tại](remediation-plan.md). Postman kiểm tra API chức năng;
không dùng chạy tuần tự để kết luận chống race, sức tải hoặc HA.

### 11.1. Java unit/build và live tests

Từ root repository, PowerShell:

```powershell
$services = @('auth-service', 'api-gateway', 'product-service',
              'order-service', 'payment-service', 'notification-service')
foreach ($service in $services) {
    Push-Location $service
    try {
        .\gradlew.bat test bootJar --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Verification failed: $service" }
    } finally {
        Pop-Location
    }
}
```

`test` loại các test tag live. Muốn kiểm tra PostgreSQL/RabbitMQ/Redis thật,
chuẩn bị stack local, topology/schema và private environment theo
[runbook ngày 2](day2-operations.md) và [runbook ngày 3](day3-operations.md),
sau đó chạy `liveTest` tương ứng. Có test cần flag để bật; **task exit 0
nhưng test bị skip không đủ bằng chứng**. Xem reports và zero fail/error/skip
của các case được yêu cầu. Một số flags dừng/restart container: chỉ dùng
đúng stack thử của dự án, không chạy đồng thời với Runner/load/deploy.

### 11.2. k6 và failure demo

Harness hiện có: [catalog](../tests/k6/load-test.js),
[order load](../tests/k6/order-load.js), [race](../tests/k6/race-condition.js),
[quota](../tests/k6/gateway-rate-limit.js). Fixture/token files phải private,
Java tool chuẩn bị dữ liệu theo runbook. Harness Order/race hiện dùng fixture
giới hạn, chưa chứng minh nhiều tài khoản; còn mở rộng theo remaining-work.

Quota dùng burst đồng thời; gửi login tuần tự với delay 1000 ms có thể không
ra 429 vì token được refill. Race hiện 100 requests cùng user qua Gateway có
thể gặp quota, không được coi 429 là xung đột stock. Theo runbook, kiểm tra
race riêng qua Order private có JWT hoặc bổ sung fixture nhiều users; không
tắt quota production để đổi kết quả.

VPS hiện khoảng 1 GiB RAM và dùng nhiều swap; các bài tải trước chưa đạt mục
tiêu. Chạy từ tải nhỏ với guard, ghi lỗi/dropped và không nâng stage sau FAIL.
Demo stop/start Notification/Payment/broker phải có fixture riêng, ghi trước/
sau, khôi phục trong finally và không xóa queue/volume. Không chạy các thao tác
đó trong lượt Postman chức năng trên VPS đang phục vụ.

## 12. OpenAPI, metrics và Grafana/Prometheus

- Public `GET {{baseUrl}}/gateway-status`: 200 khi Gateway đang phục vụ.
- Public domain `/actuator/**`: kỳ vọng 404 do Nginx không expose. Không dùng
  đây làm bằng chứng service/metrics hỏng.
- Local hoặc SSH tunnel tới đúng service: `GET /actuator/health` và
  `/actuator/prometheus`. Health UP không tự chứng minh queue/Outbox đã xử lý.
- Docs private: direct Auth/Product/Order/Payment tại port 8081/8082/8083/8085,
  `GET /v3/api-docs`: anonymous 401, USER 403, ADMIN 200; kiểm tra đúng title,
  schema references và controller. Không gọi docs qua public domain/Gateway.
- RPC private không dùng bearer JWT HTTP: cần metadata/fixture riêng, theo
  [gRPC runbook](product-order-grpc.md); không đưa key vào collection public.

Prometheus/Grafana đã deploy và kiểm chứng ở mốc 168: sáu service/năm
dependency targets UP, dashboard 22 panels, query/unit khớp provisioning và
history còn sau recreate. Truy cập bằng SSH tunnel, xem
[runbook monitoring](expiry-email-monitoring.md) và [kịch bản demo](demo-test-guide.md).
HTTP 200 của exporter riêng lẻ chưa chứng minh dashboard; alert firing/resolved
và trace/log có checklist riêng trong tài liệu observability, không suy ra PASS
từ health. No data không đồng nghĩa 0.

## 13. VNPay: chỉ chạy khi đúng feature và cấu hình đã sẵn sàng

Các endpoint đã tích hợp trên main mốc 168. Cần xác minh runtime revision,
cấu hình VNPay private và merchant sandbox trước khi chạy; integration tắt
trả 503 theo hợp đồng bên dưới. Không dùng payment đã checkout VNPay cho
bài simulate SUCCESS/FAILED trong cùng lượt thử.

| Endpoint | Auth/body | Kỳ vọng khi integration đã bật |
|---|---|---|
| POST `/api/payments/{{paymentId}}/vnpay/checkout` | Chủ payment, body none | 200, reference/checkoutUrl/expiresAt; replay URL ổn định |
| GET `/api/payments/vnpay/return` | No Auth, query provider ký hợp lệ | 200, chỉ xác minh kết quả, không đổi payment |
| GET `/api/payments/vnpay/ipn` | No Auth, query provider ký hợp lệ | HTTP 200, root `RspCode`/`Message`, không phải ApiResponse |
| POST `/api/payments/{{paymentId}}/vnpay/refund` | ADMIN, body none | 200, trạng thái refund; chỉ verified capture cần hoàn tiền |
| POST `/api/payments/{{paymentId}}/vnpay/reconcile` | ADMIN, body none | 200, querydr đối soát request refund đã có |

Checkout phải dùng payment PENDING của chủ đơn, chưa hết hạn. Owner khác 404;
payment không payable 409. Integration tắt: checkout/return/refund/reconcile
503; IPN trả protocol `RspCode=99`. Đã tạo VNPay attempt thì simulate bị chặn.

Luồng sandbox: checkout → mở checkoutUrl → thanh toán theo hướng dẫn sandbox
provider → provider gọi IPN HTTPS → GET Payment/Order/Inventory đối chiếu.
Return URL hiển thị SUCCESS chưa đủ chứng minh DB đã capture; phải kiểm IPN
và trạng thái server.

Replay **cùng query IPN có chữ ký hợp lệ** chỉ trên fixture được kiểm soát:
lần đầu `RspCode=00`, trùng `02`; amount sai nhưng ký đúng `04`, chữ ký sai/
query trùng key `97`, reference không có `01`, không thể xác nhận `99`.
HMAC phải ký toàn bộ query theo protocol; không tự sửa amount/status của
callback rồi mong server chấp nhận. Không đặt merchant secret hoặc script
ký callback thật vào collection chia sẻ; negative/parallel callback ưu tiên
Java fixture tests.

Capture đến sau expiry/FAILED được lưu với `refundStatus=REQUIRED`, không làm
Order hết hạn thành CONFIRMED. Refund timeout giữ UNKNOWN; bấm refund lại
không gửi thêm lệnh chuyển tiền, chỉ reconcile querydr. PROCESSING/response
code 00 chưa tự chứng minh hoàn tiền xong; chỉ kết quả xác minh cuối mới
REFUNDED. Đối chiếu portal sandbox và log trạng thái đã loại credential.
Xem [protocol VNPay chính thức](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
và [QueryDr/Refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

## 14. Notification, SMTP và các bằng chứng không có REST API

Notification không có API nghiệp vụ public để Postman đọc log/gửi email tùy
ý. Sau CONFIRMED/CANCELLED, operator kiểm tra notification log trong DB riêng;
sau SHIPPED/COMPLETED kiểm tra fulfillment log theo event type. Các truy vấn
này phục vụ kiểm thử/vận hành, không phải code service query chéo DB.

Đối chiếu đúng orderId/event, một log cho mỗi stage dự kiến, queue/inbox/dedup
và pending Outbox. Không gọi route `/api/notifications` tự tạo để đánh PASS.
SMTP Mailpit sandbox đã kiểm chứng confirmation/cancellation/expiry ở mốc
168. Demo phải xem thư nhận được, đúng recipient/order/content và kiểm tra
replay không tạo delivery mới; log DB riêng lẻ không chứng minh thư đã tới.
Xem [runbook SMTP](expiry-email-monitoring.md) và [demo](demo-test-guide.md).

## 15. Xử lý lỗi và ghi kết quả

| Hiện tượng | Kiểm tra tiếp |
|---|---|
| 400 tạo đơn | JSON, items chỉ productId/quantity; key ở root; ID là số, không có field thừa |
| 401 | Đúng environment, bearer header, token hết hạn; login/refresh lại |
| 403 | USER/ADMIN và JWT sau lần đổi role; không sửa token để né kiểm tra |
| 404 Payment | Poll giới hạn, đúng order/owner; kiểm tra consumer/Outbox nếu không xuất hiện |
| 409 stock/key/SKU/state | Nội dung xung đột, stock hiện tại; replay phải giữ key và payload |
| 429 | Dừng burst, chờ refill; không tự coi quota là lỗi inventory |
| 503/504 | Redis/backend/RPC readiness/deadline, health và resource; lưu cả lần lỗi |
| Stock chưa đổi ngay sau simulate | Poll Order đến terminal rồi GET Inventory |
| Biến vẫn là `{{...}}`/ID rỗng | Environment hoặc shadow collection variables; request tiền đề chưa thành công |

Mẫu nhật ký kết quả (không điền PASS trước khi chạy):

| Case ID | Môi trường/SHA | Thời gian +07 | Request/case | Kỳ vọng | Thực tế | PASS/FAIL/SKIP/NOT READY | Bằng chứng |
|---|---|---|---|---|---|---|---|
| AUTH-01 | Điền bản đang chạy | Điền thời gian | Register USER | 201/USER | Điền status/schema | Chưa chạy | Response đã redaction |
| SAGA-01 | Điền bản đang chạy | Điền thời gian | SUCCESS | CONFIRMED, stock 8/0 | Điền kết quả | Chưa chạy | Order/Payment/Inventory |
| SAGA-02 | Điền bản đang chạy | Điền thời gian | FAILED/replay | CANCELLED, hoàn một lần | Điền kết quả | Chưa chạy | Stock trước/sau |
| OBS-01 | Điền bản đang chạy | Điền thời gian | Grafana/Prometheus | Có history/dashboard | Chưa triển khai | NOT READY | Checklist remaining-work |

Lưu IDs fixture, request body đã loại credential, HTTP status, trạng thái DB/
metrics cần thiết và timestamp. Không chỉ chụp một HTTP 200 để kết luận toàn
luồng thành công. Nếu skip vì thiếu ADMIN/provider/tài nguyên, ghi nguyên nhân.
Sau test: hoàn tất/cancel/đợi expiry đúng nghiệp vụ cho đơn thử; Product có thể
soft-delete sau kiểm chứng snapshot. Không xóa volume hoặc reset stock toàn
dự án; clear credential/token variables trước khi export collection/environment.
