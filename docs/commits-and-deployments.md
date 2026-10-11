# Nhật ký commit, CI/CD và deploy

Phạm vi theo yêu cầu hiện tại: hạ tầng + Auth + CI/CD. Format mốc: `NN-ten-service: noi dung thay doi`; số tăng dần theo thứ tự thực hiện. CI/CD được chuyển lên ngày 1 theo yêu cầu, nên thứ tự các mốc sau Auth được điều chỉnh so với kế hoạch 3 ngày gốc.

## Các mốc đã push

Thời gian trong bảng là giờ Asia/Bangkok (UTC+07:00), lấy từ Git commit.

| Mốc / ghi chú push | SHA | Thời gian | Kiểm chứng trước push | VPS |
|---|---|---|---|---|
| `00-infrastructure: initialize postgres rabbitmq and deployment layout` | `2efa1b557af0c93cd091ac6c5ae0a7bbaf108b9d` | 2026-10-11 16:16:08 | 5 DB/user, isolation, durable queues/bindings, private ports đạt | Chưa deploy: thiếu quyền SSH |
| `01-auth-service: initialize gradle application and health endpoint` | `5a68927` | 2026-10-11 16:16:59 | Gradle build/context và health skeleton đạt | Chưa deploy: thiếu quyền SSH |
| `02-auth-service: implement registration login jwt and security` | `ef593b8` | 2026-10-11 16:56:27 | 11 Java tests + 1 live test PostgreSQL/restart đạt; Auth healthy | Chưa deploy: thiếu quyền SSH |
| `03-devops: add auth ci and selective vps deployment` | `4139c73ad156772c94fd6db229812c08c50375b6` | 2026-10-11 17:02:10 | actionlint, Bash syntax, production Compose và hạ tầng đạt; Python vận hành đã thay bằng Shell/PowerShell | Chưa deploy: `DEPLOY_ENABLED=false` |

## CI/CD thực tế

- [Run #1](https://github.com/dth251/md05-rikkei-project/actions/runs/38130511299): push `03-devops`, SHA `4139c73ad156772c94fd6db229812c08c50375b6`, bắt đầu 2026-10-11 10:02:18 UTC. Đang chạy khi tạo nhật ký; cần đối chiếu kết quả cuối cùng trước khi tích hợp `main`.
- Job `verify`: Gradle tests/artifact, 5 database/user, RabbitMQ topology, Auth live Java tests và restart.
- Job `publish`: chỉ sau verify đạt; image dùng SHA đầy đủ, không dùng `latest`.
- Job `deploy`: chỉ chạy trên `main` khi `DEPLOY_ENABLED=true`; health/smoke thành công mới ghi deployed. Nhật ký Job Summary chứa SHA, thời gian UTC và kết quả thực tế.

## Trạng thái VPS

SSH tới `root@42.96.2.60` hiện trả `Permission denied (publickey,password)`. Cần cấp quyền cho khóa bootstrap hiện có hoặc cung cấp đường dẫn khóa được VPS chấp nhận trước khi chạy `prepare-vps.sh`. Khóa CI riêng đã được lưu trong Actions secret; public key sẽ được cài cho user `deploy` khi bootstrap SSH hoạt động.

Chưa chạy bootstrap, chưa tạo `.env` VPS, chưa xác nhận container trên VPS, domain API hoặc HTTPS. Không đánh dấu các mục này hoàn thành. Không ghi secret vào nhật ký.
