# Checkpoint khôi phục RPC

Main 91 `356fa9e16014cfe7c796324af71a00f8839b9ce8` (2026-10-12
07:33:51 +07) được merge sau PR CI 38187929374 đạt. Tuy nhiên main
38188486613: Java success, Product live 3/4 success, case nhận stock cho
variant trả 503 thay vì 200; publish/deploy bị chặn. Artifact
11720354306 xác nhận assertion ở ProductLiveTests:129. VPS vẫn main 86,
sáu image healthy tại SSH 2026-10-12 00:33:05 UTC.

Gateway outage test trước Product live chủ động gọi Order khi Product bị
dừng. Channel dùng chung có thể còn TRANSIENT_FAILURE và reconnect backoff
khi HTTP Product đã healthy. Đây là tình huống cần sửa; CI cũ không thu
RPC status metrics nên chưa khẳng định nó là nguyên nhân duy nhất của 503.

Mốc 96 `5fce3b0dcf0cab948ddb5c68fb3ad1efa16ae074` (07:45:07): chủ động
kết nối lại khi channel TRANSIENT_FAILURE, waitForReady chỉ trong deadline
cũ, không tăng deadline và không thêm HTTP fallback/RPC request retry.
7 RPC Java tests/bootJar đạt, có case cùng channel qua server stop/start:
lượt offline trả 503, lượt sau server hồi phục chạy đúng một read thành công.

Mốc 97 `2694fbdfb758af9ff3aca88c5b605c804b8342e3` (07:45:07): assertion
stock ghi body API fixture, CI failure lưu container state và các metrics
RPC/Gateway được allowlist. Không dump env/request headers/token hay queue
payload, không chỉnh timeout để che lỗi. CI và rollout bản sửa vẫn chờ.

Một lần live local khác trong lúc khởi động bốn container OpenAPI trả 504
ở cùng stock assertion; RPC metric OK với thời gian 2.1668 s. Các container
healthy, OOMKilled=false và restart count=0. Khác với 503 trong CI; chưa
quy kết chung một nguyên nhân và chưa ghi local lần này thành pass.
