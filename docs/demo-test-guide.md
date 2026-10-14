# Kịch bản demo và tự kiểm thử MD05

Cập nhật 14/10/2026. Demo chính khoảng 20–25 phút, qua Postman, Mailpit và
Grafana; không cần frontend. Runtime VPS đã được kiểm chứng ở mốc 168
`d2779d1595fb5541675e65604eb07ac633ccf91b`, deadline ONLINE 150 giây.
Các bước dưới đây là kịch bản để chạy mới, không phải kết quả PASS của bạn.

## 1. Chuẩn bị trước khi trình bày

- Postman; import [collection hiện có](../tests/postman/md05.postman_collection.json).
  Collection có 38 requests trong 7 folders; 3 folders đầu là bài nhanh 19
  requests. Chạy folder được chọn, không Run toàn bộ vì VNPay/COD cần tiền đề
  và cấu hình riêng.
- Hai USER A/B mới cho ownership, một ADMIN thử nghiệm đã được cấp role.
  Login ADMIN bằng tài khoản thật; đăng ký public không tạo ADMIN.
- Environment riêng cho các bước thủ công: `baseUrl`, `runId`, `email`,
  `emailB`, `password`, `passwordB`, `adminEmail`, `adminPassword`, `userToken`,
  `userBToken`, `adminToken`, `productId`, `receiptKey`, `orderKey`, `orderId`,
  `paymentId`. Dùng UUID mới cho runId, email/SKU và key mỗi lượt.
- `baseUrl=https://md05-rikkei-project.duckdns.org` cho VPS hoặc
  `http://127.0.0.1:8080` cho stack local đã khởi động. Không trộn ID hai stack.
- Credentials/tokens chỉ giữ private. Trong Postman chọn Bearer Token với
  `{{userToken}}`/`{{adminToken}}`; POST JSON có Content-Type application/json.
  ID/token nằm trong `response.data`. Login lại nếu JWT hết hạn.

Mở một terminal riêng để giữ SSH tunnel (thay đường dẫn key private):

```powershell
ssh -i <private-key-path> -o StrictHostKeyChecking=yes -N `
  -L 13001:127.0.0.1:13000 `
  -L 19091:127.0.0.1:19090 `
  -L 18825:127.0.0.1:18025 deploy@42.96.2.60
```

Mở Grafana http://127.0.0.1:13001 (user ops, password private), Prometheus
http://127.0.0.1:19091/targets và Mailpit http://127.0.0.1:18825.
Dashboard UID `commerce-overview`; chọn Last 15 minutes, refresh 5s.
Prometheus phải có sáu service và năm dependency targets UP. Domain public
`/actuator/prometheus` trả 404; không mở metrics ra Internet.

## 2. Lời dẫn và thứ tự demo

“Dự án gồm sáu service Spring Boot, đi qua Gateway. Order giữ kho trong
transaction, Payment và Notification trao đổi event qua RabbitMQ/Outbox.
Tôi sẽ thử thanh toán thành công, thất bại, hết hạn 150 giây, rồi đối chiếu
email và số liệu giám sát. Cuối cùng thử COD, giao hàng và trả hàng.”

| Mã | Thời lượng | Nội dung và bằng chứng |
|---|---|---|
| D01 | 3 phút | Auth/JWT, 401/403 và ownership |
| D02 | 2 phút | Tạo sản phẩm, nhập 10, replay phiếu không tăng kho |
| D03 | 3 phút | ONLINE SUCCESS, replay, email xác nhận, ship/complete |
| D04 | 2 phút | FAILED → CANCELLED, hoàn kho một lần, email hủy |
| D05 | 4 phút | Chờ thật 150 giây → EXPIRED; payment muộn bị từ chối |
| D06 | 4 phút | COD → giao → thu tiền → hoàn tất → trả hàng/hoàn tiền |
| D07 | 2 phút | Grafana/Prometheus và số đo khớp cửa sổ demo |
| D08 | Riêng | k6 500/1.000 RPS, ghi kết quả thực và tài nguyên |

## 3. D01 — Đăng nhập, phân quyền, ownership

1. POST `/api/auth/register`, No Auth, body
   `{"fullName":"Demo User A","email":"{{email}}","password":"{{password}}"}`:
   201. Đăng ký USER B với email/password khác. Password tối thiểu 8 ký tự,
   tối đa 72 UTF-8 bytes. Gửi lại email A: 409.
2. POST `/api/auth/login` với email/password A: 200; lưu
   `data.accessToken` vào userToken. Login B và ADMIN tương tự.
   ADMIN phải có `data.user.role=ADMIN`.
3. GET `/api/auth/me`: không token 401, token sai 401, token A 200.
4. USER tạo Product ở D02: 403; chuyển ADMIN để tạo thật.
5. Sau khi có Order ở D03, USER B GET Order/Payment của A: 404;
   USER A vẫn đọc được 200. Không dùng ID giả để thay kiểm tra ownership.

## 4. D02 — Sản phẩm và nhập kho

ADMIN POST `/api/products`, kỳ vọng 201; lưu `data.id` vào productId:

```json
{
  "sku": "DEMO-{{runId}}",
  "name": "Demo Shirt",
  "description": "Fixture cho buoi demo",
  "unitPrice": 125000,
  "category": "Clothing",
  "brand": "DemoFixture"
}
```

Public GET `/api/products/{{productId}}`: 200, giá 125000.
GET `/api/products?q=Demo&brand=DemoFixture&page=0&size=20`: 200, thấy fixture.
ADMIN POST `/api/inventory/stocks`, kỳ vọng 200:

```json
{
  "productId": {{productId}},
  "quantity": 10,
  "idempotencyKey": "{{runId}}-receipt"
}
```

USER/ADMIN GET `/api/inventory/{{productId}}`: available=10, reserved=0.
Replay nguyên body: vẫn 10/0. Giữ key, đổi quantity=11: 409, kho vẫn 10/0.
Không dùng SKU/sản phẩm đã có trước để tính số kho trong các bước tiếp theo.

## 5. D03 — ONLINE thành công và email xác nhận

USER A POST `/api/orders`, kỳ vọng 201; lưu orderId từ data.id:

```json
{
  "channel": "WEB",
  "paymentMethod": "ONLINE",
  "idempotencyKey": "{{runId}}-success",
  "items": [{"productId": {{productId}}, "quantity": 2}],
  "shippingAddress": {
    "recipientName": "Demo User A",
    "phone": "0901234567",
    "street": "123 Demo Street",
    "ward": "Demo Ward",
    "district": "Demo District",
    "province": "Demo Province",
    "postalCode": "70000"
  }
}
```

1. Order PENDING_PAYMENT, totalAmount=250000; inventory 8/2.
2. Replay nguyên body/key: 200, cùng orderId, vẫn 8/2. Đổi quantity nhưng
   giữ key: 409. Khi thử lỗi, đổi lại body gốc trước khi replay tiếp.
3. USER A GET `/api/payments/by-order/{{orderId}}` mỗi giây, tối đa 30 giây;
   404 ban đầu có thể do consumer chưa xử lý. Khi 200, lưu data.id vào
   paymentId; kiểm tra orderId/amount=250000/status=PENDING.
4. Trước deadline 150 giây, ADMIN POST
   `/api/payments/{{paymentId}}/simulate`, body `{"status":"SUCCESS"}`: 200.
5. Poll GET `/api/orders/{{orderId}}` tối đa 30 giây đến CONFIRMED.
   Inventory phải là 8/0. Payment SUCCESS riêng lẻ chưa đủ kết luận Order.
6. Mailpit: tìm đúng email A và orderId, có thư xác nhận, tổng 250000.
   Replay SUCCESS: 200, inventory vẫn 8/0; chờ thêm 10 giây, không có thêm
   thư xác nhận cho cùng stage/order. Đây là SMTP sandbox, không chuyển tiền.
7. ADMIN POST `/api/orders/{{orderId}}/ship`, body
   `{"carrier":"DemoCarrier","trackingNumber":"TRACK-{{runId}}"}`:
   200/SHIPPING. POST `/api/orders/{{orderId}}/complete`, body none:
   200/COMPLETED. Replay hai thao tác: vẫn 8/0, timestamp đã chốt không đổi.

Thực hiện nhóm ownership của D01 với order/payment này.

## 6. D04 — Thất bại và hoàn kho một lần

1. Tạo Order ONLINE mới với payload D03, đổi key thành
   `{{runId}}-failed`, quantity=1; lưu orderId/paymentId riêng.
2. PENDING_PAYMENT, totalAmount=125000; inventory 7/1.
3. Poll Payment của đúng đơn; ADMIN simulate `{"status":"FAILED"}`: 200.
4. Poll Order đến CANCELLED, tối đa 30 giây; inventory trở lại 8/0.
5. Mailpit có thư hủy cho orderId mới/email A.
6. Replay FAILED: 200, vẫn 8/0; đổi sang SUCCESS: 409.
   Đối chiếu inventory và không có thêm thư hủy cho cùng order/stage.

## 7. D05 — Hết hạn thật 150 giây và payment đến muộn

Login lại A/ADMIN trước bước này nếu cần. Tạo ONLINE mới, quantity=1,
key `{{runId}}-expiry`; ghi orderId và `data.createdAt` ngay khi trả 201.
Kho ban đầu 7/1. Lấy paymentId bằng GET by-order nhưng **không simulate**.

| Mốc tính từ createdAt của server | Thao tác | Kỳ vọng |
|---|---|---|
| Khoảng 120 giây | GET Order + Inventory | PENDING_PAYMENT, 7/1 |
| Từ 150 giây | GET Order mỗi 2 giây, tối đa thêm 30 giây | EXPIRED, 8/0 |
| Sau event expiry | GET Payment mỗi giây, tối đa 30 giây | reservationExpired=true; payment đang PENDING chuyển FAILED |
| Sau EXPIRED | ADMIN simulate SUCCESS | 409; Order vẫn EXPIRED, kho 8/0 |
| 10 giây sau | GET lại Order/Inventory, xem Mailpit | Không hoàn thêm kho, một thư thông báo hết hạn đúng order/email |

Scheduler quét mỗi giây, batch 50; backlog hoặc downtime có thể làm quét
chậm. Nếu vượt thời gian chờ, ghi FAIL và kiểm tra queue/Outbox/health,
không sửa createdAt hay TTL để biến kết quả thành PASS.
Postman tuần tự kiểm tra late payment; tranh chấp song song giữa scheduler
và payment được kiểm tra bằng Java tests, không suy ra từ việc bấm Send.

## 8. D06 — COD, giao hàng, trả hàng và hoàn tiền

Payload D03, đổi paymentMethod=COD, quantity=1, key=`{{runId}}-cod`.
Dùng biến `codOrderId`/`codPaymentId` riêng và poll các event tối đa 30 giây.
Giữ ONLINE ở các bước trước đã terminal để kho có baseline 8/0.

| Bước | API / quyền / body | Kỳ vọng |
|---|---|---|
| Tạo COD | POST orders, USER A | 201/CONFIRMED, inventory 7/1 |
| Lấy Payment | GET payments/by-order/codOrderId, USER A | 200, amount=125000, paymentMethod=COD |
| Giao | POST orders/codOrderId/ship, ADMIN; carrier/tracking như D03 với suffix COD | 200/SHIPPING, inventory 7/0 |
| Thu đủ tiền | POST payments/codPaymentId/cod/collect, ADMIN; body bên dưới | 200; poll Order đến codPaidAt khác null |
| Hoàn tất | POST orders/codOrderId/complete, ADMIN, none | 200/COMPLETED |
| Yêu cầu trả | POST orders/codOrderId/return, USER A; body bên dưới | 200/REQUESTED |
| Duyệt | POST orders/codOrderId/return/decision, ADMIN; `{"approved":true,"note":"Chap nhan tra toan bo"}` | 200/APPROVED |
| Nhận và nhập lại | POST orders/codOrderId/return/receive, ADMIN; `{"warehouseReceipt":"RETURN-{{runId}}","restock":true}` | 200; inventory 8/0 |
| Chờ refund | GET payments/codPaymentId, USER A | refundStatus=REQUIRED |
| Hoàn tiền mặt | POST payments/codPaymentId/cod/refund, ADMIN; body bên dưới | 200; poll GET orders/codOrderId/return đến REFUNDED |

Body thu tiền:

```json
{"amount":125000,"idempotencyKey":"{{runId}}-collect","receiptNumber":"COLLECT-{{runId}}"}
```

Body yêu cầu trả:

```json
{"idempotencyKey":"{{runId}}-return","reason":"Demo tra toan bo don hang"}
```

Body hoàn tiền mặt:

```json
{"amount":125000,"idempotencyKey":"{{runId}}-refund","receiptNumber":"REFUND-{{runId}}"}
```

Payment chưa nhận shipment có thể trả 409 khi collect: poll shipment rồi
gửi lại cùng key/receipt, tối đa 30 giây. Replay receive/refund không cộng
kho hay ghi nhận hoàn tiền lần hai. Return là toàn bộ đơn, không partial.
Không gọi complete trước khi Order nhận event codPaidAt.
Các folders COD/return trong collection có thể dùng sau khi chuẩn bị đúng
biến/stock; chỉ chạy folder cần thử, kiểm tra assert cuối mỗi lượt.

## 9. D07 — Prometheus/Grafana trong lúc demo

1. Targets: sáu service + năm dependency UP; Grafana datasource Prometheus
   hoạt động, dashboard commerce-overview có 22 panels.
2. Khi gửi API D03–D06, xem HTTP throughput, p95/p99, error/429 cùng JVM,
   GC, CPU/RAM; chọn đúng cửa sổ thời gian, chờ ít nhất 2–3 scrape.
3. Sau D05, counter expiry/email phải xuất hiện hoặc tăng so với baseline;
   queue/Outbox giảm sau xử lý. No data cần kiểm tra metric/query, không
   diễn giải thành 0. Một request có thể chưa đủ cho quantile ổn định.
4. Có thể kiểm tra query `up{job="services"}` tại Prometheus: sáu series=1.
   Query chi tiết/panel/unit nằm trong
   [dashboard nguồn](infrastructure/monitoring/grafana/dashboards/commerce.json).
5. Lưu ảnh cửa sổ demo và summary đã bỏ credentials. Không restart hệ thống
   chỉ để trình bày; lịch sử qua recreate đã có bằng chứng tại
   [checkpoint monitoring](expiry-email-monitoring.md).

## 10. D08 — Bài tải riêng 500/1.000 requests/s

Chạy sau bài nghiệp vụ, khi CPU/RAM đã phục hồi. Đây là đo capacity,
không cam kết VPS đạt target. Kết quả trước đó chưa đạt; xem
[báo cáo tải](capacity-monitoring-report.md).

Operator chuẩn bị fixture private mới, 50 tài khoản Auth thật, bằng Java
tool `multiUserLoadFixture`. Các process env bắt buộc: LOAD_BASE_URL,
LOAD_FIXTURE_FILE (đường dẫn mới), JWT_SECRET đã nạp private; thêm
LOAD_ACCOUNTS=50, LOAD_STOCK=1. Tool không in credentials; không đặt secret
literal vào lệnh, Git hoặc ảnh. Đây là fixture vận hành, không API cấp ADMIN.

```powershell
.\order-service\gradlew.bat -p order-service multiUserLoadFixture `
  -PfixtureAction=prepare --no-daemon --max-workers=1

powershell -ExecutionPolicy Bypass -File docs/infrastructure/scripts/run-capacity-demo.ps1 `
  -K6Path '<private-tools>/k6.exe' `
  -FixtureFile '<private-fixtures>/capacity-50.json' `
  -SshKey '<private-key-path>' -DurationSeconds 10
```

FixtureFile phải đúng LOAD_FIXTURE_FILE đã chuẩn bị. Chuẩn bị sát lúc chạy
vì token USER có hạn; kiểm tra một catalog request qua HTTPS trả 200 trước
khi đo. k6 chạy ở máy client; harness giữ quota, giới hạn VUs/timeout,
đo CSV CPU/RAM/swap và baseline trước từng mức; mức bị guard chặn là chưa đo.

Mặc định `.verification/capacity` giữ JSON/CSV private. Ghi target/actual
requests/s, successful throughput, p95/p99, 4xx/5xx/429, transport errors,
dropped iterations và health sau tải. Threshold FAIL vẫn là kết quả cần
giữ. Grafana rate/quantile có cửa sổ dài hơn bài k6 10 giây, không ép hai số
bằng nhau. GET catalog không đặt đơn hay giữ kho.

## 11. Bảng chấm và kết thúc

Copy bảng này vào ghi chú private, điền runtime SHA và thời gian thực tế:

| Mã | Thực tế / ID / cửa sổ thời gian | PASS / FAIL / chưa chạy | Bằng chứng |
|---|---|---|---|
| D01 | | Chưa chạy | HTTP 401/403/404/200 |
| D02 | | Chưa chạy | Product + inventory 10/0 + replay |
| D03 | | Chưa chạy | COMPLETED + 8/0 + thư xác nhận |
| D04 | | Chưa chạy | CANCELLED + 8/0 + thư hủy |
| D05 | | Chưa chạy | createdAt/150s + EXPIRED + 409 + 8/0 + thư |
| D06 | | Chưa chạy | COD/REFUNDED + 8/0 + receipt |
| D07 | | Chưa chạy | Targets/query/dashboard ảnh đúng cửa sổ |
| D08 | | Chưa chạy | JSON/CSV/k6/health; target bị chặn ghi chưa đo |

Nếu baseline/kết quả lệch, dừng nhóm phụ thuộc và giữ response/log đã bỏ
secret. Không đổi ngưỡng hoặc sửa DB để ép PASS. Giữ fixture IDs cho audit;
không reset stock/xóa volume. Đóng đúng tunnel bằng Ctrl+C ở terminal của
phiên demo; bỏ credential variables trước khi export Postman. Kịch bản này
không thay thế Java unit/integration/live tests.

VNPay sandbox là bài riêng, cần merchant/config private đang bật. Không Run
folder VNPay trong bài simulate vì checkout sẽ khóa đường simulate của
payment; xem [hướng dẫn VNPay](postman-testing-guide.md#13-vnpay-chỉ-chạy-khi-đúng-feature-và-cấu-hình-đã-sẵn-sàng).
