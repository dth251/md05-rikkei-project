# Nhật ký commit, CI/CD và deploy

Phạm vi hiện tại gồm ngày 2: Order WEB/POS/reservation/idempotency, RabbitMQ/Outbox, Payment mock, Saga và Notification log, kèm kiểm thử/CI/CD/VPS. Format mốc: `NN-ten-service: noi dung thay doi`, số tăng dần theo lịch sử thực tế. Không giới hạn số lần commit; hoàn thành và kiểm thử phần công việc thì commit. Các phần bên dưới ghi trạng thái tại thời điểm từng mốc, không thay thế trạng thái hiện tại ở cuối tài liệu.

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

## Các mốc ngày 2

Thời gian commit 2026-10-11, Asia/Bangkok (+07:00). Các mốc develop và merge được cấp số khi commit thực tế tồn tại; số trên các nhánh có thể xen kẽ do PR/CI đang chạy. Không đổi hoặc force-push lịch sử.

| Mốc | SHA | Thời gian +07:00 | Kiểm chứng / trạng thái tại mốc |
|---|---|---|---|
| `22-docs: record day one service tests and verified vps deployment` | `2de3c449b2d02fad78929b038e545ebea0147ab4` | 19:36:16 | Docs links/diff/secret, VPS/domain đạt; không đổi image |
| `23-docs: integrate verified day one completion and deployment record` | `f98c5f9ca6a4a719d52f637b484cf527b6d69002` | 19:41:24 | [PR #7](https://github.com/dth251/md05-rikkei-project/pull/7) sau CI success; docs không deploy |
| `24-order-service: implement atomic web pos orders and request idempotency` | `fb3b6fb14d1624446a4e7f1a65991edf0d763415` | 19:52:52 | 10 unit + 4 live đạt; 100 request/1 stock và concurrent replay; chưa VPS |
| `25-order-service: publish durable order events through transactional outbox` | `f71f21dda709f77b25d0db3adacb4efd2d1caf6b` | 19:59:59 | 13 unit + 5 live đạt; broker failure/recovery, ACK/NACK/returns; chưa VPS |
| `26-order-service: integrate atomic orders and durable outbox after verification` | `805d791e01c678eb47d9e047617cc3e01d73c3c7` | 20:06:20 | [PR #8](https://github.com/dth251/md05-rikkei-project/pull/8) sau CI; main rollout đạt và SSH health lúc 13:17:09 UTC |
| `27-infrastructure: preserve rabbitmq node identity across container recreation` | `a06da8a21f56dc5757857e921ecedd639c14be52` | 20:13:55 | Durable probe sống qua recreate; giữ hostname node cũ trong .env local/VPS; chưa rollout sửa Compose |
| `28-payment-service: consume orders simulate payments and publish idempotent outcomes` | `971cb239b905607d0e2be5c550045277ce0dc9bd` | 20:17:11 | 7 unit + 3 live đạt; own DB, duplicate, outcomes/Outbox; chưa VPS Payment |
| `29-payment-service: disable unused default credentials in application logs` | `7e5349d6133c1e53bf917da6d5a9ab4745f71a6a` | 20:25:27 | 7 unit/build chạy lại đạt, current reports không còn generated credential; chưa VPS Payment |
| `30-order-service: consume payment outcomes and compensate reserved inventory once` | `bea24d243aa221a61551c0d696579813a20bf0a3` | 20:30:20 | Order 18 unit + 8 live đạt, stock math/duplicate/late results/Payment recovery; chưa VPS Saga |
| `31-payment-service: integrate verified mock payments and durable broker identity` | `ae93d718a5c2a42da27fe245f550b6d2ffc5bfed` | 20:30:49 | [PR #9](https://github.com/dth251/md05-rikkei-project/pull/9) sau head cuối CI đạt; rollout đối chiếu riêng bên dưới |
| `32-notification-service: persist final order events once and verify saga recovery` | `178669cd946c89fc2d5a55924e6132e764999341` | 20:35:34 | 4 unit + 4 live đạt, Auth token E2E/duplicate/consumer recovery; chưa VPS Notification |
| `33-order-service: integrate verified saga outcomes and inventory compensation` | `150cbf912f0a22865de0a94790bc4f28c3c52b2b` | 20:36:48 | [PR #10](https://github.com/dth251/md05-rikkei-project/pull/10) sau CI trên main có Payment; rollout đối chiếu riêng bên dưới |
| `34-notification-service: integrate verified notification consumer and complete day two services` | `2f8bb9446582ea3788222b3112c8b655cea331c2` | 20:46:38 | [PR #11](https://github.com/dth251/md05-rikkei-project/pull/11) sau CI success; rollout đối chiếu riêng bên dưới |
| `35-docs: document day two api contracts test results and six service operations` | `06973be3e8d5572d40ebb876a29a70682f74a863` | 20:50:49 | Liên kết Markdown, diff/secret checks đạt; XML local xác nhận 63 unit + 18 live; [PR #12](https://github.com/dth251/md05-rikkei-project/pull/12); không đổi image VPS |
| `36-notification-service: allow clock margin in vps admin test fixture` | `62c8a955c7d2b91070e024e8519a8dd78585345c` | 21:18:17 | Ba Java E2E HTTPS đạt sau backdate ADMIN fixture 5 giây; một recovery test skip có chủ ý; chỉ đổi test, không đổi xác thực/nghiệp vụ |

## CI ngày 2

| Run | SHA / mốc | UTC 2026-10-11 | Kết quả |
|---|---|---|---|
| [Order API push](https://github.com/dth251/md05-rikkei-project/actions/runs/38140962975) | `fb3b6fb`, 24 | Kết thúc 12:57:27 | Verify/publish success, deploy skipped trên feature |
| [Order PR](https://github.com/dth251/md05-rikkei-project/actions/runs/38141450051) | `f71f21d`, 25 | Kết thúc 13:04:36 | Verify success; publish/deploy skipped |
| [Order API/Outbox main](https://github.com/dth251/md05-rikkei-project/actions/runs/38141855301) | `805d791`, 26 | 13:06:23–13:15:54 | Verify/publish/deploy success; bốn service được chọn do Compose/workflow |
| [Hostname push](https://github.com/dth251/md05-rikkei-project/actions/runs/38142353335) | `a06da8a`, 27 | 13:14:00–13:18:37 | Success; deploy skipped trên feature |
| [Payment PR ban đầu](https://github.com/dth251/md05-rikkei-project/actions/runs/38142595515) | `971cb23`, 28 | Kết thúc 13:22:01 | Verify success; publish/deploy skipped |
| [Payment PR head cuối](https://github.com/dth251/md05-rikkei-project/actions/runs/38143119704) | `7e5349d`, 29 | Kết thúc 13:29:59 | Verify success; publish/deploy skipped |
| [Saga PR](https://github.com/dth251/md05-rikkei-project/actions/runs/38143477475) | `bea24d2`, 30 | Kết thúc 13:35:43 | Verify success; publish/deploy skipped |
| [Payment main](https://github.com/dth251/md05-rikkei-project/actions/runs/38143475079) | `ae93d71`, 31 | 13:30:52–13:43:16 | Verify/publish/deploy success; SSH lúc 13:46:40 UTC xác nhận năm app healthy đúng SHA |
| [Notification PR](https://github.com/dth251/md05-rikkei-project/actions/runs/38143878409) | `178669c`, 32 | 13:36:54–13:43:36 | Verify success; publish/deploy skipped |
| [Saga main](https://github.com/dth251/md05-rikkei-project/actions/runs/38143875230) | `150cbf9`, 33 | 13:36:51–13:55:38 | Verify/publish/deploy success; năm app được chọn, Order có consumer compensation |
| [Docs PR head 35](https://github.com/dth251/md05-rikkei-project/actions/runs/38144830568) | `06973be`, 35 | 13:51:17–13:57:38 | Verify success với sáu app; publish/deploy skipped |
| [Sáu app main](https://github.com/dth251/md05-rikkei-project/actions/runs/38144528920) | `2f8bb94`, 34 | 13:46:41–14:10:54 | Verify/publish/deploy success; gồm thời gian chờ main 33; cả sáu app được chọn |

Chi tiết các test, lỗi hostname đã phát hiện/sửa và giới hạn: [ngày 2](day2-test-results.md). Runbook/API: [operations](day2-operations.md), [contract](api-contract.md). Không ghi secret/fixture JWT vào nhật ký; SSH/DB kiểm chứng không mở public ports.

## Checkpoint VPS ngày 2, trước merge PR tài liệu/fixture #12

SSH lúc 2026-10-11 14:12:47 UTC xác nhận `.image-tags` và image thực của **cả sáu app** cùng SHA `2f8bb9446582ea3788222b3112c8b655cea331c2`. `current` trỏ tới đúng release SHA này; `.env` owner deploy/mode 600.

| App | Loopback port | Container / health |
|---|---:|---|
| Gateway | 8080 | Healthy / UP |
| Auth | 8081 | Healthy / UP |
| Product | 8082 | Healthy / UP |
| Order/Inventory | 8083 | Healthy / UP |
| Notification | 8084 | Healthy / UP |
| Payment | 8085 | Healthy / UP |

Verifier chạy lại đạt: năm DB/user, 20 hướng CONNECT chéo bị từ chối, ba durable queues/năm bindings, DB/MQ không publish port. Redis vẫn private. RabbitMQ giữ hostname node cũ `b5688ec24920`; không xóa volume/dữ liệu. HTTPS actuator trả 404.

Ba Java E2E qua `https://md05-rikkei-project.duckdns.org` đạt lúc 14:16:30–14:17:39 UTC: WEB SUCCESS → CONFIRMED, POS FAILED → CANCELLED/hoàn kho, duplicate event ID và ID khác cùng order chỉ một log. USER token do Auth register/login/me cấp; ADMIN fixture ký trong process kiểm thử. DB/MQ kiểm chứng qua SSH tunnel loopback, sau đó đóng; recovery test skip có chủ ý, không stop service VPS.

Lần HTTPS đầu 14:12:18–khoảng 14:13:50 UTC có một bài đạt, hai bài 401 tại ADMIN nhập kho và một bài skip. Fixture dùng iat ngay tại giờ client; Gateway/business services từ chối future iat. Backdate fixture năm giây ở mốc 36 rồi chạy lại đạt, không nới contract xác thực. Reports cả lần lỗi/lần đạt được lưu ngoài Git; không ghi lần lỗi là success.

Đối chiếu 14:18:17–14:18:23 UTC: Order/Payment pending Outbox đều 0; ba queue đều ready=0/unacknowledged=0/consumers=1; Notification có bốn logs, gồm một scenario đạt từ lần đầu và ba scenario chạy lại. Chưa thử k6/load hoặc forced rollback VPS.

SHA/time của mốc tài liệu cuối và merge, CI head cuối và rollout phát sinh sau khi sửa fixture được cập nhật trong [PR #12](https://github.com/dth251/md05-rikkei-project/pull/12). Bảng checkpoint trên ghi trạng thái đã kiểm chứng tại thời điểm cụ thể; không suy ra image bằng SHA main mới hơn.

## Ngày 3: mốc đã kiểm chứng

Ngày bắt đầu 2026-10-11, tiếp tục sau 00:00 ngày 2026-10-12 (+07:00). Số tiếp tục từ Git thật. Kết quả và giới hạn tải: [day3-test-results.md](day3-test-results.md).

| Mốc | SHA | Thời gian +07:00 | Kiểm chứng / deploy tại checkpoint |
|---|---|---|---|
| 39-infrastructure | `9981dfb29457eae8a6fbd84e7a757e7e7490b9da` | 2026-10-11 23:27:19 | Bash/diff, UFW, real forced smoke rollback VPS đạt; image/current/manifest khôi phục |
| 40-auth-service | `a16b7d3ed78a853744d6cfe0d98c8b1d03213b48` | 23:30:27 | 12 unit/build đạt, private Prometheus + sensitive endpoint protection; chưa metrics VPS |
| 41-product-service | `88ec1d793cb49b6adaf4bab51b1849b0359780cd` | 23:34:11 | 9 unit/build đạt sau sửa matcher; chưa metrics VPS |
| 42-payment-service | `790654be58695e2b7bd4b6330a9f4a72ea966bfd` | 23:34:51 | 8 unit/build đạt; chưa metrics VPS |
| 43-notification-service | `05d3e5b00e80e7bb2a4aeb8003e4bd890e4f312c` | 23:34:52 | 5 unit/build đạt, internal metrics; chưa metrics VPS |
| 44-infrastructure | `6bca54e8d915a786eb5aa6c49b00fd57f90389fe` | 23:34:54 | [PR #13](https://github.com/dth251/md05-rikkei-project/pull/13) sau CI; [main deploy](https://github.com/dth251/md05-rikkei-project/actions/runs/38156063749) success, SSH 16:51 UTC sáu image đúng SHA/UP, pending Outbox/queues=0 |
| 45-order-service | `dbbae4e503358e6e3fcf420dc67e35b3a673eb49` | 23:39:46 | 19 unit/build đạt; chưa metrics VPS |
| 46-api-gateway | `10d7eadb78c61b9500a364022f1da71acce99fa7` | 23:42:28 | 16 unit/build + 3 live Product/Redis/JWT/quota đạt; actionlint đạt |
| 47-order-service | `dcff91a022f1790e3100c344877c68446e1b8746` | 23:47:42 | Java fixture prepare/SQL race/finish local đạt: 12 orders confirmed/reserved=0 |
| 48-infrastructure | `18ed2dcf9ecf6a1171ba18b3c4fa688e42289aee` | 23:52:53 | k6 race/order/quota, Newman 12 requests/16 assertions, resource guard stop, syntax/actionlint đạt; chưa tool main rollout |
| 49-api-gateway | `8ece1e0a5d2b0da16e83369facad1815803746f6` | 23:53:13 | [PR #14](https://github.com/dth251/md05-rikkei-project/pull/14) sau CI 69 unit/20 live đạt; metrics main rollout đang chờ tại checkpoint này |

Những mục “chưa/đang chờ” là trạng thái tại mốc, không phải khẳng định trạng thái cuối. SHA/time mốc docs/harness cuối, CI head cuối và deploy thực tế sau checkpoint được ghi trong PR tương ứng và phần hoàn tất bên dưới.

| Mốc | SHA | Thời gian +07:00 | Kiểm chứng / trạng thái thực tế |
|---|---|---|---|
| 50-docs | `787be39088655f0ac49202178707ced0f6ee636f` | 2026-10-11 23:56:49 | README/architecture/runbook/contract; SVG→PNG đã xem, link/diff kiểm tra; chưa merge tài liệu hoàn tất |
| 51-infrastructure | `894caa3f8804c8bde0f34cf91841e4f42d3349c1` | 23:58:47 | 9 selector cases + actionlint đạt; workflow_dispatch chọn full, first push dùng main merge base; PR #15 head CI đạt |
| 52-api-gateway | `12538403d9a7e3be4fecf4bef13177e43a883e64` | 2026-10-12 00:07:56 | Concurrent Java quota local/VPS 401/403/429 đạt sau sequential fixture không tạo đủ burst; PR #16 head CI đạt |
| 53-infrastructure | `fb348bd1bc12d208d8ddef5d3373fb235d7a2567` | 00:10:04 | PR #15 merge sau CI 69 unit/20 live; main deploy **Order only** success 17:19:47 UTC; SSH image/health đúng SHA |
| 54-api-gateway | `06340098eae04131a8e8da856f746f27109916d2` | 00:16:59 | PR #16 merge sau CI; main publish/deploy **Gateway only** success 17:31:10 UTC; sáu health/queues/Outbox audit sau rollout đạt |
| 55-infrastructure | `f5f6869653732a1814be27e35da50e88ea074b06` | 00:26:17 | Full status counters race/order; local k6 100 request 1/99 + SQL + finish đạt; VPS giảm 1/s 19×201/zero error counters, dropped 2 vẫn ghi hạn chế |
| 56-docs | `3c51bcbe44366a6cc8b9163080c54b7086205ba0` | 00:45:34 | Report/checklist cập nhật, 46 orders finalize/Notification một log mỗi order, PNG đã xem, video 63.24s/1440×900 decode; 251 text files secret scan và 59 file links đạt; đã push feature |

Main metrics 49 hoàn tất deploy sáu app 17:08:44 UTC. [Audit cuối](evidence/vps-final-audit.txt) 17:41:02–17:41:28 UTC xác nhận Gateway image 54, Order 53, Auth/Product/Payment/Notification 49; current release 54. Sáu health UP, metrics private có JVM/application tag, public metrics 404. Ba queue ready/unacked=0/consumer=1, hai pending Outbox=0; 46 fixture orders CONFIRMED/reserved=0 và 46 notification logs (mỗi order một).

[PR #14](https://github.com/dth251/md05-rikkei-project/pull/14), [#15](https://github.com/dth251/md05-rikkei-project/pull/15), [#16](https://github.com/dth251/md05-rikkei-project/pull/16) có SHA/time/CI/deploy thực tế cập nhật. Head/merge tài liệu và CI cuối ghi trong PR hoàn tất để tránh cần commit chỉ để ghi SHA của chính commit. Kết quả tải lỗi/stop/throttle được lưu nguyên trong [báo cáo](day3-test-results.md); không dùng trạng thái CI đạt làm bằng chứng đạt capacity.

## Khắc phục sau rà soát: Outbox

| Mốc | SHA | Thời gian +07:00 | Kiểm chứng / deploy tại checkpoint |
|---|---|---|---|
| 59-order-service | `c74b44e8a6bb76a72d08750fcb42416b4099d1a5` | 2026-10-12 01:42:51 | 21 unit/bootJar đạt, NACK không chặn 101 event; chưa VPS |
| 60-payment-service | `79ee731d572027374a89ffc8ce45121b36ad3ce9` | 01:43:07 | 13 unit/bootJar đạt; chưa VPS |
| 61-order-service | `3dc5d27ef8bc1ff8c8996098afd3299cac51a210` | 01:51:40 | PR #18 merge sau CI 38165003440: Java và live/recovery đạt; main CI/deploy 38165583249 đang chạy |

Trạng thái deploy cuối được cập nhật theo image/health thực tế và PR, không suy ra từ merge. Kế hoạch tiếp theo: [remediation-plan.md](remediation-plan.md).

## Khắc phục: nhận kho, DLQ và hết hạn reservation

| Mốc | SHA | Thời gian +07:00, 2026-10-12 | Kiểm chứng / deploy tại checkpoint |
|---|---|---|---|
| 62-order-service | `171f3f7470f49d895b7fd20e0ed5c5cc2c88db07` | 01:52:45 | Nhận kho idempotent, retry có giới hạn, công cụ DLQ; Order 25 unit và 10 live đạt; chưa VPS tại commit |
| 63-infrastructure | `27e1155386ff6de24e799fce62cec154aa8e0017` | 01:53:21 | DLQ và policy theo từng queue, Postman cập nhật receipt key; topology local đạt |
| 64-payment-service | `755608cf149b9490bbf88995e6eb5b191b02d848` | 01:53:26 | Payment 15 unit/bootJar đạt; chưa VPS tại commit |
| 65-notification-service | `b56d46a772eebda20e5bd340c34a4e1de6993e81` | 01:53:33 | Notification 7 unit/bootJar đạt; chưa VPS tại commit |
| 66-order-service | `ad2a84fc2603ad87d73c3c5b5e5d0cce1f9e81d9` | 02:02:17 | PR #19 CI 38165773294 đạt; main CI/publish/deploy 38166325281 success; SSH 19:25:24 UTC xác nhận sáu image 66 healthy |
| 67-order-service | `af1182b9ba7c301aef135442b0f3647d96aa0e70` | 02:10:42 | Order 28 unit/bootJar đạt; test live expiry đạt; chưa merge/deploy |
| 68-infrastructure | `5014c71f03830d847c72f4e1ae8c2b6189b25132` | 02:10:46 | SQL migration/checksum áp dụng và chạy lại local đạt; queue expiry được cấu hình; chưa VPS |
| 69-payment-service | `27200e8e1ddb62c77fd9427b83b78857d7017d96` | 02:12:13 | Payment 18 unit/bootJar đạt; PR #20 CI 38167069975 Java/live/recovery success; chưa merge/deploy |

Live Order local ở mốc expiry có 10/11 bài đạt: bài giới hạn nhận kho trả 503,
lần kiểm tra riêng đầu trả 504. Kiểm tra riêng tiếp theo đạt; giữ nguyên
assertion 409 và bổ sung body phản hồi để chẩn đoán lỗi trong lần chạy sau.
Lần chạy lại toàn bộ Order local đạt 11/11, không fail/error/skip, gồm kiểm tra
hết hạn, overflow, nhận kho đồng thời, broker outage và khôi phục Payment.
CI head 69 chạy toàn bộ Java/live/recovery đạt. Không ghi các lần local lỗi
thành success và không suy ra capacity từ CI.

Checkpoint IDE: sáu service biên dịch source/test đạt; nhập lại Gradle sau
backup cache riêng của workspace, loại project tạm và classpath cũ. Chi tiết
trong [java-ide.md](java-ide.md). SHA/time của commit cấu hình IDE và kết quả
CI sau commit được ghi trong PR #20; chưa có deploy mới tại checkpoint này.

| Mốc | SHA | Thời gian +07:00 | Kiểm chứng / deploy tại checkpoint |
|---|---|---|---|
| 70-infrastructure | `35c9bcb0d1354aa65560330aa4eacc77898bb904` | 2026-10-12 02:26:37 | Sáu compile source/test đạt; sáu Gradle project nhập lại, marker Java severity error bằng 0; chưa merge/deploy |
| 71-order-service | `bdaa9fec94229dd0e14e3dc2d4c87180fcc7ea9d` | 02:28:25 | Order 11/11 live đạt, zero fail/error/skip; thêm body vào assertion overflow để chẩn đoán; CI head 71 success |
| 72-order-service | `5fba35d89112d6a1c338eceb5f5b9554e7233dc8` | 02:41:28 | PR #20 merge sau CI 38168169242 toàn bộ Java/live/recovery đạt; main rollout đang chờ tại checkpoint |
| 73-infrastructure | `b7e8570099f9eeef1bf035c794ca61de721a0b11` | 02:50:37 | Worktree di chuyển ra ngoài thư mục IDE, settings watcher và JSON/diff kiểm tra đạt, sáu project thật và zero error markers; PR #21 CI đang chờ, không cần deploy app |

Phần Auth refresh/logout/địa chỉ tiếp theo: [auth-sessions-and-addresses.md](auth-sessions-and-addresses.md).

Rollout main 72: CI 38169090727 verify/publish/deploy success; SSH
2026-10-11 20:02:57 UTC xác nhận sáu image 72 healthy. PR #21 CI
38169782416 toàn bộ Java/live/recovery success; sửa IDE được tích hợp bằng
merge 76. Kiểm tra lại sau main 77: IDE biên dịch class Auth mới lúc
03:03:01–03:03:02, sáu project thật và zero Java error markers.

## Refresh/logout, địa chỉ và Gateway

| Mốc | SHA | Thời gian +07:00, 2026-10-12 | Kiểm chứng / deploy thực tế |
|---|---|---|---|
| 74-auth-service | `481950a694826f9d2a8e471ceb5404226c520744` | 02:51:36 | 20 unit/bootJar đạt; live PostgreSQL rotation/replay, 12 concurrent refresh, address/default/ownership và restart đạt; chưa VPS tại commit |
| 75-api-gateway | `3ec12459fab3a5e2ad028ed29bddc00f3e9d2e91` | 02:51:39 | 18 unit/bootJar đạt; PR #22 CI 38169836536 Java/live/recovery success |
| 76-infrastructure | `4cf097455977a46f72ce7f859f58bd0b7bb68d11` | 03:02:49 | PR #21 merge sau CI; chỉ IDE/docs, không cần app rollout |
| 77-auth-service | `b18c9a437fc82b577522c030f18d8c8abca3a289` | 03:02:52 | PR #22 merge sau CI; main 38170555620 verify/publish/deploy success, Auth/Gateway only |

SSH 2026-10-11 20:15:50 UTC: Auth và Gateway image 77 healthy;
Product/Order/Payment/Notification image 72 healthy. HTTPS domain trả 400
cho malformed refresh credential và 401 cho address thiếu JWT. PR #21/#22
ghi SHA, thời gian và kết quả cuối. Logout thu hồi refresh family; JWT
stateless đã cấp tiếp tục dùng đến exp.

## Product search và variants

| Mốc | SHA | Thời gian +07:00, 2026-10-12 | Kiểm chứng / deploy tại checkpoint |
|---|---|---|---|
| 78-infrastructure | `f7f1d9f4d06606f63c8b8e803e54a1a6858de1ba` | 03:17:04 | SQL migration áp dụng/chạy lại trên local PostgreSQL đạt; giữ 65/65 sản phẩm cũ; chưa VPS |
| 79-product-service | `4ee23243bb7b00e0dbcdbe67c59c4b0483a97fa2` | 03:18:48 | 18 unit/bootJar và 3 live qua Gateway đạt, zero fail/error/skip; đã push feature, chưa merge/VPS |

Live lần đầu 2/3 đạt: assertion SKU của fixture dùng UUID chữ thường,
khác contract SKU luôn lưu chữ hoa. Sửa expected theo contract, thêm case
khoảng trắng Unicode/NFKC. Lần cuối 3/3 đạt: 20 request cùng tổ hợp chỉ một
biến thể, tìm `%` theo ký tự thường, mua biến thể qua Order/Payment, reserved
về 0 và snapshot SKU/giá vẫn giữ khi ẩn parent. Unit chạy lại sau thay đổi
chuẩn hóa đạt 18/18. CI head và rollout sau checkpoint ghi trong PR.

Product head 80 `a37a5bca3b071bdd072488e9ff305f4def26cbee`, 03:19:35:
PR #23 CI 38171736979 toàn bộ Java/live/recovery success. Merge
81-product-service `5862f4ce70056d4d83853353e0709e3121040385`, 03:29:36.
Main 38172436422 verify và publish sáu image đã đạt, deploy đang chạy tại
checkpoint Shipping. Không suy ra Product VPS đã cập nhật từ trạng thái publish.

## Shipping/Completed và notification stage

| Mốc | SHA | Thời gian +07:00, 2026-10-12 | Kiểm chứng / deploy tại checkpoint |
|---|---|---|---|
| 82-infrastructure | `561ea7be9cc878ab3c0905bd146cb53be5e3a867` | 03:38:24 | SQL apply/replay giữ 63/63 đơn cũ; topology/durable policy đạt, verifier local loopback đạt; chưa VPS |
| 83-order-service | `174802836ad8eb04c881d30282f19f003331be78` | 03:39:10 | 33 unit/bootJar đạt; fulfillment local qua Gateway/PostgreSQL đạt; chưa merge/VPS |
| 84-notification-service | `15a94efb1c5870a4d4a83d79acf9e86a39a69ea7` | 03:39:15 | 10 unit/bootJar, 5/5 live đạt gồm 20 request/stage, stage log dedup và restart recovery; chưa merge/VPS |

Order local chạy lại toàn bộ live sau source/test thay đổi: 11/11 đạt,
zero fail/error/skip, gồm expiry/stock race, broker outage và Payment recovery.
Notification 5/5 zero fail/error/skip. Verifier default chạy trên local có
port test từ chối đúng ở postgres; không ghi thành success. Chế độ explicit
`--allow-loopback` đạt, chỉ nhận loopback và giữ default không publish cho
CI/VPS. CI head cuối và rollout phát sinh sau checkpoint ghi trong PR.

Product main 81: CI 38172436422 verify/publish/deploy success, SSH
2026-10-11 20:47:45 UTC xác nhận sáu image 81 healthy. PR #23 đã cập nhật
kết quả cuối. Shipping PR #24 head 85 `5c3c928a896eefe7157868256330ff46e0b506aa`
(03:42:45) CI 38173359619 Java/live/recovery success. Merge
86 `c1133bbca3ed1d0c72db7238a5feea8cc7c0d6bc` (07:14:43).
Main 38187261497 verify/publish đủ sáu service success; deploy đang chạy
tại checkpoint RPC. SSH 2026-10-12 00:23:44 UTC vẫn sáu image 81 healthy;
không ghi rollout 86 thành công trước khi image/health được kiểm tra lại.

## Product–Order gRPC

| Mốc | SHA | Thời gian +07:00, 2026-10-12 | Kiểm chứng / deploy tại checkpoint |
|---|---|---|---|
| 87-product-service | `9f5bc62f7de7709b2b03e3d32071acf374390b23` | 07:20:10 | 21 unit/bootJar đạt, batch 50/authentication/retired; chưa VPS |
| 88-order-service | `7e9e76b8ee255e8c1916761e8fa7a6f65fbf81d0` | 07:20:20 | 39 unit/bootJar đạt, Netty wire/deadline/no fallback/invalid reply; chưa VPS |
| 89-infrastructure | `3d04ed08390334b045d06c033ce9ade432f5f0ae` | 07:24:26 | Compose/private env/contract/loopback guard đạt; local Product 4/4 live và Order 11/11 live RPC enabled đạt, zero fail/error/skip; chưa CI/VPS RPC |

Hai private env local/VPS đã được thêm random RPC token, không overwrite
secret khác và không commit. Gradle dependencyInsight xác nhận runtime
protobuf-java 3.25.9. Verifier loopback đạt trên topology thật; unpublished
container fixture với HostIp trống tạo giá trị non-loopback bị từ chối,
container fixture đã remove, không chạy/open port hay gắn volume.
Chi tiết API/giới hạn: [product-order-grpc.md](product-order-grpc.md).

## RPC recovery và readiness

Shipping main 86 CI 38187261497 success; SSH 2026-10-12 00:33:05 UTC
xác nhận sáu image 86 healthy. RPC PR #25 head 90 CI 38187929374
success, merge 91 `356fa9e16014cfe7c796324af71a00f8839b9ce8`
(07:33:51 +07). Main 91 CI 38188486613 thất bại ở Product live (503
nhận kho), chưa publish/deploy RPC. PR #26 head 98 CI 38189342703
success sau thêm kiểm thử recovery, merge 103
`37f6e7ba150ae1549bc723ee4d4d5436ede5e88b` (07:59:09 +07).
Main 103 CI 38190107701 verify/publish Product–Order/deploy success.

SSH 2026-10-12 01:09:30 UTC: Product/Order image 103 healthy;
Auth/Gateway/Payment/Notification vẫn image 86 healthy. Không ghi sáu
image 103. Luồng mua variant VPS lỗi 504 rồi 503 tại nhận kho dù health
UP; probe gRPC trực tiếp qua SSH đạt. Chạy lại cùng case bằng fixture
mới đạt lúc 08:44 +07: nhận kho, đặt hàng, capture mô phỏng, CONFIRMED,
reserved về 0 và snapshot giữ đúng sau ẩn parent. Không phải VNPay thật.

| Mốc | SHA | Thời gian +07:00, 2026-10-12 | Kiểm chứng / deploy tại checkpoint |
|---|---|---|---|
| 107-order-service | `a0f60bb1e3fcb6a866bf3b76f54691aebcc7b98b` | 08:47:35 | 42 unit/bootJar đạt; 4 Product live local với Order vừa recreate đạt; chưa VPS bản readiness |
| 108-product-service | `8e2d5b0da0ddd32de5977bdac0c5dfda327c7684` | 08:48:05 | task probe VPS riêng đạt 1 test, không ghi nghiệp vụ; không thay runtime Product |

Readiness không coi TCP/socket hoặc một probe thành công trong quá khứ
là đủ: phải có RPC có xác thực hiện tại. Không tăng deadline nghiệp vụ
hay thêm replay để che lỗi. Full Order live/recovery, CI exact head và
deploy bản readiness còn chờ kết quả tại checkpoint này.

## OpenAPI va checkpoints CI

| Moc | SHA | Thoi gian +07:00, 2026-10-12 | Kiem chung / deploy tai checkpoint |
|---|---|---|---|
| 92-auth-service | `b73601e6b000ebf17d96e60e5cf72bbb8e168b16` | 07:36:21 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 93-product-service | `34cfb62c8ac07e3fb5b2bcbc8ea572d896c3a76e` | 07:36:22 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 94-order-service | `530cda53c35d0026151b64ab120f55c9e65cf390` | 07:36:22 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 95-payment-service | `ee40c3b16951d94a94299ef971b50eb26c66525f` | 07:36:23 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 99-auth-service | `8312cc00f9fb399c64d6f00104d7c95b5ea3d0f1` | 07:51:52 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 100-product-service | `804d4ee4aad48b112d2a87c2a6018ea2bed5790f` | 07:51:53 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 101-order-service | `f0aec601e225ea0b595f620c8336ec550159640c` | 07:52:31 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 102-payment-service | `64423edbbd3047a6ac7aed66e8b2ae64df711aa4` | 07:54:38 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 104-auth-service | `e9fa69aa3fa7ba780fb4ad782a50396d8e7f9da9` | 08:07:41 | 1 live docs tren bon container dat sau schema fix; chua VPS |
| 105-infrastructure | `e4a8fa12e30806a442c5392acb2ce6a1287fb043` | 08:07:41 | Local memory 290/292/292/266 MiB; budget 320 MiB; export dat; chua VPS |
| 106-order-service | `731ef0780a1f61d8799e6855047360827a4930bf` | 08:10:09 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 110-auth-service | `7dc06e8c878b88b2cc2baf27889b39d1ccbc9b25` | 08:51:57 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 111-product-service | `bace07ad3ce8a0afb4c01c6ee9bf7568064c1d08` | 08:51:57 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 112-order-service | `a35c1b6ed0d32808f27b1b246ab5ef10bf437d53` | 08:51:58 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 113-payment-service | `3af628ea23f9cac66866aa4da36875bc6bf8a6e5` | 08:51:58 | OpenApiTests/bootJar local dat; chua merge/deploy OpenAPI tai commit |
| 114-infrastructure | `d268b849832f710b7e9d2c337af117563182adf2` | 08:52:18 | 20 paths/27 operations, 27 operation IDs duy nhat; export/CI artifact; chua VPS |
| 116-order-service | `598ed87d939f20108c724c95f3b9ab11482b5cba` | 08:56:23 | 43 Order unit/bootJar dat sau merge readiness; chua VPS OpenAPI |

Bốn OpenApiTests sau sửa references/format đều đạt. Auth/Product/Payment
baseline toàn bộ unit trước đó đạt 21/22/19; Order chạy toàn bộ lần cuối
sau merge readiness đạt 43/43, zero fail/error/skip. Live docs đọc bốn
container kiểm tra anonymous 401, USER 403, ADMIN 200 và đầy đủ schema.
Không ghi OpenAPI VPS đã hoàn tất từ các kết quả local.

Readiness PR #27 head 109 CI 38193360607 Java/live/recovery success.
Order local cuối 11/11 live gồm broker outage và Payment recovery đạt.
Merge 115 `37e8173df2863991fe20e2e7a160e3b5a132f813`, 08:56:08 +07.
Main CI 38193814101 đang kiểm thử tích hợp tại checkpoint, chưa có
image/health VPS của mốc 115. Nhánh OpenAPI đã merge origin/main qua
116 `598ed87d939f20108c724c95f3b9ab11482b5cba`, 08:56:23 +07.

## Tài liệu checklist và hướng dẫn Postman

| Mốc | SHA | Thời gian +07:00, 2026-10-12 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 130-docs | `e3289e590a937cd8edaa0f95624940c5fa72e0d2` | 10:02:25 | 55 checklist items, relative links/diff check đạt; PR #30 head 130 CI 38198221287 completed success; chưa merge/deploy từ PR này |
| 131-tests | `0baaa6984d048b620fb42bfdb23257f45249eac9` | 10:32:11 | Sửa hai body Order trong collection: bỏ key phiếu nhập kho khỏi item; JSON/21 scripts parse đạt, 19 requests/2 body Order khớp fields DTO; chưa chạy lại collection qua HTTP |

Hướng dẫn `postman-testing-guide.md` đối chiếu controller/DTO của main 125
và API VNPay trên feature riêng. Kiểm tra tài liệu lúc 10:40 +07: relative
links tồn tại, 6 JavaScript blocks/10 JSON examples/1 PowerShell block parse
đạt. Phân biệt hướng dẫn/expected với kết quả API đã thực thi; không ghi
Postman, load hoặc failure demo đã PASS từ các kiểm tra cú pháp này.

Không đổi runtime hoặc deploy trong các mốc tài liệu/collection trên. Bản
ứng dụng VPS kiểm chứng gần nhất vẫn image main 119; README đã xóa ở main
125. Nhánh VNPay head 129 CI 38196864016 completed success, chưa merge/deploy
VNPay. PR #30 cần kiểm tra lại ở head mới trước khi tích hợp các thay đổi tiếp.
## Checkpoint VNPay, OpenAPI VPS và xóa README

| Mốc | SHA | Thời gian +07:00, 2026-10-12 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 117-payment-service | `009a193c380bb70e6788891e5d16674541c6d9a3` | 08:56:35 | 6 signature tests gồm RFC vector/bootJar đạt; chưa deploy VNPay |
| 119-infrastructure | `b253c9528c764fc24dded222ee464dc4ba99b206` | 09:11:00 | PR #28 CI 38194092002 đạt; main CI 38194787693 success, deploy sáu service |
| 120-payment-service | `428752f84a31a8191e9b4c826072788bedb4c8c8` | 09:15:43 | 33 unit/bootJar, thêm 2 facade tests đạt riêng; checkout/IPN vẫn feature |
| 121-api-gateway | `9c594971f9f88fe2a76f7893d8b7b8b97201bc36` | 09:15:43 | 20 unit/bootJar, thêm 3 routing tests đạt riêng; chưa deploy VNPay route |
| 122-payment-service | `448ac52483096653f7a1dbef5982f4ebe52d92dd` | 09:16:44 | Merge main 119 vào feature; chưa deploy VNPay |
| 123-infrastructure | `0454c406a70aed28ef464985f6b5fedc7ea514bf` | 09:18:41 | Compose config/bâsh syntax đạt; provider mặc định disabled |
| 124-docs | `99ff0d1b49884cf9e67d374f25759b60b1c3c939` | 09:28:13 | Chỉ xóa README gốc, diff check đạt; PR #29 CI 38195920870 Java/live/recovery success |
| 125-docs | `f7c5e8b0dc411482e38edb64b8141c8531240217` | 09:36:44 | Merge PR #29 sau CI; origin/main không còn README.md; không chọn service deploy |
| 126-payment-service | `254e43ffdc1f7322d68a157d02ddca2a1af9ffed` | 09:40:13 | 42/42 unit zero fail/error/skip và bootJar đạt; HTTP fixture refund, chưa sandbox/PG live/CI/deploy VNPay |
| 127-payment-service | `8800ee1840752fd231d78b0ad49c871901ae2121` | 09:40:13 | Merge main 125; feature giữ việc xóa README; chưa deploy VNPay |
| 128-infrastructure | `e342b1572fce18b206a670915d4a3458b72cc047` | 09:41:05 | Compose config/syntax Bash/diff check đạt; server IP private cho ledger; chưa rollout |

Readiness main 115 CI 38193814101 success; SSH 02:06:24 UTC xác nhận
Product/Order image 115 healthy, bốn ứng dụng khác image 86. Smoke mua biến
thể thực qua HTTPS VPS sau rollout đạt 1/1, gồm stock/order/payment mô phỏng/
CONFIRMED/snapshot. Không ghi đây là giao dịch VNPay.

OpenAPI main 119 SSH 02:34:49 UTC: cả sáu image 119 healthy. Probe read-only
qua SSH 02:40:20 UTC: bốn service docs anonymous 401, USER 403, ADMIN 200,
title đúng và ApiError schema tồn tại; Auth/Product/Order/Payment có 8/3/6/3
paths. PR #28 đã cập nhật bằng chứng rollout cuối cùng.

Full Payment lần đầu sau refund có 42 tests/1 fail: checkout trả expiry theo
nanosecond khác giá trị reload database. Chuẩn hóa timestamp theo giây đúng
protocol; full chạy lại 09:38:44 +07 đạt 42/42, không bỏ test hoặc bỏ assert.
VNPay vẫn ở feature; không có cấu hình merchant/SMTP private hoặc giao dịch
thực với provider. Phần việc còn lại được ghi tại `remediation-plan.md`.

## Bổ sung remaining-work ngày 13/10/2026

Nhánh `feature/remaining-work-implementation`; chưa merge/deploy code mới ở checkpoint này.

| Mốc | SHA | Thời gian +07:00 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 133-payment-service | `e51c901f93469f88350efa1651857511c0fb1f56` | 14:42:00 | Kết hợp VNPay mốc 129, sửa fixture Gateway bằng port chưa dùng; Gateway 23 tests đạt; chưa deploy |
| 134-notification-service | `6e13a635b2587d10ed4996541ec478f7a2dd5aff` | 15:43:55 | Auth 23/Notification 16 unit, 3 PG/Mailpit live đạt; API recipient private, durable SMTP/retry/dedup; chưa SMTP ngoài/deploy |
| 135-payment-service | `32365474c8f1bf2d5a2e137a5106792e4c5fedd5` | 15:46:32 | Order 45/Payment 42 unit, 12 VNPay PG/Gateway/refund-restart live đạt; measured backlog/Outbox trace; chưa provider thật/deploy |
| 136-notification-service | `d6f8f877ebae379d2bf50b3bdf603e5916372495` | 16:36:04 | Notification 16 unit/bootJar và 3 SMTP PG live chạy lại đạt; trace lưu trong delivery, span riêng lúc gửi; chưa deploy |
| 137-infrastructure | `efc011cd8093cecd2255e7f1260a8b4e5dffcf37` | 16:36:50 | Compose/Bash/Collector/Alloy/Loki/Prometheus/Alertmanager config đạt; 11 rules hợp lệ/2 firing-resolved fixture đạt; local targets/history/Grafana restart, Tempo privacy và Docker audit Loki privacy đạt; chưa VPS deploy |
| 138-tests | `a91f3793f9a03a9d88ab4e2044277b279d8823cf` | 16:37:50 | Java multi-user fixture compile, PowerShell parser/actionlint đạt; bổ sung stage VNPay/SMTP/load/monitoring/tracing/privacy/exporter outage. Các stage mới cần chạy ở head CI; không ghi runtime đạt từ cú pháp |

Tổng unit sáu service: 171, zero fail/error. BootJar đạt. VNPay provider dùng HTTP
fixture công khai, SMTP dùng Mailpit private; chưa có merchant hoặc SMTP ngoài.
Trace privacy kiểm chứng bằng gửi fixture rồi đọc trace đã lưu từ Tempo, không chỉ validate
YAML. Logging lần đầu không tìm thấy audit; chuyển targets sang relabel output, giới hạn
phạm vi service và CPU/Go memory, chạy lại tìm được audit theo trace ID và marker bị loại.
Không chuyển raw log ứng dụng vào Loki.

Tải Windows lần đầu: 11 HTTP thành công, zero 429/transport/drop, warm-up p95 8.304 ms,
p99 8.509 ms, vượt threshold 2.000 ms. Harness dừng các pha sau; không coi load đạt.
Resource guard 512 MiB available trước pha, 256 MiB khi đang chạy, 4 GiB disk; không hạ
threshold để biến thất bại thành thành công. Chưa xác nhận mục tiêu 500–1.000 RPS.

VPS sau nâng cấp root disk: 40 GB, used 19 GB, available 19 GB (50%); RAM 4.926 MiB,
available khoảng 2.830 MiB. Sáu image vẫn `b253c9528c764fc24dded222ee464dc4ba99b206`,
health UP; HTTPS catalog 200. Đồng hồ sai +25.200 giây; đã sửa lúc 09:21:48 UTC bằng
HTTPS Date Google/Cloudflare, hai nguồn chênh 1 giây. Drop-in timesyncd IPv4 Cloudflare/
Google đã cấu hình; NTP vẫn timeout và `NTPSynchronized=no`. Không xóa dữ liệu/volume,
chưa rollout app hoặc monitoring, chưa chạy dependency demo VPS.

Mốc 139-docs: `f457e4279175edba5722ba4eb895dbdd7dedfc39`, 16:38:28 +07.
[PR #31](https://github.com/dth251/md05-rikkei-project/pull/31) draft.
[Push CI 38377774329](https://github.com/dth251/md05-rikkei-project/actions/runs/38377774329)
đạt unit/build, live infrastructure, VNPay, SMTP và multi-user load/reconciliation;
monitoring thất bại do kiểm tra fresh TSDB trước khi đủ ba samples. PR CI 38377824082
cùng head cũng chạy đến monitoring. Không merge/deploy ở checkpoint này.
Verifier đã thêm bounded wait qua scrape intervals và kiểm tra cùng lịch sử sau restart;
PowerShell parser/actionlint/diff check đạt, cần CI ở head sửa. Windows local rerun có
warm-up/catalog/orders đạt, nhưng finalize HTTP 504, E2E không chạy; guard hiện cũng
kiểm tra tài nguyên trước Java fixture. Không ghi toàn bộ tải local đạt.

VPS HTTPS smoke sau sửa giờ: register/login/me đạt, 401/403/409 đúng; actuator public
và internal recipient public trả 404. RabbitMQ queues ready/unacked/DLQ đều 0, năm
consumer hoạt động; hostname/Mnesia node được giữ bằng private `RABBITMQ_HOSTNAME`.

### Mốc 140–141: CI đạt và tích hợp, checkpoint 17:11 +07 ngày 13/10

| Mốc | SHA | Thời gian +07:00 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 140-infrastructure | `175095963f15aa23846dcce0673384e796758d90` | 16:53:12 | Bounded wait cho scrape history, guard trước Java fixture; PR CI 38379527058 và push CI 38379521378 success toàn bộ; chưa rollout ở checkpoint |
| 141-infrastructure | `19d9fc6d797154dd15a296590a4c773b7eca08e1` | 17:11 | Merge PR #31 sau CI head 140 đạt; main CI/CD 38381634257 đang chạy, chưa ghi deploy mới |

CI head 140 đã qua Java/build/live, 12 VNPay PostgreSQL tests, 3 SMTP tests,
multi-account small load/reconciliation, monitoring/history/provisioning qua restart,
persisted Tempo privacy, business trace đủ sáu service, durable context và collector outage,
Docker audit/Loki privacy. Artifact `java-test-reports` chứa JSON/metrics đã loại credential.
Tải hai tài khoản, target 1 iteration/s/phase 10s: tạo 10 đơn orders và 11 đơn E2E;
đối chiếu 21 CONFIRMED, stock 39/40 từ 50 mỗi tài khoản, reserved 0. Không xác nhận
capacity VPS từ kết quả Linux CI. Số đo p95/p99 nằm trong `remaining-implementation.md`.

UTC và RTC đã sửa, RTC ghi lại lúc 09:58:32 UTC; NTP vẫn timeout cả pool Việt Nam,
`NTPSynchronized=no`. Chưa deploy stack quan sát hoặc chạy dependency demo trên VPS.
Merchant VNPay, SMTP ngoài và kênh alert chưa có cấu hình private. Theo dõi trạng thái
rollout sau checkpoint ở [main CI/CD](https://github.com/dth251/md05-rikkei-project/actions/runs/38381634257)
và [PR #31](https://github.com/dth251/md05-rikkei-project/pull/31).

## Rà soát file hỗ trợ ngày 13/10/2026

Nhánh `feature/cleanup-support-files`; phạm vi `docs`, `infrastructure`, Docker
và `tests`, giữ nguyên luồng hoạt động theo yêu cầu.

| Mốc | SHA | Thời gian +07:00 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 144-infrastructure | `6f4e0be1a15f4ce70374683035d1639234170b75` | 19:30:15 | Auth 23/23 unit, zero fail/error/skip; bootJar và Dockerfile.runtime build đạt. 11 tổ hợp Compose giống hệt cấu hình trước dọn; Bash/PowerShell/k6/JSON/RPC và diff check đạt. Commit local, chưa push/CI/PR/deploy |

Đã bỏ `auth-service/Dockerfile` tự build Gradle trong image vì Compose/CI chỉ
dùng `Dockerfile.runtime`. Nội dung `docs/rpc-recovery-checkpoint.md` được giữ
trong [tài liệu gRPC](product-order-grpc.md), rồi bỏ file checkpoint riêng;
không còn tham chiếu tới hai đường dẫn đã xóa.

Rà soát không phát hiện file hạ tầng/test nào đủ căn cứ để xóa. Schema SQL
được `apply-schema.sh` tìm theo glob, Grafana đọc cả thư mục provisioning/dashboard;
thiếu tham chiếu tên file trực tiếp không có nghĩa file thừa. Giữ các Compose
profile cho alerts/profiling, SMTP/VNPay fixture, script khởi tạo VPS/domain,
Java tests, k6/Postman và bằng chứng cũ có ghi checkpoint.

Kiểm tra local: 20 Bash scripts, 10 PowerShell scripts, 5 k6 scripts và 21 JSON
files hợp lệ; hợp đồng proto Product–Order khớp. Các Dockerfile runtime và bind
mount hạ tầng trong cấu hình Compose đều tồn tại. So sánh SHA-256 đầu ra
`docker compose config --format json` trước/sau bằng `.env.example` và giá trị
public chỉ dùng validation: base, chín override riêng và tổ hợp observability/
SMTP/VNPay đều không đổi. Đây là kiểm tra cấu hình, không thay thế live tests.

Image kiểm chứng local `omnichannel-auth:cleanup-144` build từ JAR vừa kiểm thử;
không recreate container, thay đổi volume/dữ liệu hoặc rollout VPS. Thay đổi
`.vscode/settings.json` có sẵn trong workspace không nằm trong commit này.

## Khôi phục build IDE của API Gateway ngày 13/10/2026

Nhánh `feature/fix-gateway-ide-build`. Lỗi Java builder báo resource
`/api-gateway/bin/main/application.yml` đã tồn tại được xác nhận trực tiếp qua
API diagnostics của IDE; Gradle vẫn compile/test/build bình thường.

| Mốc | SHA | Thời gian +07:00 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 146-api-gateway | `3c84bfdd903da46cb83b758c3016b96238d90899` | 19:42:24 | Gateway 23/23 tests, zero fail/error/skip, bootJar đạt. Reload/full rebuild IDE đạt; diagnostics 19:41:35 xác nhận Gateway và toàn workspace 0 lỗi Java. Chỉ sửa trạng thái IDE và ghi runbook; commit local, chưa push/CI/PR/deploy |

Log Java extension ghi pipe timeout/fallback stdio và hai standard servers dùng
cùng workspace cache, kèm lỗi lưu index. Đã sao lưu metadata Gateway private,
dừng process pipe cũ đúng workspace, gọi reload cấu hình/full build của Java
extension và đối chiếu diagnostics. Cuối phiên kiểm tra chỉ còn một standard
server của workspace. Không tắt cảnh báo/null analysis, không sửa source/routes/
JWT/rate limit, không thay đổi dữ liệu hoặc recreate container. Thay đổi
`.vscode/settings.json` có sẵn vẫn giữ nguyên ngoài commit. Hướng xử lý trong
[tài liệu IDE](java-ide.md), kết quả trong [Gateway tests](gateway-test-results.md).

## Phát triển COD ngày 13/10/2026

Nhánh feature/cod-payment; tiếp nối feature/fix-gateway-ide-build.

| Mốc | SHA | Thời gian +07:00 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 148-payment-service | e5a6ec771789452800000a4955083b0d2906a7bb | 2026-10-13T20:44:28+07:00 | Order 50/50 và Payment 50/50 Java tests đạt; bootJar đạt. Kiểm tra lại OpenAPI Order/Payment và COD Payment đạt; Product OpenAPI 1/1 đạt. Live 13/13 đạt, không fail/error/skip. Docker local Order/Payment build, recreate và healthy; chưa push/PR/CI/GHCR/deploy VPS. |

Chi tiết live: CodPaymentLiveTests 3/3 (thu tiền + 24 request đồng thời,
complete chờ acknowledgement, hủy trước giao, tranh chấp giao/hủy và terminal
Notification); CodProjectionPgLiveTests 3/3 (lifecycle trước creation,
Inbox/advisory lock PostgreSQL, mismatch phương thức và CHECK từ chối SUCCESS
thiếu biên nhận); PaymentLiveTests 3/3; VnpayLiveTests 4/4. Kết thúc lúc
20:43:45 +07:00, BUILD SUCCESSFUL. Lần chạy đầu có hai lỗi 503 qua Gateway
sau recreate service; đã thêm readiness GET qua Gateway trước assertions
nghiệp vụ, chạy lại toàn bộ 13 ca đạt. Không đổi timeout/routing/rate limit.

Migration Order 004 và Payment 002 đã áp dụng qua runner checksum và chạy
lại đạt; các hàng cũ mặc định ONLINE. RabbitMQ thêm queue COD/lifecycle,
DLQ/policy/bindings; cả Bash và PowerShell infrastructure verifier đạt.
Compose config và cú pháp scripts đạt; 11 script COD Postman parse đạt,
chưa chạy Postman Runner. OpenAPI export thành công: 27 paths/34 operations,
operation IDs không trùng. Không đổi payload event ONLINE/fingerprint cũ.

Image local Order: sha256:00f4afc8a20036229f5201a32b2156b05d54e2d4fb72c561ce1725fca15067cf.
Image local Payment: sha256:38aef0091054cf525c92a6b68005a0f4afd4e329adab6ed2658183fc6b788759.
Chỉ rollout hai container ứng dụng local và bổ sung schema/topology;
không xóa volume. Payment local bật fixture VNPay public test-only để chạy
regression. Không thay đổi cấu hình VPS. Các fixture COD là dữ liệu kiểm thử.

Luồng: COD CONFIRMED giữ kho; ship chốt kho; ADMIN ghi nhận đủ tiền và biên
nhận; Order nhận payment.succeeded rồi mới complete. Hủy chỉ trước ship.
Trả hàng/đối soát hãng vận chuyển chưa triển khai. Chi tiết API và Mermaid
trong [luồng COD](cod-payment.md). CI thêm live COD và rollout Payment trước
Order. Thay đổi .vscode/settings.json có sẵn không nằm trong commit.

## Trả toàn bộ đơn và hoàn tiền ngày 13/10/2026

Nhánh `feature/returns-refunds`, tiếp nối COD. Phạm vi là toàn bộ đơn đã
COMPLETED: khách yêu cầu, ADMIN duyệt và nhận đủ hàng, sau đó hoàn đủ tổng
tiền gốc. Hồ sơ trả hàng có trạng thái riêng, giữ lịch sử Order COMPLETED.

| Mốc | SHA | Thời gian +07:00 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 150-payment-service | `3fd8bc02458b447bdd15fe97b87f7c8ea3499a5f` | 2026-10-13T21:31:03+07:00 | Order 57/57 và Payment 59/59 Java tests, bootJar đạt; live 27/27 đạt, zero fail/error/skip. Docker local Order/Payment build, recreate và healthy. Commit local, chưa push/PR/CI/GHCR/deploy VPS. |

Java tests phủ chủ đơn/quyền ADMIN, replay, từ chối hồ sơ, hàng hỏng không
restock, nhiều item rollback khi tràn kho, biên nhận trùng, nhận hàng/chi
hoàn đồng thời và VNPay chưa rõ kết quả. Kho chỉ nhập lại một lần; chi COD
chỉ ghi một biên nhận. Payment giữ SUCCESS/completedAt và audit thu tiền
gốc, refundStatus chuyển REQUIRED rồi REFUNDED. Event completion đến trước
ready hoặc gửi lại không lùi trạng thái hồ sơ. Không hỗ trợ trả một phần;
REJECTED là kết quả cuối của một hồ sơ trên mỗi đơn.

Live kết thúc 21:30:12 +07:00, BUILD SUCCESSFUL: ReturnRefundLiveTests 4/4
(COD + 24 request nhận hàng/chi hoàn cùng lúc, từ chối, hàng hỏng, ONLINE
mô phỏng); VnpayRefundPgLiveTests 10/10 (signed provider ledger, type 01
không chứng minh hoàn tiền, full return qua Gateway/RabbitMQ, không submit
lại sau restart); CodPaymentLiveTests 3/3; CodProjectionPgLiveTests 3/3;
PaymentLiveTests 3/3; VnpayLiveTests 4/4. VNPay dùng HTTP fixture với khóa
public test-only, không có giao dịch ngân hàng thật. Lần live đầu 25/27:
fixture trả cố định 1250 minor units trong khi đơn có tổng 2500; Payment
từ chối đúng số tiền lệch. Đã sửa fixture giữ số tiền theo TxnRef, chạy lại
toàn bộ 27 ca đạt, không nới kiểm tra chữ ký/số tiền trong production.

Order migration 005 và Payment migration 003 đã apply local, chạy lại
runner checksum sau live đạt. Topology bổ sung payment.return-received.q,
order.return-result.q và DLQ/policy/bindings; Bash/PowerShell infrastructure
verifier đều đạt. Kiểm tra cuối hai queue có một consumer mỗi queue,
ready/unacked/DLQ đều 0; đủ chín core containers healthy. Không xóa volume
hoặc dữ liệu. Chỉ rollout ứng dụng local Payment trước Order; các record
live là dữ liệu kiểm thử, không phải giao dịch khách hàng.

Image Order local: `sha256:082553d0359f132d9ae3ea4877b55d5ace2fca252ece9fea2a063925d234338a`.
Image Payment local: `sha256:66fe6e3453c3b2b760b3177be0962a81e39cc319b4c77b0a538d95c5a4a26888`.
SHA-256 JAR trong container khớp build artifact vừa kiểm thử: Order
`f22f920300205333b77a01a8ffdadf1f02a94e482bd2e789f4fd6d96bea4c308`,
Payment `e4d9b0da624ad0ece26a8b948f312f562ccfa8262b55f01eaa4253997ed14b3b`.

OpenAPI export 32 paths/41 operations, ID duy nhất; Postman collection
JSON hợp lệ và 19 scripts COD/return qua node --check, chưa chạy Postman
Runner. Bash/PowerShell syntax và diff check đạt. CI tự khám phá Java live
COD/return và stage VNPay chạy full-return/recovery; chưa chạy CI remote
tại checkpoint này. .env/.verification và thay đổi .vscode/settings.json
có sẵn không nằm trong commit. Không bổ sung frontend/shared module,
không thay luồng ONLINE/COD hoặc notification hiện có. API, Mermaid,
biên nhận cash, giới hạn và rollout trong [luồng trả hàng](returns-refunds.md).

## Sửa IDE và triển khai COD / trả hàng ngày 13/10/2026

| Mốc | SHA | Thời gian +07:00 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 152-devops | `b4ca46d72b8d0dfa38a9f883f56a6a1f2e241397` | 2026-10-13T21:55:26+07:00 | Bổ sung Order 006 / Payment 004 để bảo đảm FK/CHECK khi Hibernate tạo bảng trước. Chuỗi migration local và kiểm tra dữ liệu không hợp lệ đạt trong schema kiểm chứng rồi rollback. CI đầu còn lỗi CHECK fulfillment trùng tên, chưa deploy. |
| 153-devops | `daf52df07bf31c8dba1af34836a4038311fcf2b7` | 2026-10-13T22:04:51+07:00 | CI chỉ thay CHECK fulfillment của Hibernate khi database Order rỗng, rồi áp dụng SQL bất biến. PR CI 38418958902 đạt toàn bộ Java, API/live, VNPay fixture/recovery, SMTP, tải nhỏ và observability. Publish/deploy được skip đúng trên PR. |
| 154-payment-service | `7839183b15a78b30234182e4f0e96cbfb1c9874e` | 2026-10-13T23:30:27+07:00 | Merge PR #33 sau CI đạt. Main pipeline 38431072534: verify, observability-config, publish sáu image và deploy đều success. Rollout hoàn tất 23:58:09 +07:00. SSH xác nhận sáu image/revision đúng SHA, healthy và restart=0; HTTPS trả 200 / UP. Bốn Java return/refund live tests qua HTTPS VPS đạt lúc 2026-10-14T00:00:12+07:00, không fail/error/skip. |

IDE thực tế ban đầu có 1.055 Java errors. Rebuild sáu Gradle projects giảm
còn 82 lỗi generated gRPC của Order; reload cấu hình Gradle để nhận hai
source roots `build/generated/sources/proto/main/java` và `main/grpc` giảm
còn 0. Restart Java server và đọc diagnostics lại vẫn 0; lần kiểm tra
cuối 2026-10-13T17:01:12.276Z (00:01:12 +07:00 ngày 14/10) xác nhận 0. Không tắt diagnostics hoặc thay mã
nghiệp vụ để che lỗi. Chi tiết trong [runbook IDE](java-ide.md).

CI database mới cần Hibernate tạo base tables trước migration; VPS có dữ
liệu giữ thứ tự schema trước recreate service. Không sửa SQL đã checksum.
Order 006 / Payment 004 bổ sung các ràng buộc khi bảng return/refund đã
được Hibernate tạo. Toàn chuỗi Order 001–006 / Payment 001–004 đã được
chạy lại trên schema kiểm chứng rỗng và rollback; 17 Bash blocks của CI
qua syntax check, diff check đạt.

[PR #33](https://github.com/dth251/md05-rikkei-project/pull/33),
[CI PR đạt](https://github.com/dth251/md05-rikkei-project/actions/runs/38418958902),
[main CI/CD](https://github.com/dth251/md05-rikkei-project/actions/runs/38431072534).

Trước rollout, VPS còn sáu image `19d9fc6d797154dd15a296590a4c773b7eca08e1`;
HTTPS `/gateway-status` trả 200 / UP lúc 23:33:41 +07:00. Thay đổi local
`.vscode/settings.json` và việc di chuyển `infrastructure/` sang
`docs/infrastructure/` ngoài PR được giữ nguyên, không stage cùng bản
deploy. `.env`, SSH key và bằng chứng `.verification` không đưa vào Git.

Rollout thực tế: Auth 23:51:10, Gateway 23:52:19, Product 23:53:49,
Payment 23:55:11, Order 23:56:48, Notification 23:58:09 +07:00.
Deployment áp dụng topology và schema trước recreate, không xóa volume.
SSH xác nhận `current` trỏ release SHA 154; manifest và OCI revision của
cả sáu service cùng SHA. Order database có versions 001–006, Payment
001–004; các FK/CHECK return/refund có trong PostgreSQL.

Kiểm thử VPS dùng `ReturnRefundLiveTests` 4/4 qua domain HTTPS và tunnel
PostgreSQL private: hoàn COD với 24 request nhận hàng/chi hoàn đồng thời,
hồ sơ bị từ chối giữ nguyên kho/tiền, hàng hỏng hoàn tiền nhưng không
restock, và ONLINE legacy mô phỏng. Các ca kiểm tra quyền, tổng tiền,
stock available/reserved, một biên nhận chi, một event hoàn và trạng thái
Order REFUNDED. Không restart container VPS hoặc gọi VNPay provider;
không xác nhận chuyển tiền ngân hàng thật. Bốn đơn/sản phẩm là fixture
riêng, giữ dữ liệu và audit kiểm thử. File cấu hình private tạm và tunnel
đã được đóng/xóa sau kiểm tra; không ghi secret ra log.

Đối soát sau live ngày 14/10: sáu health UP, hai queue returns đều có một
consumer, mọi queue ready/unacked/DLQ=0; Order/Payment pending Outbox=0.
HTTPS `/gateway-status` trả 200 với `{"status":"UP"}` lúc 00:01:12 +07:00.
Các ca VNPay signed callback/refund/restart được xác minh bằng fixture
trong CI, không phải merchant sandbox hoặc giao dịch khách hàng thực tế.

## Hết hạn 5 phút, SMTP và monitoring ngày 14/10/2026

| Mốc | SHA | Thời gian +07:00 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 156-order-service | `ae134e8541aff254d8e3e6b7a87c560cfe4834e2` | 2026-10-14T00:40:37+07:00 | Order 60/60 và Notification 18/18 Java tests/bootJar đạt. Docker local hai service healthy; SMTP live 5/5 đạt và ca expiry qua queue riêng chạy lại 1/1 đạt. PR #34 CI 38440649153 đạt toàn bộ verify/observability; publish/deploy skip đúng trên PR. |
| 157-order-service | `069ad87d0cdfd768f847dbd214d85095a693947e` | 2026-10-14T01:00:05+07:00 | Merge PR #34 sau CI đạt. Main 38443206215 verify/observability và sáu publish đạt. Deploy lần đầu dừng trước rollout do IMAGE_TAG chưa truyền vào SSH; lần chạy lại riêng job deploy đạt, rollout xong 01:30:20 +07:00. Sáu image/revision đúng SHA, healthy/restart=0. SMTP VPS 3/3 đạt, gồm chờ đủ 5 phút, và monitoring/load được kiểm chứng thực tế. |
| 158-devops | `97be0ef1a059b02c0d4493342270267c2b981a5f` | 2026-10-14T01:20:29+07:00 | Truyền full SHA trực tiếp vào lệnh provision remote. Bash syntax/diff check đạt; cả hai production Compose overlays SMTP/monitoring config --quiet đạt trên VPS. PR #35 CI 38445960395 verify/observability success, publish/deploy skipped vì chỉ đổi workflow. |
| 159-devops | `9643712b44429f01eab75b560d1d49d79706562c` | 2026-10-14T01:39:25+07:00 | Merge PR #35 sau CI đạt. Main 38448516283 verify/observability-config success; publish/deploy skipped đúng vì thay đổi chỉ ở workflow, không chọn service để recreate. VPS tiếp tục chạy bản ứng dụng 157 đã được kiểm thử và deploy thành công. |

Main 159 kết thúc success lúc 01:57:06 +07:00, xác minh qua GitHub run
status/conclusion và head SHA. Ứng dụng VPS giữ SHA 157 vì mốc 159 chỉ
sửa bước provision trong workflow, không thay artifact ứng dụng.

Deadline ONLINE PENDING_PAYMENT dùng cùng policy PT5M ở scheduler và
payment consumer, dưới cùng pessimistic row lock/transaction. Consumer
không xác nhận đơn sau hạn kể cả scheduler chưa quét; expiry hoàn kho
một lần. Tiền đã capture đến muộn chuyển luồng refund REQUIRED/đối soát
hiện có, không phục hồi đơn. COD không thuộc timeout ONLINE. Java tests
phủ race, callback trước/sau hạn, scan trễ, replay và rollback tồn kho.

Notification dùng Spring Mail và queue mới `notification.expiry.q` để
event hết hạn chờ bền vững trong rollout từng service. Migration 004
cho phép paymentId null đúng riêng stage expiry và thêm CHECK phù hợp;
001–003 đã checksum được giữ nguyên. VPS có Notification 001–004,
Order 001–006 và Payment 001–004. Bash/PowerShell topology verifier đạt;
queue expiry có một consumer, DLQ/ready/unacked đều 0 sau live/load.

VPS private config bật SMTP sandbox/monitoring, recipient token riêng và
mật khẩu monitoring ngẫu nhiên; có backup private trước thay đổi. Lỗi
deploy đầu 01:18:49 nằm ở interpolation IMAGE_TAG, chưa thay ứng dụng.
Để rerun bản đã verify, ghi fallback full SHA 157 vào `.env` private rồi
rerun riêng failed deploy job, không rebuild/publish lại image chưa thử.
Mốc 158 sửa workflow để các lần sau truyền SHA từ runner một cách rõ
ràng. Không commit hoặc in `.env`, credential, SSH key, fixture token.

Monitoring role chỉ có pg_monitor/CONNECT, không cấp quyền dữ liệu ứng
dụng. Preflight xác nhận đủ RAM/disk và UTC qua hai HTTPS Date; VPS NTP
vẫn unsynchronized, không tuyên bố NTP đã đồng bộ. Mailpit và năm
container monitoring đã provision lúc 01:20:52 +07:00, cổng UI chỉ bind
loopback. Có volume history riêng, không xóa volume hoặc dữ liệu.

Rollout thực tế +07:00: Auth 01:22:48, Gateway 01:24:06, Product
01:25:42, Payment 01:27:16, Order 01:28:54, Notification 01:30:20.
SSH xác nhận current/manifest/OCI revision của sáu service đều là SHA
157. Kiểm tra monitoring sau rollout và sau tải đều đạt: sáu service và
năm target hạ tầng UP; Grafana database/datasource OK, dashboard 22
panel, HTTP/resource metrics và hai Outbox snapshot hợp lệ.

Snapshot Prometheus lúc 01:55:34 +07:00 có một scheduler expiry, ba email
SENT, các state email còn lại 0 và email snapshot valid=1; panel nghiệp
vụ có dữ liệu từ các ca kiểm thử SMTP.

Ba Java EmailLiveTests chạy qua HTTPS VPS, PostgreSQL/RabbitMQ/Mailpit
private tunnel đạt 3/3, zero fail/error/skip, suite time 323,859 giây:
expiry 311,852 giây, email hủy 5,850 giây, email xác nhận/replay 6,145
giây. Gradle BUILD SUCCESSFUL in 5m45s. Đơn fixture 122 tạo lúc
01:31:27.786439 +07:00, giữ PENDING_PAYMENT trước hạn, sau hạn EXPIRED;
stock available=1/reserved=0. Email expiry SENT, một attempt, lúc
01:36:34.851290 +07:00. Không sửa createdAt trong lần kiểm chứng VPS.
Recipient là chủ đơn Auth thật, replay không thêm delivery hoặc SMTP
message. Hai ca confirmation/cancellation dùng payment simulation của
fixture, không phải giao dịch VNPay ngân hàng. Không dừng hoặc restart
container trên VPS để kiểm thử SMTP. Tunnel được đóng sau chạy.

Tải catalog với 50 token Auth mới còn hạn, 10 giây/mức và tối đa 200
VUs: target 500 phát 800 request, 9 HTTP 200, 791 timeout, 4.201 dropped;
target 1.000 phát 800 request, 800 timeout, 9.200 dropped. Cả hai exit99
và threshold fail; CPU mẫu 2 giây đều 100%. Throughput thành công theo
summary chỉ 0,71/s và 0/s, không tuyên bố đạt 500–1.000 requests/s.
Lần đo trước dùng token hết hạn 15 phút bị loại, giữ evidence riêng;
token cũ xác nhận HTTP 401, fixture mới kiểm tra 50 token và request200
trước đo. Sau tải sáu health UP/restart0, Outbox/DLQ0 và toàn bộ scrape
targets UP. JSON/CSV/XML giữ private trong .verification.

Chi tiết thời gian, p95/p99, TLS, RAM/swap, cách diễn giải và cửa sổ
Prometheus trong [báo cáo tải/monitoring](capacity-monitoring-report.md);
luồng và cách truy cập UI trong [runbook](expiry-email-monitoring.md).
[PR #34](https://github.com/dth251/md05-rikkei-project/pull/34),
[main 157, attempt 2 success](https://github.com/dth251/md05-rikkei-project/actions/runs/38443206215),
[PR #35](https://github.com/dth251/md05-rikkei-project/pull/35),
[main 159](https://github.com/dth251/md05-rikkei-project/actions/runs/38448516283).

Phát triển/kiểm thử trong worktree feature/expiry-email-monitoring để giữ
thay đổi có sẵn ở workspace gốc: `.vscode/settings.json` và việc di
chuyển infrastructure sang docs/infrastructure. Không stage/khôi phục
những thay đổi đó. Không thêm frontend/shared/Maven hoặc Python source.

## Tiếp nối ưu tiên expiry, SMTP và monitoring ngày 14/10/2026

Nhánh `feature/expiry-150s-email-monitoring` tích hợp lịch sử worktree
`feature/expiry-email-monitoring` mốc 160. Giữ nguyên thay đổi IDE của
người dùng; hoàn tất chuyển thư mục hạ tầng sang `docs/infrastructure/`
và cập nhật Compose/CI/scripts/release tương ứng. Không sửa checksum SQL
đã áp dụng: Notification 004 giữ nguyên bytes; thêm 005 cho ràng buộc.

| Mốc | SHA | Thời gian +07 | Kiểm tra / deploy thực tế |
|---|---|---|---|
| 161-order-service | `cbc38d6883ff19c552ef33666abb659fea94a056` | 2026-10-14T09:21:51+07:00 | Deadline mặc định 150 giây, consumer kiểm tra sau row lock; 60 Java tests và bootJar đạt. Chưa deploy VPS. |
| 162-notification-service | `e42eb61914d29b65bbea0bf67ec2804bb7699321` | 2026-10-14T09:26:23+07:00 | Email expiry, nullable payment ID chỉ cho expiry, listener validation/SMTP UTF-8; 20 Java tests đạt trước tích hợp. |
| 163-infrastructure | `f97c9fd34ccace99bf5f7d71e9f0efb3f934c8f3` | 2026-10-14T09:26:36+07:00 | Compose/Bash/PowerShell syntax, Prometheus 11 rules/fixtures, Collector/Alloy/Loki/Alertmanager config đạt. Runner checksum được thử trên schema PostgreSQL riêng: apply/replay đạt, mismatch trả nonzero trước DDL. |
| 164-infrastructure | `1ccf6ebb0bdd2e2f55018b2d483d65e756055ad7` | 2026-10-14T09:38:18+07:00 | Tích hợp mốc 160, queue expiry riêng/DLQ và scripts VPS. Order 60 hồi quy + 1 policy test, Notification 21 Java tests và bootJar đạt; schema/topology/replay đạt local. |
| 165-infrastructure | `341c17eb0e10855278d1ca0c24ec76281457bf83` | 2026-10-14T09:45:26+07:00 | SSH chạy verifier mới bằng stdin: sáu service/năm dependency UP, history hai phút, 22 panel/query/unit khớp file đang deploy, datasource/resource/business metrics đạt. Không đổi bản ứng dụng trên VPS. |
| 166-infrastructure | `0b9f51e39c761b23bdb4cd2fab744ed791e3593b` | 2026-10-14T09:48:06+07:00 | CI đối chiếu projection dashboard trước/sau restart; syntax/projection 22 panel đạt. Live SMTP JVM giới hạn heap 128 MiB. |

VPS kiểm tra lại 09:29–09:44 +07: sáu app mốc 157 healthy, monitoring/Mailpit
healthy; khoảng 2.146 MiB RAM available và 14 GiB disk trống. Private
`RESERVATION_TTL=PT5M` vẫn còn tại checkpoint; cần đổi PT150S khi rollout
code mới. Không coi kiểm tra verifier bằng stdin là deploy bản 150 giây.

Lần SMTP local đầu 5/5 thất bại với 503/504 và timeout khôi phục; Windows
có lúc chỉ còn khoảng 15 MiB RAM available. Sau đó app và Mailpit được
khôi phục healthy, test worker giảm heap 128 MiB; lần chạy lại đang chờ
đủ 150 giây thực tế tại checkpoint. Không nâng timeout/threshold nghiệp vụ
để biến kết quả thất bại thành đạt. Một ca boundary H2 ban đầu lệch do
round nanosecond theo precision database; clock fixture đã chuẩn hóa
millisecond và toàn bộ 60 ca hồi quy chạy lại đạt.

CI/PR và deploy của nhánh mới chưa hoàn tất tại checkpoint này. Mức tải
500/1.000 requests/s trước đó vẫn không đạt; không chạy tải lớn lại để
khẳng định capacity. Xem báo cáo tải/monitoring và runbook tương ứng.
