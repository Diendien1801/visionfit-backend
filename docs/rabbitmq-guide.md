# Chủ đề 14 + 15: xoá tài khoản qua RabbitMQ và Outbox

> Code nằm trên nhánh `feat/rabbitmq-user-deleted`, **chưa commit**. Đọc file này theo thứ tự các bước: mỗi bước ghi rõ làm gì, ở file nào, vì sao, và có câu hỏi tự kiểm tra (đáp án ở cuối file).

## Luồng tổng quát

```
App ── DELETE /api/v1/me ──► gateway ──► identity-service
                                           │ 1 transaction:
                                           │   xoá refresh_tokens, xoá users, INSERT outbox
                                           ▼
                                     bảng outbox (identity-db)
                                           │ OutboxRelay, mỗi 2 giây:
                                           │   gửi các dòng sent_at IS NULL, chờ RabbitMQ xác nhận, ghi sent_at
                                           ▼
                        exchange "identity.events" (topic), routing key "user.deleted"
                ┌──────────────────────────┼──────────────────────────┐
                ▼                          ▼                          ▼
     profile.user-deleted        meal.user-deleted         analysis.user-deleted      (mỗi queue có .dlq riêng)
                ▼                          ▼                          ▼
     xoá body_profiles           xoá meals (+ items)       xoá analysis_jobs (+ ảnh, detections)
```

| Khái niệm đã học | Nằm ở đâu trong code |
|---|---|
| Producer | `identity-service`: `AccountService` ghi outbox, `OutboxRelay` gửi |
| Exchange **topic** | `identity.events`, khai báo ở `identity/config/RabbitConfig` và ở `UserEventsConfig` của mỗi consumer |
| Queue riêng cho mỗi service (câu 4) | `profile.user-deleted`, `meal.user-deleted`, `analysis.user-deleted` |
| Binding | `UserEventsConfig.userDeletedBinding()`: `bind(queue).to(exchange).with("user.deleted")` |
| Ack **sau** khi xoá (câu 5) | Mặc định của Spring AMQP: method listener chạy xong mới ack, ném exception thì không ack |
| Idempotent | `DELETE ... WHERE user_id = ?` chạy lần hai xoá 0 dòng |
| `eventId` | `outbox.id`, gửi đi thành `message_id` của thông điệp |
| Outbox (câu 7–11) | Bảng `outbox` + `OutboxRelay` + partial index `idx_outbox_unsent` |

---

## Bước 1. Thêm thư viện

**File:** `build.gradle` của identity, profile, meal, analysis: thêm `spring-boot-starter-amqp`.

Starter này mang theo:
- `RabbitTemplate`: để **gửi** thông điệp.
- `@RabbitListener`: để **nhận** thông điệp.
- `RabbitAdmin`: lúc khởi động, tự **khai báo** mọi bean `Exchange`, `Queue`, `Binding` lên RabbitMQ.

Gateway không dùng nên không thêm.

## Bước 2. Chạy RabbitMQ

**File:** `docker-compose.yml` (laptop) và `docker-compose.prod.yml` (server).

- Laptop: image `rabbitmq:4.1-management`, có trang quản trị ở http://localhost:15672.
- Server: image `rabbitmq:4.1-alpine`, **không** có trang quản trị và **không** mở cổng nào, chỉ các container trong mạng `backend` gọi được. Giới hạn RAM 256 MB.
- Cả hai đều có `hostname: rabbitmq` và một volume. RabbitMQ lưu dữ liệu trong thư mục **đặt theo tên máy**. Nếu để Docker đặt tên ngẫu nhiên, mỗi lần tạo lại container sẽ ra một thư mục mới, và các queue cùng thông điệp đang chờ coi như **mất**.

**Câu hỏi 1:** Vì sao trong `docker-compose.prod.yml`, các service có `depends_on: rabbitmq: { condition: service_healthy }`? Nếu bỏ đi thì chuyện gì xảy ra lúc deploy?

## Bước 3. Cấu hình kết nối

**File:** `application.properties` của 4 service.

| Thuộc tính | Ý nghĩa |
|---|---|
| `spring.rabbitmq.host/port/username/password` | Mặc định `127.0.0.1:5672`, `guest`/`guest`. Trên server lấy từ `RABBITMQ_*` trong compose |
| `spring.rabbitmq.publisher-confirm-type=simple` (chỉ identity) | Cho phép `waitForConfirmsOrDie`: chờ RabbitMQ báo "đã nhận" rồi mới ghi `sent_at` |
| `spring.rabbitmq.listener.simple.retry.*` (3 consumer) | Listener ném exception thì thử lại 4 lần, cách nhau 1, 2, 4, 8 giây, rồi mới bỏ vào `.dlq` |
| `management.health.rabbit.enabled=false` | RabbitMQ sập **không** làm `/actuator/health` báo DOWN. Service vẫn phục vụ API bình thường, event thì chờ |
| Test: `visionfit.outbox.relay.enabled=false`, `spring.rabbitmq.listener.simple.auto-startup=false` | Lúc chạy test không có RabbitMQ: tắt relay và listener, test gọi thẳng method |

**Câu hỏi 2:** Vì sao `.env.example` để `# RABBITMQ_USER=` dạng **comment**, chứ không ghi `RABBITMQ_USER=` để trống?

## Bước 4. Identity: bảng outbox

**File:** `identity-service/src/main/resources/db/migration/V3__create_outbox_table.sql`, `domain/OutboxEvent.java`, `repository/OutboxEventRepository.java`.

- Cột như đã thiết kế ở câu 9–11, cộng thêm **`request_id`**: lưu `X-Request-Id` của request xoá, để log của consumer mang **cùng mã** (nối với chủ đề 25).
- `payload` là `TEXT`, không phải `JSONB` như lúc thảo luận: không chỗ nào truy vấn vào bên trong JSON, nên `TEXT` là đủ và đơn giản hơn.
- Partial index: `CREATE INDEX idx_outbox_unsent ON outbox (created_at) WHERE sent_at IS NULL;`
- `findTop50BySentAtIsNullOrderByCreatedAtAsc()`: Spring Data tự sinh đúng câu `SELECT ... WHERE sent_at IS NULL ORDER BY created_at LIMIT 50` từ **tên** method.

## Bước 5. Identity: xoá tài khoản

**File:** `controller/MeController.java` (`DELETE /api/v1/me`, trả 204), `service/AccountService.java`, `repository/RefreshTokenRepository.java` (`deleteByUser`).

```java
@Transactional
public void deleteAccount(String email) {
    User user = userDetailsService.loadUserEntityByEmail(email);
    refreshTokenRepository.deleteByUser(user);
    userRepository.delete(user);
    outboxEventRepository.save(OutboxEvent.userDeleted(user.getId(), MDC.get("requestId")));
}
```
Ba lệnh nằm trong **một** transaction. Đây là toàn bộ lời giải của câu 7 và câu 8. Request **không** gọi RabbitMQ.

Gateway đã có sẵn route `/api/v1/me` sang identity nên không phải sửa gì.

**Câu hỏi 3:** Có ai đó chuyển dòng `outboxEventRepository.save(...)` sang **một method khác** cũng có `@Transactional(propagation = REQUIRES_NEW)`. Chuyện gì có thể xảy ra?

## Bước 6. Identity: OutboxRelay

**File:** `service/OutboxRelay.java`, `config/RabbitConfig.java`, `IdentityServiceApplication.java` (`@EnableScheduling`).

```java
@Scheduled(fixedDelayString = "${visionfit.outbox.relay.interval:2s}")
public void sendPending() {
    for (OutboxEvent event : outboxEventRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()) {
        rabbitTemplate.invoke(channel -> {
            channel.send(EXCHANGE, event.getType(), toMessage(event));
            channel.waitForConfirmsOrDie(5_000);      // chờ RabbitMQ xác nhận
            return null;
        });
        event.markSent();
        outboxEventRepository.save(event);            // ghi sent_at SAU khi được xác nhận
    }
}
```
- `fixedDelay`: lần chạy sau bắt đầu **2 giây sau khi lần trước xong**, nên hai lần chạy không bao giờ chồng lên nhau.
- RabbitMQ không gọi được thì ghi **một** dòng WARN rồi dừng lượt chạy đó. Các dòng vẫn `sent_at IS NULL`, lần sau thử lại.
- Thông điệp mang `message_id` = `outbox.id`, `content_type` = JSON, header `requestId`.

**Câu hỏi 4:** Vì sao `@Scheduled` chạy **bên ngoài** transaction là đúng? Nếu cả vòng `for` nằm trong một `@Transactional` thì gửi 50 thư mà thư thứ 30 lỗi sẽ ra sao?

## Bước 7. Consumer: queue, binding, listener

**File** (giống nhau ở profile, meal, analysis, chỉ khác tên queue và repository):
- `events/UserEventsConfig.java`: exchange, queue, queue `.dlq`, binding, `JacksonJsonMessageConverter`.
- `events/UserDeletedListener.java`: `@RabbitListener` → `repository.deleteByUserId(userId)`.
- `repository/...Repository.java`: thêm `deleteByUserId` (một câu `DELETE`; bảng con xoá theo nhờ `ON DELETE CASCADE`).

```java
@RabbitListener(queues = UserEventsConfig.QUEUE)
void onUserDeleted(UserDeleted event,
                   @Header(AmqpHeaders.MESSAGE_ID) String eventId,
                   @Header(name = "requestId", required = false) String requestId) {
    if (requestId != null) MDC.put("requestId", requestId);    // thread của listener không có request
    MDC.put("userId", event.userId().toString());
    try {
        int deleted = mealRepository.deleteByUserId(event.userId());
        log.info("Account deleted (event {}): removed {} meals", eventId, deleted);
    } finally {
        MDC.clear();
    }
}
```
- **Ack:** listener **trả về bình thường** thì Spring ack, **ném exception** thì không ack. Đúng thứ tự "xoá trước, ack sau" ở câu 5.
- **Dead letter queue:** queue chính khai báo `x-dead-letter-exchange=""` và `x-dead-letter-routing-key=<queue>.dlq`. Hết lượt thử lại, thư bị **reject** và RabbitMQ tự chuyển nó sang `.dlq`. Thư không mất, cũng không bị thử lại vô hạn.
- **Vì sao mỗi service tự khai báo exchange:** service nào khởi động trước cũng được. Khai báo một exchange đã tồn tại với cùng thiết lập thì RabbitMQ không làm gì.
- **Vì sao không dùng thư viện chung:** mỗi service tự định nghĩa `record UserDeleted(UUID userId)` của mình. Identity thêm trường mới vào JSON thì consumer bỏ qua, không phải deploy lại cả 4 service cùng lúc.

**Câu hỏi 5:** Thư `{"userId":"not-a-uuid"}` đi vào meal. Lần theo từng bước: listener làm gì, mất bao lâu, cuối cùng thư nằm ở đâu?

---

## Đã kiểm tra những gì

**Test tự động: 48/48 xanh** (`./gradlew test`). Test mới:

| Test | Kiểm tra |
|---|---|
| `identity/.../AccountDeletionTest` | Xoá tài khoản thì user và refresh token mất, outbox có đúng **một** dòng `user.deleted` với `userId` và `requestId` |
| (cùng file) | RabbitMQ lỗi thì dòng vẫn chưa gửi; RabbitMQ nhận thì `sent_at` được ghi |
| `meal`, `profile`, `analysis`: `events/UserDeletedListenerTest` | Chỉ dữ liệu của An bị xoá, của Bình còn nguyên; nhận **cùng event hai lần** vẫn không lỗi; body được đọc qua đúng JSON converter |

**Chạy thật trên laptop** (4 service + RabbitMQ + 4 Postgres):

| Kịch bản | Kết quả |
|---|---|
| Đăng ký → tạo hồ sơ + bữa ăn → `DELETE /api/v1/me` | 204. Khoảng 0,5 giây sau, profile trả 404, meal còn 0 bữa, đăng nhập lại trả 401. `grep e2e-req-42` ra log của **cả 4 service** |
| **Tắt RabbitMQ** rồi mới xoá | Vẫn 204, `/actuator/health` vẫn UP. Relay báo `RabbitMQ unreachable, 1 event(s) waiting` mỗi 2 giây. Bật RabbitMQ lại thì khoảng 12 giây sau dữ liệu được xoá |
| Gửi tay thư hỏng `{"userId":"not-a-uuid"}` | Thử lại 4 lần, rồi nằm trong cả ba queue `*.user-deleted.dlq` với `x-death: rejected` |

Ba thư hỏng đó **vẫn đang nằm** trong RabbitMQ trên laptop. Mở http://localhost:15672 → Queues → `meal.user-deleted.dlq` → *Get messages* để xem tận mắt.

## Tự chạy lại trên laptop

```bash
docker compose up -d
```
Lệnh này khởi động 4 Postgres + RabbitMQ (`-d` = chạy ngầm). Container `rabbit-lab` của bài lab đã được **dừng** (chưa xoá) vì trùng cổng 5672 và 15672. Muốn xoá hẳn: `docker rm rabbit-lab`.

Sau đó chạy 4 service như mọi khi (IntelliJ hoặc `./gradlew :meal-service:bootRun`…), gọi `DELETE /api/v1/me` với access token của một tài khoản test, rồi xem trang quản trị và log.

## Trước khi push lên `main` (CI sẽ tự deploy)

1. **Thêm `RABBITMQ_PASSWORD` vào `.env` trên server**, nếu không deploy sẽ đỏ với lỗi `RABBITMQ_PASSWORD missing in .env`. Lỗi này xảy ra **trước khi** dừng container nào, nên server vẫn chạy bản cũ. Tạo mật khẩu bằng `openssl rand -base64 24`.
2. **RAM:** server 2 GB đang chạy sát giới hạn; RabbitMQ thêm khoảng 150 MB. Sau deploy, xem `free -h` và `docker stats --no-stream`.
3. **Chỉ chạy một bản identity-service.** Hai bản thì hai relay đọc cùng các dòng, mỗi event gửi hai lần (consumer chịu được, nhưng phí). Muốn chạy nhiều bản thì dùng `SELECT ... FOR UPDATE SKIP LOCKED`.
4. **Đổi tham số của queue đã tồn tại** (ví dụ thêm `x-dead-letter-*` sau này) thì RabbitMQ báo `PRECONDITION_FAILED`, listener không khởi động được. Phải xoá queue cũ trên trang quản trị rồi để service tạo lại.
5. **Event phát ra trước khi queue tồn tại sẽ mất** (thí nghiệm 1 của bài lab). Lần deploy đầu, cả 4 service cùng lên nên không sao. Nhưng một consumer mới thêm sau này sẽ không nhận được những event cũ.

## Còn chưa làm (để học tiếp)

- App BaoFit chưa có nút "Xoá tài khoản" gọi `DELETE /api/v1/me`.
- Access token của user đã xoá vẫn gọi được profile/meal/analysis tới khi hết hạn (tối đa 1 giờ, bài 9.2 trong `backend-deep-dive.md`). Họ chỉ thấy dữ liệu rỗng, vì dữ liệu đã bị xoá.
- Job phân tích ảnh **đang chạy dở** đúng lúc user bị xoá: job sẽ ghi vào một dòng không còn tồn tại và log lỗi. Không mất dữ liệu ai, chỉ ồn log.
- Dọn bảng outbox: các dòng đã gửi nằm mãi. Có thể xoá định kỳ những dòng `sent_at` cũ hơn 30 ngày.
- Chưa có cảnh báo khi `.dlq` có thư.

## Sửa lại một ý đã nói lúc học

Mình đã nói tài khoản `guest`/`guest` "chỉ dùng được từ localhost". Điều đó đúng với RabbitMQ nói chung, nhưng **image Docker chính thức đã tắt giới hạn đó**, nên `guest` đăng nhập được từ bất kỳ đâu gọi tới được cổng. Vì vậy, trên server, compose tạo user `visionfit` với mật khẩu riêng, và không mở cổng ra ngoài.

---

## Đáp án câu hỏi tự kiểm tra

<details><summary>Câu 1</summary>

Không có nó, service có thể khởi động **trước** khi RabbitMQ sẵn sàng. Spring vẫn chạy được (nó tự kết nối lại sau), nhưng log đầy lỗi kết nối trong vài chục giây đầu, và listener khởi động trễ. `service_healthy` nghĩa là chờ tới khi `rabbitmq-diagnostics ping` trả lời được mới bật service.
</details>

<details><summary>Câu 2</summary>

`.env` được Spring nạp làm thuộc tính. `RABBITMQ_USER=` là "có, nhưng rỗng", nên `${RABBITMQ_USER:guest}` lấy **chuỗi rỗng** chứ không lấy giá trị mặc định `guest`, và kết nối bị từ chối. Mặc định chỉ được dùng khi **không có** biến đó.
</details>

<details><summary>Câu 3</summary>

`REQUIRES_NEW` mở một transaction **riêng** và commit nó ngay. Nếu sau đó transaction xoá user bị rollback, thì event `user.deleted` **vẫn còn** trong outbox. Profile, meal, analysis sẽ xoá dữ liệu của một user vẫn còn tồn tại. Đó chính là lỗi của câu 8, quay lại qua cửa sau.
</details>

<details><summary>Câu 4</summary>

Nếu vòng lặp nằm trong một transaction, thư 1–29 **đã ra khỏi** RabbitMQ (không thu hồi được), còn `sent_at` của chúng bị **rollback** cùng thư 30. Lần chạy sau sẽ gửi lại cả 29 thư. Mỗi lần `save` riêng nghĩa là cái gì đã được xác nhận thì ghi nhận ngay.
</details>

<details><summary>Câu 5</summary>

JSON converter không đọc được `"not-a-uuid"` thành `UUID` nên ném exception. Retry thử thêm 4 lần, sau 1 + 2 + 4 + 8 = 15 giây. Hết lượt thì `RejectAndDontRequeueRecoverer` reject thư với `requeue=false`, và RabbitMQ chuyển nó sang `meal.user-deleted.dlq`, gắn header `x-death` ghi lý do `rejected`. Queue chính trống, các thư sau đó vẫn được xử lý bình thường.
</details>
