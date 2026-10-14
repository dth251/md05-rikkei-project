# Mẫu email hóa đơn MD05

[Xem mẫu HTML](invoice-preview.html), [ảnh desktop](invoice-preview.png),
[ảnh điện thoại](invoice-preview-mobile.png).

Mẫu có mã/ngày hóa đơn, người nhận, sản phẩm, số lượng, đơn giá, thành tiền,
tổng thanh toán, phương thức và trạng thái thanh toán. Dữ liệu demo theo
đơn #145: một sản phẩm 125.000 đồng, ONLINE mô phỏng. Không có giao dịch
ngân hàng hoặc hóa đơn thuế được phát hành từ bản demo này.

Template dùng bảng và CSS inline, có bố cục mobile, không có JavaScript,
font hoặc ảnh ngoài. Bản gửi là multipart/alternative với nội dung text và
HTML UTF-8. Đã kiểm tra hình ảnh desktop 820px và mobile 390px, không tràn
ngang; chưa coi kết quả trình duyệt là kiểm chứng mọi phiên bản Gmail.

## Render và gửi thử

```powershell
& docs/infrastructure/scripts/render-invoice-preview.ps1 `
  -DataFile docs/email/invoice-demo-data.json `
  -OutputFile docs/email/invoice-preview.html
```

Renderer kiểm tra tổng bằng tổng các dòng, từ chối số lượng/giá sai và
HTML-escape dữ liệu khách/sản phẩm. Dùng bản JSON private ngoài Git khi có
email hoặc thông tin khách thật.

Chỉ gửi khi người dùng yêu cầu gửi thử:

```powershell
& docs/infrastructure/scripts/send-invoice-preview.ps1 `
  -Recipient '<email-nhan-thu>' `
  -HtmlFile '<ban-HTML-da-render>' `
  -SshKey '<private-key-path>' `
  -ResultFile "$PWD/.verification/invoice-status.json" `
  -Subject 'MD05 - Hoa don mua hang mau'
```

Script gửi qua SMTP Gmail đã cấu hình trên VPS, lấy credentials từ
Notification runtime, không truyền password qua command arguments/log.
`smtp-accepted` / mã 250 nghĩa là máy chủ nhận gửi; chỉ ghi nhận inbox
thành công sau khi người nhận xác nhận. `unconfirmed` cần đối soát trước
khi gửi lại để tránh thư trùng.

## Trạng thái áp dụng

Đã bật Gmail SMTP trên VPS lúc 23:02:42 +07 ngày 14/10/2026, giữ image
Notification `d2779d1595fb5541675e65604eb07ac633ccf91b`. Luồng tự động của
đơn #145 ghi `order.confirmed / SENT / attempts=1` lúc 23:03:27 +07.

Sau khi người dùng yêu cầu làm giao diện email trước khi gửi, đã render
và kiểm tra mẫu rồi gửi bản HTML đến đúng địa chỉ người dùng chỉ định lúc
23:13:29 +07; Gmail SMTP trả 250. Chưa xác nhận nội dung trong inbox tại
thời điểm ghi tài liệu.

Đây là template và công cụ gửi **mẫu**. Notification tự động hiện vẫn dùng
`EmailMapper` text hiện tại; chưa thay mapper hoặc deploy binary để tự
render hóa đơn HTML cho mọi đơn. Việc áp dụng động cần lấy snapshot
sản phẩm/phương thức thanh toán của đúng đơn và kiểm tra quyền truy cập;
không dùng dữ liệu cố định của file demo cho email khách thật.
