# Bằng chứng CI head 140

SHA: `175095963f15aa23846dcce0673384e796758d90`, ngày 13/10/2026.
Nguồn: artifact `java-test-reports` của
[PR CI 38379527058](https://github.com/dth251/md05-rikkei-project/actions/runs/38379527058),
completed success. Đây là Linux CI, không phải VPS hoặc provider sandbox thật.

- `observability.json`: sáu target service/năm dependency jobs, ba datasource,
  21 panels, sáu history series và restart được kiểm chứng.
- `trace-privacy.json`: fixture lưu rồi đọc từ Tempo, loại marker nhạy cảm.
- `tracing-business.json`: trace đủ sáu service, context lưu bền vững trong ba DB.
- `logging.json`: Docker audit truy vấn được theo trace ID, marker/raw line bị loại.
- `e2e-reconciliation.json`: quyền sở hữu fixture và Order/Payment/stock được đối chiếu.
- `small-load.json`: trích metric/status/threshold của k6; không chứa fixture/token.

Hai tài khoản, 1 iteration/s, các pha chính 10 giây, 5 VU ban đầu/tối đa 20.
HTTP rate thực lấy từ k6, bao gồm request polling; 4xx ở E2E là 404 khi Payment
chưa được consumer tạo và được bài kiểm thử chấp nhận. Không ghi 4xx bằng 0 hoặc
nhầm iteration/s với HTTP RPS. Collector outage có bốn đơn tạo/xác nhận thành công.
Threshold giữ nguyên, dữ liệu fixture được giữ để đối chiếu, không reset/purge.

Raw JVM/SQL/MQ/resource snapshots có trong artifact gốc. Không xác nhận JFR,
capacity 500–1.000 RPS, deploy monitoring VPS, merchant/SMTP ngoài, alert channel
hoặc video demo từ các file này. Xem [runbook](https://github.com/dth251/md05-rikkei-project/blob/8a909c99a3cf246d26da9148f5502b02f37bf8a0/docs/remaining-implementation.md).
