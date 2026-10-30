# Bàn giao buổi học: tiếp tục trên máy khác

> Cập nhật: 2026-10-30 (buổi học trên Mac). File này để **một phiên Claude mới** (trên máy khác) đọc và học tiếp đúng chỗ đang dừng.
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
- **Giải thích dày đặc** (nhiều bảng, code, sơ đồ trong cùng một lượt) làm người học rối ("giải thích gì ko hiểu j hết"). Dùng **một hình ảnh đời thường** rồi vài bước ngắn. Các hình ảnh đã hiệu quả: SSH = bảo vệ cổng (Security Group) + ổ khoá/chìa (public/private key); Security Group = bảo vệ xem biển số xe, IAM = nhân viên quầy xem thẻ; container = thùng container chở hàng; S3 = nhà kho / két sắt; biến môi trường `PATH` = túi đồ / các ngăn kéo để tìm lệnh.
- Người học **hay bỏ qua câu hỏi kiểm tra** (nhắn "tiếp"): lượt sau đưa đáp án ngắn rồi đi tiếp, đừng hỏi lại.
- Người học **hay gõ nhầm máy**: lệnh của server lại chạy trên laptop. Ô `!` trong Claude Code luôn chạy trên laptop. Nhắc nhìn dấu nhắc: `ubuntu@ip-172-31-…` là server.

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
| SSH | `ssh -i <key> ubuntu@3.26.113.232` (IP **đổi** mỗi lần stop/start máy). **Mỗi máy một key, không có trong git:** Windows dùng file `.pem` tải lúc tạo EC2; Mac dùng `~/.ssh/visionfit_mac` (ed25519, có passphrase, public key đã thêm vào `~/.ssh/authorized_keys` trên server). Security Group cho cổng 22: IP Mac `101.99.41.30/32` + prefix list `ec2-instance-connect` (nút *Connect* trên web). Vào từ mạng mới mà bị `timed out` thì thêm IP đó vào Security Group, hoặc vào bằng nút *Connect* trên web |
| Backup | Bucket S3 `visionfit-backup-dien-2026` (Sydney, chặn public). IAM role `visionfit-backup-role` gắn vào instance, inline policy `put-backups` **chỉ** cho `s3:PutObject` trên `bucket/*`. `aws-cli` cài bằng snap. Cron của user `ubuntu`: `0 20 * * *` (20:00 UTC = 03:00 giờ VN) chạy [`infra/ec2/backup.sh`](../infra/ec2/backup.sh), log ở `~/backups/backup.log`, bản trên máy giữ 7 ngày |
| Secret | `.env` chỉ nằm trên server và laptop cũ, **không có trong git** |

Máy laptop cũ có vài đặc thù **không mang sang máy mới**: cổng 8080 bị `PEMHTTPD` chiếm nên gateway chạy 8090; nginx cài ở `C:\nginx`. Trên máy mới không cần quan tâm.

## 3. Đã học (tới 2026-10-29)

- Xây 5 service, idempotency, race condition, `@EntityGraph`, job bất đồng bộ, gateway, filter, rate limit, nginx, Cloudflare, deploy EC2. Tài liệu: [`backend-deep-dive.md`](backend-deep-dive.md), [`edge-stack-quiz.md`](edge-stack-quiz.md).
- **Bài thiết kế DB cho auth** (kiểu phỏng vấn), các lỗi đã sửa: nhảy vào vẽ bảng trước khi hỏi yêu cầu; Base64 không phải băm, băm khác mã hoá; `UNIQUE` của Postgres phân biệt hoa thường (code đổi chữ thường + `CHECK (email = lower(email))`); `UNIQUE (a, b)` khác `UNIQUE (a)` + `UNIQUE (b)`; `updated_at` không thay được `password_changed_at` (một cột một ý nghĩa); `revoked_at` khác `expires_at`; partial unique index `UNIQUE (account_id) WHERE revoked_at IS NULL` cho luật "một thiết bị"; cú pháp `CHECK ((a IS NULL) = (b IS NULL))`.
- Đã lập **lộ trình 34 chủ đề**, 5 giai đoạn: [`learning-roadmap.md`](learning-roadmap.md).
- **Buổi 2026-10-30 (Mac):** SSH (2 lớp: Security Group chặn theo IP, key chặn theo danh tính; `timed out` khác `Permission denied`; `authorized_keys`; passphrase); Security Group khác IAM; EC2 (dịch vụ) / instance (máy ảo) / server (vai trò, và cũng là chương trình); container khác máy ảo (chung kernel), image, volume; S3 là object storage (khác Firestore); IAM role, policy, quyền tối thiểu; `pg_dumpall` khác `pg_dump`; đọc cấu trúc file dump (CREATE TABLE → COPY → constraint/index); cron (5 ô thời gian, đồng hồ UTC, `PATH` rút gọn, chuyển hướng log).

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

6. **Hai quyết định đã chốt (2026-10-30):**
   - **Ai chạy backup:** `cron` trên EC2. Người học trả lời: "mang theo khi dựng máy mới" đúng một nửa (cron có sẵn, nhưng dòng hẹn giờ không nằm trong git); "dễ thấy còn sống" đúng (container có `ps`/`logs`, cron im lặng); "gọi Postgres" và "dùng thẻ IAM" chưa biết, đã giải thích (`docker compose exec` chui vào container nên không cần mật khẩu; `aws-cli` trên máy dùng được role ngay). Vá điểm yếu của cron: script nằm trong git, ghi dòng cron ở đầu script, chuyển hướng ra file log.
   - **Quyền ghi S3:** IAM role chỉ `s3:PutObject`. Đáp án này được **đưa thẳng, người học chưa tự lập luận**: buổi sau hỏi lại để ôn "vì sao role tốt hơn access key trong `.env`" và "vì sao không cho quyền xoá".
7. **Backup tay trên server → `scp` về Mac → restore thử** vào container `postgres:18-alpine` trống trên Mac: 4 DB về đủ, đúng chủ, chỉ có lỗi `role "postgres" already exists` (bình thường).
8. **S3 + IAM:** đã tạo bucket và role, gắn role vào instance. `aws sts get-caller-identity` ra `assumed-role/visionfit-backup-role/i-…`; upload thử vào `test/` thành công.
9. **Tự động:** [`infra/ec2/backup.sh`](../infra/ec2/backup.sh) và cron 20:00 UTC. Chạy tay trên server thành công (`daily/visionfit-2026-10-30-0922.sql.gz`). Chạy thử trong môi trường giống cron (`env -i HOME=$HOME PATH=/usr/bin:/bin ./infra/ec2/backup.sh <bucket>`): đi tới bước upload thì báo `NoSuchBucket` vì gõ thừa chữ `v` trong tên bucket. Phần quan trọng (tìm được `docker` và `aws` khi `PATH` rút gọn) đã đạt.

### ĐANG DỪNG Ở ĐÂY
1. **Mở đầu buổi sau:** trên server chạy `cat ~/backups/backup.log`. Phải có dòng `… ok …` từ lần cron tự chạy đầu tiên (20:00 UTC ngày 2026-10-30, tức 03:00 VN ngày 31), và trên S3 console phải có file mới trong `daily/`. Nếu lỗi thì đọc log cùng người học.
2. Chưa thấy tận mắt: `aws s3 ls s3://visionfit-backup-dien-2026 --region ap-southeast-2` trên server phải ra `AccessDenied`. Lần trước người học quên thay `TEN-BUCKET` nên ra `NoSuchBucket`.
3. **Bước 9, dọn bản cũ.** Theo vòng dạy: nêu vấn đề (mọi bản đều nằm trong `daily/` và được giữ mãi; server không có quyền xoá), rồi hỏi người học sẽ làm luật 7 ngày + 4 tuần + 3 tháng thế nào. *Hướng đúng (đừng nói trước):* script chọn thư mục theo ngày (`monthly/` vào ngày 1, `weekly/` vào Chủ nhật, còn lại `daily/`), và S3 Lifecycle rule xoá theo từng thư mục sau 7/28/90 ngày. S3 tự xoá nên máy vẫn không cần quyền xoá.

### Các bước còn lại của chủ đề 7
| Bước | Việc |
|---|---|
| 7 | ✅ Tạo bucket S3; cấp quyền **chỉ ghi, không xoá** cho máy |
| 8 | ✅ Viết script sao lưu, chạy tự động mỗi ngày |
| 9 | Dọn bản cũ theo luật 7 ngày + 4 tuần + 3 tháng |
| 10 | Khôi phục thử tự động mỗi tuần, báo lỗi nếu hỏng |
| 11 | Đánh dấu `[x]` chủ đề 7 trong `learning-roadmap.md` |

Sau chủ đề 7, theo lộ trình giai đoạn 1: **CI (1) → Registry (3) → CD (2) → Log + request id (25)**.

## 5. Việc còn treo (không gấp)

- App (`ApiConfig` trong repo BaoFit) chưa trỏ về `https://api.baofit.tech`; repo app chưa có phần nối analysis-service.
- Cloudflare: nên bật *SSL/TLS → Edge Certificates → Always Use HTTPS*.
- Bucket còn thư mục `test/` (file upload thử). Xoá trên console khi tiện; server không có quyền xoá.
- Trên Mac còn `~/Downloads/visionfit-2026-10-30-0656.sql` và `.sql.gz` (dữ liệu thật): giữ làm "bản ngoài AWS" theo 3-2-1 hoặc xoá. Không đưa vào git.
- Lúc SSH vào, server báo RAM 67% và swap 46%: tư liệu cho chủ đề Metrics và Cảnh báo.
- Bug đã biết, dùng làm bài tập: xem bảng cuối [`backend-deep-dive.md`](backend-deep-dive.md) (6.A, 7.B, 7.C, 9.A, 10.A…).
