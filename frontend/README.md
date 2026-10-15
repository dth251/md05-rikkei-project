# MONO — cửa hàng và quản trị

Frontend riêng dùng React, TypeScript và Vite. Giao diện đen/trắng tối giản,
responsive trên desktop/mobile; API đi qua Gateway, backend thực thi quyền
và toàn bộ thay đổi nghiệp vụ.

## Chạy

```powershell
cd frontend
npm ci
npm run dev
```

Mở `http://127.0.0.1:5173`. Dev server proxy `/api` tới domain VPS hiện có.
Để dùng backend local, đặt `API_PROXY_TARGET=http://127.0.0.1:8080` trong
process trước khi chạy. Không đưa merchant secret hoặc mật khẩu vào Vite.

```powershell
npm run build
npm test
npm run test:e2e
```

E2E mặc định dùng API fixture, tách khỏi backend thật. Local dùng Edge;
CI cài Chromium. Chạy các lượt trình duyệt lần lượt, không chạy nhiều
config dùng chung server/output directory đồng thời.

## Màn hình

| USER | ADMIN |
|---|---|
| Trang chủ, tìm kiếm/lọc/sắp xếp/phân trang | Tổng quan với số liệu API thật |
| Sản phẩm và lựa chọn biến thể | Tạo/sửa/ngừng bán, tạo biến thể |
| Giỏ hàng, checkout WEB, ONLINE/COD | Nhập kho, available/reserved |
| Đăng ký/login, refresh/logout | Danh sách đơn, lọc trạng thái/kênh |
| Hồ sơ tài khoản, CRUD địa chỉ/default | Giao hàng, thu COD, hoàn tất/hủy COD |
| Danh sách đơn của chính mình | Duyệt/từ chối trả, nhận/restock, hoàn COD |
| Chi tiết, đếm ngược giữ hàng 200 giây | Yêu cầu hoàn/đối soát VNPay |
| Mở VNPay sandbox khi đơn còn hiệu lực | Bán POS với kho chung WEB |
| Yêu cầu và theo dõi trả toàn bộ đơn | Tra khách hàng/Payment theo ID |
| Phân biệt đã thu tiền với cần hoàn tiền | Công cụ simulate chỉ dành cho kiểm thử |

Notification được kích hoạt qua nghiệp vụ hiện có. Màn hình thông báo
trình bày thời điểm gửi, không giả trạng thái thư đã tới inbox. Hình sản
phẩm là bộ ảnh minh họa AI lưu tại `src/assets/products/`, được chọn theo
tên/danh mục/màu biến thể vì API hiện chưa có trường ảnh. Có áo trắng,
xanh, đỏ, đen, hoodie, áo dài, balô, cốc và điện thoại; fixture chưa mô tả
vật phẩm dùng ảnh hộp hàng. Ảnh tải lỗi có dự phòng, ảnh danh sách tải lazy;
Vite tạo tên file theo nội dung để cache đúng giữa các release. Prompt
tạo ảnh được lưu tại `src/assets/products/prompts.json`. Không bổ sung
khuyến mãi, phí giao hàng, vận chuyển tự động hoặc đổi role khi backend
chưa có nghiệp vụ tương ứng.

## Quy tắc

- Cart lưu trong localStorage; access/refresh credential lưu sessionStorage
  để giữ phiên khi quay lại từ VNPay. Không lưu mật khẩu. Phải logout trên
  thiết bị dùng chung; token không xuất hiện trong URL hoặc logs.
- Login USER/ADMIN theo tài khoản thật; route guard là hỗ trợ giao diện,
  quyền và ownership được kiểm tra lại ở từng API.
- Checkout và nhập kho giữ idempotency key khi retry cùng nội dung.
- ONLINE tính 200 giây từ createdAt; không mở checkout sau hạn. Callback
  tới muộn vẫn tuân theo trạng thái backend và yêu cầu hoàn tiền.
- COD chỉ hoàn tất sau khi Order nhận acknowledgement thu tiền; trả hàng
  phải qua duyệt và nhận hàng trước khi hoàn. Không coi REFUND_PENDING là
  đã hoàn tiền. Refund chưa rõ kết quả phải đối soát.
- Lịch sử đơn dùng API mới `GET /api/orders?page=0&size=20&status=&channel=`.
  USER chỉ xem đơn của mình; ADMIN được xem tất cả. Giới hạn size 100.

## Kiểm thử backend thật

Lượt này tạo fixture riêng và có thể phát email trạng thái. Cung cấp file
private qua biến môi trường, không commit file hoặc ghi credential vào log:

```powershell
$env:FRONTEND_LIVE_CREDENTIALS='C:/duong-dan-private/credentials.json'
npx playwright test --config playwright.live.config.ts
```

File private có `adminEmail`, `adminPassword`, `userEmail` chưa đăng ký,
`userPassword`, `runId`. Live test tắt trace/video/screenshot chứa phiên,
lưu kết quả đã loại thông tin đăng nhập trong `.verification/frontend-live`.
Không chạy với tài khoản/dữ liệu khách hàng đang sử dụng.

## Triển khai

`npm run build` tạo `dist/`. Trên VPS, root tạo
`/var/www/md05-frontend` với owner deploy/mode 755 một lần. Cài release
bằng `scripts/install-frontend.sh` và `FRONTEND_REVISION=<full-git-sha>`.
Các location trong `nginx-locations.conf` phục vụ SPA ở `/` và `/admin`;
giữ nguyên proxy `/api`, Swagger UI và health. Kiểm tra `nginx -t` trước
khi reload. Runtime chỉ cần Nginx phục vụ file tĩnh, không cần Node server.
