# Bàn giao buổi học: tiếp tục trên máy khác

> Cập nhật: 2026-10-29. File này để **một phiên Claude mới** (trên máy khác) đọc và học tiếp đúng chỗ đang dừng.
> **Cách dùng trên máy mới:** `git pull` repo `visionfit-backend`, mở Claude Code trong thư mục repo, rồi nhắn:
> *"Đọc `docs/session-handoff.md` và `docs/learning-roadmap.md`, rồi tiếp tục dạy tôi chủ đề 7 (Backup) từ bước đang dừng."*

---

## 1. Người học và cách dạy (bắt buộc làm theo)

- Người học là người Việt, **trả lời bằng tiếng Việt**. Đây là dự án microservices đầu tiên, vững Spring Boot Java ở mức trung bình.
- **Cách dạy cho mỗi chủ đề trong lộ trình, theo đúng vòng này:**
  1. Nêu **vấn đề** trước (tình huống cụ thể trên VisionFit).
  2. **Hỏi người học hướng đi** trước khi đưa đáp án.
  3. Người học trình bày, rồi **sửa bài**: chấm từng câu, nói rõ chỗ đúng, chỗ sai.
  4. **Phân tích** vì sao, đưa khái niệm chuẩn (bảng so sánh rất hiệu quả).
  5. **Dẫn đi đúng hướng**, làm thực hành.
- **Không đưa lời giải trước khi người học đề xuất.**
- **Lệnh terminal:** người học thấy lệnh "khá nâng cao" và muốn tự làm lại được. Mỗi lệnh phải **tách thành từng mảnh** (chương trình, cờ, tham số) và giải thích từng mảnh; đi **từng bước một**, chờ người học nhắn "xong". Người học dùng **PowerShell 5.1** trên Windows: gọi `curl.exe` (không phải `curl`); JSON trong `-d` phải escape `\"`.
- Người học hay hỏi chen ngang: trả lời câu hỏi đó trước, rồi kéo về mạch chính và nhắc đang ở bước nào.
- Thích **học chủ động**: câu hỏi trước, tranh luận "phương án của bạn / phản biện", bảng so sánh theo từng service, bug thật làm bài tập.

## 2. Dự án và hệ thống đang chạy

| Thứ | Giá trị |
|---|---|
| Repo backend | `github.com/Diendien1801/visionfit-backend` (nhánh `main`) |
| Repo app (KMP) | `github.com/Diendien1801/BaoFit` |
| Service | identity 8081 · profile 8082 · meal 8083 · analysis 8084 · gateway 8080 (Spring Boot 4.1.1, Java 21) |
| Production | `https://api.baofit.tech`, chạy thật |
| Hạ tầng | AWS EC2 **Sydney (ap-southeast-2), t4g.small** (2 GB RAM + 2 GB swap), Ubuntu 26.04, Free plan (100 USD credit, ~6 tháng) |
| Đường vào | Điện thoại → Cloudflare → Cloudflare Tunnel (`baofit`) → container `cloudflared` → `nginx:8000` → `gateway:8080` → các service → **một** container Postgres chứa 4 database |
| Deploy | `docker-compose.prod.yml`; trên server chạy `./infra/ec2/deploy.sh` (build jar trong container JDK rồi `up -d --build`). Hướng dẫn: [`deploy-aws.md`](deploy-aws.md) |
| SSH | `ssh -i <key> ubuntu@<IP>`. File key là `visionfit1.pem` (**không có trong git**; trên máy mới phải chép key sang, hoặc tạo key mới trên AWS). IP công khai **đổi** mỗi lần stop/start máy |
| Secret | `.env` chỉ nằm trên server và laptop cũ, **không có trong git** |

Máy laptop cũ có vài đặc thù **không mang sang máy mới**: cổng 8080 bị `PEMHTTPD` chiếm nên gateway chạy 8090; nginx cài ở `C:\nginx`. Trên máy mới không cần quan tâm.

## 3. Đã học (tới 2026-10-29)

- Xây 5 service, idempotency, race condition, `@EntityGraph`, job bất đồng bộ, gateway, filter, rate limit, nginx, Cloudflare, deploy EC2. Tài liệu: [`backend-deep-dive.md`](backend-deep-dive.md), [`edge-stack-quiz.md`](edge-stack-quiz.md).
- **Bài thiết kế DB cho auth** (kiểu phỏng vấn), các lỗi đã sửa: nhảy vào vẽ bảng trước khi hỏi yêu cầu; Base64 không phải băm, băm khác mã hoá; `UNIQUE` của Postgres phân biệt hoa thường (code đổi chữ thường + `CHECK (email = lower(email))`); `UNIQUE (a, b)` khác `UNIQUE (a)` + `UNIQUE (b)`; `updated_at` không thay được `password_changed_at` (một cột một ý nghĩa); `revoked_at` khác `expires_at`; partial unique index `UNIQUE (account_id) WHERE revoked_at IS NULL` cho luật "một thiết bị"; cú pháp `CHECK ((a IS NULL) = (b IS NULL))`.
- Đã lập **lộ trình 34 chủ đề**, 5 giai đoạn: [`learning-roadmap.md`](learning-roadmap.md).

## 4. Đang học: chủ đề 7, Backup và restore

### Đã xong
1. **Vấn đề:** dữ liệu chỉ nằm ở một Docker volume trên EC2, không có bản sao. 6 tình huống mất dữ liệu: `docker compose down -v`, migration `DELETE` thiếu `WHERE`, máy/ổ hỏng, hết credit nên tài khoản bị khoá, ransomware, bug âm thầm ghi sai nhiều ngày.
2. **Người học đề xuất** (copy thư mục volume, lưu về DB local, 1 ngày 1 lần, không biết giữ bao nhiêu bản, không biết kiểm tra) và **đã được sửa**.
3. **Chép file khác `pg_dump`:** chép file là copy **cả** `$PGDATA` (`/var/lib/postgresql/18/docker`; tên `docker` chỉ là tên thư mục image đặt), phải dừng Postgres, cùng phiên bản và cùng kiến trúc; copy riêng `base/<số>` thì vô dụng (thiếu `global/`, `pg_xact/`, `pg_wal/`, `pg_control`). `pg_dump` xuất ra file SQL đọc được, chạy được khi DB đang chạy.
4. **Thực hành local đã làm xong:** tạo DB `backup_lab` trong container `identity-db`, xem file nhị phân của bảng bằng `od -c` (hiểu checkpoint: dữ liệu nằm trong RAM trước), `pg_dump` ra SQL, `DELETE` sạch, `DROP`/`CREATE DATABASE`, khôi phục bằng `psql -f`, dữ liệu quay lại.
5. **Kế hoạch đã chốt cho VisionFit:**

| Câu hỏi | Đáp án | Khái niệm |
|---|---|---|
| Sao lưu cái gì | `pg_dumpall` (4 DB + user), nén gzip. `.env` giữ riêng, có bảo mật | |
| Lưu ở đâu | Trên máy + S3 + thỉnh thoảng một bản ngoài AWS | Quy tắc 3-2-1 |
| Bao lâu | Mỗi ngày | RPO = mất tối đa bao nhiêu dữ liệu |
| Giữ bao nhiêu | 7 ngày + 4 tuần + 3 tháng | Giữ lâu hơn thời gian một bug nằm im |
| Kiểm tra | Khôi phục thử tự động mỗi tuần | RTO; "backup chưa thử khôi phục thì không phải backup" |

### ĐANG DỪNG Ở ĐÂY: chờ người học trả lời 2 quyết định
Người học **chưa trả lời**. Hỏi lại 2 câu này (theo vòng dạy ở mục 1, không đưa đáp án trước):

1. **Ai chạy `pg_dumpall` mỗi ngày?** (a) `cron` trên máy EC2, hay (b) một container `backup` riêng trong `docker-compose.prod.yml`? Gợi ý cân nhắc: dựng lại server mới thì cách nào dễ mang theo; cách nào dễ thấy nó còn chạy hay đã chết; mỗi cách gọi tới Postgres (đang ở trong container) bằng cách nào.
2. **EC2 lấy quyền ghi S3 bằng cách nào?** (a) access key dán vào `.env`, hay (b) IAM role gắn vào máy EC2? Gợi ý cân nhắc: kẻ xấu đọc được `.env` thì mang được gì ra khỏi máy; tình huống ransomware: quyền nên là "chỉ ghi thêm" hay cả "xoá".

### Các bước còn lại của chủ đề 7
| Bước | Việc |
|---|---|
| 7 | Tạo bucket S3; cấp quyền **chỉ ghi, không xoá** cho máy |
| 8 | Viết script sao lưu, chạy tự động mỗi ngày |
| 9 | Dọn bản cũ theo luật 7 ngày + 4 tuần + 3 tháng |
| 10 | Khôi phục thử tự động mỗi tuần, báo lỗi nếu hỏng |
| 11 | Đánh dấu `[x]` chủ đề 7 trong `learning-roadmap.md` |

Sau chủ đề 7, theo lộ trình giai đoạn 1: **CI (1) → Registry (3) → CD (2) → Log + request id (25)**.

## 5. Việc còn treo (không gấp)

- App (`ApiConfig` trong repo BaoFit) chưa trỏ về `https://api.baofit.tech`; repo app chưa có phần nối analysis-service.
- Cloudflare: nên bật *SSL/TLS → Edge Certificates → Always Use HTTPS*.
- Bug đã biết, dùng làm bài tập: xem bảng cuối [`backend-deep-dive.md`](backend-deep-dive.md) (6.A, 7.B, 7.C, 9.A, 10.A…).
