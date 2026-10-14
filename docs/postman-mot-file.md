# Một file Postman cho toàn bộ API

Import **[md05-tat-ca-api.postman_collection.json](../tests/postman/md05-tat-ca-api.postman_collection.json)**:
181 requests, 19 folders, tiếng Việt, bao phủ toàn bộ method/path API hiện có.
Chọn environment production private đã chuẩn bị theo
[hướng dẫn production](test-production.md#1-import-và-chọn-đúng-environment).
File collection không chứa mật khẩu ADMIN, JWT hoặc HashSecret; không cần
import riêng collection nghiệp vụ và collection VNPay nữa.

1. **P1:** chọn tất cả folders mang tiền tố P1, chạy theo thứ tự, Iterations
   1, Delay 1100 ms, dừng khi lỗi. Bộ tạo dữ liệu mới, chạy các tình huống
   nghiệp vụ rồi tạo một đơn ONLINE riêng để thanh toán trên VNPay sandbox.
   Sản phẩm chưa bị xóa mềm ở giai đoạn này.
2. Runner **tự dừng sau khi tạo link sandbox**. Mở biến environment
   `vnpayCheckoutUrl` trên trình duyệt ngay, thanh toán bằng thẻ test theo
   [hướng dẫn sandbox](vnpay-sandbox-browser.md). Đơn hết hạn sau 150 giây
   từ lúc tạo, cần hoàn tất thao tác trong thời gian đó.
3. Sau khi trình duyệt quay về, chọn **riêng P2** để kiểm tra Payment
   SUCCESS, Order CONFIRMED và kho, sau đó giao/hoàn tất/trả/hoàn tiền
   sandbox. Nếu chỉ cần demo thanh toán, chạy P2-V02; các P2-V03/V04 là
   bài hoàn tiền riêng. Không chạy lại P1 vì nó khởi tạo ID/tài khoản mới.
4. Chạy **P3** cuối cùng để xóa mềm đúng sản phẩm vừa tạo và kiểm tra
   snapshot đơn lịch sử. Giữ nguyên environment trong mọi giai đoạn.

Các requests simulate trong P1 là bài kiểm thử nghiệp vụ riêng. Đơn VNPay
được tạo mới sau đó; không dùng simulate trên payment đã tạo checkout
VNPay. Toàn bộ URL thanh toán provider được chốt vào sandbox, không có
giao dịch tiền thật. Provider cần merchant sandbox/IPN đã cấu hình đúng.
Không kết luận đã đạt toàn bộ từ việc P1 dừng đúng tại checkout; P2 cần
kết quả từ giao dịch trên trình duyệt và IPN thực tế.

Gửi request thủ công theo thứ tự cũng được; lệnh dừng/điều hướng chỉ có
tác dụng trong Runner. Token hết hạn thì chạy lại riêng request login,
giữ ID/key đang thử. Không export bản chứa credentials/tokens vào Git.

File được tạo từ hai collection nguồn bằng
[build-postman-all.ps1](infrastructure/scripts/build-postman-all.ps1).
Khi sửa nguồn, chạy lại script và kiểm tra cả scripts/requests trước khi
chia sẻ bản gộp.
