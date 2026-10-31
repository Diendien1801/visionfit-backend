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

---

## Vận hành

| Việc | Lệnh (trên máy chủ, trong `~/visionfit-backend`) |
|---|---|
| Cập nhật phiên bản mới | `./infra/ec2/deploy.sh` (pull `main`, kéo image CI đã build cho commit đó, khởi động lại những gì thay đổi) |
| Quay về bản cũ | `./infra/ec2/deploy.sh 1a2b3c4` (7 ký tự đầu của commit muốn quay về) |
| Xem log một service | `docker compose -f docker-compose.prod.yml logs -f --tail 100 meal-service` |
| Sao lưu database | `./infra/ec2/backup.sh <bucket>`: cron chạy mỗi đêm, lên S3 (cách cài ở đầu file script) |
| RAM còn bao nhiêu | `free -h` và `docker stats --no-stream` |

Chưa làm, nên làm sớm: giới hạn request theo IP ngay trên Cloudflare (*Security → WAF → Rate limiting rules*); `server.address` cho các service (trong Docker chúng đã không mở cổng ra ngoài, nên chưa cần).
