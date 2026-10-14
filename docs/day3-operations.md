# Chạy bộ kiểm chứng ngày 3

Xem [API contract](api-contract.md), [kiến trúc](architecture.md), [kết quả thật](day3-test-results.md). Không dùng load/recovery flags trên dữ liệu ngoài dự án. Java unit/live tests vẫn là gate CI; k6/Postman là harness của công cụ, không thay thế kiểm thử service.

## Metrics và vận hành VPS

Qua SSH, chạy `bash /opt/omnichannel-ecommerce/current/docs/infrastructure/scripts/observe-vps.sh`. Kết quả gồm RAM/CPU, health, pending Outbox, queue ready/unacked/consumer. Có thể curl `/actuator/prometheus` trên loopback 8080–8085; domain public phải trả 404. Không mở ports để scrape từ Internet. Health không kiểm tra RabbitMQ/Redis trong một số app để lỗi dependency không làm app mất liveness; kiểm tra workflow nghiệp vụ riêng.

`secure-vps-network.sh` chạy root sau audit listener, không reset rule. `verify-rollback.sh FULL_CACHED_CANDIDATE_SHA` chạy deploy trong thời điểm không có rollout: chọn SHA known-good khác Notification hiện tại. Harness chỉ recreate Notification, ép một health smoke failure, kiểm tra image trước/manifest/current và health. Không rollback schema/volume; không kiểm tra registry vì dùng candidate đã cache.

## Java và recovery local

Theo [runbook ngày 2](day2-operations.md) để build sáu app và nạp secret vào process từ `.env`. Bổ sung `GATEWAY_FAILURE_TEST=true` khi chạy Gateway `liveTest`: stop/start Product và Redis, kiểm tra fail policy rồi phục hồi trong finally. Flag này yêu cầu `GATEWAY_LIVE_URL` loopback để tránh dùng nhầm trên VPS. Các flags Payment/Notification/broker ở runbook chỉ dành cho stack local/CI. CI chạy đầy đủ các flags và không xóa volumes của VPS.

## k6 catalog có resource guard

Dùng k6 v2.3.0. Binary chính thức/checksum xác minh nằm ngoài source; không commit công cụ. Ví dụ PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File docs/infrastructure/scripts/run-catalog-load.ps1 `
    -K6Path 'YOUR_PRIVATE_TOOLS/k6.exe' -SshKey 'YOUR_PRIVATE_SSH_KEY'
```

Mỗi stage 20 giây, target 50→100→250→500→1000 RPS, max 100 VUs. Dừng trước stage khi RAM available <64 MiB, swap used >1792 MiB, CPU >85%; dừng tăng sau lỗi threshold, RAM/CPU hoặc dropped iterations. Threshold abort: failure ≥5% hoặc p95 ≥2000ms sau 5 giây. Cold start/network giữa máy phát tải và VPS cũng thuộc số đo HTTP; không coi RPS target là actual. Queue/Outbox chụp trước/sau; CSV CPU/RAM lấy 2 giây/lần trong mỗi stage. Báo cáo JSON/CSV mặc định dưới `.verification`, không commit thư mục này; chỉ đưa số liệu đã loại secret vào docs/evidence.

## Fixture, Order load và race k6

`order-service/gradlew.bat -p order-service loadFixture -PfixtureAction=prepare` là Java CLI test tool, không tự chạy trong app hoặc CI. Cần process env `LOAD_BASE_URL`, `JWT_SECRET`, `LOAD_FIXTURE_FILE` (file private chưa tồn tại). Tool đăng ký/login USER thật, tạo hai Product và nhập stock 1000/1 bằng ADMIN fixture JWT một giờ, không in token/password. File fixture có token nên chỉ nằm trong thư mục riêng/.verification, ACL private; không export/commit. ADMIN fixture dùng cho test có quyền quản trị, không phải cơ chế cấp ADMIN trong sản phẩm.

Set `FIXTURE_FILE`, `BASE_URL`, `SUMMARY_FILE`; chạy `k6 run tests/k6/order-load.js` (2 Order/s ×20s), sau đó `k6 run tests/k6/gateway-rate-limit.js` riêng (30 login/20 VUs). Order load đo write/Saga admission, chưa simulate payment trong vòng tải.

Giảm tải bằng `RATE=1` khi cần. Mỗi lần đo Order tạo mới phải dùng `runId` UUID mới trong một bản sao fixture **private**; giữ product IDs/tokens. Nếu dùng lại runId, server trả replay 200, không phải đo tạo mới 201. Không reset stock hoặc xóa orders để chạy lại. `finish` chọn theo hai product IDs nên bao gồm orders của các runId khác nhau. Báo cáo dropped iterations và actual RPS kể cả khi HTTP checks/exit code đạt; arrival boundary và VUs có thể khiến tổng request khác target × duration.

Mốc 55 thêm counters 2xx/4xx/5xx/429/status=0 cho Order/race. `http_req_failed` mặc định tính 409/401/429 là failed; trong bài race/quota chúng có thể là kết quả mong đợi. Kết luận theo custom thresholds đúng mục tiêu và SQL, không dùng tỷ lệ này như production error budget. Không nới thresholds sau khi đo lỗi. Ghi rõ sampler bắt đầu muộn hoặc bao gồm recovery; CSV không tự chứng minh toàn bộ cửa sổ load.

Để race đúng inventory, mở **foreground SSH tunnel** local `18083` tới VPS `127.0.0.1:8083`, và PostgreSQL `15433` tới địa chỉ Docker private đã inspect. Không public DB/Order, không bỏ security/rate limit production. Set `ORDER_URL=http://127.0.0.1:18083`; chạy `k6 run tests/k6/race-condition.js`: 100 VUs ×1 iteration, keys khác nhau, đúng một 201 và 99×409. Thử Gateway quota không phải chứng minh chống âm kho.

Set process env `ORDER_LIVE_DB_URL`, `ORDER_LIVE_DB_PASSWORD`, giữ fixture env; chạy Java `loadFixture -PfixtureAction=verify-race` để kiểm tra SQL available=0/reserved=1/orders=1. Cuối cùng set `LOAD_RESULT_FILE` và chạy `loadFixture -PfixtureAction=finish`: simulate SUCCESS cho đúng orders của hai product fixture, chờ CONFIRMED, reserved=0, lưu JSON chỉ chứa IDs/số liệu. Tool không xóa dữ liệu; giữ fixture rows phục vụ audit. Đóng tunnel và xóa env/token files sau kiểm chứng.

Đợi rollout xong rồi đo/finalize; phối hợp bằng cùng `.deploy.lock` nếu cần tránh recreate trong cửa sổ kiểm chứng. Không giữ verification lock quá thời hạn chờ deploy. Tool không tự bỏ qua 503/504: ghi nhận failure, quan sát stack, rồi có thể chạy lại `finish` sau phục hồi vì SUCCESS idempotent; không dùng rerun để thay thế kết quả load đã lỗi. Token fixture một giờ, hoàn tất trước expiry. Chỉ đóng đúng tunnel của phiên kiểm chứng và chỉ xóa token files do harness này tạo.

## Postman và demo

Import [collection](../tests/postman/md05.postman_collection.json) 19 requests. Chạy Auth rồi Product/Inventory rồi Saga. Cấu hình ADMIN email/password riêng sau khi cấp role trên server, không lưu trong repo. Payment/final-order bất đồng bộ: retry 404 hoặc trạng thái chưa final; login có thể trả 429. Collection kiểm tra USER 403, replay Order và FAILED compensation; flow SUCCESS và duplicate-event/Notification được Java live tests kiểm chứng. Clear credential/token variables trước export. Không có frontend trong dự án.

Sơ đồ, screenshot và video là bằng chứng backend/test, không đại diện cho UI bán hàng. Report ghi rõ timestamp, SHA image thật, lần thử lỗi và giới hạn tải; không coi file script hoặc CI chưa xong là deploy thành công.
