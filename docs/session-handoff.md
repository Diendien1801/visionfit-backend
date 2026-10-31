# Bàn giao buổi học: tiếp tục trên máy khác

> Cập nhật: 2026-10-31 (buổi học trên Mac). File này để **một phiên Claude mới** (trên máy khác) đọc và học tiếp đúng chỗ đang dừng.
> **Cách dùng trên máy mới:** `git pull` repo `visionfit-backend`, mở Claude Code trong thư mục repo, rồi nhắn:
> *"Đọc `docs/session-handoff.md` và `docs/learning-roadmap.md`, rồi tiếp tục dạy tôi chủ đề 25 (Log + request id)."*

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
- **Phân việc:** Claude viết code; người học **tự làm** mọi thao tác trên web (AWS, GitHub, Cloudflare) và trên server, tự `git commit`/`push`. Không bao giờ hỏi hay nhận key, mật khẩu, token qua chat.
- **Việc trên web:** đưa **đường bấm** (thanh tìm kiếm → dịch vụ → menu trái → nút), **không** đưa link thẳng: người học muốn biết mọi thứ nằm ở đâu.
- **Lệnh terminal:** mỗi lệnh phải **tách thành từng mảnh** (chương trình, cờ, tham số) và giải thích từng mảnh; đi **từng bước một**, chờ người học nhắn "xong". Trên Windows người học dùng **PowerShell 5.1**: gọi `curl.exe` (không phải `curl`); JSON trong `-d` phải escape `\"`.
- Người học hay hỏi chen ngang: trả lời câu hỏi đó trước, rồi kéo về mạch chính và nhắc đang ở bước nào.
- **Giải thích dày đặc** (nhiều bảng, code, sơ đồ trong cùng một lượt) làm người học rối. Dùng **một hình ảnh đời thường** rồi vài bước ngắn. Các hình ảnh đã hiệu quả:
  - SSH = bảo vệ cổng (Security Group) + ổ khoá/chìa (public/private key); Security Group = bảo vệ xem biển số xe, IAM = nhân viên quầy xem thẻ.
  - Container = thùng container chở hàng; image = bản thiết kế / hộp cơm đóng sẵn; volume = tủ gửi đồ ở quầy lễ tân; S3 = nhà kho / két sắt; `PATH` = các ngăn kéo để tìm lệnh.
  - CI/CD = nhà máy (GitHub Actions) → kho (GHCR) → tàu (server).
  - IAM role gắn vào máy = **thẻ nhân viên** máy đang đeo, trên thẻ ghi danh sách quyền; thêm quyền = ghi thêm dòng lên thẻ.
  - SSM agent = **nhân viên chi nhánh tự gọi về trụ sở** hỏi "có việc không?" (giống `cloudflared`): chỉ gọi ra, nên không mở cổng. SSM document = **phiếu việc in sẵn**.
  - OIDC = **căn cước công dân** GitHub cấp cho từng job, ghi rõ repo và nhánh.
- Người học **hay bỏ qua câu hỏi kiểm tra** (nhắn "tiếp"): lượt sau đưa đáp án ngắn rồi đi tiếp, đừng hỏi lại.
- **Không bày bài demo** cho thứ người học đã trải nghiệm (ví dụ cố tình làm hỏng test để xem CI đỏ): chuyển sang chủ đề tiếp.
- Người học **hay gõ nhầm máy**: lệnh của server lại chạy trên laptop. Ô `!` trong Claude Code luôn chạy trên laptop. Nhắc nhìn dấu nhắc: `ubuntu@ip-172-31-…` là server.

## 2. Dự án và hệ thống đang chạy

| Thứ | Giá trị |
|---|---|
| Repo backend | `github.com/Diendien1801/visionfit-backend` (nhánh `main`) |
| Repo app (KMP) | `github.com/Diendien1801/BaoFit` |
| Service | identity 8081 · profile 8082 · meal 8083 · analysis 8084 · gateway 8080 (Spring Boot 4.1.1, Java 21) |
| Production | `https://api.baofit.tech`, chạy thật |
| Hạ tầng | AWS EC2 **Sydney (ap-southeast-2), t4g.small** (ARM, 2 GB RAM + 2 GB swap), Ubuntu 26.04, instance `i-09892524bb422eacd`, tài khoản `796468524979`. Tài khoản loại **giao diện rút gọn** (Free plan, có spend limit): một số tính năng IAM bị khoá. **Đừng** bấm *Activate advanced features* (không đảo ngược, mất spend limit) |
| Đường vào | Điện thoại → Cloudflare → Cloudflare Tunnel → container `cloudflared` → `nginx:8000` → `gateway:8080` → các service → **một** container Postgres chứa 4 database |
| CI | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml), runner `ubuntu-24.04-arm`: mỗi lần push chạy `./gradlew test bootJar` (39 test, H2) |
| Image | Chỉ trên `main`: CI build 5 image ARM, đẩy lên `ghcr.io/diendien1801/visionfit-<service>` (public), tag 7 ký tự commit + `latest` |
| Deploy (CD) | Chỉ trên `main`, sau khi đẩy image: CI gửi SSM document `VisionfitDeploy` ([`infra/ec2/ssm-deploy.yml`](../infra/ec2/ssm-deploy.yml)), agent trên server chạy [`deploy.sh`](../infra/ec2/deploy.sh) bằng user `ubuntu` (`git pull` → `compose pull` → `up -d`, không build), CI in log và báo xanh/đỏ. Bằng tay: `./infra/ec2/deploy.sh`; quay về bản cũ: `./infra/ec2/deploy.sh <7 ký tự commit>`. Hướng dẫn và sơ đồ: [`deploy-aws.md`](deploy-aws.md) Bước 6 và 8 ([`diagrams/cicd-flow.svg`](diagrams/cicd-flow.svg)) |
| IAM | Role `visionfit-backup-role` **gắn vào instance**: inline `put-backups` (chỉ `s3:PutObject` lên bucket backup) + `AmazonSSMManagedInstanceCore` (cho SSM agent gọi ra SSM). IAM user `github-deploy` (không đăng nhập web): inline `send-deploy` chỉ `ssm:SendCommand` document `VisionfitDeploy` lên đúng instance + `ssm:GetCommandInvocation`; access key nằm trong GitHub Secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` |
| SSH | `ssh -i <key> ubuntu@3.26.113.232` (IP **đổi** mỗi lần stop/start máy). **Mỗi máy một key, không có trong git:** Windows dùng file `.pem` tải lúc tạo EC2; Mac dùng `~/.ssh/visionfit_mac` (ed25519, có passphrase). Security Group cho cổng 22: IP Mac `101.99.41.30/32` + prefix list `ec2-instance-connect` (nút *Connect* trên web). Vào từ mạng mới mà bị `timed out` thì thêm IP đó vào Security Group, hoặc vào bằng nút *Connect* trên web |
| Backup | Bucket S3 `visionfit-backup-dien-2026` (Sydney, chặn public). Cron của user `ubuntu`: `0 20 * * *` (03:00 giờ VN) chạy [`infra/ec2/backup.sh`](../infra/ec2/backup.sh): `pg_dumpall` nén gzip, lên `monthly/` (ngày 1), `weekly/` (Chủ nhật) hoặc `daily/`; S3 lifecycle xoá sau 90/28/7 ngày; ping Healthchecks.io (`BACKUP_HEALTHCHECK_URL` trong `.env`) để có email khi một đêm không chạy. Log ở `~/backups/backup.log` |
| Secret | `.env` chỉ nằm trên server và laptop cũ, **không có trong git**, không nằm trong image: container đọc lúc khởi động |

Máy laptop Windows cũ có vài đặc thù **không mang sang máy mới**: cổng 8080 bị `PEMHTTPD` chiếm nên gateway chạy 8090; nginx cài ở `C:\nginx`.

## 3. Đã học

- Xây 5 service, idempotency, race condition, `@EntityGraph`, job bất đồng bộ, gateway, filter, rate limit, nginx, Cloudflare, deploy EC2. Tài liệu: [`backend-deep-dive.md`](backend-deep-dive.md), [`edge-stack-quiz.md`](edge-stack-quiz.md).
- **Bài thiết kế DB cho auth** (kiểu phỏng vấn): băm khác mã hoá, `UNIQUE` phân biệt hoa thường, `UNIQUE (a, b)` khác `UNIQUE (a)` + `UNIQUE (b)`, một cột một ý nghĩa, `revoked_at` khác `expires_at`, partial unique index, `CHECK ((a IS NULL) = (b IS NULL))`.
- Đã lập **lộ trình 34 chủ đề**, 5 giai đoạn: [`learning-roadmap.md`](learning-roadmap.md).
- **2026-10-30:** SSH (Security Group chặn theo IP, key chặn theo danh tính; `timed out` khác `Permission denied`); Security Group khác IAM; EC2 / instance / server; container khác máy ảo, image, volume; S3 là object storage; IAM role, policy, quyền tối thiểu; `pg_dumpall` khác `pg_dump`; cron (5 ô thời gian, đồng hồ UTC, `PATH` rút gọn, chuyển hướng log).
- **Chủ đề 7 Backup (xong 2026-10-30):** 3-2-1, RPO/RTO, restore thử bằng tay, chia tầng daily/weekly/monthly + S3 lifecycle (server không cần quyền xoá), Healthchecks.io.
- **2026-10-31, chủ đề 1 CI:** GitHub Actions chạy test trên máy sạch; lần đầu đỏ vì `gradlew` commit từ Windows thiếu quyền chạy (`chmod +x`).
- **2026-10-31, chủ đề 3 Registry:** jar, JVM/JRE/JDK, bytecode; image build trên x86 không chạy được trên máy ARM (`exec format error`) nên CI dùng runner ARM; tag theo commit để quay về bản cũ; secret nạp lúc chạy chứ không nằm trong image; 502 Bad Gateway = người đứng giữa (Cloudflare, nginx, gateway) không gọi được người phía sau, ví dụ lúc JVM đang khởi động lại sau deploy.
- **2026-10-31, chủ đề 2 CD:** SSM (agent chỉ gọi ra; role của máy cần `AmazonSSMManagedInstanceCore`; document giới hạn đúng một việc; Run Command); OIDC (căn cước ngắn hạn, AWS tin chữ ký GitHub, trust policy chỉ nhận đúng repo + nhánh); tài khoản giao diện rút gọn khoá OIDC nên dùng IAM user + access key, bù lại bằng quyền hẹp.

## 4. ĐANG DỪNG Ở ĐÂY

Giai đoạn 1 đã xong 4/5 chủ đề: **Backup (7) → CI (1) → Registry (3) → CD (2)**. Push lên `main` giờ tự test, tự build image, tự deploy; lần deploy tự động đầu tiên đã **Success** (2026-10-31).

1. **Câu ôn mở đầu buổi sau** (người học chưa trả lời): *"Server dùng role gắn vào máy, còn GitHub phải dùng access key. Vì sao server không cần key mà GitHub lại cần?"* Đáp án: server nằm trong AWS nên đeo được role, AWS tự cấp key tạm và tự đổi; GitHub ở ngoài AWS nên không đeo được role của EC2. OIDC lẽ ra thay được key, nhưng tài khoản này khoá nó, nên dùng key vĩnh viễn và giới hạn quyền tới mức lộ ra cũng chỉ deploy lại được `main`.
2. **Chủ đề tiếp theo: 25, Log có cấu trúc + request id.** Theo vòng dạy: nêu vấn đề trước (một request lỗi đi qua gateway → service; log 5 container nằm riêng, không biết dòng nào thuộc request nào), rồi hỏi người học sẽ lần theo một request thế nào. *Hướng đúng (đừng nói trước):* gateway gắn header `X-Request-Id` (hoặc giữ lại nếu đã có), mỗi service đọc header đó đưa vào MDC để mọi dòng log có id; trả id về client để báo lỗi kèm id.

## 5. Việc còn treo (không gấp)

- Chủ đề 7: **chưa làm khôi phục thử tự động** mỗi tuần. Chưa thấy tận mắt `aws s3 ls s3://visionfit-backup-dien-2026 --region ap-southeast-2` trên server ra `AccessDenied` (server chỉ được ghi).
- Chưa thử **quay về bản cũ** (`./infra/ec2/deploy.sh <tag>`): giờ đã có hơn một bản image trên GHCR nên thử được.
- Dọn đồ của cách deploy cũ trên server: `docker volume rm visionfit-gradle && rm -rf ~/visionfit-backend/dist`.
- Hai push liên tiếp quá nhanh: lần deploy đầu có thể đỏ `manifest unknown` (server kéo code mới hơn image đã build). Lần sau tự sửa.
- App (`ApiConfig` trong repo BaoFit) chưa trỏ về `https://api.baofit.tech`; repo app chưa có phần nối analysis-service.
- Cloudflare: nên bật *SSL/TLS → Edge Certificates → Always Use HTTPS*.
- Bucket còn thư mục `test/` (file upload thử). Xoá trên console khi tiện; server không có quyền xoá.
- Trên Mac còn `~/Downloads/visionfit-2026-10-30-0656.sql` và `.sql.gz` (dữ liệu thật): giữ làm "bản ngoài AWS" theo 3-2-1 hoặc xoá. Không đưa vào git.
- Server báo RAM 67% và swap 46%: tư liệu cho chủ đề Metrics và Cảnh báo.
- Bug đã biết, dùng làm bài tập: xem bảng cuối [`backend-deep-dive.md`](backend-deep-dive.md) (6.A, 7.B, 7.C, 9.A, 10.A…).
