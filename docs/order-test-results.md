# Kiểm thử Order skeleton

Kiểm chứng local ngày 2026-10-11, Asia/Bangkok:

- 5 Java tests đạt: lưu Order/OrderItem snapshot, tổng tiền, idempotency key UNIQUE theo user, constraint tồn kho không âm, quantity hợp lệ, health public và JWT bắt buộc.
- 1 Java live test với PostgreSQL thật và Docker Gateway đạt: kết nối bằng `order_user` vào `order_db`; đủ bảng `orders`, `order_items`, `inventory`; PostgreSQL trả SQLSTATE 23514 cho inventory âm và transaction test được rollback.
- Gateway route `/api/orders`: không token trả 401; token hợp lệ tới Order skeleton trả 404 cùng response của Order. Đây chưa phải API đặt hàng.
- Atomic reservation, Order API, publish/consume RabbitMQ và Saga compensation thuộc ngày 2; chưa được đánh dấu hoàn thành hoặc đã kiểm thử tải.
- CI/VPS deployment chỉ được ghi đạt khi PR và image SHA thực tế được xác nhận.
- [PR #5](https://github.com/dth251/md05-rikkei-project/pull/5) được tích hợp tại SHA `63dd5be22f455d77af1f673fda41cb116ea2e3a3`; [run main](https://github.com/dth251/md05-rikkei-project/actions/runs/38136798372) kết thúc 2026-10-11 11:55:40 UTC, verify/publish/deploy success. SSH đối chiếu lúc 12:33:55–12:34:07 UTC: Order dùng đúng image SHA trên, healthy và health UP; PostgreSQL riêng được verifier xác nhận đạt.
