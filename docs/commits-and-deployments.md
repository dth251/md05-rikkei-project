# Nhật ký commit, CI/CD và deploy

Phạm vi theo yêu cầu hiện tại: hạ tầng + Auth + CI/CD. Format mốc: `NN-ten-service: noi dung thay doi`; số tăng dần theo thứ tự thực hiện. CI/CD được chuyển lên ngày 1 theo yêu cầu, nên thứ tự các mốc sau Auth được điều chỉnh so với kế hoạch 3 ngày gốc.

## Các mốc đã push

Thời gian trong bảng là giờ Asia/Bangkok (UTC+07:00), lấy từ Git commit.
Trạng thái VPS ở mỗi hàng là trạng thái tại thời điểm mốc đó; trạng thái hiện tại được ghi riêng bên dưới.

| Mốc / ghi chú push | SHA | Thời gian | Kiểm chứng trước push | VPS |
|---|---|---|---|---|
| `00-infrastructure: initialize postgres rabbitmq and deployment layout` | `2efa1b557af0c93cd091ac6c5ae0a7bbaf108b9d` | 2026-10-11 16:16:08 | 5 DB/user, isolation, durable queues/bindings, private ports đạt | Chưa deploy: thiếu quyền SSH |
| `01-auth-service: initialize gradle application and health endpoint` | `5a689272b7dfd20c25995c40dfcad1c381626af9` | 2026-10-11 16:16:59 | Gradle build/context và health skeleton đạt | Chưa deploy: thiếu quyền SSH |
| `02-auth-service: implement registration login jwt and security` | `ef593b80331afd33a4918c54ead7298b371918b6` | 2026-10-11 16:56:27 | 11 Java tests + 1 live test PostgreSQL/restart đạt; Auth healthy | Chưa deploy: thiếu quyền SSH |
| `03-devops: add auth ci and selective vps deployment` | `4139c73ad156772c94fd6db229812c08c50375b6` | 2026-10-11 17:02:10 | actionlint, Bash syntax, production Compose và hạ tầng đạt; Python vận hành đã thay bằng Shell/PowerShell | Chưa deploy: `DEPLOY_ENABLED=false` |
| `04-devops: record ci results by numbered push milestone` | `c694454e8687d608cecc78f6d6257eb671c18462` | 2026-10-11 17:06:07 | actionlint và kiểm tra staged diff/secret đạt; Job Summary theo mốc push | Chưa deploy: `DEPLOY_ENABLED=false` |
| `05-docs: record passing auth ci and pending vps access` | `9ba23abfbd4a1d89ea944df3b0c90496be8d0d85` | 2026-10-11 17:09:29 | Đối chiếu kết quả CI/GHCR thực tế | Chưa deploy: thiếu quyền SSH |
| `06-devops: name workflow auth ci cd without day label` | `822f41b3f3a69c96f56aa0e1bea46c19d145215d` | 2026-10-11 17:11:03 | actionlint đạt; workflow hiển thị `Auth CI/CD` | Chưa deploy: thiếu quyền SSH |
| `07-infrastructure: configure auth domain nginx and https setup` | `6533449ca78d93e31b19e9a57f3686c78f11fc6e` | 2026-10-11 17:17:54 | `bash -n`, `nginx -t` đạt; proxy/redirect health local đạt | Chưa deploy: thiếu quyền SSH |
| `08-devops: integrate auth ci cd and domain deployment setup` | `5f441d2c5c85de10d0d68dcbf02a38952937c6d7` | 2026-10-11 17:21:00 | [PR #1](https://github.com/dth251/md05-rikkei-project/pull/1) đã merge sau checks trên head cuối đạt | Run push đầu tiên bỏ qua deploy; sau đó chạy lại khi SSH đã sẵn sàng |

## CI/CD thực tế

- [Run #1](https://github.com/dth251/md05-rikkei-project/actions/runs/38130511299): push `03-devops`, SHA `4139c73ad156772c94fd6db229812c08c50375b6`, bắt đầu 2026-10-11 10:02:18 UTC. Kết quả: `verify=success`, `publish=success`, `deploy=skipped`. Image: `ghcr.io/dth251/md05-rikkei-project/auth-service:4139c73ad156772c94fd6db229812c08c50375b6`.
- [Run #2](https://github.com/dth251/md05-rikkei-project/actions/runs/38130745804): push `04-devops`, SHA `c694454e8687d608cecc78f6d6257eb671c18462`, 2026-10-11 10:06:11–10:08:38 UTC. Kết quả: `verify=success`, `publish=success`, `deploy=skipped`. Image: `ghcr.io/dth251/md05-rikkei-project/auth-service:c694454e8687d608cecc78f6d6257eb671c18462`.
- [Run #3](https://github.com/dth251/md05-rikkei-project/actions/runs/38130766520): PR trên head `c694454e8687d608cecc78f6d6257eb671c18462`, `verify=success`; publish/deploy được bỏ qua đúng chế độ PR.
- [PR #1](https://github.com/dth251/md05-rikkei-project/pull/1): tích hợp Auth và CI/CD vào `main` sau khi checks trên head cuối đạt.
- Job `verify`: Gradle tests/artifact, 5 database/user, RabbitMQ topology, Auth live Java tests và restart.
- Job `publish`: chỉ sau verify đạt; image dùng SHA đầy đủ, không dùng `latest`.
- Job `deploy`: chỉ chạy trên `main` khi `DEPLOY_ENABLED=true`; health/smoke thành công mới ghi deployed. Nhật ký Job Summary chứa SHA, thời gian UTC và kết quả thực tế.

## Trạng thái VPS

Vướng mắc SSH đã được giải quyết lúc 2026-10-11 10:29:27 UTC: public key máy vận hành được thêm vào `root` và xác minh đăng nhập bằng khóa thành công. Khóa CI riêng được cài cho user `deploy`; đã xác minh SSH và quyền sử dụng Docker. Không ghi private key/mật khẩu vào Git hoặc logs.

VPS Ubuntu 22.04, khoảng 1 GB RAM. Đã cài Docker Engine 29.9.0, Docker Compose 5.6.0, Nginx, Certbot; thêm 2 GB swap và giữ lại dữ liệu có sẵn. `.env` mới được tạo riêng trên VPS, owner `deploy`, mode `600`; mật khẩu DB/MQ và JWT secret sinh ngẫu nhiên. Mật khẩu root cũ được đổi sau khi SSH bằng khóa được kiểm chứng; bản dự phòng lưu riêng với quyền hạn chế, không nằm trong Git.

`DEPLOY_ENABLED=true`. [Auth CI/CD deploy run](https://github.com/dth251/md05-rikkei-project/actions/runs/38132486801) trên SHA `5f441d2c5c85de10d0d68dcbf02a38952937c6d7` được dispatch sau khi xác minh SSH và `.env`: verify/publish/deploy đều `success`, thời gian 2026-10-11 10:35:21–10:43:21 UTC.

## 08-devops: verified Auth VPS and HTTPS deployment

- Image được triển khai: `ghcr.io/dth251/md05-rikkei-project/auth-service:5f441d2c5c85de10d0d68dcbf02a38952937c6d7`. `.current-image-tag` trên VPS xác nhận cùng SHA; release nằm tại `/opt/omnichannel-ecommerce/releases/<SHA>` và `current` trỏ tới release thành công.
- Digest trên VPS: `sha256:324710e0401fb6a378d90215978a52c9daa73f716e1d7c3dc734f0a9d579ae64`, đối chiếu lúc 2026-10-11 10:45:52 UTC.
- PostgreSQL, RabbitMQ và Auth chạy healthy. DB/MQ không publish port; Auth bind `127.0.0.1:8081` và được Nginx proxy. 5 database/user sở hữu riêng, CONNECT isolation và durable queues/bindings đạt.
- Nginx config test đạt. Certbot cấp và cài chứng chỉ thành công cho `md05-rikkei-project.duckdns.org`, hạn 2027-01-09; `certbot.timer` active.
- Kiểm tra HTTPS từ máy vận hành lúc 2026-10-11 10:44:29 UTC: [trang gốc](https://md05-rikkei-project.duckdns.org/) chuyển tới `/actuator/health`, trả `{"status":"UP"}`. HTTP redirect sang HTTPS.
- Smoke HTTPS: health/register/login/me đạt, missing token 401, USER gọi admin 403, duplicate email 409. Không ghi password/JWT vào log.
- Minh chứng raw local: `.verification/vps-verification.log` (được ignore); không commit `.env`, private key hoặc bản dự phòng mật khẩu root.
