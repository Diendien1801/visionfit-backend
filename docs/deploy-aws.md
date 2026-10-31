# Deploy lên AWS EC2 + Cloudflare Tunnel

> Kiến trúc: `điện thoại → https://api.baofit.tech (Cloudflare) → tunnel → cloudflared → nginx → gateway → 4 service → Postgres`, tất cả chạy bằng Docker Compose trên **một** máy EC2. Máy **không mở cổng web nào** ra Internet; chỉ có SSH, và chỉ cho IP của bạn.
> Phần code (Dockerfile, `docker-compose.prod.yml`, `infra/`) đã có sẵn trong repo và đã chạy thử trên laptop. Tài liệu này là **những việc chỉ chủ tài khoản làm được**: AWS và Cloudflare.

Thời gian ước tính: 45–60 phút, trong đó vài phút là chờ CI build image.

---

## Bước 1. Tài khoản AWS (10 phút)

1. Đăng ký tại https://aws.amazon.com (cần thẻ tín dụng/ghi nợ quốc tế).
2. **Bật MFA cho tài khoản root ngay**: góc phải trên → *Security credentials* → *Assign MFA device* → dùng app Authenticator trên điện thoại.
3. **Đặt cảnh báo chi phí**: tìm dịch vụ *Billing* → *Budgets* → *Create budget* → *Zero spend* hoặc *Monthly cost budget* với **40 USD**, nhập email. AWS sẽ báo khi vượt mức.
4. Chọn **region Singapore (`ap-southeast-1`)** ở góc phải trên. Mọi bước sau làm trong region này.

## Bước 2. Tạo máy EC2 (10 phút)

Tìm dịch vụ **EC2** → *Launch instance*:

| Mục | Chọn |
|---|---|
| Name | `visionfit-backend` |
| AMI (hệ điều hành) | **Ubuntu Server 24.04 LTS**, kiến trúc **64-bit (Arm)** |
| Instance type | **t4g.small** (2 vCPU, 2 GB RAM): loại lớn nhất Free plan cho phép. Script cài máy tạo thêm 2 GB swap |
| Key pair | *Create new key pair* → tên `visionfit`, loại **ED25519**, định dạng **.pem** → file tải về **giữ cẩn thận**, mất là không vào được máy |
| Network settings → Security group | *Create security group*: **chỉ một luật**: `SSH`, source **My IP**. **Không** tick *Allow HTTP/HTTPS* |
| Storage | **30 GB gp3** |

Bấm *Launch instance*. Khi trạng thái là *Running*, copy **Public IPv4 address**.

Chuyển file `.pem` vào thư mục an toàn trên laptop, ví dụ `C:\Users\dient\.ssh\visionfit.pem`.

Kết nối (PowerShell):
```powershell
ssh -i C:\Users\dient\.ssh\visionfit.pem ubuntu@<Public-IPv4>
```
Lần đầu hỏi *Are you sure you want to continue connecting* → gõ `yes`. Thấy dấu nhắc `ubuntu@ip-…:~$` là đã vào máy.

> IP nhà mạng của bạn đổi thì SSH sẽ bị chặn: vào *EC2 → Security Groups → Inbound rules → Edit* và chọn lại *My IP*.

## Bước 3. Cài máy chủ (5 phút)

Trong phiên SSH:
```bash
curl -fsSL https://raw.githubusercontent.com/Diendien1801/visionfit-backend/main/infra/ec2/setup-server.sh | bash
```
Script cài Docker, tạo 2 GB swap, bật tự động cập nhật bảo mật và clone repo. Xong thì **thoát ra và SSH lại** (để nhóm `docker` có hiệu lực):
```bash
exit
```

## Bước 4. Cloudflare Tunnel (10 phút)

1. Vào https://one.dash.cloudflare.com (Zero Trust). Lần đầu nó bắt chọn tên team và gói **Free**.
2. *Networks → Tunnels → Create a tunnel* → chọn **Cloudflared** → tên `visionfit` → *Save tunnel*.
3. Trang *Install and run a connector*: chọn **Docker**. Trong lệnh hiện ra có đoạn `--token eyJ…` rất dài. **Copy riêng phần token** (chuỗi sau `--token`). Không cần chạy lệnh đó. Bấm *Next*.
4. *Route tunnel* → **Public Hostname**:
   - Subdomain: `api` · Domain: `baofit.tech`
   - Type: **HTTP** · URL: `nginx:8000` (tên container nginx trong compose, không phải localhost)
   - *Save tunnel*. Cloudflare tự tạo bản ghi DNS `api.baofit.tech`.
5. Bảo mật thêm (khuyến nghị, trong trang chính của domain `baofit.tech`): *SSL/TLS → Overview* đặt **Full**; *SSL/TLS → Edge Certificates* bật **Always Use HTTPS**.

## Bước 5. Cấu hình bí mật trên máy chủ (5 phút)

SSH lại vào máy, rồi:
```bash
cd ~/visionfit-backend && cp .env.example .env && nano .env
```
Sửa **từng dòng** sau (các dòng `*_PORT` giữ nguyên, không dùng tới):

| Biến | Giá trị |
|---|---|
| `IDENTITY_DB_PASSWORD`, `PROFILE_DB_PASSWORD`, `MEAL_DB_PASSWORD`, `ANALYSIS_DB_PASSWORD`, `POSTGRES_PASSWORD` | 5 mật khẩu **khác nhau**, ngẫu nhiên. Tạo bằng `openssl rand -base64 24` (chạy 5 lần) |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `ANTHROPIC_API_KEY` | Key từ https://console.anthropic.com (để trống thì phân tích ảnh luôn FAILED) |
| `CLOUDFLARE_TUNNEL_TOKEN` | Token copy ở Bước 4.3 |

Trong `nano`: `Ctrl+O` rồi `Enter` để lưu, `Ctrl+X` để thoát. Khoá file lại:
```bash
chmod 600 .env
```

## Bước 6. Chạy (3 phút)

```bash
./infra/ec2/deploy.sh
```
Máy chủ không build gì: CI (`.github/workflows/ci.yml`) test xong thì đẩy 5 image lên `ghcr.io`, gắn nhãn là 7 ký tự đầu của mã commit. Script kéo đúng image của commit vừa pull rồi khởi động mọi container. Nếu báo `manifest unknown` thì CI của commit đó chưa xong: chờ dấu ✅ trên GitHub rồi chạy lại. Theo dõi:
```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f cloudflared
```
Log của cloudflared có dòng `Registered tunnel connection` là đường hầm đã nối. Trên trang Cloudflare, tunnel chuyển sang **HEALTHY**.

Kiểm tra từ **laptop** (hoặc điện thoại 4G, mở trình duyệt vào URL đầu):
```powershell
curl.exe https://api.baofit.tech/actuator/health
curl.exe -i -X POST https://api.baofit.tech/api/v1/auth/register -H "Content-Type: application/json" -d "{\"email\":\"first@baofit.tech\",\"password\":\"password123\"}"
```
Dòng đầu trả `{"status":"UP"}`, dòng sau trả `201` kèm token: **backend đã lên Internet**.

## Bước 7. App trỏ vào server

Trong app, `ApiUrls` đổi cả ba địa chỉ thành **một**: `https://api.baofit.tech/`. Gateway tự chia về đúng service.

## Bước 8. Tự deploy khi push lên `main` (15 phút)

GitHub không SSH vào máy. Nó nhờ AWS SSM: agent trên máy tự gọi ra SSM để nhận việc, nên không phải mở cổng nào. Tài khoản AWS dạng giao diện rút gọn không cho thêm OIDC provider, nên GitHub dùng key của một IAM user chỉ có đúng một quyền.

1. **Máy được nói chuyện với SSM**: IAM → Roles → role đang gắn vào máy (`visionfit-backup-role`) → *Add permissions* → *Attach policies* → `AmazonSSMManagedInstanceCore`. Trên máy chạy `sudo snap restart amazon-ssm-agent`. Máy phải hiện *Online* trong Systems Manager → Fleet Manager.
2. **Phiếu việc duy nhất**: Systems Manager → Documents → *Create document* → *Command or Session*, tên `VisionfitDeploy`, YAML, dán `infra/ec2/ssm-deploy.yml`.
3. **IAM user `github-deploy`**: không cho đăng nhập web; inline policy `send-deploy` chỉ có `ssm:SendCommand` trên document `VisionfitDeploy` và đúng instance của máy, cộng `ssm:GetCommandInvocation` để đọc kết quả. Tạo access key loại *Third-party service*.
4. **GitHub**: repo → Settings → Secrets and variables → Actions → hai secret `AWS_ACCESS_KEY_ID` và `AWS_SECRET_ACCESS_KEY`.

Bước *Deploy* trong `.github/workflows/ci.yml` gửi phiếu, chờ kết quả rồi in log của `deploy.sh`. Máy mới thì sửa ID instance ở đó và trong policy.

---

## Vận hành

| Việc | Lệnh (trên máy chủ, trong `~/visionfit-backend`) |
|---|---|
| Cập nhật phiên bản mới | Tự động: push lên `main` là CI test, đẩy image rồi bảo server chạy `deploy.sh` qua SSM (cài đặt ở Bước 8). Bằng tay: `./infra/ec2/deploy.sh` |
| Quay về bản cũ | `./infra/ec2/deploy.sh 1a2b3c4` (7 ký tự đầu của commit muốn quay về) |
| Xem log một service | `docker compose -f docker-compose.prod.yml logs -f --tail 100 meal-service` |
| Sao lưu database | `./infra/ec2/backup.sh <bucket>`: cron chạy mỗi đêm, lên S3 (cách cài ở đầu file script) |
| RAM còn bao nhiêu | `free -h` và `docker stats --no-stream` |

Chưa làm, nên làm sớm: giới hạn request theo IP ngay trên Cloudflare (*Security → WAF → Rate limiting rules*); `server.address` cho các service (trong Docker chúng đã không mở cổng ra ngoài, nên chưa cần).
