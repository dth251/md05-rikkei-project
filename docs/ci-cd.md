# CI/CD ngày 1

Workflow: `.github/workflows/ci.yml` — `Day 1 Auth CI/CD`.

## Quy tắc push và ghi chú

- Commit giữ format `NN-ten-service: noi dung thay doi`, theo số tăng dần. CI/CD thuộc `devops`; tài liệu thuộc `docs`. Ví dụ: `03-devops: add auth ci and selective vps deployment`.
- Phạm vi ngày 1 đã được điều chỉnh theo yêu cầu: hạ tầng, Auth và CI/CD. Mốc CI/CD của kế hoạch gốc được làm sớm; số commit hiện tại chạy liên tiếp từ `00`, không ghi các service chưa triển khai là hoàn thành.
- Build, kiểm thử và health local đạt trước khi commit/push. Feature branch được tích hợp vào `main` bằng PR sau khi CI đạt; không force-push.
- Tên run của push lấy từ commit; tiêu đề PR cũng giữ format trên. Job Summary ghi mốc, SHA thực sự kiểm thử, thời gian UTC, kết quả từng bước và image digest. Deploy chỉ ghi thành công sau health/smoke đạt.
- Nhật ký tại [commits-and-deployments.md](commits-and-deployments.md) ghi riêng commit/push, CI/publish và VPS, kèm SHA, thời gian và liên kết log. `skipped` hoặc chưa có SSH không được ghi `deployed`.
- Không đưa mật khẩu, token JWT, private key hoặc `.env` vào commit, PR, summary và log.

1. Build Java 21, chạy 11 bài kiểm thử Auth với H2 và tạo `app.jar`.
2. Khởi chạy PostgreSQL/RabbitMQ, kiểm tra 5 database/user và durable topology.
3. Đóng gói Auth container từ artifact đã build, chạy bài kiểm thử Java với PostgreSQL thực: API, RBAC, email trùng đồng thời, BCrypt và restart.
4. Upload Java test reports/artifact. Chỉ push hoặc manual run mới publish image GHCR; Pull Request chỉ kiểm thử.
5. Image gắn SHA commit: `ghcr.io/dth251/md05-rikkei-project/auth-service:<40-character-SHA>`.
6. Push vào `main` deploy Auth nếu `DEPLOY_ENABLED=true`. Các service tương lai không bị build/deploy trong ngày 1.

| Loại | Tên | Nội dung |
|---|---|---|
| Variable | VPS_HOST | 42.96.2.60 |
| Variable | VPS_USER | deploy |
| Variable | DEPLOY_PATH | /opt/omnichannel-ecommerce |
| Variable | DOMAIN | md05-rikkei-project.duckdns.org |
| Variable | DEPLOY_ENABLED | Chỉ bật `true` sau khi user deploy SSH được và VPS đã có `.env` |
| Secret | VPS_SSH_KEY | SSH key riêng cho CI, không dùng mật khẩu root |
| Secret | VPS_KNOWN_HOSTS | Khóa nhận diện VPS đã được kiểm tra |

Publish/pull image sử dụng `GITHUB_TOKEN`, không yêu cầu PAT registry riêng. Cấu hình Docker login trên VPS dùng thư mục credential tạm, được dọn sau khi job kết thúc.

Deploy chuyển cấu hình vào `/opt/omnichannel-ecommerce/releases/<SHA>`, dùng `.env` riêng bên ngoài release. Bind mount hạ tầng nằm ở đường dẫn cố định để deployment Auth không làm đổi cấu hình container PostgreSQL/RabbitMQ chỉ vì tên thư mục release mới.

Chỉ Auth được recreate với `--no-deps --no-build`. Script khóa deploy bằng `flock`, pull image trước, đợi health rồi smoke test. Nếu thất bại và đã có bản thành công trước, script quay về image SHA cũ và vẫn báo job thất bại.

Không xóa volume hoặc chạy `docker compose down -v` trên VPS. Init SQL chỉ tạo database/user khi volume PostgreSQL còn mới. Hibernate `ddl-auto=update` là lựa chọn demo; rollback image không tự rollback schema.
