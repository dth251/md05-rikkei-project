# Nhật ký commit, CI/CD và deploy

Phạm vi hiện tại theo kế hoạch mới: hạ tầng, Auth, Gateway, Product CRUD và Order skeleton, kèm kiểm thử/CI/CD/VPS. Format mốc: `NN-ten-service: noi dung thay doi`, số tăng dần theo lịch sử thực tế. Không giới hạn số lần commit; hoàn thành và kiểm thử phần công việc thì commit. Các phần bên dưới ghi trạng thái tại thời điểm từng mốc, không thay thế trạng thái hiện tại ở cuối tài liệu.

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

## Trạng thái VPS tại mốc Auth ban đầu

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
- Tệp kiểm chứng tạm đã được chuyển ra ngoài dự án. Khóa truy cập và bản dự phòng mật khẩu root được lưu riêng với quyền truy cập hạn chế; không commit `.env`, private key hoặc bản dự phòng mật khẩu root.

## Các mốc kế hoạch mới có Gateway

Thời gian commit theo Asia/Bangkok (+07:00); SHA lấy từ Git thực tế. Trạng thái trong bảng là tại mốc đó.

| Mốc | SHA | Thời gian 2026-10-11 | Kiểm chứng / deploy |
|---|---|---|---|
| `09-docs: record vps ssh ci and https deployment evidence` | `e733376eef9c70e6153a9d793e625b19824456c2` | 17:49:05 | Đối chiếu Auth CI/VPS/domain; không thay image |
| `10-docs: integrate verified vps and https deployment record` | `9aa11c4d9ed0212ab0d63d58b40052070b472105` | 17:51:06 | Merge PR tài liệu; Auth vẫn SHA 08 |
| `11-api-gateway: initialize gradle webflux routes and health` | `eb33bcd13f4121263b23db451a6014907e633133` | 18:11:22 | Gradle/context/routes đạt; chưa VPS Gateway |
| `12-api-gateway: verify auth jwt and enforce redis rate limits` | `0d2222a945c25c4cc9434e808dc3cc5c45d42e22` | 18:20:04 | 13 Java unit + 1 live test Docker; Redis down trả 503 |
| `13-devops: deploy services independently and prepare gateway https routing` | `422f89b6ff203806fe99a115d812ebd67521dd98` | 18:22:27 | actionlint/Bash/Compose đạt; PR live test ban đầu lỗi PostgreSQL port |
| `14-devops: preserve postgres loopback port during live integration tests` | `facfbf32d1668b4f161162cb504f771241247439` | 18:28:19 | Sửa khởi chạy app không recreate PostgreSQL; PR CI đạt |
| `15-api-gateway: integrate jwt routing redis limits and selective deployment` | `6a9752c4bc6a157717661cc261a2c3ff060f9b44` | 18:32:06 | Merge PR #3; CI deploy Auth/Gateway + Redis đạt |
| `16-product-service: implement catalog crud admin roles and postgres tests` | `a0ba05dac8f22f03ff052190776730da21a90cad` | 18:32:31 | 8 Java unit + 1 live; 6 request SKU trùng: 1 thành công/5 conflict |
| `17-product-service: integrate catalog crud admin security and database ownership` | `4f97c29fae14edda496dfabc388e4d1ca16927aa` | 18:37:24 | Merge PR #4; CI deploy Product/Auth/Gateway đạt |
| `18-order-service: add order inventory schema and isolated postgres tests` | `22ca35c3307471ce383b2e9b19b7a39f2e2a941d` | 18:38:49 | 5 Java unit + 1 live: DB riêng, constraint, JWT route skeleton |
| `19-order-service: integrate order inventory skeleton and java live checks` | `63dd5be22f455d77af1f673fda41cb116ea2e3a3` | 18:46:33 | Merge PR #5 sau CI đạt; rollout được đối chiếu ở phần trạng thái cuối |
| `20-api-gateway: reject redis limiter fallback and standardize gateway errors` | `fb9fff578e8c9395bf41367f6b45a97227b8053a` | 18:46:54 | 15 Java unit + 1 live; hai test mới cho Redis Lua failure/quota |
| `21-api-gateway: reject redis limiter fallback and standardize gateway errors` | `867e87b010698c77dad07ef4ea1e745384ec24c2` | 18:51:12 | Merge PR #6 sau CI đạt; chỉ Gateway được chọn publish/deploy |

## CI kế hoạch mới đã đối chiếu

| Run | SHA / mốc | UTC 2026-10-11 | Kết quả thực tế |
|---|---|---|---|
| [PR Gateway ban đầu](https://github.com/dth251/md05-rikkei-project/actions/runs/38135335805) | `422f89b`, 13 | 11:22:47–11:24:51 | Failure: Compose bỏ port PostgreSQL local; không deploy |
| [PR Gateway sau sửa](https://github.com/dth251/md05-rikkei-project/actions/runs/38135683515) | `facfbf3`, 14 | 11:28:26–11:31:12 | Verify success; publish/deploy skipped đúng chế độ PR |
| [Gateway main](https://github.com/dth251/md05-rikkei-project/actions/runs/38135918524) | `6a9752c`, 15 | 11:32:08–11:38:33 | Verify/publish/deploy success |
| [PR Product](https://github.com/dth251/md05-rikkei-project/actions/runs/38135948621) | `a0ba05d`, 16 | 11:32:38–11:35:53 | Verify success; publish/deploy skipped |
| [Product main](https://github.com/dth251/md05-rikkei-project/actions/runs/38136242187) | `4f97c29`, 17 | 11:37:27–11:46:23 | Verify/publish/deploy success |
| [PR Order](https://github.com/dth251/md05-rikkei-project/actions/runs/38136328782) | `22ca35c`, 18 | 11:38:57–11:42:43 | Verify success; publish/deploy skipped |
| [Order main](https://github.com/dth251/md05-rikkei-project/actions/runs/38136798372) | `63dd5be`, 19 | 11:46:35–11:55:40 | Verify/publish/deploy success; Auth/Gateway/Product/Order được chọn do thay đổi cấu hình dùng chung |
| [PR Gateway hardening](https://github.com/dth251/md05-rikkei-project/actions/runs/38136824938) | `fb9fff5`, 20 | 11:47:01–11:49:45 | Verify success; publish/deploy skipped |
| [Gateway hardening main](https://github.com/dth251/md05-rikkei-project/actions/runs/38137082597) | `867e87b`, 21 | 11:51:15–12:01:47 | Verify/publish/deploy success; chỉ Gateway được chọn |

Product Java live test qua HTTPS thật đạt sau rollout 17: ADMIN CRUD, USER 403, anonymous 401, validation 400, SKU trùng 409, 6 request cùng SKU đúng 1 response 201 và 5 response 409, soft-delete GET 404. Token Product live là fixture ký bằng secret của môi trường VPS, giữ trong process kiểm thử; Gateway live dùng token thật do Auth phát. Không ghi secret/token vào logs.

## Trạng thái VPS cuối ngày 1

Đối chiếu trực tiếp bằng SSH lúc 2026-10-11 12:33:55–12:34:07 UTC; cả `.image-tags` và image container xác nhận:

| Service | Image SHA thực tế | Trạng thái |
|---|---|---|
| Auth | `63dd5be22f455d77af1f673fda41cb116ea2e3a3` | Healthy, loopback 8081, health UP |
| Gateway | `867e87b010698c77dad07ef4ea1e745384ec24c2` | Healthy, loopback 8080, health UP |
| Product | `63dd5be22f455d77af1f673fda41cb116ea2e3a3` | Healthy, loopback 8082, health UP |
| Order skeleton | `63dd5be22f455d77af1f673fda41cb116ea2e3a3` | Healthy, loopback 8083, health UP |

`current` trỏ tới release Gateway SHA `867e87b010698c77dad07ef4ea1e745384ec24c2`; mỗi service vẫn giữ SHA riêng. PostgreSQL/RabbitMQ/Redis healthy và không publish port; `.env` mode 600, owner deploy. Chạy lại verifier trên VPS đạt: 5 database/user độc lập, durable topology và private ports.

HTTPS kiểm tra lúc 12:33:57 UTC: `/` tới `/gateway-status` trả UP và header `X-Gateway: api-gateway`; catalog 200, Order thiếu token 401, `/actuator/health` 404. Java `GatewayLiveTests` chạy lại qua domain trên image Gateway cuối đạt: register/login/me, JWT, 401/403, quota 429 và header IP giả không vượt giới hạn. Order là skeleton; không ghi nhận API đặt hàng hoặc Saga đã hoàn thành.

Tài liệu hoàn tất ngày 1 được chuẩn bị trên `feature/day1-completion-docs`; mốc tài liệu không thay đổi image đang chạy. SHA commit và kết quả PR được ghi trong mô tả PR sau khi commit thực tế tồn tại.
