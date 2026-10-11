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
