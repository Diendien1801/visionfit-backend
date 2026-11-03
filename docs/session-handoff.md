# Bàn giao buổi học: tiếp tục trên máy khác

> Cập nhật: 2026-11-03 (buổi học trên Mac). File này để **một phiên Claude mới** (trên máy khác) đọc và học tiếp đúng chỗ đang dừng.
> **Cách dùng trên máy mới:** `git pull` repo `visionfit-backend`, mở Claude Code trong thư mục repo, rồi nhắn:
> *"Đọc `docs/session-handoff.md` và `docs/learning-roadmap.md`, rồi tiếp tục phần thực hành của chủ đề 35 (gom log trên CloudWatch), xong thì sang chủ đề 33."*

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
  - OIDC = **căn cước công dân** GitHub cấp cho từng job, ghi rõ repo và nhánh; access key của IAM user = **chìa khoá nhà**: ai cầm cũng mở được, ổ khoá không nhìn mặt.
  - MDC = **bảng tên dán trên áo** nhân viên (thread) đang xử lý request: ai ghi sổ (log) cũng tự chép bảng tên; `ThreadLocal` = **túi áo riêng** của mỗi nhân viên. Request id trên header = **mã vận đơn trên phong bì**: lễ tân thấy, người trong kho thì không.
- Khi người học nói **"chưa hiểu"**, đi đúng thứ tự họ đã yêu cầu: **nêu lại vấn đề bằng ví dụ cụ thể** → giải thích lại **từng câu trả lời** của họ → rồi mới tới phương án. Ví dụ đã hiệu quả: An và Bình cùng lỗi trong một giây, đặt log gateway và log meal-service cạnh nhau.
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
| IAM | Role `visionfit-backup-role` **gắn vào instance**: inline `put-backups` (chỉ `s3:PutObject` lên bucket backup) + `AmazonSSMManagedInstanceCore` (cho SSM agent gọi ra SSM) + inline `write-logs` (`logs:CreateLogStream`, `logs:PutLogEvents` vào log group `visionfit-prod`). IAM user `github-deploy` (không đăng nhập web): inline `send-deploy` chỉ `ssm:SendCommand` document `VisionfitDeploy` lên đúng instance + `ssm:GetCommandInvocation`; access key nằm trong GitHub Secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` |
| SSH | `ssh -i <key> ubuntu@3.26.113.232` (IP **đổi** mỗi lần stop/start máy). **Mỗi máy một key, không có trong git:** Windows dùng file `.pem` tải lúc tạo EC2; Mac dùng `~/.ssh/visionfit_mac` (ed25519, có passphrase). Security Group cho cổng 22: IP Mac `101.99.41.30/32` + prefix list `ec2-instance-connect` (nút *Connect* trên web). Vào từ mạng mới mà bị `timed out` thì thêm IP đó vào Security Group, hoặc vào bằng nút *Connect* trên web |
| Backup | Bucket S3 `visionfit-backup-dien-2026` (Sydney, chặn public). Cron của user `ubuntu`: `0 20 * * *` (03:00 giờ VN) chạy [`infra/ec2/backup.sh`](../infra/ec2/backup.sh): `pg_dumpall` nén gzip, lên `monthly/` (ngày 1), `weekly/` (Chủ nhật) hoặc `daily/`; S3 lifecycle xoá sau 90/28/7 ngày; ping Healthchecks.io (`BACKUP_HEALTHCHECK_URL` trong `.env`) để có email khi một đêm không chạy. Log ở `~/backups/backup.log` |
| Log | Mỗi dòng log có `[req=<X-Request-Id> user=<uid>]` (gateway và identity chỉ có `req`; `-` khi không có). Code: `config/LogContext.java` trong từng service, `logging.pattern.correlation` trong `application.properties`. Cả 8 container gửi log lên **CloudWatch Logs**, log group `visionfit-prod` (Sydney, giữ 1 tháng), mỗi container một stream (khối `x-logging` trong `docker-compose.prod.yml`). Tìm một request: CloudWatch → Logs Insights, `filter @message like "<id>"` ([`deploy-aws.md`](deploy-aws.md) Bước 9). Trên server `docker compose … logs` vẫn chạy, nhưng chỉ có log của container hiện tại |
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
- **2026-10-31, chủ đề 2 CD:** SSM (agent chỉ gọi ra; role của máy cần `AmazonSSMManagedInstanceCore`; document giới hạn đúng một việc; Run Command); OIDC (căn cước ngắn hạn, AWS tin chữ ký GitHub, trust policy chỉ nhận đúng repo + nhánh); tài khoản giao diện rút gọn khoá OIDC nên dùng IAM user + access key, bù lại bằng quyền hẹp. IAM user là một "tài khoản nhân viên"; access key ID ≈ tên đăng nhập, secret ≈ mật khẩu dùng để ký request; key chỉ chứng minh "đang cầm chìa", **không** chứng minh request đến từ GitHub.
- **2026-11-02, chủ đề 25 (khái niệm + code):** gateway sinh request id (UUID, mỗi lần bấm một mã, trả về app trong header) và gửi xuống service; service nhận được nhưng log ở tầng sâu (service, repository, Hibernate) không nhìn thấy request. MDC = Map riêng cho từng thread (`ThreadLocal`), Logback in ra qua `%X{requestId}`. Phải gỡ khi xong vì thread được dùng lại cho request khác; gỡ ở `requestDestroyed` (cuối cùng của Tomcat) chứ không trong `finally` của filter, vì Tomcat ghi stack trace của lỗi không ai bắt **sau** khi filter chạy xong. Thread khác (job phân tích ảnh) không thấy MDC nên chép sang bằng `TaskDecorator` (Spring Boot tự gắn vào executor). `userId` lấy từ JWT **sau** khi Spring Security đã kiểm; gateway không kiểm token nên không ghi user. Lúc chạy bình thường service gần như không log gì, nên đã thêm 2 dòng log kết quả cho job phân tích.
- **2026-11-03, sau chủ đề 25:** log theo **sự kiện**, không theo tầng (gateway ghi mọi request; service chỉ ghi lỗi và việc nền; muốn biết từng bước chậm bao nhiêu là việc của tracing, chủ đề 27). Mức log ERROR/WARN/INFO/DEBUG. "Ai nuốt lỗi thì người đó phải log": `@ExceptionHandler(Exception.class)` của identity trả 500 mà không ghi stack trace, và JSON hỏng cũng ra 500; đã sửa (log lỗi, JSON hỏng → 400, có test). Cách Spring chọn handler lỗi: `@ExceptionHandler` cụ thể nhất thắng; `ProblemDetailsExceptionHandler` (bật bằng `spring.mvc.problemdetails.enabled`) kế thừa `ResponseEntityExceptionHandler`, bắt sẵn khoảng 20 lỗi của Spring. Cây handler ("người bắt") khác cây exception ("thứ bị bắt").
- **2026-11-03, chủ đề 35 gom log:** người học đề xuất đẩy log lên S3 (lưu được nhưng không tìm được); chọn CloudWatch Logs vì Docker có sẵn driver `awslogs`. Log group = tủ, log stream = ngăn kéo (một container một ngăn, `tag: {{.Name}}` để ngăn không đổi tên qua mỗi deploy). IAM role của máy = thẻ nhân viên, Docker tự lấy chìa tạm, không có key nào. `mode: non-blocking`: mặc định (blocking) thì CloudWatch không liên lạc được sẽ làm treo lệnh ghi log của app; non-blocking chịu mất vài dòng để app vẫn chạy. Người học tự gây lỗi 500 trên production (đăng ký email 260 ký tự) và thấy: mỗi dòng stack trace là một event riêng không mang mã, nên tìm theo id chỉ ra dòng ERROR. Hibernate tự ghi 2 dòng WARN khi DB từ chối câu SQL nên lỗi DB vẫn lộ nguyên nhân; lỗi chỉ trong code (`NullPointerException`) thì không. Sửa bằng `awslogs-datetime-format` (dòng bắt đầu bằng ngày giờ mở event mới), chỉ cho 5 service Java: [`cloudwatch-stacktrace-lab.md`](cloudwatch-stacktrace-lab.md). Phải tạo log group và quyền **trước** khi deploy, thiếu là container không khởi động.

## 4. ĐANG DỪNG Ở ĐÂY

Giai đoạn 1: **Backup (7) → CI (1) → Registry (3) → CD (2) → Log + request id (25)** đã xong. **Chủ đề 35 (gom log)**: log đã lên CloudWatch (8 stream), đã tìm một request trong Logs Insights, đã tái hiện lỗi stack trace và push bản sửa.

1. **Mở đầu buổi sau:** lần chạy CI của commit `awslogs-datetime-format` phải xanh. Đỏ thì đọc log bước *Deploy* cùng người học.
2. **Bài lỗi stack trace, mục 5–6** trong [`cloudwatch-stacktrace-lab.md`](cloudwatch-stacktrace-lab.md) (người học tự làm): gọi lại lệnh `curl` đăng ký email 260 ký tự, tìm mã mới trong Logs Insights, mở dòng ERROR phải thấy cả stack trace và `Caused by: …PSQLException`. Sau đó sửa lỗi gốc: `@Size(max = 255)` cho email, request đó phải ra 400.
3. **Câu ôn** (chưa hỏi): *"Vì sao gỡ bảng tên ở `requestDestroyed` mà không gỡ trong `finally` của filter?"* Đáp án ở mục 3.
4. Xong thì đánh dấu `[x]` chủ đề 35 trong lộ trình, rồi sang **giai đoạn 2, chủ đề 33: Testcontainers**. Theo vòng dạy: nêu vấn đề trước (41 test đang chạy trên H2 "giả làm Postgres"; có chỗ H2 khác Postgres thật nên test xanh mà production vẫn lỗi), rồi hỏi người học sẽ làm test giống production hơn thế nào.

## 5. Việc còn treo (không gấp)

- Chủ đề 7: **chưa làm khôi phục thử tự động** mỗi tuần. Chưa thấy tận mắt `aws s3 ls s3://visionfit-backup-dien-2026 --region ap-southeast-2` trên server ra `AccessDenied` (server chỉ được ghi).
- Chưa thử **quay về bản cũ** (`./infra/ec2/deploy.sh <tag>`): giờ đã có hơn một bản image trên GHCR nên thử được.
- Dọn đồ của cách deploy cũ trên server: `docker volume rm visionfit-gradle && rm -rf ~/visionfit-backend/dist`.
- App BaoFit: khi có lỗi thì hiện mã trong header `X-Request-Id` ("Mã lỗi: …") để người dùng gửi kèm, tìm log nhanh nhất.
- Log JSON (log có cấu trúc): chưa bật. Log đã lên CloudWatch nên giờ đáng làm: thêm `logging.structured.format.console=logstash`, các trường MDC tự vào JSON và Logs Insights tự tách thành cột (`filter requestId = "…"` thay cho `like`). Lưu ý: dòng JSON không bắt đầu bằng ngày giờ, phải bỏ `awslogs-datetime-format` (stack trace nằm gọn trong một dòng JSON).
- Sau khi sửa identity: thành công hay lỗi 4xx thì service không có dòng log nào kèm user, chỉ gateway ghi (không có user). Đủ dùng ở giai đoạn này.
- Hai push liên tiếp quá nhanh: lần deploy đầu có thể đỏ `manifest unknown` (server kéo code mới hơn image đã build). Lần sau tự sửa.
- App (`ApiConfig` trong repo BaoFit) chưa trỏ về `https://api.baofit.tech`; repo app chưa có phần nối analysis-service.
- Cloudflare: nên bật *SSL/TLS → Edge Certificates → Always Use HTTPS*.
- Bucket còn thư mục `test/` (file upload thử). Xoá trên console khi tiện; server không có quyền xoá.
- Trên Mac còn `~/Downloads/visionfit-2026-10-30-0656.sql` và `.sql.gz` (dữ liệu thật): giữ làm "bản ngoài AWS" theo 3-2-1 hoặc xoá. Không đưa vào git.
- Server báo RAM 67% và swap 46%: tư liệu cho chủ đề Metrics và Cảnh báo.
- Bug đã biết, dùng làm bài tập: xem bảng cuối [`backend-deep-dive.md`](backend-deep-dive.md) (6.A, 7.B, 7.C, 9.A, 10.A…).
