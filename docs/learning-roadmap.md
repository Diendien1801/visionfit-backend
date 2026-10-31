# Lộ trình học backend trên VisionFit

> Bắt đầu: 2026-10-29. Nền đã có: 5 service Spring Boot, Postgres, Docker Compose, gateway, nginx, Cloudflare Tunnel, chạy thật tại `https://api.baofit.tech` (EC2 t4g.small).
> Cách học: **chiều rộng trước**: mỗi chủ đề 1–3 buổi, làm một bài thực hành nhỏ **chạy được trên VisionFit** và hiểu "nó giải quyết bài toán gì". Xong 5 giai đoạn thì chọn 2–3 chủ đề để **đi sâu**.
> Đánh dấu `[x]` khi xong. Ghi ngày và một dòng "điều rút ra" vào cột cuối.

---

## Giai đoạn 1. Vận hành an toàn

Kết quả: không mất dữ liệu; push code là tự test, tự deploy, không cần SSH.

| Xong | # | Chủ đề | Bài thực hành trên VisionFit | Vì sao cần | Ghi chú |
|---|---|---|---|---|---|
| [x] | 7 | Backup và restore | `pg_dump` hằng ngày lên S3, rồi **thử khôi phục** vào một DB trống | Chưa có backup nào. Backup chưa từng thử khôi phục thì coi như không có | 2026-10-30: backup tự động mỗi đêm lên S3 (cron + IAM role chỉ ghi), đã restore thử bằng tay; daily/weekly/monthly + S3 lifecycle; Healthchecks.io báo khi một đêm không chạy. Chưa làm: restore thử tự động |
| [x] | 1 | CI | GitHub Actions: mỗi lần push hoặc mở PR thì build và chạy toàn bộ test | Hiện không có gì chặn một commit làm hỏng test | 2026-10-31: `.github/workflows/ci.yml` chạy 39 test mỗi lần push. Lần đầu đỏ vì `gradlew` thiếu quyền x (commit từ Windows): CI bắt lỗi mà máy mình che đi |
| [ ] | 3 | Container registry, versioning image | Build image ở GitHub Actions, đẩy lên GHCR, tag theo commit; EC2 chỉ `pull` | Máy 2 GB không phải build; quay về bản cũ chỉ cần đổi tag | |
| [ ] | 2 | CD | Merge vào `main` thì tự deploy lên EC2 | Bỏ bước SSH và chạy `deploy.sh` bằng tay | |
| [ ] | 25 | Log có cấu trúc + request id | Đưa `X-Request-Id` vào MDC của từng service | Lần theo một request qua 5 service | |

## Giai đoạn 2. Dữ liệu và hiệu năng

Kết quả: biết đo, biết cache, biết giới hạn của máy.

| Xong | # | Chủ đề | Bài thực hành trên VisionFit | Vì sao cần | Ghi chú |
|---|---|---|---|---|---|
| [ ] | 33 | Testcontainers | Test chạy trên Postgres thật (trong Docker) thay cho H2 | H2 chỉ "giả làm Postgres", có chỗ khác bản thật | |
| [ ] | 9 | Index, hiệu năng query | `EXPLAIN ANALYZE` để thấy bug 6.A (`IgnoreCase` làm index vô dụng), rồi sửa | Biết **đo** chứ không đoán | |
| [ ] | 11 | Redis: cache | Cache `GET /profile/me` và tổng dinh dưỡng của ngày; có hết hạn, xoá cache khi dữ liệu đổi | Giảm tải DB; học bài toán "cache bị cũ" | |
| [ ] | 12 | Redis: dữ liệu dùng chung | `RateLimitFilter` chuyển từ `HashMap` sang Redis | Bắt buộc khi chạy 2 bản gateway | |
| [ ] | 29 | Load test | k6: máy 2 GB chịu được bao nhiêu request mỗi giây | Biết giới hạn trước khi người dùng tìm ra | |

## Giai đoạn 3. Giao tiếp giữa các service và thiết kế API

Kết quả: các service phối hợp với nhau mà vẫn độc lập; app cũ và app mới cùng chạy được.

| Xong | # | Chủ đề | Bài thực hành trên VisionFit | Vì sao cần | Ghi chú |
|---|---|---|---|---|---|
| [ ] | 14 | Message broker, event | RabbitMQ: identity phát `UserDeleted`, profile và meal tự xoá dữ liệu của user | Không có FK sang DB khác (bài D6) | |
| [ ] | 15 | Outbox pattern | Lưu event vào bảng `outbox` trong cùng transaction, rồi mới gửi | Tránh "đã lưu DB nhưng gửi event thất bại" | |
| [ ] | 16 | Gọi đồng bộ giữa service | `RestClient` với timeout, retry, circuit breaker (Resilience4j) | Khi nào gọi trực tiếp, khi nào dùng event | |
| [ ] | 17 | Hàng đợi công việc | Job phân tích ảnh đi qua hàng đợi thay vì thread pool trong bộ nhớ | Nhiều worker; không mất job khi restart | |
| [ ] | 18 | API versioning | `/api/v2/meals` song song với `v1`, kèm lộ trình ngừng `v1` | App trên điện thoại không cập nhật cùng lúc | |
| [ ] | 19 | OpenAPI / Swagger | Sinh tài liệu API tự động từ code | "Hợp đồng" giữa backend và app | |
| [ ] | 20 | Phân trang, chuẩn lỗi | Phân trang lịch sử bữa ăn; thống nhất định dạng lỗi (bug 10.A) | | |

## Giai đoạn 4. Quan sát hệ thống và bảo mật

Kết quả: thấy được hệ thống đang chạy ra sao; chặn được các tấn công phổ biến.

| Xong | # | Chủ đề | Bài thực hành trên VisionFit | Vì sao cần | Ghi chú |
|---|---|---|---|---|---|
| [ ] | 26 | Metrics | Micrometer + Prometheus + Grafana: số request, độ trễ, RAM | Biết hệ thống **đang** thế nào, không chỉ khi có lỗi | |
| [ ] | 27 | Distributed tracing | OpenTelemetry: một request thành một dòng thời gian qua gateway → service | Tìm ra chặng nào chậm | |
| [ ] | 28 | Cảnh báo | Báo về điện thoại khi `api.baofit.tech` sập | Hiện tại server sập thì không ai biết | |
| [ ] | 21 | JWT nâng cao | HS256 → RS256 + JWKS | Service bị lộ cũng không ký được token giả (mục 9.3) | |
| [ ] | 22 | Refresh token rotation | Code đúng luồng phát hiện token bị đánh cắp đã thiết kế | Áp dụng bài thiết kế DB auth | |
| [ ] | 23 | OWASP Top 10 | Rà project theo 10 lỗi bảo mật phổ biến nhất | Phỏng vấn nào cũng hỏi | |
| [ ] | 24 | WAF, rate limit ở Cloudflare | Luật giới hạn request theo IP ngay ở Cloudflare | Chặn từ xa, trước khi chạm máy chủ | |

## Giai đoạn 5. Mở rộng

Kết quả: sẵn sàng khi có nhiều người dùng.

| Xong | # | Chủ đề | Bài thực hành trên VisionFit | Vì sao cần | Ghi chú |
|---|---|---|---|---|---|
| [ ] | 4 | Quản lý secret | `.env` → GitHub Secrets hoặc AWS SSM Parameter Store | Secret đang chỉ nằm trên một máy | |
| [ ] | 5 | Nhiều môi trường | Thêm môi trường staging trước production | Thử bản mới mà không ảnh hưởng người dùng thật | |
| [ ] | 30 | Chạy nhiều bản | 2 bản meal-service phía sau gateway | Hiểu "stateless" trong thực tế | |
| [ ] | 31 | Deploy không gián đoạn | Health check, graceful shutdown, rolling update | Hiện mỗi lần deploy là API tạm ngừng | |
| [ ] | 13 | Object storage | Ảnh bữa ăn lưu trên S3 thay cho `bytea` | Ghi chú `ponytail` ở analysis-service | |
| [ ] | 10 | Concurrency trong DB | Isolation level, `@Version`, `SKIP LOCKED`; sửa bug 7.C | Lỗi check-then-act đã gặp ở 6 chỗ | |
| [ ] | 8 | Migration khi đang chạy | Đổi tên một cột theo kiểu expand/contract | Sửa schema mà app đang chạy không lỗi | |
| [ ] | 6 | Infrastructure as Code | Mô tả EC2, Security Group bằng Terraform | Dựng lại hạ tầng bằng một lệnh | |
| [ ] | 32 | Kubernetes cơ bản | Chạy project trên k3s | Bước tiếp theo sau Docker Compose | |
| [ ] | 34 | Test end-to-end | Bộ test gọi thẳng `https://api.baofit.tech` sau mỗi lần deploy | Biết ngay bản vừa deploy có chạy không | |

---

## Giai đoạn 6. Đi sâu (chọn 2–3 chủ đề sau khi xong giai đoạn 1–5)

| Chủ đề chọn | Vì sao chọn | Mục tiêu đi sâu |
|---|---|---|
| | | |
| | | |
| | | |

## Tài liệu liên quan trong repo

- [`backend-deep-dive.md`](backend-deep-dive.md): mổ xẻ 4 service, có các bug được đánh số (6.A, 7.B, 7.C, 9.A, 10.A…) dùng làm bài thực hành ở trên.
- [`deploy-aws.md`](deploy-aws.md): cách hệ thống đang được deploy.
- [`edge-stack-quiz.md`](edge-stack-quiz.md): câu hỏi ôn về gateway, nginx, Cloudflare.
