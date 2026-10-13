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

Giữ Git worktree bên ngoài thư mục đang mở trong IDE, ví dụ
`C:\md05-rikkei-worktrees\ten-feature`. `java.import.exclusions` chỉ loại build
khỏi importer; watcher vẫn có thể nhận file Java được tạo trong bản sao và
đưa vào `jdt.ls-java-project` không có dependency. Settings cũng loại thư mục
kiểm chứng/worktree khỏi file watcher. Source và test của sáu service thật
vẫn được nhập và kiểm tra bình thường.

```powershell
git worktree add C:\md05-rikkei-worktrees\ten-feature -b feature/ten-feature
```

Khi đã có worktree trong `.worktrees`, dùng `git worktree move` tới thư mục
ngoài và kiểm tra `git worktree list`; không copy/xóa worktree vì Git cần cập
nhật metadata và giữ thay đổi chưa commit. Chỉ làm sạch cache Java riêng
workspace sau khi đã giữ bản sao và xác định đúng workspace.

Checkpoint 2026-10-12: cả sáu `compileJava`/`compileTestJava` đạt. Cache cũ
chứa project `wrapper-bootstrap` và API Gateway trong project Java tạm;
classpath Spring AMQP/JWT bị thiếu. Cache Java của riêng workspace được sao lưu
ngoài Git, Java Language Server khởi động lại và nhập đủ sáu build. Log xác nhận
`initialization job finished` lúc 02:21:37 và `build jobs finished` lúc
02:22:14 (+07:00). Đối chiếu marker mới không còn lỗi thiếu dependency; còn
một cảnh báo project encoding của Gateway tại checkpoint build đầu; cấu hình
UTF-8 sau đó đã được ghi vào preferences của project. Các kết quả này không thay thế
kiểm thử nghiệp vụ và không phải bằng chứng deploy VPS.

Checkpoint sau merge main 77: cache mới chỉ có sáu project thật, không còn
`jdt.ls-java-project`. IDE đã biên dịch class refresh/session mới của Auth
lúc 03:03:01–03:03:02 (+07:00); kiểm tra lại marker Java severity error bằng
0. Worktree đang phát triển đặt trong `C:\md05-rikkei-worktrees`, bên ngoài
workspace đang mở. PR #21 merge sau CI head 73 toàn bộ Java/live/recovery đạt.

## Lỗi resource đầu ra đã tồn tại của Gateway

Thông báo `Resource already exists on disk: '/api-gateway/bin/main/application.yml'`
là lỗi trạng thái resource của Java builder trong IDE. File nguồn vẫn là
`api-gateway/src/main/resources/application.yml`; không xóa hoặc đổi tên file
nguồn để xử lý output `bin/main`.

Kiểm tra riêng bằng Wrapper:

```powershell
./api-gateway/gradlew.bat -p api-gateway test bootJar --no-daemon
```

Khi Gradle đạt, chạy `Java: Reload Projects`, rồi `Java: Rebuild Projects` và chọn
`api-gateway` trong Command Palette. Đợi build kết thúc rồi kiểm tra Problems.
Nếu vẫn lỗi, restart Java Language Server rồi reload/rebuild project; không xóa
output hoặc metadata trong lúc builder đang dùng chúng.

Checkpoint 2026-10-13 19:41:35 +07: lỗi trên đã được tái hiện qua diagnostics của
IDE. Log có pipe startup timeout/fallback sang stdio, hai standard language servers
dùng cùng `jdt_ws`, cùng lỗi lưu index; đây là bằng chứng trạng thái IDE không nhất
quán, không phải lỗi biên dịch Gradle. Metadata riêng Gateway đã được sao lưu private;
dừng process pipe cũ của đúng workspace, reload cấu hình và full rebuild Gateway
bằng command của Java extension. Sau xử lý chỉ còn một standard server của workspace,
diagnostics Gateway và tổng số lỗi Java trong workspace đều bằng 0. Cảnh báo vẫn
được giữ nguyên, không tắt kiểm tra Java/null analysis.

Gateway `test bootJar --rerun-tasks --no-daemon` đạt 23/23 tests, zero fail/error/skip.
Không đổi Java source, routes, JWT/rate limit, cấu hình runtime, dữ liệu hoặc container;
không cần rollout VPS cho việc sửa trạng thái IDE này.

Checkpoint 2026-10-13 21:50 +07: IDE có 1.055 lỗi thiếu class nội bộ sau khi
phát triển COD/returns; Gradle vẫn kiểm thử/build đạt. Full rebuild cả sáu
project giảm còn 82 lỗi gRPC của Order. Reload `order-service/build.gradle`
khôi phục các source roots `build/generated/sources/proto/main/java` và
`main/grpc`; diagnostics về 0. Restart Java server và đọc lại project model
xác nhận đủ sáu Gradle projects, 0 errors. Không tắt kiểm tra hoặc sửa file
Java để che lỗi. Source sinh bởi protobuf nằm ở `generated/sources`, không
phải `generated/source`; kiểm tra source roots trước khi xóa cache.
