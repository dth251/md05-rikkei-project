# Shipping và Completed

Luồng: `CONFIRMED → SHIPPING → COMPLETED`. Chỉ ADMIN được ghi; USER 403,
anonymous 401. Pending/cancelled/expired không giao; không bỏ qua Shipping.
GET Order giữ ownership và trả snapshot cho chủ đơn/ADMIN.

`POST /api/orders/{id}/ship` nhận:

```json
{
  "carrier": "Delivery carrier",
  "trackingNumber": "TRACKING_REFERENCE",
  "shippingAddress": {
    "recipientName": "Recipient",
    "phone": "0901234567",
    "street": "Delivery street",
    "ward": "Ward",
    "district": "District",
    "province": "Province",
    "postalCode": "70000"
  }
}
```

Carrier 1–100, trackingNumber 1–128 ký tự. Address: recipientName 1–100,
phone 9–15 chữ số với `+` tùy chọn, street 1–255, ward/district/province
1–100, postalCode tối đa 10. Client lấy địa chỉ của chính mình từ Auth rồi
copy các field khi tạo đơn; `defaultAddress` không thuộc body Order.

`POST /api/orders` bổ sung `shippingAddress` tùy chọn. Fingerprint của
idempotency key gồm snapshot; cùng key đổi address trả 409. Address đã chốt
không đổi khi sửa trong Auth. Client/đơn cũ thiếu address vẫn hoạt động;
ADMIN phải bổ sung lúc ship, thiếu trả 400. Khi đã lưu, ship bỏ address
được, gửi khác trả 409. Đây là snapshot do client nhập, chưa xác minh với
hãng vận chuyển hoặc lookup address ID từ Auth.

`POST /api/orders/{id}/complete` không cần body. Hai API trả Order response
có `shippingAddress`, `shipment` (carrier/trackingNumber/shippedAt/completedAt).
Ship lặp cùng thông tin, kể cả sau Completed, trả trạng thái hiện tại;
đổi thông tin trả 409. Complete lặp không đổi timestamp. Row lock serialize
request/event thanh toán; mỗi stage và Outbox event commit cùng transaction.
Giao/hoàn tất không trừ kho lần nữa. Payment event đến sau khi giao không
đổi trạng thái/tồn kho theo quy tắc terminal hiện có.

## Event và Notification

`order.shipped`, `order.completed`: CommerceEvent cũ với paymentId null,
queue durable `notification.fulfillment.q`, DLQ/policy scope riêng.
Notification ghi `fulfillment_logs`, unique `(order_id,event_type)`, Inbox
theo eventId. Terminal confirmed/cancelled vẫn một log/đơn trong bảng cũ.
Producer phát ID mới cùng stage không tạo log nữa; amount/user khác bị reject.
Các stage có thể đến không theo thứ tự; log độc lập không dùng để suy ra
trạng thái hiện tại. Không đưa address/credential/payload vào log vận hành.
Chưa kết nối API hãng vận chuyển hoặc gửi email SMTP thật.

## Vận hành và kiểm chứng

Provision definitions trước image. Migration `order-service/002-order-fulfillment.sql`
thêm columns/constraint, schema runner checksum áp dụng một lần. Local
PostgreSQL apply/replay giữ 63/63 đơn cũ. Image cũ không hiểu enum mới;
khi đã có Shipping/Completed, rollback cần image tương thích hoặc fix forward.

Local: `bash infrastructure/scripts/verify-infrastructure.sh --allow-loopback`
chỉ chấp nhận `127.0.0.1`/`::1`. Mặc định vẫn cấm publish DB/MQ port cho CI/VPS;
lần chạy default trên local có port test phải reject, không ghi thành pass.
Java unit: Order 33, Notification 10 đạt (roles, validation, immutable snapshot,
20 request/stage, unique log, envelope rejection). Live PostgreSQL/RabbitMQ
qua Gateway và recovery có kết quả cuối trong nhật ký/PR; không suy ra deploy
từ unit. Worktree ngoài IDE đặt `ORDER_SMOKE_PROJECT_DIR`,
`NOTIFICATION_SMOKE_PROJECT_DIR` tới thư mục Compose có private `.env` cho recovery.

## COD

COD được ship khi payment còn PENDING; kho chốt tại ship. Complete yêu cầu
Order đã nhận kết quả thu tiền. Hủy COD chỉ trước ship. Xem [luồng COD](cod-payment.md).
