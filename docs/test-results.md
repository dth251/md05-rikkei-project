# Kết quả kiểm thử hạ tầng và Auth

Phạm vi thực hiện: service 0 (hạ tầng), service 1 (Auth), CI/CD. Thời gian kiểm chứng local: 2026-10-11, múi giờ Asia/Bangkok (UTC+07:00).

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

Artifact local: `auth-service/build/reports/tests/test/index.html`, `auth-service/build/reports/tests/liveTest/index.html` và XML trong `auth-service/build/test-results/` (không commit build output). CI lưu cùng loại reports vào artifact `auth-java-test-reports`.

## GitHub Actions và VPS

Run đầu tiên: [03-devops: add auth ci and selective vps deployment](https://github.com/dth251/md05-rikkei-project/actions/runs/38130511299), SHA `4139c73ad156772c94fd6db229812c08c50375b6`. Trạng thái CI được đối chiếu trực tiếp và cập nhật trong [nhật ký commit/deploy](commits-and-deployments.md).

[Run trên mốc 04-devops](https://github.com/dth251/md05-rikkei-project/actions/runs/38130745804), SHA `c694454e8687d608cecc78f6d6257eb671c18462`: verify và GHCR publish đều success; deploy skipped. [PR verify](https://github.com/dth251/md05-rikkei-project/actions/runs/38130766520) trên cùng head cũng success. Bộ kiểm thử Java với PostgreSQL thực đã chạy trên runner Ubuntu, không chỉ local Windows.

## Kiểm chứng VPS và domain

Vướng mắc SSH ban đầu đã giải quyết: public key máy vận hành được cấp quyền cho root; khóa CI riêng được cấp cho user `deploy`. Cả hai đăng nhập bằng khóa đã được kiểm chứng. `.env` VPS có owner `deploy`, mode `600`; mật khẩu root cũ đã được thay sau khi xác minh SSH bằng khóa.

[Auth CI/CD deploy run](https://github.com/dth251/md05-rikkei-project/actions/runs/38132486801), SHA `5f441d2c5c85de10d0d68dcbf02a38952937c6d7`, kết thúc 2026-10-11 10:43:21 UTC: verify, publish và deploy đều success. VPS chạy PostgreSQL, RabbitMQ và Auth; health/smoke nội bộ đạt.

Kiểm tra từ máy vận hành lúc 2026-10-11 10:44:29 UTC: [trang gốc HTTPS](https://md05-rikkei-project.duckdns.org/) chuyển tới health và trả `{"status":"UP"}`. Nginx/Certbot đã cấu hình; chứng chỉ Let's Encrypt hết hạn 2027-01-09, `certbot.timer` active. Smoke qua HTTPS đạt register 201, login/me 200, thiếu token 401, USER gọi admin 403 và đăng ký trùng 409.

DB/MQ không publish port; Auth chỉ bind loopback 8081 và public qua Nginx 80/443. 5 database có owner riêng và kiểm tra CONNECT isolation/durable topology đạt. Nhật ký raw không chứa credential được giữ local ở `.verification/vps-verification.log`; bằng chứng tóm tắt và liên kết CI nằm trong [commits-and-deployments.md](commits-and-deployments.md).
