# Nhập dự án Java trong IDE

Mở thư mục gốc `md05-rikkei-project`. Sáu thư mục service là sáu Gradle build
độc lập, mỗi build có Wrapper và Java toolchain 21. Thư mục gốc không có
`build.gradle` hoặc `pom.xml`.

`.vscode/settings.json` dùng chung cho VS Code và Antigravity IDE:

- Nhập Gradle bằng Wrapper, tự cập nhật classpath khi build script thay đổi.
- Loại `.verification`, `.worktrees`, `.gradle` và output `build` khỏi quá trình
  tìm project. Các bản sao kiểm chứng không được nhập như ứng dụng khác.
- Tắt Maven importer vì dự án dùng Gradle.
- Dùng UTF-8 cho file và đặt encoding mặc định của workspace lên project.
- Không cố định đường dẫn JDK của một máy và không tắt cảnh báo Java.

Cài JDK 21 trên máy phát triển. Kiểm tra compiler cho cả source và test:

```powershell
foreach ($service in @(
    'auth-service', 'api-gateway', 'product-service',
    'order-service', 'payment-service', 'notification-service'
)) {
    & "$service/gradlew.bat" -p $service compileJava compileTestJava --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Compile failed: $service"
    }
}
```

Nếu Gradle biên dịch đạt nhưng Problems báo `cannot be resolved` cho thư viện
Spring/JWT, chạy `Java: Reload Projects`. Khi vẫn còn classpath cũ, chạy
`Java: Clean Java Language Server Workspace` và để IDE nhập lại đủ sáu service.
Không thêm thư viện thủ công vào `java.project.referencedLibraries` và không
thêm Maven để chữa lỗi import.

Checkpoint 2026-10-12: cả sáu `compileJava`/`compileTestJava` đạt. Cache cũ
chứa project `wrapper-bootstrap` và API Gateway trong project Java tạm;
classpath Spring AMQP/JWT bị thiếu. Cache Java của riêng workspace được sao lưu
ngoài Git, Java Language Server khởi động lại và nhập đủ sáu build. Log xác nhận
`initialization job finished` lúc 02:21:37 và `build jobs finished` lúc
02:22:14 (+07:00). Đối chiếu marker mới không còn lỗi thiếu dependency; còn
một cảnh báo project encoding của Gateway. Các kết quả này không thay thế
kiểm thử nghiệp vụ và không phải bằng chứng deploy VPS.
