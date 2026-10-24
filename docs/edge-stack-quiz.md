# Bài kiểm tra: Gateway → nginx → Cloudflare Tunnel

> **Tạm dừng ngày 2026-10-24**, đang làm dở câu 8.
> Khi muốn học tiếp, mở chat và gõ: **"tiếp tục bài kiểm tra trong docs/edge-stack-quiz.md"**.

**Luật chơi:** trả lời bằng trí nhớ, không mở code. Kẹt thì gõ "gợi ý", muốn nghỉ thì gõ "dừng".
Đáp án được giấu trong các mục **"Đáp án"**, chỉ bấm mở sau khi đã tự trả lời.

---

## 1. Ba câu đang chờ (làm trước tiên)

**A.** Một máy khác trong mạng văn phòng gửi request **thẳng vào gateway** `:8080` (không qua nginx), kèm header `X-Forwarded-For: 6.6.6.6`. Gateway ghi nhận IP của client là `6.6.6.6`, hay IP thật của máy đó? Vì sao?

<details><summary>Đáp án</summary>

**IP thật của máy đó.** Gateway chỉ tin `X-Forwarded-For` khi **kết nối đến từ `127.0.0.1`**
(`server.tomcat.remoteip.internal-proxies=127.0.0.0/8, ::1/128`). Máy LAN không nằm trong danh sách
này, nên gateway bỏ qua header. Ví dụ dễ nhớ: thư do **người quen** (nginx) đưa thì tin tờ giấy dán trên thư,
thư do **người lạ** đưa thì mặc kệ tờ giấy.
</details>

**B. (câu 8.2)** meal-service bị treo: nhận request nhưng không bao giờ trả lời. App chờ **bao lâu**, nhận **mã gì**, và **ai** trả mã đó?

<details><summary>Đáp án</summary>

Chờ **60 giây**, sau đó **gateway** trả **`504`** dạng JSON. Service treo vẫn "bắt máy" (vẫn nhận kết nối),
nên timeout được áp dụng là **read-timeout 60s** chứ không phải connect-timeout 2s.
</details>

**C. (câu 8.3)** Trong cả hệ thống, kết nối **đi ra (outbound)** duy nhất là từ **cái gì** tới **cái gì**?

<details><summary>Đáp án</summary>

**`cloudflared` → Cloudflare.** Kết nối này được mở **một lần lúc khởi động** rồi giữ mãi. nginx → gateway → service
đều là gọi **local** trên cùng máy, không phải outbound.
</details>

---

## 2. Bảng điểm

| Câu | Chủ đề | Kết quả |
|---|---|---|
| 1 | Vẽ lại đường đi của một request | ~70% |
| 2 | IP thật nằm ở đâu qua từng chặng | 3 ô chính đúng hết, câu phụ đúng một nửa |
| 3 | Router chặn kết nối vào / chặn kết nối ra | 1/2 (A đúng, B sai) |
| 4a | Ai chặn tấm ảnh thứ 11 | 1/3 |
| 4b | 10 người chung IP: chung hay riêng suất | 0/2, **làm lại được 3/3** |
| 5 | meal-service treo, timeout | 0/5 |
| 6 | Bắt lỗi trong file nginx | tìm được 2/6 lỗi |
| 7 | Tự viết lại các dòng đúng | 2/5 |
| 8.1 | nginx đọc header nào, ghi header nào | ✅ |
| 8.2, 8.3 | | Chưa làm |

---

## 3. Ưu điểm

- **Hiểu "phòng thủ nhiều lớp".** Ngay câu 1 bạn đã tự nêu được lý do service vẫn phải kiểm token: kẻ nào đi vòng qua các lớp phía trước thì vẫn bị chặn.
- **Hiểu tunnel giữ một kết nối liên tục.** Chế độ A ở câu 3 bạn trả lời đúng, và lập luận cũng đúng.
- **Học nhanh khi có ví dụ cụ thể.** Câu 4b bạn sai 0/2, nhưng sau ví dụ "cuốn sổ ghi theo token" thì làm lại đúng 3/3. Bạn còn tự thấy được hai lớp chặn chồng lên nhau (auth 20/phút ở gateway và 300/phút ở nginx).
- **Hay hỏi đúng chỗ, tự đào sâu.** Các câu hỏi "gateway có nghe local không?", "khi dev thì service chạy ở IP nào?", "lúc chưa có Cloudflare thì ai gọi được vào?" đã mở ra cả bài về `0.0.0.0`, private IP và NAT.
- **Nắm đúng điểm mấu chốt của config nginx:** `listen 127.0.0.1:8000` và `set_real_ip_from 127.0.0.1` (câu 7), ý nghĩa của `set_real_ip_from 0.0.0.0/0` (câu 6). Tóm tắt lại được đúng chuỗi CF-Connecting-IP → nginx → X-Forwarded-For → gateway (câu 8.1).
- **Thật thà nói "không biết", "không hiểu".** Nhờ vậy dễ thấy ngay chỗ hổng, thay vì đoán bừa cho qua.

---

## 4. Khuyết điểm (chỗ hổng cần ôn)

Sắp xếp từ chỗ hổng lớn nhất. Mỗi mục gồm: bạn đã sai thế nào, đáp án đúng, và một câu để nhớ.

### 4.1 Hướng của kết nối: ai mở kết nối tới ai (nhầm 3 lần: câu 1, câu 2, câu 3B)
- **Đã sai:** nghĩ rằng nginx "gọi outbound" ra Cloudflare, và nghĩ tunnel vẫn chạy khi router chặn kết nối đi ra.
- **Đúng:** chỉ có `cloudflared` mở kết nối **đi ra** Cloudflare. Request của điện thoại đi **ngược vào** trên chính kết nối đó, sau đó `cloudflared` gọi local tới nginx. Tunnel **bắt buộc phải đi ra được**, còn kết nối đi vào thì không cần.
- **Câu nhớ:** *cuộc gọi không cúp máy*. Bị chặn gọi đi thì không có cuộc gọi nào cả.

### 4.2 nginx đọc IP thật từ header nào (nhầm 3 lần: câu 1, câu 7, câu phụ của câu 2)
- **Đã sai:** cho rằng nginx đọc IP thật từ `X-Forwarded-For`; viết `$new_address` (biến không tồn tại).
- **Đúng:** `real_ip_header CF-Connecting-IP;` rồi `proxy_set_header X-Forwarded-For $remote_addr;`. `X-Forwarded-For` do client gửi lên thì không đáng tin, vì client tự điền vào được. `CF-Connecting-IP` thì chỉ Cloudflare đặt được (client tự gửi header này là bị trả `403`).
- **Câu nhớ:** ***Đọc CF, ghi XFF.***

### 4.3 Quên rằng gateway cũng có rate limit (2 lần: câu 1, câu 4)
- **Đã sai:** cho rằng nginx chặn tấm ảnh thứ 11, và nghĩ 10 người chung IP thì ở nginx mỗi người có suất riêng.
- **Đúng:**

  | Ở đâu | Giới hạn | Tính theo | 10 người cùng quán |
  |---|---|---|---|
  | nginx | 300/phút | IP | **Chung** |
  | gateway, `/api/v1/auth/**` | 20/phút | IP | **Chung** |
  | gateway, `POST /api/v1/analysis` | 10/phút | token | **Riêng** |
  | gateway, mọi request còn lại | 300/phút | token | **Riêng** |

  Vượt giới hạn thì nhận `429`, kèm header **`Retry-After`** (số giây phải chờ). Cả quán (hoặc cả trạm 4G, do CGNAT) đi ra internet bằng **một IP**, nên giới hạn theo IP phải đặt rộng tay. Giới hạn công bằng cho từng người thì phải tính theo token.
- **Câu nhớ:** *nginx đếm theo địa chỉ quán, gateway đếm theo thẻ thành viên của từng khách.*

### 4.4 Lẫn connect-timeout với read-timeout (câu 5)
- **Đã sai:** nghĩ service bị treo thì 2 giây sau sẽ có phản hồi; nghĩ nginx `proxy_read_timeout 30s` chỉ làm app chờ lâu hơn.
- **Đúng:**

  | Tình huống | Chờ bao lâu | Mã |
  |---|---|---|
  | Service **tắt** | gần như 0 giây | `502` |
  | Không kết nối được tới máy chạy service | 2 giây (connect-timeout) | `504` |
  | Service **treo** (vẫn bắt máy nhưng im lặng) | **60 giây** (read-timeout) | **`504` JSON từ gateway** |

  Nếu nginx đặt `30s` thì nginx cắt request **trước** gateway, nên app nhận trang **HTML** `504` của nginx, không đọc được như JSON.
- **Câu nhớ:** *chờ bắt máy khác với chờ trả lời.* Lớp ở ngoài phải chờ lâu hơn lớp ở trong: **Cloudflare 100s > nginx 65s > gateway 60s**.

### 4.5 Đọc config để tìm lỗi (câu 6)
- `listen 8000` mà không ghi IP nghĩa là **nghe trên mọi IP** của máy, ai trong LAN cũng gọi thẳng được.
- `$proxy_add_x_forwarded_for` (có chữ **add**) là **nối thêm** vào giá trị cũ, nên IP giả vẫn còn nằm trong header. Phải **ghi đè** bằng `$remote_addr`.
- Thiếu `client_max_body_size 6m;` thì body mặc định chỉ được 1MB, ảnh 5MB bị trả `413`. **Một dòng bị thiếu khó thấy hơn một dòng viết sai.**
- Ba lỗi nhỏ (`listen` mọi IP + tin mọi IP + đọc `X-Forwarded-For`) cộng lại thành một lỗ hổng thật: mỗi request giả một IP mới, nên rate limit trở nên vô dụng.

### 4.6 Các điểm nhỏ khác
- **Reverse proxy:** đứng **phía server**, nhận request **thay mặt** server. Forward proxy thì đứng **phía client** (proxy công ty, VPN). nginx và gateway **đều** là reverse proxy.
- **Gateway và 4 service đang nghe trên mọi IP** (Spring Boot mặc định dùng `0.0.0.0`), chỉ có nginx là nghe local. *Chỉ nghe local* (không ai kết nối được) khác với *chỉ tin header từ local* (kết nối được nhưng không giả được IP).

### 4.7 Cách học hợp với bạn
- Khi giải thích **dày đặc** (nhiều bảng, nhiều code cùng lúc) thì bạn bị ngợp ("giải thích gì ko hiểu j hết vậy").
- Ví dụ đời thường thì vào ngay: *cuộc gọi không cúp máy*, *cuốn sổ ghi theo token*, *lá thư kèm tờ giấy*.

---

## 5. Câu hỏi ôn lại (sau 3 câu đang chờ)

**R1.** Firewall văn phòng chặn kết nối **đi ra** tới cổng 7844 (cả UDP lẫn TCP), còn kết nối đi vào thì mở hết. Tunnel có lên được không? Nếu firewall chỉ chặn UDP thì sao?

<details><summary>Đáp án</summary>

Chặn cả hai thì **không lên được**: `cloudflared` đi ra qua cổng 7844, thử **QUIC (UDP)** trước, rồi chuyển sang
**HTTP/2 (TCP)**, và cả hai đều dùng cổng 7844. Mở kết nối đi vào cũng không giúp gì, vì tunnel không dùng tới.
Nếu firewall chỉ chặn UDP thì **vẫn lên được**, nhờ chuyển sang HTTP/2 qua TCP.
</details>

**R2.** Bạn chụp liên tục 11 tấm ảnh trong 1 phút. Tấm thứ 11 bị **ai** chặn, nhận **mã gì**, và **header nào** cho app biết bao lâu nữa được gửi lại?

<details><summary>Đáp án</summary>

**Gateway** chặn (giới hạn 10 lần/phút theo token), trả **`429`** kèm **`Retry-After`**.
nginx chưa chặn, vì 300/phút còn xa.
</details>

**R3.** meal-service bị **tắt** hẳn (không chạy). App nhận mã gì, sau bao lâu? So sánh với lúc nó bị **treo**.

<details><summary>Đáp án</summary>

Bị tắt: nhận **`502` gần như ngay lập tức** (kết nối bị từ chối). Bị treo: nhận **`504` sau 60 giây**.
</details>

**R4.** Làm lại câu 6 mà không nhìn mục 4.5: file nginx dưới đây có **6 lỗi**. Tìm hết và nói hậu quả của từng lỗi.

```nginx
limit_req_zone $binary_remote_addr zone=per_ip:10m rate=300r/m;

server {
    listen 8000;

    set_real_ip_from 0.0.0.0/0;
    real_ip_header   X-Forwarded-For;

    location / {
        limit_req zone=per_ip burst=60 nodelay;
        limit_req_status 429;

        proxy_pass http://127.0.0.1:8080;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 30s;
    }
}
```

<details><summary>Đáp án</summary>

1. `listen 8000` → phải là `listen 127.0.0.1:8000`, nếu không thì cả LAN gọi thẳng được vào nginx.
2. `set_real_ip_from 0.0.0.0/0` → phải là `127.0.0.1`, nếu không thì nginx tin header của bất kỳ ai.
3. `real_ip_header X-Forwarded-For` → phải là `CF-Connecting-IP`, vì client tự điền được `X-Forwarded-For`.
4. `$proxy_add_x_forwarded_for` → phải là `$remote_addr` (ghi đè), vì bản `add` vẫn giữ lại IP giả.
5. `proxy_read_timeout 30s` → phải là `65s`, lâu hơn gateway (60s), nếu không thì app nhận trang HTML 504 của nginx.
6. Thiếu `client_max_body_size 6m;` → mặc định chỉ 1MB, ảnh 5MB bị trả `413`.
</details>

**R5.** Không mở file, tự viết lại toàn bộ khối `server { ... }` của `nginx/nginx.conf`, rồi so với file thật.

---

## 6. Lịch ôn

| Ngày | Làm gì |
|---|---|
| 2026-10-25 | 3 câu đang chờ (A, B, C) và R1–R3 |
| 2026-10-27 | R4 (bắt 6 lỗi), không nhìn mục 4.5 |
| 2026-10-31 | Tự vẽ lại toàn bộ đường đi của request (câu 1) và làm R5 |

Câu nào vẫn sai thì ôn lại câu đó sau 2 ngày. Câu nào đúng 2 lần liên tiếp thì có thể bỏ khỏi lịch.
