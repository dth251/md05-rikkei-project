# Kết quả kiểm thử ngày 1

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

VPS `42.96.2.60` chưa deploy: SSH bootstrap trả `Permission denied (publickey,password)` với khóa máy hiện tại. Actions variables/secrets đã cấu hình; `DEPLOY_ENABLED=false` cho đến khi kiểm chứng SSH user deploy và `.env` VPS. Chưa xác nhận API online hoặc HTTPS. Không coi local tests hoặc GHCR publish là bằng chứng VPS đã chạy.
