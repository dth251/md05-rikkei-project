# Kết quả kiểm thử

Phạm vi hiện tại: hạ tầng/Auth, Gateway + Redis rate limit, Product CRUD và Order skeleton theo ngày 1 của kế hoạch mới. Các phần Auth ban đầu bên dưới là bằng chứng lịch sử; kết quả cho phạm vi mới nằm ở cuối tài liệu. Thời gian kiểm chứng local: 2026-10-11, múi giờ Asia/Bangkok (UTC+07:00).

## Kiểm chứng local

| Kiểm tra | Kết quả thực tế |
|---|---|
| Java 21 / Gradle 8.9 / Spring Boot 3.5.6 | `test bootJar` thành công |
| AuthApplicationTests | 1 test, 0 failure, 0 error |
| AuthApiTests | 10 tests, 0 failure, 0 error |
| AuthLiveTests (HTTP + PostgreSQL thực + Docker restart) | 1 test, 0 failure, 0 error |
| PostgreSQL | 5 database/user riêng, owner đúng, user không CONNECT được database service khác |
| RabbitMQ | exchange topic, 3 durable queues, 5 routing keys được provision |
| Network | DB/MQ không có public port; port PostgreSQL local chỉ bind loopback |
| Auth container | healthy, `/actuator/health` trả 200/UP |
| Workflow / Bash / production Compose | actionlint, `bash -n`, `docker compose config --quiet` đạt |

Bài kiểm thử Auth bao gồm register/login/me, BCrypt, normalize email, USER/ADMIN, lỗi 400/401/403/404/409, JWT hết hạn/sai issuer/sai claims, chống tự chọn role, giới hạn password theo UTF-8 bytes. Live test thực hiện 6 đăng ký trùng đồng thời: đúng 1 thành công và 5 conflict; kiểm tra dữ liệu giữ nguyên sau restart container.

Artifact local: `auth-service/build/reports/tests/test/index.html`, `auth-service/build/reports/tests/liveTest/index.html` và XML trong `auth-service/build/test-results/` (không commit build output). Workflow hiện lưu reports của tất cả module vào artifact `java-test-reports`.

## GitHub Actions và VPS

Run đầu tiên: [03-devops: add auth ci and selective vps deployment](https://github.com/dth251/md05-rikkei-project/actions/runs/38130511299), SHA `4139c73ad156772c94fd6db229812c08c50375b6`. Trạng thái CI được đối chiếu trực tiếp và cập nhật trong [nhật ký commit/deploy](commits-and-deployments.md).

[Run trên mốc 04-devops](https://github.com/dth251/md05-rikkei-project/actions/runs/38130745804), SHA `c694454e8687d608cecc78f6d6257eb671c18462`: verify và GHCR publish đều success; deploy skipped. [PR verify](https://github.com/dth251/md05-rikkei-project/actions/runs/38130766520) trên cùng head cũng success. Bộ kiểm thử Java với PostgreSQL thực đã chạy trên runner Ubuntu, không chỉ local Windows.

## Kiểm chứng VPS và domain

Vướng mắc SSH ban đầu đã giải quyết: public key máy vận hành được cấp quyền cho root; khóa CI riêng được cấp cho user `deploy`. Cả hai đăng nhập bằng khóa đã được kiểm chứng. `.env` VPS có owner `deploy`, mode `600`; mật khẩu root cũ đã được thay sau khi xác minh SSH bằng khóa.

[Auth CI/CD deploy run](https://github.com/dth251/md05-rikkei-project/actions/runs/38132486801), SHA `5f441d2c5c85de10d0d68dcbf02a38952937c6d7`, kết thúc 2026-10-11 10:43:21 UTC: verify, publish và deploy đều success. VPS chạy PostgreSQL, RabbitMQ và Auth; health/smoke nội bộ đạt.

Kiểm tra từ máy vận hành lúc 2026-10-11 10:44:29 UTC: [trang gốc HTTPS](https://md05-rikkei-project.duckdns.org/) chuyển tới health và trả `{"status":"UP"}`. Nginx/Certbot đã cấu hình; chứng chỉ Let's Encrypt hết hạn 2027-01-09, `certbot.timer` active. Smoke qua HTTPS đạt register 201, login/me 200, thiếu token 401, USER gọi admin 403 và đăng ký trùng 409.

DB/MQ không publish port; Auth chỉ bind loopback 8081 và public qua Nginx 80/443. 5 database có owner riêng và kiểm tra CONNECT isolation/durable topology đạt. Tệp kiểm chứng tạm đã được chuyển ra ngoài dự án; bằng chứng tóm tắt và liên kết CI nằm trong [commits-and-deployments.md](commits-and-deployments.md).

## Bộ kiểm thử phạm vi ngày 1 mới

XML reports local xác nhận 39 unit tests và 4 live tests Java, 0 failures/errors:

| Module | Unit | Live | Phạm vi chính |
|---|---:|---:|---|
| Auth | 11 | 1 | Register/login/me, BCrypt, JWT/RBAC, 6 email trùng đồng thời, PostgreSQL/restart |
| Gateway | 15 | 1 | Routes, JWT claims/signature/expiry, chống giả header, Redis 429 và Lua failure 503 |
| Product | 8 | 1 | ADMIN CRUD, public catalog, validation/SKU unique, soft-delete, 6 SKU trùng đồng thời |
| Order skeleton | 5 | 1 | Order/OrderItem/Inventory, tổng tiền, DB constraint/idempotency uniqueness, PostgreSQL/JWT routing |

Reports theo mỗi module tại `<service>/build/reports/tests/{test,liveTest}/index.html` và XML `build/test-results/`; không commit build output. CI chạy tất cả module hiện có, lưu artifact `java-test-reports` và JAR đã kiểm thử `tested-jars`. Chi tiết: [Gateway](gateway-test-results.md), [Product](product-test-results.md), [Order](order-test-results.md).

Kiểm tra vận hành đạt: actionlint, cú pháp tất cả Bash scripts, production Compose với SHA image hợp lệ; 5 DB đúng owner và 20 hướng CONNECT chéo bị từ chối; 3 durable queues/5 bindings chính xác. Generator từ chối OpenSSL lỗi trước khi tạo `.env`; topology verifier từ chối binding sai. Tắt Redis local trả 503 cho login rồi khởi động lại healthy. Hai unit tests Gateway kiểm tra thêm lỗi Lua xảy ra sau availability probe.

Product live test qua HTTPS thật đã đạt sau rollout 17, gồm CRUD/RBAC/validation/concurrent SKU/soft-delete. Fixture ADMIN/USER ký bằng secret môi trường VPS chỉ phục vụ kiểm thử; luồng cấp token Auth được kiểm chứng riêng bằng Gateway live test. Source nghiệp vụ/kiểm thử dùng Java; vận hành Bash/PowerShell, không có Python được track.

Đối chiếu VPS ngày 2026-10-11 12:33–12:35 UTC: Auth/Product/Order chạy image `63dd5be`, Gateway chạy `867e87b`; bốn container healthy và health nội bộ UP. PostgreSQL/RabbitMQ/Redis healthy, không public port; verifier hạ tầng chạy lại đạt. Java Gateway live test chạy lại qua HTTPS trên bản Gateway cuối đạt, bao gồm token thật do Auth cấp và Redis 429. Root tới `/gateway-status` UP, catalog 200, Order thiếu token 401, actuator công khai 404. Hai run main Order/Gateway đều verify/publish/deploy success; SHA đầy đủ và thời gian nằm trong nhật ký.

Order hiện chỉ là skeleton. Atomic stock reservation, request idempotency nghiệp vụ, RabbitMQ publisher/consumer, Payment, Notification, Saga, outbox và kiểm thử tải chưa được triển khai trong ngày 1. Constraint UNIQUE trong schema không đồng nghĩa đã có API idempotent.
