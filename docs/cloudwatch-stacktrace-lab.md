# Bài lỗi: stack trace bị xé lẻ trên CloudWatch

> Ghi ngày 2026-11-03. Làm trên production `https://api.baofit.tech`, mất khoảng 20 phút.
> Bản sửa **chưa push**: production đang mang lỗi này, nên tái hiện được ngay. Thuộc chủ đề 35 trong [`learning-roadmap.md`](learning-roadmap.md).

## 1. Lỗi là gì

Java ghi một lỗi thành **nhiều dòng**. Dòng đầu có ngày giờ và mã request, các dòng sau là stack trace. Ví dụ (rút gọn):

```
2026-11-03T07:44:55.234Z ERROR 1 --- … [req=7f3c…] c.v.i.exception.GlobalExceptionHandler : Unexpected error on POST /api/v1/auth/register
org.springframework.dao.DataIntegrityViolationException: could not execute statement …
	at org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator.convertHibernateAccessException(…)
	at …   (khoảng 300 dòng nữa)
Caused by: org.postgresql.util.PSQLException: ERROR: value too long for type character varying(255)
```

Driver `awslogs` của Docker mặc định coi **mỗi dòng là một event**. Stack trace 300 dòng thành 300 event, và chỉ event đầu tiên có `req=…`. Tìm theo mã request thì chỉ ra dòng `Unexpected error on POST …`. Thông tin cần nhất là lỗi gì và vì sao (`Caused by: …`) lại nằm ở các event không mang mã.

Giống như máy in tách một hoá đơn thành 300 tờ và chỉ in số hoá đơn lên tờ đầu. Tra theo số hoá đơn thì chỉ lấy được tờ đầu.

## 2. Gây ra một lỗi 500 thật

Cần một request chắc chắn ra 500 có stack trace. Cách dễ nhất là **đăng ký với email dài 260 ký tự**:

- `@Email` cho qua, vì phần trước `@` có 64 ký tự và mỗi đoạn tên miền có 63 ký tự, đều trong giới hạn.
- Cột `users.email` chỉ là `VARCHAR(255)`, nên Postgres từ chối câu `INSERT`. Spring ném `DataIntegrityViolationException`, handler chung của identity ghi stack trace rồi trả 500.
- Không có user nào được tạo, vì transaction bị rollback.

Đã thử trên máy (H2): kết quả 500, stack trace dài 313 dòng.

Chạy lệnh dưới đây trong Terminal trên Mac. Trên Windows thì dùng **Git Bash**, vì PowerShell xử lý dấu nháy của JSON khác.

```bash
curl -si -X POST https://api.baofit.tech/api/v1/auth/register -H "Content-Type: application/json" -d '{"email":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.ddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd.com","password":"password123"}'
```

Kết quả đúng:
- Status `HTTP/2 500`.
- Body có `"message":"Unexpected error"`.
- **Ghi lại mã trong header `X-Request-Id`.**

## 3. Nhìn thấy lỗi

Vào CloudWatch → Logs → *Logs Insights* → chọn log group `visionfit-prod`, chạy query với mã vừa ghi:

```
fields @timestamp, @logStream, @message
| filter @message like "<X-Request-Id>"
| sort @timestamp asc
```

Phải thấy 4 dòng (đã thử thật ngày 2026-11-03):

- 2 dòng `WARN … org.hibernate.orm.jdbc.error` của identity. Khi DB từ chối câu SQL, Hibernate tự ghi mã lỗi và câu báo lỗi của Postgres (`value too long…`). Mỗi dòng chỉ là một dòng nên vẫn mang mã request.
- 1 dòng `ERROR … Unexpected error on POST /api/v1/auth/register` của identity. Bấm mở dòng này: **không có stack trace**, không biết lỗi phát ra từ dòng code nào, đi qua những hàm nào.
- 1 dòng của gateway (`-> 500`).

Lỗi này "may mắn" vì đi qua DB, nên Hibernate làm lộ nguyên nhân. Một lỗi chỉ nằm trong code, ví dụ `NullPointerException`, không có dòng WARN nào cứu: chỉ còn dòng ERROR trống trơn.

Tiếp theo, xem các dòng cuối của identity, không lọc theo mã:

```
fields @timestamp, @message
| filter @logStream = "visionfit-prod-identity-service-1"
| sort @timestamp desc
| limit 30
```

Sẽ thấy hàng loạt dòng `at org.springframework…`, mỗi dòng là một event và không dòng nào có `req=`. Nếu 2 người cùng gặp lỗi trong một giây, các dòng của hai stack trace xen vào nhau và không còn cách nào tách ra.

## 4. Sửa

Docker có tuỳ chọn `awslogs-datetime-format`: dòng nào **bắt đầu bằng ngày giờ** thì mở event mới, dòng nào không thì nối vào event ngay trước. Dòng log của Spring luôn bắt đầu bằng `2026-11-03T07:44:55`, còn các dòng `at …` và `Caused by: …` thì không. Nhờ vậy cả stack trace dính vào dòng `ERROR … [req=…]`.

Tuỳ chọn này **chỉ** dành cho 5 service Java. Postgres và nginx ghi ngày giờ theo kiểu khác (`2026-11-03 07:44:55`, `2026/11/03`, hoặc `[03/Nov/2026…]`). Nếu áp cho chúng, không dòng nào khớp, và mọi dòng trong 5 giây sẽ dính thành một cục.

Trong [`docker-compose.prod.yml`](../docker-compose.prod.yml), thay hai khối `x-logging` và `x-app` bằng nội dung dưới đây. Postgres, nginx và cloudflared vẫn giữ `logging: *logging` như cũ.

```yaml
# Every container's output goes to CloudWatch Logs (log group visionfit-prod, one stream per container), so
# logs outlive the containers each deploy replaces and are searched from the AWS console, without SSH.
# Docker signs in with the server's instance role (policy write-logs). `docker compose logs` still works:
# Docker keeps a local copy of the last 100 MB per container.
x-logging: &logging
  driver: awslogs
  options: &logging-options
    awslogs-region: ap-southeast-2
    awslogs-group: visionfit-prod
    tag: "{{.Name}}"
    # The default (blocking) stalls the app's log writes while CloudWatch is unreachable: drop lines instead.
    mode: non-blocking

x-app: &app
  restart: unless-stopped
  networks: [backend]
  logging:
    <<: *logging
    options:
      <<: *logging-options
      # A Spring Boot line starts with its timestamp; the stack trace lines below it don't. Without this, each
      # line of a stack trace is a separate CloudWatch event without the [req=…] tag, so a search by request id
      # finds the ERROR line but not the exception.
      awslogs-datetime-format: "%Y-%m-%dT%H:%M:%S"
  # Sized for a 2 GB server with 2 GB swap: the JVM takes 60% of its container limit as heap, the
  # serial collector keeps the rest small, and an OOM restarts the container instead of hanging it.
  environment: &app-env
    JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError
  deploy:
    resources:
      limits:
        memory: 400m
```

- `&logging-options` đặt tên cho khối `options`, để `x-app` lấy lại toàn bộ rồi thêm đúng một dòng.
- `<<:` nghĩa là "chép hết khối kia vào đây", giống cách các service đang dùng `<<: *app-env`.

Kiểm tra file trước khi commit. Lệnh này cần biến môi trường, nên chạy trên server, hoặc trên máy có file `.env` giả:

```bash
docker compose -f docker-compose.prod.yml config -q
```

Không in ra gì nghĩa là hợp lệ. Sau đó commit và push, rồi chờ CI xanh. Lần deploy này tạo lại 5 container Java; Postgres không bị đụng tới.

```bash
git add docker-compose.prod.yml && git commit -m "feat(logging): keep Java stack traces in one CloudWatch event"
```

```bash
git push
```

## 5. Kiểm tra lại

Chạy lại lệnh `curl` ở mục 2 để lấy **mã mới**, rồi chạy query ở mục 3. Mở dòng của identity: lần này cả stack trace nằm trong **một** event, và cuối event có dòng `Caused by: org.postgresql.util.PSQLException: ERROR: value too long for type character varying(255)`. Chỉ cần mã request là trả lời được ngay "lỗi gì, vì sao".

## 6. Bài phụ: lỗi gốc

Email dài là lỗi của **người gọi**, nên đúng ra phải trả 400, không phải 500. Đây cũng là một lỗi thật. Lý do: kiểm tra đầu vào không biết cột chỉ chứa được 255 ký tự. Sửa trong `identity-service/src/main/java/com/visionfit/identity/dto/RegisterRequest.java`:

```java
package com.visionfit.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
```

Sau khi push, chạy lại lệnh ở mục 2: kết quả phải là `HTTP/2 400`, kèm `"message":"email: size must be between 0 and 255"`, và identity không ghi stack trace nào nữa. Lệnh đó chỉ gây được lỗi 500 trước khi sửa, nên **làm xong mục 5 rồi mới sửa mục 6**.

Làm xong cả hai thì đánh dấu `[x]` chủ đề 35 trong lộ trình.
