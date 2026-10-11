# Kiểm thử API Gateway

## 12-api-gateway: local JWT, routing and Redis verification

- Kiểm chứng ngày 2026-10-11, Asia/Bangkok; Gateway skeleton `eb33bcd`, JWT/rate limit `0d2222a`.
- `gradlew test bootJar`: 13 Java tests đạt. Token HS256 theo Auth; sai issuer/signature/expiry/subject/role và thiếu expiry trả 401. Giữ nguyên URL prefix, loại header giả mạo identity, request ID, backend không hoạt động trả 503.
- `gradlew liveTest`: 1 Java integration test đạt với Auth/PostgreSQL/Redis thật qua Docker Gateway; register/login/me, 401/403, Redis quota 429, Auth vẫn phục vụ sau burst login.
- Tắt Redis local: login trả 503 thay vì bỏ qua rate limit; Gateway health vẫn UP. Redis đã được khởi động lại và healthy.
- Hai lỗi script đã tái hiện và sửa: generator từ chối khi OpenSSL lỗi trước khi tạo `.env`; kiểm chứng topology từ chối binding sai exchange/queue.
- `actionlint`, cú pháp Bash và production Compose đạt. Tại mốc `12`, domain còn dùng Auth; trạng thái mới được ghi bên dưới sau khi Gateway deploy.

Gateway không truy cập PostgreSQL/RabbitMQ. Redis chỉ phục vụ rate limit. IP chỉ được tin khi peer nằm trong danh sách proxy cấu hình; Nginx ghi đè header địa chỉ client.

## 20-api-gateway: Redis failure fallback và ApiResponse

- SHA `fb9fff578e8c9395bf41367f6b45a97227b8053a`, 2026-10-11 18:46:54 +07:00.
- 15 Java unit tests và 1 live test Docker đạt. Hai test mới dùng RedisRateLimiter thực với Redis template giả lập: lỗi Lua sau availability probe phải trả 503; Redis khỏe nhưng quota hết vẫn từ chối đúng cơ chế 429.
- Gateway sở hữu DTO ApiResponse lỗi, gồm `data=null`; inventory route tách khỏi limiter dành cho Order API.
- [PR #6](https://github.com/dth251/md05-rikkei-project/pull/6) CI verify đạt trên head cuối trước merge `21-api-gateway`.
- HTTPS domain đã qua Gateway từ rollout `15`: Java live test dùng token thật do Auth phát, kiểm tra 401/403/429 và đổi header X-Real-IP không vượt được quota.
- Rollout `21` [run main](https://github.com/dth251/md05-rikkei-project/actions/runs/38137082597) kết thúc 2026-10-11 12:01:47 UTC: verify/publish/deploy success, chỉ Gateway được chọn. SSH đối chiếu image `867e87b010698c77dad07ef4ea1e745384ec24c2` và health UP lúc 12:33:55 UTC. Java live test qua HTTPS chạy lại trên image này đạt lúc khoảng 12:35 UTC.
