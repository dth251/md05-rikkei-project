# Kiểm thử Postman bằng tiếng Việt — production

**Theo nhu cầu chỉ test luồng chính:** dùng [bản 42 requests / 8 nhóm](md05-luong-chinh.postman_collection.json),
xem [hướng dẫn gọn](../../docs/test-luong-chinh.md). Có COD, VNPay sandbox,
trả/hoàn tiền, chờ thanh toán 150 giây và hướng dẫn đối chiếu email;
chỉ khách hàng và ADMIN.
Chọn **No environment**, nhập tài khoản trực tiếp ở Body đăng ký/đăng nhập;
URL production có sẵn, token và ID tự lưu trong Variables của collection.

**Bản gộp một file:** [181 requests cho tất cả API](md05-tat-ca-api.postman_collection.json),
xem [cách chạy theo giai đoạn và mở sandbox](../../docs/postman-mot-file.md).

Bộ kiểm thử nghiệp vụ đầy đủ bên dưới dùng **[hướng dẫn production](../../docs/test-production.md)**.
Domain API: **https://md05-rikkei-project.duckdns.org**. Bản 162 requests
có bài mô phỏng SUCCESS/FAILED; bản luồng chính ở đầu trang dùng VNPay
sandbox đã cấu hình trên VPS. Cả hai không dùng thanh toán thật.

Import hai file vào Postman:

- [Collection nghiệp vụ](md05-tieng-viet.postman_collection.json): 162 requests trong 14 folders, tên request và assertion bằng tiếng Việt.
- [Environment production mẫu](md05-tieng-viet.production.postman_environment.json): đúng domain HTTPS, không có credentials.

Chọn environment production, điền `adminEmail`, `adminPassword` của ADMIN thử nghiệm và `notificationMailbox` là Gmail nhận thử vào giá trị private/local. Có thể import bản private đã chuẩn bị theo hướng dẫn production để dùng ADMIN sẵn có. Đăng ký công khai chỉ tạo USER; không có API cấp ADMIN.

Trong **Collection Runner**, chọn folders theo thứ tự số, **Iterations = 1**, **Delay = 1100 ms**. Dừng khi có lỗi request/script/assertion. Request tự dừng luồng khi assertion thất bại hoặc thiếu biến tiền đề. Không chạy song song hai phiên dùng chung environment.

Folder 01 tạo USER A/B và `runId` mới, dùng nhãn `+md05-a/b-...` trên Gmail nhận thử để thư về cùng inbox. Mật khẩu USER được sinh ngẫu nhiên nếu chưa điền. Các folder sau tự lưu token/ID/key vào environment; dùng cùng environment trong cả phiên. Không tạo globals hoặc dữ liệu iteration trùng tên vì chúng có thể che giá trị vừa lưu. Khi bấm Send thủ công, cần chạy đúng thứ tự và tự gửi lại request đang chờ; `setNextRequest` chỉ điều khiển Runner.

| Folder | Requests | Nội dung / tiền đề |
|---|---:|---|
| 01 | 13 | Đăng ký A/B, JWT, 401/403; khởi tạo phiên |
| 02 | 8 | Rotation, replay thu hồi family, logout; sau 01 |
| 03 | 12 | CRUD địa chỉ, default duy nhất, ownership; sau 01 |
| 04 | 17 | Login ADMIN, CRUD/tìm kiếm Product, variants; sau 01 |
| 05 | 7 | Nhập kho, replay/xung đột key; sau 04 |
| 06 | 20 | ONLINE SUCCESS, ownership, chốt kho, giao/hoàn tất; sau 01–05 |
| 07 | 10 | FAILED → CANCELLED, hoàn kho một lần; sau 01–05 |
| 08 | 15 | COD → giao → thu tiền → hoàn tất; sau 01–05 |
| 09 | 7 | Hủy COD riêng trước giao; sau 01–05 |
| 10 | 19 | Trả toàn bộ COD và hoàn tiền mặt; cần 08 hoàn tất |
| 11 | 10 | Chờ thật 150 giây, EXPIRED, từ chối tiền muộn; sau 01–05 |
| 12 | 10 | POS mua variant, kho cha độc lập, từ chối vượt kho; sau 01–05 |
| 13 | 9 | Trả ONLINE và simulate-refund; cần 06 hoàn tất |
| 14 | 5 | Xóa mềm cha/variant, snapshot lịch sử; chạy cuối sau 06 |

Từng luồng lấy baseline kho, không phụ thuộc ID production có sẵn. Chạy lại folder tạo đơn sẽ sinh key mới và có thể tiêu thụ thêm hàng. Chạy lại 04/05 với cùng `runId` sẽ xung đột SKU/phiếu; muốn phiên mới, bắt đầu lại 01. Folder 10/13 chỉ chạy khi đơn chưa có hồ sơ trả; request replay riêng dùng nguyên key/body đã lưu.

Chạy production bằng Postman CLI, dùng environment private ngoài Git:

```powershell
postman collection run tests/postman/md05-tieng-viet.postman_collection.json `
  -e '<duong-dan-environment-private.json>' `
  --delay-request 1100 --timeout-request 15000 --timeout 1200000 `
  --bail --no-report-events
```

Không cần login Postman cloud để chạy file local. CLI có thể nhắc login để publish; kiểm tra exit code và bảng assertions cuối lượt. `--no-report-events` tắt gửi sự kiện chạy. Không truyền mật khẩu trên command line hoặc export token/credentials vào Git.

Collection tạo tài khoản, nhập kho, ghi nhận thu/chi demo và xóa mềm sản phẩm mới của lượt thử. Xem [kết quả production](../../docs/postman-production-results.md) cho trạng thái thực tế. Environment local cũ vẫn có trong repository để dùng riêng khi cần, không phải environment của hướng dẫn này.

## ONLINE mô phỏng trên production

Dùng folders **06, 07, 11** của bộ chính cho SUCCESS, FAILED và EXPIRED.
API simulate trả JSON. SUCCESS tự xác
nhận đơn qua sự kiện và gửi email; không cần POST xác nhận đơn lần nữa.
Để tạo link sang VNPay sandbox và thao tác trên trang thanh toán, dùng
bản gộp hoặc collection sandbox ở phần tiếp theo.
Đây là API simulate kết quả thanh toán, không phát sinh tiền thật và chưa
kiểm chứng callback/chữ ký nhà cung cấp. Khi VNPay trên VPS đang tắt, có
[bộ kiểm tra phản hồi feature tắt](md05-production-vnpay-disabled.postman_collection.json)
riêng; xem thứ tự trong hướng dẫn production.

## VNPay sandbox có merchant và trang kết quả

Theo yêu cầu mới, xem [hướng dẫn thanh toán trên sandbox rồi quay về dự án](../../docs/vnpay-sandbox-browser.md).
Sandbox dùng merchant/thẻ test, không dùng tài khoản production. Cần cấu
hình private và triển khai trang kết quả trước khi ghi nhận bài này đạt.

Import [collection VNPay tiếng Việt](md05-tieng-viet-vnpay.postman_collection.json), dùng cùng environment. Chạy 01–05 của collection nghiệp vụ trước, chưa chạy 14. Cần merchant sandbox đã cấu hình, không đưa hash secret vào Postman.

1. **V00:** VNPay bật; IPN không ký trả HTTP 200/`RspCode=97`, return không ký trả 400. Khi provider bị tắt, IPN có mã khác; đó là thiếu tiền đề.
2. **V01:** tạo đơn ONLINE mới và URL checkout. Mở biến `vnpayCheckoutUrl` trong trình duyệt, thanh toán sandbox trước hạn 150 giây.
3. **V02:** chỉ chạy sau thao tác trên trình duyệt; chờ Payment SUCCESS và Order CONFIRMED. Return URL không tự chứng minh đã nhận tiền; cần IPN hợp lệ.
4. **V03:** giao/hoàn tất, trả/nhận lại toàn bộ hàng, chờ REQUIRED rồi gửi **một lần** yêu cầu hoàn VNPay.
5. **V04:** đối soát; chỉ PASS hoàn tiền khi Payment và hồ sơ return cùng REFUNDED. UNKNOWN/PROCESSING/REJECTED không phải hoàn thành. Khi chưa rõ kết quả, dùng reconcile theo runbook, không chạy lại V03.

Không Run toàn bộ collection VNPay liên tục: giữa V01/V02 cần thao tác ngoài Postman. Bộ sandbox chưa xác minh giao dịch ngân hàng thật.

## Phạm vi và kết quả

Các collection bao phủ các HTTP operation nghiệp vụ công khai trong [OpenAPI Gateway](../../docs/openapi/api-gateway.json). API recipient nội bộ của Notification, tài liệu OpenAPI private và Actuator không nằm trong bộ HTTP nghiệp vụ này. Production kiểm tra email qua Gmail; monitoring qua Grafana/Prometheus private trên VPS. Không tạo endpoint Notification giả để demo.

[Kịch bản demo và lời dẫn](../../docs/demo-luong-hoat-dong.md) có thứ tự trình bày, bảng tồn kho và checklist bằng chứng. [Kết quả kiểm chứng](../../docs/postman-tieng-viet-results.md) phân biệt phần chạy live, kiểm tra tĩnh và phần chưa chạy. Postman tuần tự không thay thế Java unit/integration tests, kiểm thử race hoặc k6.

Muốn gửi đến hộp thư thật của khách đã đăng ký: xem [cấu hình SMTP thật](../../docs/email-thuc.md). Dữ liệu tự sinh của collection dùng email giả; Mailpit không chuyển thư ra Gmail/Outlook.

Hai collection cũ `md05.postman_collection.json` và `md05-production-fixtures.postman_collection.json` vẫn có hướng dẫn/biến riêng. Không dùng lẫn với environment của bộ tiếng Việt.
