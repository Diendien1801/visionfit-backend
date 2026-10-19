# Mổ xẻ 4 service VisionFit: vì sao code được viết như vậy

> Tài liệu học **chủ động**. Không đọc lướt từ trên xuống: mỗi phần bắt đầu bằng câu hỏi, bạn phải **trả lời trước**, rồi mới mở đáp án.
> Phạm vi: `identity-service` · `profile-service` · `meal-service` · `analysis-service`, đúng như code hiện có trong repo.

## Cách dùng tài liệu này

| Ký hiệu | Bạn làm gì |
|---|---|
| ❓ **Đoán trước** | Viết câu trả lời ra (giấy, comment, hoặc gõ vào chat) **trước khi** mở `▶ Đáp án`. Đoán sai cũng được: cái sai là thứ bạn nhớ lâu nhất |
| 🥊 **Phương án của bạn** | Có một đề bài thiết kế. Bạn tự đưa ra phương án, **nêu lý do**, rồi mới mở `▶ Phản biện`. Nếu phương án của bạn khác đáp án nhưng bạn vẫn bảo vệ được nó, hãy gửi vào chat để mentor phản biện tiếp |
| 🔤 **Syntax** | Bảng cú pháp, có ghi rõ phần nào **dùng chung** cho mọi service và phần nào **riêng** một service |
| 🐞 **Bug thật** | Lỗi **đang có** trong code. Tự sửa, viết test chứng minh đã sửa đúng, rồi nhờ review |
| 📌 **Rút kinh nghiệm** | Checklist mang sang service tiếp theo |

Mỗi bảng so sánh có **một cột cho mỗi service**. Ô ghi `=` nghĩa là "giống cột bên trái".

---

## 0. Bản đồ tổng thể

| | identity | profile | meal | analysis |
|---|---|---|---|---|
| Vai trò | Đăng ký, đăng nhập, **ký** token | Hồ sơ cơ thể (1 user, 1 hồ sơ) | Nhật ký bữa ăn đã xác nhận | Phân tích ảnh bằng AI (job bất đồng bộ) |
| Cổng app / cổng DB | 8081 / 5433 | 8082 / 5434 | 8083 / 5435 | 8084 / 5436 |
| Bảng | `users`, `refresh_tokens` | `body_profiles` | `meals`, `meal_items` | `analysis_jobs`, `analysis_photos`, `analysis_detections` |
| Token | **Ký** (jjwt) + tự kiểm tra (filter tự viết) | Chỉ kiểm tra (resource server) | = | = |
| Số test | 2 | 4 | 16 | 10 |
| Bài học lớn nhất | Hash mật khẩu, refresh token, rotation | PK = `user_id`, upsert, resource server | Idempotency, race condition, `@EntityGraph`, server tự tính | Job bất đồng bộ, luồng nền, `TransactionTemplate`, dịch vụ bên ngoài |

❓ **Đoán trước:** vì sao lại là **4 database** riêng, mà không phải 1 database chung có 4 schema?

<details><summary>▶ Đáp án</summary>

Quy tắc cốt lõi không phải là "phải có 4 container", mà là **không service nào được đọc hay ghi bảng của service khác**. Có DB riêng thì quy tắc này được **ép buộc về mặt vật lý**:

| Lợi ích | Ví dụ trong VisionFit |
|---|---|
| Đổi schema không ảnh hưởng service khác | meal sửa `meal_items`, identity không cần biết |
| Một DB sập không kéo các service khác sập theo | analysis-db đầy ổ đĩa vì ảnh, nhưng đăng nhập vẫn chạy |
| Scale riêng từng phần | analysis lưu ảnh (nặng), profile chỉ có một dòng cho mỗi user |

**Cái giá phải trả** (bạn sẽ gặp lại ở cả tài liệu này): không `JOIN` được giữa các service, không có foreign key `user_id → users`, không có transaction nào trải trên hai service.

Dùng 1 database với 4 schema, mỗi service một user DB riêng, cũng là một bước trung gian hợp lệ, miễn là giữ đúng quy tắc trên.
</details>

---

## 1. Gradle: `build.gradle`

| Dependency | identity | profile | meal | analysis | Vì sao |
|---|---|---|---|---|---|
| `starter-webmvc` | ✓ | ✓ | ✓ | ✓ | REST + Tomcat |
| `starter-data-jpa` + `postgresql` | ✓ | ✓ | ✓ | ✓ | Entity, repository, driver |
| `starter-flyway` + `flyway-database-postgresql` | ✓ | ✓ | ✓ | ✓ | Migration. Từ Flyway 10, phần hỗ trợ từng loại DB tách thành module riêng |
| `starter-validation` | ✓ dùng | ✓ dùng | ✓ dùng | ✓ **không dùng** | `@Valid`, `@NotNull` |
| `starter-actuator` | ✓ | ✓ | ✓ | ✓ | `/actuator/health` |
| `starter-security` | ✓ | | | | Filter chain, `PasswordEncoder`, `AuthenticationManager` |
| `starter-security-oauth2-resource-server` | | ✓ | ✓ | ✓ | Kiểm tra JWT bằng Nimbus (đã bao gồm `starter-security`) |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | ✓ | | | | **Ký** token |
| `anthropic-java` | | | | ✓ | Gọi Claude |
| `h2` (test) | ✓ | ✓ | ✓ | ✓ | DB trong bộ nhớ, dùng cho test |

❓ **Đoán trước 1.1:** vì sao `jjwt-api` là `implementation`, còn `jjwt-impl` lại là `runtimeOnly`?

<details><summary>▶ Đáp án</summary>

Code của bạn chỉ được phép `import` phần **API** (`Jwts`, `Claims`, `Keys`). Phần **impl** chỉ được nạp lúc chạy, qua cơ chế ServiceLoader. Vì không nằm trên classpath lúc compile, bạn **không thể vô tình** `import io.jsonwebtoken.impl.*`. Đó là các class nội bộ, có thể đổi bất cứ lúc nào giữa hai phiên bản. Driver `postgresql` là `runtimeOnly` với cùng lý do: code chỉ biết tới JDBC, không bao giờ `import org.postgresql`.
</details>

❓ **Đoán trước 1.2:** vì sao chỉ identity có `jjwt`? Profile cũng cần đọc token mà.

<details><summary>▶ Đáp án</summary>

Hai việc khác nhau:
- **Ký** token cần `jjwt`, và chỉ identity làm việc này.
- **Kiểm tra** token đã có sẵn trong `oauth2-resource-server`, thông qua thư viện Nimbus.

Thêm `jjwt` vào profile không làm gì sai, nhưng sẽ có **hai thư viện JWT** trong cùng một service. Đọc code, người khác không biết thư viện nào mới là cái đang dùng.
</details>

🔤 **Syntax: các loại phạm vi (scope) của Gradle** (chung cho mọi service)

| Scope | Có lúc compile code chính? | Có lúc chạy? | Có trong test? | Ví dụ |
|---|---|---|---|---|
| `implementation` | ✓ | ✓ | ✓ | `starter-webmvc`, `jjwt-api` |
| `runtimeOnly` | ✗ | ✓ | ✓ | `postgresql`, `jjwt-impl` |
| `testImplementation` | ✗ | ✗ | ✓ (compile + chạy) | `starter-webmvc-test` |
| `testRuntimeOnly` | ✗ | ✗ | ✓ (chỉ chạy) | `h2`, `junit-platform-launcher` |

Plugin của module **không ghi version**, vì version được khai báo một lần ở `build.gradle` gốc (`apply false`). Dependency của Spring cũng không ghi version, vì `io.spring.dependency-management` tra từ BOM của Boot 4.1.1. Dependency **ngoài** BOM (`jjwt`, `anthropic-java`) thì **bắt buộc** ghi version.

🐞 **Bug 1.A (dễ):** analysis-service khai báo `starter-validation` nhưng không dùng ở đâu cả. Hãy quyết định: **xoá** dependency, hoặc **dùng nó**, bằng cách thêm `@Validated` cho controller và `@NotBlank @Size(max = 2048)` cho `photoRef`, thay cho câu `if` đang tự kiểm tra. Lựa chọn nào tốt hơn, và vì sao?

📌 **Rút kinh nghiệm**
- Service chỉ **đọc** token: dùng `oauth2-resource-server`, **không** thêm `jjwt`.
- Copy `build.gradle` từ service khác sang thì phải rà lại **từng dòng**: dòng nào service mới không dùng thì xoá.

---

## 2. Cấu hình: `application.properties`

| Dòng | identity | profile | meal | analysis | Vì sao |
|---|---|---|---|---|---|
| `spring.config.import=optional:file:.env[.properties],…../.env…` | ✓ | ✓ | ✓ | ✓ | Spring **không tự đọc** `.env`, chỉ Docker Compose làm vậy |
| `server.port=${X_SERVICE_PORT:808n}` | 8081 | 8082 | 8083 | 8084 | |
| `ddl-auto=validate` | ✓ | ✓ | ✓ | ✓ | Flyway là bên duy nhất được sửa schema |
| `open-in-view=false` | ✓ | ✓ | ✓ | ✓ | Đọc quan hệ lazy ngoài transaction sẽ **lỗi ngay**, không âm thầm chạy thêm query |
| `hibernate.jdbc.time_zone=UTC` | ✓ | ✓ | **✗** | **✗** | Xem câu 2.1 |
| `problemdetails.enabled=true` | **✗** (có handler riêng) | ✓ | ✓ | ✓ | Lỗi trả theo chuẩn RFC 9457 |
| JWT | `secret`, `expiration-ms`, `refresh-expiration-ms` → `@ConfigurationProperties` | `secret` → `@Value` | = | = | |
| `multipart.max-file-size=5MB` / `max-request-size=6MB` | | | | ✓ | Giới hạn ảnh của Claude là 5 MB |
| `visionfit.ai.api-key=${ANTHROPIC_API_KEY:}` | | | | ✓ | Mặc định rỗng: thiếu key thì service vẫn khởi động được |

❓ **Đoán trước 2.1:** identity và profile đặt `time_zone=UTC`, còn meal và analysis thì cố ý **không** đặt. Vì sao?

<details><summary>▶ Đáp án</summary>

| | Các cột thời gian | Kiểu Java | Ý nghĩa |
|---|---|---|---|
| identity, profile | `created_at`, `updated_at`, `expires_at` | `Instant` | Một **thời điểm** tuyệt đối trên server |
| meal, analysis | `logged_at`, `captured_at` | `LocalDateTime` | "19:05 **theo đồng hồ của user**" |

`time_zone=UTC` bảo Hibernate quy đổi thời gian khi ghi xuống và đọc lên JDBC. Với `Instant` thì không có vấn đề: một thời điểm đổi sang UTC vẫn là chính thời điểm đó. Còn với **giờ trên đồng hồ** thì việc quy đổi là **sai**: user ăn lúc 19:05, nhưng nếu JVM chạy ở múi giờ khác UTC thì giá trị lưu xuống có thể bị dịch đi vài tiếng. Một bữa tối có thể **nhảy sang hôm khác**, và streak bị tính sai. Các cột `created_at` của meal và analysis là `Instant` lưu vào `TIMESTAMPTZ`, vốn đã là thời điểm tuyệt đối, nên không cần thiết lập này.
</details>

❓ **Đoán trước 2.2:** vì sao identity dùng `@ConfigurationProperties` (`JwtProperties`), còn ba service kia dùng `@Value`?

<details><summary>▶ Đáp án</summary>

Identity có **3 giá trị liên quan với nhau**, dùng ở **2 class** (`JwtService`, `RefreshTokenService`). Gom chúng vào một class có kiểu rõ ràng thì tránh được việc gõ sai tên key ở nhiều chỗ. Các service còn lại chỉ có **1 giá trị**, dùng ở **1 chỗ**, nên `@Value` là đủ. Quy tắc thực tế: từ khoảng 3 giá trị trở lên, hoặc dùng ở nhiều class, thì chuyển sang `@ConfigurationProperties`.

Ngoài ra, `@EnableConfigurationProperties(JwtProperties.class)` nằm trên `SecurityConfig` chỉ để **đăng ký** class đó thành bean. Đặt trên bất kỳ class `@Configuration` nào cũng được.
</details>

🥊 **Phương án của bạn 2.3:** "Để `JWT_SECRET` có giá trị mặc định ngay trong `application.properties` cho tiện, lỡ quên `.env` thì vẫn chạy được." Bạn đồng ý hay phản đối? Nghĩ về trường hợp **production**.

<details><summary>▶ Phản biện</summary>

Giá trị mặc định đó **nằm trong git**. Nếu production quên đặt `JWT_SECRET`:
- identity **ký** token bằng một secret công khai, ai đọc repo cũng biết;
- bất kỳ ai cũng **tự tạo** được token với `uid` của người khác, và profile, meal, analysis đều chấp nhận token đó.

Lỗi này **không gây ra một exception nào**, nên không ai phát hiện. So sánh với các lựa chọn khác:

| Phương án | Dev | Production |
|---|---|---|
| Có mặc định trong git (hiện tại) | Tiện | 🔥 Âm thầm mất an toàn |
| Không có mặc định: `${JWT_SECRET}` | Phải có `.env` | ✅ Service **không khởi động** khi thiếu biến (fail fast) |
| Mặc định chỉ trong profile `dev` (`application-dev.properties`) | Tiện | ✅ An toàn |

Mật khẩu DB mặc định `change_me` thì đỡ nguy hiểm hơn: DB chỉ mở trên `127.0.0.1`, và production sẽ không có user DB nào dùng mật khẩu đó.
</details>

🔤 **Syntax: placeholder của properties** (chung)

| Cú pháp | Nghĩa |
|---|---|
| `${VAR}` | Bắt buộc phải có, thiếu thì service không khởi động |
| `${VAR:abc}` | Thiếu thì dùng `abc` |
| `${VAR:}` | Thiếu thì dùng **chuỗi rỗng** (analysis dùng cho API key) |
| `optional:file:.env` | Không có file thì bỏ qua, không lỗi |
| `[.properties]` | Báo cho Spring biết file `.env` (đuôi lạ) có định dạng properties |

Test có file `src/test/resources/application.properties` **riêng**. File này che hẳn file ở `main` (cùng tên trên classpath), nên phải ghi lại các dòng cần thiết, và test sẽ **không** đọc `.env`.

📌 **Rút kinh nghiệm**
- Lưu `Instant` thì đặt `time_zone=UTC`. Có cột giờ theo đồng hồ của user (`LocalDateTime`) thì **không đặt**, và ghi comment giải thích vì sao.
- Secret: production **không có mặc định**, để thiếu biến là service dừng ngay.

---

## 3. Thiết kế database (Flyway migration)

### 3.1 Toàn cảnh

```
identity-db   users (1) ──< refresh_tokens          FK thật (cùng DB)
profile-db    body_profiles                          PK = user_id, không có FK
meal-db       meals (1) ──< meal_items               FK thật · meals.user_id không có FK
analysis-db   analysis_jobs (1) ──< analysis_detections
                            (1) ──  analysis_photos  (1–1, dùng chung khoá chính)
```

### 3.2 So sánh theo từng quyết định thiết kế

| Quyết định | identity | profile | meal | analysis |
|---|---|---|---|---|
| **Khoá chính** | `users.id` UUID, Hibernate sinh | `user_id` (lấy từ token) | `meals.id` **app** sinh · `meal_items.id` Hibernate sinh | `jobs.id` Hibernate sinh · `photos.job_id` dùng chung với job |
| **Khoá ngoại** | `refresh_tokens.user_id → users` `ON DELETE CASCADE` | Không có (user nằm ở DB khác) | `meal_items → meals` CASCADE · `meals.user_id` không có FK | `detections`, `photos → jobs` CASCADE |
| **Enum** | Không có | `VARCHAR + CHECK IN (...)` | = | = |
| **`CHECK` khoảng giá trị** | Không có | tuổi 14–90, cao 120–220, nặng 30–200 | grams 1–5000, kcal 0–900, macro 0–100 | **Giống hệt** meal + region 0–1 |
| **`UNIQUE`** | `email`, `token_hash` | (khoá chính đã đảm bảo) | `(user_id, job_id)`, `(meal_id, position)` | `(job_id, position)` |
| **Index** | `email` (**thừa**), `user_id`, `expires_at` | | `(user_id, logged_at)` | `(user_id, captured_at)` (**chưa dùng**) |
| **Kiểu thời gian** | `TIMESTAMPTZ` | `TIMESTAMPTZ` | `logged_at TIMESTAMP` · `created_at TIMESTAMPTZ` | `captured_at TIMESTAMP` · `created_at`, `queued_at TIMESTAMPTZ` |
| **Dữ liệu bí mật** | `password_hash` (BCrypt), `token_hash` (SHA-256) | | | |
| **Dữ liệu lớn** | | | `photo_url` (chỉ đường dẫn) | `BYTEA` nằm ở **bảng riêng** |
| **Cách xoá** | Thu hồi bằng **`revoked_at`** (xoá mềm) | Ghi đè (upsert) | **Xoá thật** (`DELETE` + CASCADE) | Chưa có |

### 3.3 Phương án của bạn (phần quan trọng nhất)

Mỗi đề bài: **viết ra phương án của bạn trước**, kèm 1–2 lý do, rồi mới mở phần phản biện.

🥊 **D1. Khoá chính của `body_profiles`.** Phương án A: `user_id` là khoá chính (hiện tại). Phương án B: thêm cột `id UUID` làm khoá chính, cộng với `UNIQUE(user_id)`. Bạn chọn phương án nào?

<details><summary>▶ Phản biện</summary>

| | A: PK = `user_id` | B: `id` + `UNIQUE(user_id)` |
|---|---|---|
| "Mỗi user một hồ sơ" | DB tự đảm bảo nhờ khoá chính | Cũng được đảm bảo, nhưng cần thêm một index |
| API | `/profile/me`, không cần id hồ sơ | Cột `id` tồn tại nhưng không ai dùng |
| Đổi luật thành "một user nhiều hồ sơ" | Phải đổi khoá chính, migration khó | Chỉ cần bỏ `UNIQUE` |

A đúng cho **luật nghiệp vụ hiện tại**. Hãy chọn B **chỉ khi** bạn thật sự thấy trước tương lai cần nhiều hồ sơ, ví dụ hồ sơ cho cả gia đình. Đừng thiết kế cho một tương lai chưa chắc sẽ xảy ra.
</details>

🥊 **D2. Có nên lưu cột `total_kcal` trong bảng `meals` cho nhanh?**

<details><summary>▶ Phản biện</summary>

Hiện tại tổng được **tính lúc đọc** (`Meal.nutrition()` → `MealDto.kcal`). Nếu lưu sẵn một cột tổng:

| | Tính lúc đọc (hiện tại) | Lưu sẵn cột tổng |
|---|---|---|
| Đúng sau khi sửa món | Luôn đúng | Mọi nơi sửa `meal_items` đều phải nhớ cập nhật cột tổng. Quên một chỗ là **hai con số mâu thuẫn nhau** |
| Tốc độ | Cộng khoảng 5 món mỗi bữa, không đáng kể | Nhanh hơn một chút |
| Đổi công thức (ví dụ cách làm tròn) | Sửa code, dữ liệu cũ tự đúng theo | Phải chạy migration tính lại toàn bộ |

Quy tắc chung: **không lưu dữ liệu suy ra được**, trừ khi việc tính toán đã được **đo** và thấy chậm. Profile cũng vậy: nó lưu chiều cao, cân nặng, mục tiêu, chứ **không** lưu `target_kcal`.
</details>

🥊 **D3. Dinh dưỡng của món: lưu "trên 100 g" hay lưu "cho cả phần ăn"?**

<details><summary>▶ Phản biện</summary>

Lưu **trên 100 g** cộng với `grams`. User sửa 150 g thành 200 g thì kcal **tự đổi theo**, vì kcal = per100g × grams / 100. Nếu lưu "cho cả phần", khi đổi gram bạn phải tự chia rồi nhân lại, và sai số làm tròn **cộng dồn** sau mỗi lần sửa. Đây cũng là định dạng app (`NutrientsPer100g`) và AI dùng, nên ba bên nói cùng một "ngôn ngữ".
</details>

🥊 **D4. Mật khẩu dùng BCrypt, refresh token dùng SHA-256.** Phương án A: dùng BCrypt cho cả hai, "cho an toàn". Phương án B: lưu nguyên văn refresh token, "vì nó vốn là chuỗi ngẫu nhiên". Bạn chọn gì?

<details><summary>▶ Phản biện</summary>

| | Mật khẩu | Refresh token |
|---|---|---|
| Ai tạo ra | **Người dùng**, nên thường dễ đoán (`12345678`) | Server: 32 byte ngẫu nhiên (`SecureRandom`) |
| Kẻ tấn công lấy được hash thì | Thử hàng tỷ mật khẩu phổ biến. Phải dùng hàm hash **chậm** (BCrypt) | Không thể thử hết 2²⁵⁶ khả năng, nên hash **nhanh** cũng an toàn |
| Cần **tìm theo** giá trị? | Không: tìm user theo email, rồi `matches()` | **Có**: `findByTokenHash(sha256(token))` |

- **A sai:** BCrypt có muối (salt) ngẫu nhiên, nên cùng một token sẽ ra hash khác nhau mỗi lần. Bạn **không thể** `WHERE token_hash = ?`, mà phải lấy hết token ra so từng cái một.
- **B sai:** ai đọc được DB (bản backup, hay lỗi SQL injection) là dùng được ngay mọi refresh token, tức là đăng nhập như bất kỳ user nào trong 7 ngày.

`token_hash VARCHAR(64)` vì SHA-256 viết dạng hex dài đúng 64 ký tự. `password_hash VARCHAR(255)`, dù BCrypt chỉ dài 60 ký tự, để còn chỗ khi đổi thuật toán sau này (ví dụ sang Argon2).
</details>

🥊 **D5. Lưu enum thế nào?** Có 4 phương án: (A) `VARCHAR + CHECK` (hiện tại), (B) kiểu `ENUM` của Postgres, (C) một bảng tra cứu `meal_types`, (D) `INTEGER` (ORDINAL).

<details><summary>▶ Phản biện</summary>

| | A `VARCHAR + CHECK` | B kiểu `ENUM` Postgres | C bảng tra cứu | D `INTEGER` |
|---|---|---|---|---|
| Đọc DB bằng mắt | `'DINNER'` | `'DINNER'` | Cần `JOIN` | `3`, nhưng 3 là gì? |
| Thêm giá trị | Migration: thay `CHECK` | `ALTER TYPE … ADD VALUE` (có giới hạn khi chạy trong transaction) | `INSERT` một dòng | Chèn vào giữa là **làm hỏng dữ liệu cũ** |
| H2 khi test | Chạy được | Khó | Chạy được | Chạy được |
| JPA | `@Enumerated(STRING)` | Cần cấu hình thêm | Cần entity riêng | `ORDINAL`, mặc định nguy hiểm |

A là lựa chọn cân bằng nhất cho một tập giá trị **nhỏ, ít khi đổi, và gắn với code** (vì code có `switch` trên chúng). Dùng C khi giá trị do **người dùng hoặc admin** thêm vào, ví dụ danh mục món ăn.
</details>

🥊 **D6. User xoá tài khoản ở identity.** Bữa ăn của họ trong meal-db thì sao? Phương án của bạn: "Thêm `FOREIGN KEY (user_id) REFERENCES users ON DELETE CASCADE` vào `meals`."

<details><summary>▶ Phản biện</summary>

**Không làm được**: `users` nằm ở **một database khác**, mà Postgres không tạo được FK sang DB khác. Đây chính là cái giá của việc mỗi service một DB (phần 0). Các hướng xử lý:

| Cách | Mô tả | Độ phức tạp |
|---|---|---|
| Kệ nó | Dữ liệu mồ côi vẫn nằm đó, không ai truy cập được (vì không còn token nào có `uid` đó) | Thấp, nhưng **vi phạm quyền được xoá dữ liệu** (GDPR, Nghị định 13) |
| Identity gọi `DELETE` sang từng service | Gọi đồng bộ qua HTTP | Một service sập là xoá dở dang |
| **Sự kiện** `UserDeleted` qua message broker (Kafka, RabbitMQ) | Mỗi service tự nghe sự kiện và tự xoá dữ liệu của mình | Cách chuẩn trong microservice. Thuộc phần "hạ tầng" sẽ học sau |

Chú ý: `refresh_tokens → users` **có** FK, vì hai bảng nằm **cùng một DB**.
</details>

🥊 **D7. Ảnh của job phân tích lưu ở đâu?** (A) Thêm cột `photo BYTEA` vào `analysis_jobs`. (B) Bảng riêng (hiện tại). (C) Object storage (S3/MinIO), DB chỉ lưu đường dẫn.

<details><summary>▶ Phản biện</summary>

| | A cùng bảng | B bảng riêng | C S3/MinIO |
|---|---|---|---|
| App polling `GET /{id}` mỗi giây | Hibernate `SELECT` **mọi cột**, nên mỗi lần đọc kéo theo khoảng 170 KB ảnh | Chỉ vài trăm byte | Chỉ vài trăm byte |
| `@Basic(fetch = LAZY)` có cứu được A không? | **Không**, nếu không bật bytecode enhancement | | |
| Hạ tầng | Không cần thêm gì | Không cần thêm gì | Thêm một dịch vụ, thêm quyền truy cập, thêm việc dọn file rác |
| Khi nào hợp lý | Không bao giờ | Ảnh nhỏ, ít user (hiện tại) | Nhiều user, ảnh thật từ camera |

Từ B chuyển sang C chỉ phải sửa **một bảng và một repository**. Tách bảng ngay từ đầu là để giữ được đường lui đó.
</details>

🥊 **D8. `meals.job_id` là `VARCHAR(64)`, không phải `UUID` và không có FK.** Vì sao không dùng `UUID` cho chặt?

<details><summary>▶ Phản biện</summary>

`job_id` là id của **một service khác** (analysis). Meal không được giả định bất cứ điều gì về định dạng của nó: hôm nay là UUID, mai có thể là `job_01H…` (ULID). `VARCHAR(64)` nghĩa là "một chuỗi định danh nào đó, đủ ngắn", và vẫn chứa vừa một UUID (36 ký tự). FK thì không thể có, vì hai bảng nằm ở hai DB khác nhau (D6).
</details>

🥊 **D9. Xoá mềm hay xoá thật?** Refresh token dùng `revoked_at` (xoá mềm). Bữa ăn thì `DELETE` thật. Có nên thống nhất một kiểu cho cả hai?

<details><summary>▶ Phản biện</summary>

Hai loại dữ liệu có **mục đích khác nhau**:
- Một token đã thu hồi **vẫn còn giá trị**: nếu một token đã bị thu hồi mà vẫn có người gửi lên, đó là dấu hiệu token bị đánh cắp (*reuse detection*). Xoá thật là mất dấu vết này.
- Bữa ăn bị xoá thì user muốn nó **biến mất**. Xoá mềm buộc **mọi** query phải thêm `WHERE deleted_at IS NULL`. Quên ở một chỗ là bữa đã xoá vẫn được tính vào tổng kcal.

Chọn xoá mềm khi dữ liệu đã xoá **vẫn được dùng vào việc gì đó** (kiểm toán, phát hiện tấn công, khôi phục). Ngoài các trường hợp đó, xoá thật.
</details>

🥊 **D10. Index `(user_id, logged_at)`.** Phương án của bạn: "Tạo hai index riêng lẻ, `(user_id)` và `(logged_at)`, cho linh hoạt."

<details><summary>▶ Phản biện</summary>

Câu query thật là `WHERE user_id = ? AND logged_at >= ? AND logged_at < ? ORDER BY logged_at DESC`.
- **Index ghép** `(user_id, logged_at)`: nhảy thẳng tới các dòng của user đó, mà các dòng này **đã được sắp xếp sẵn** theo `logged_at`. Chỉ cần quét đúng khoảng ngày, không phải sort lại.
- **Hai index riêng lẻ**: Postgres chọn một trong hai (hoặc kết hợp bitmap), sau đó vẫn phải lọc tiếp và sort.

**Thứ tự cột trong index rất quan trọng:** cột so sánh bằng (`=`) đặt trước, cột so sánh khoảng (`>=`, `<`) đặt sau. Index `(logged_at, user_id)` sẽ phải quét **mọi user** trong khoảng ngày đó.
</details>

🥊 **D11. Cột `position` cùng `UNIQUE(meal_id, position)`.** Phương án của bạn: "Bỏ cột `position` đi, sắp xếp các món theo `id` là được."

<details><summary>▶ Phản biện</summary>

`id` là UUID **ngẫu nhiên**, nên sắp theo `id` cho ra thứ tự ngẫu nhiên. Có thể đổi sang sắp theo thời gian tạo, nhưng khi user sửa bữa ăn, món mới thêm vào luôn bị dồn xuống cuối, không giữ được thứ tự user đã thấy. `position` lưu **đúng thứ tự trên màn hình**. Còn `UNIQUE` thì chặn được trường hợp hai món cùng vị trí.

Hệ quả bạn đã gặp: Hibernate `INSERT` các dòng mới trước khi `DELETE` các dòng cũ. Vì vậy `Meal.update()` phải **ghi đè từng dòng theo vị trí**, thay vì xoá hết rồi thêm lại.
</details>

🥊 **D12. `logged_at` dùng `TIMESTAMP` (không múi giờ).** Phương án của bạn: "Dùng `TIMESTAMPTZ` cho mọi cột thời gian, cho chuẩn."

<details><summary>▶ Phản biện</summary>

Hỏi: "Bữa này thuộc **ngày nào** trong nhật ký?" Câu trả lời đúng là **ngày trên đồng hồ của user lúc ăn**. Một user ở Việt Nam ăn lúc 23:30 rồi bay sang Nhật: bữa đó vẫn phải nằm ở ngày cũ.
- `TIMESTAMPTZ` lưu **thời điểm tuyệt đối**. Muốn biết "ngày" thì phải biết múi giờ của user, mà server không biết.
- `TIMESTAMP` cộng `LocalDateTime` lưu **đúng con số user nhìn thấy**, nên `toLocalDate()` luôn ra đúng ngày.

Đây cũng là lý do `GET /streak?today=` nhận `today` **từ app**: chỉ app biết hôm nay là ngày nào với user.
</details>

### 3.4 Câu hỏi nhanh về hành vi của SQL

❓ **3.4.1** `UNIQUE (user_id, job_id)`: user tự nhập 5 bữa (cả 5 có `job_id = NULL`). Có bị vi phạm `UNIQUE` không?
❓ **3.4.2** `failure VARCHAR(30) CHECK (failure IN ('A','B'))`: một job chưa lỗi có `failure = NULL`. `CHECK` có từ chối không?
❓ **3.4.3** `grams BETWEEN 1 AND 5000`: giá trị 5000 có được chấp nhận không?
❓ **3.4.4** Sửa nội dung file `V1__create_meals_tables.sql` **sau khi** nó đã chạy trên DB của bạn. Khởi động lại service thì sao?
❓ **3.4.5** `users` có `UNIQUE (email)`, lại có thêm `CREATE INDEX idx_users_email ON users (email)`. Vì sao index thứ hai là thừa?

<details><summary>▶ Đáp án</summary>

1. **Không vi phạm.** Với SQL, `NULL = NULL` không cho ra `true` mà là *unknown*, nên các `NULL` không bao giờ "trùng" nhau. Đây là lý do `SaveMealRequest.jobId` có `@Size(min = 1)`: chuỗi rỗng `""` thì lại **trùng nhau** được.
2. **Không từ chối.** `NULL IN (...)` cho ra *unknown*, và `CHECK` chỉ từ chối khi biểu thức cho ra `false`. Muốn bắt buộc phải có giá trị thì thêm `NOT NULL`.
3. **Có.** `BETWEEN` bao gồm cả hai đầu mút.
4. Flyway so sánh **checksum** của file với checksum đã lưu trong bảng `flyway_schema_history`. Lệch là báo *Migration checksum mismatch*, và **service không khởi động**. Một migration đã chạy thì **không bao giờ được sửa**: muốn thay đổi thì viết `V2__…sql`.
5. Postgres **tự tạo một index** để thực thi `UNIQUE`. Index thứ hai trùng hoàn toàn: tốn chỗ, và làm chậm mọi lần `INSERT`. (Xem thêm 🐞 6.A: cả hai index này còn **không được dùng** khi đăng nhập.)
</details>

🔤 **Syntax SQL / Flyway** (chung)

| Cú pháp | Ví dụ trong repo | Ghi nhớ |
|---|---|---|
| `V<n>__<mô_tả>.sql` | `V2__create_refresh_tokens_table.sql` | **Hai** dấu gạch dưới. Chạy theo thứ tự số `n` |
| `CHECK (x IN (...))` | `stage IN ('RECOGNIZING', …)` | Cho qua `NULL` |
| `CHECK (x BETWEEN a AND b)` | `grams BETWEEN 1 AND 5000` | Bao gồm cả hai đầu |
| `CONSTRAINT <tên> UNIQUE (...)` | `uq_meals_user_job` | Đặt tên để thông báo lỗi dễ đọc và để sau này `DROP` được |
| `REFERENCES t (id) ON DELETE CASCADE` | `meal_items → meals` | Chỉ dùng được **trong cùng một DB** |
| `TIMESTAMPTZ` / `TIMESTAMP WITH TIME ZONE` | `created_at` | Hai cách viết, cùng một kiểu |
| `DEFAULT NOW()` | `users.created_at` | Chỉ áp dụng khi `INSERT` **không gửi** cột đó. Entity có `@PrePersist` thì luôn gửi, nên `DEFAULT` chỉ còn là lớp dự phòng |
| `BYTEA` | `analysis_photos.bytes` | Ứng với `byte[]` trong Java |

🐞 **Bug 3.A (dễ):** `meal_items.name` và `analysis_detections.name` thiếu `CHECK (length(trim(name)) > 0)`. Viết `V2__…sql` cho meal, kèm một test chứng minh DB từ chối tên chỉ gồm khoảng trắng.
🐞 **Bug 3.B (trung bình):** `analysis_jobs` vẫn cho lưu `stage = 'COMPLETED'` cùng lúc với `failure = 'NO_FOOD_DETECTED'`. Viết `CHECK ((stage = 'FAILED') = (failure IS NOT NULL))` trong `V2`. Câu hỏi thêm: H2 có chạy được câu này không?
🐞 **Bug 3.C (dễ):** xoá `idx_users_email` bằng `V3__drop_redundant_email_index.sql`. **Không** sửa `V1`.

📌 **Rút kinh nghiệm thiết kế DB**
1. Viết **luật nghiệp vụ** ra trước ("mỗi user một hồ sơ", "một ảnh một bữa"), rồi biến **từng luật** thành một ràng buộc (`PK`, `UNIQUE`, `CHECK`).
2. `user_id` lấy từ token. Không bao giờ có FK sang DB của service khác.
3. Enum dùng `VARCHAR + CHECK`. Khoảng giá trị: `CHECK` trong DB **trùng khớp** với annotation validation trong DTO.
4. Thời điểm dùng `TIMESTAMPTZ` + `Instant`. Giờ theo đồng hồ của user dùng `TIMESTAMP` + `LocalDateTime`.
5. Không lưu dữ liệu suy ra được. Dữ liệu lớn để ở bảng riêng.
6. Index đặt theo **câu query thật**. Cột `=` đứng trước, cột khoảng đứng sau. `UNIQUE` đã tự có index.
7. Migration đã chạy thì không sửa. Muốn đổi thì thêm `V<n+1>`.

---

## 4. Entity (package `domain/`)

| Khía cạnh | identity | profile | meal | analysis |
|---|---|---|---|---|
| **Kiểu entity** | **Thiếu hành vi** (anemic): có setter cho mọi field | Có hành vi: `update(...)` | Có hành vi: `update`, `nutrition()` | **State machine**: `start`, `recognized`, `complete`, `fail`, `requeue` |
| Constructor | Mặc định `public` | `protected` + `BodyProfile(userId)` | `protected` + constructor đầy đủ | = |
| Sinh id | `@GeneratedValue(UUID)` | Gán tay (`userId`) | Gán tay (app gửi) + **`Persistable`** | `@GeneratedValue` · `AnalysisPhoto` gán tay |
| Thời gian | `@PrePersist` / `@PreUpdate` | `@PrePersist @PreUpdate touch()` | `@PrePersist` | Truyền `now` qua tham số |
| Quan hệ | `RefreshToken @ManyToOne(LAZY) User` | Không có | `@OneToMany` một chiều → `MealItem` | `@OneToMany` → `Detection` · ảnh **chỉ nối qua id** |
| Enum lồng trong entity | | `Sex`, `ActivityLevel`, `FitnessGoal` | `Type` | `MealType`, `Stage`, `Failure` |
| Logic nghiệp vụ trong entity | `RefreshToken.isActive()` | | `MealItem.nutrition()`, `Meal.update()` | `start()` chặn worker thứ hai |

❓ **Đoán trước 4.1:** profile và meal đều có id **gán tay**. Vì sao meal phải implement `Persistable`, còn profile thì không?

<details><summary>▶ Đáp án</summary>

Khi id đã có giá trị, `repository.save()` của Spring Data gọi `merge`: chạy `SELECT` trước, rồi `INSERT` nếu chưa có dòng nào, hoặc **`UPDATE` (ghi đè)** nếu đã có.

| | Mong muốn | `merge` có đúng ý không? |
|---|---|---|
| profile | **Upsert**: chưa có thì tạo, có rồi thì ghi đè | ✅ `merge` **chính là** upsert |
| meal | **Chỉ được tạo mới**. Trùng id thì phải báo lỗi để service xử lý (200 / 409) | ❌ Trong một cuộc đua, `merge` có thể **ghi đè bữa ăn của người khác** |

`Persistable.isNew()` trả `true` trước khi lưu, nên `save()` gọi `persist`, tức là chỉ `INSERT`. Trùng id thì nhận lỗi `DataIntegrityViolationException`. `@PostPersist` và `@PostLoad` đặt `isNew = false`, để entity vừa đọc lên **không** bị coi là mới.

Cùng một cơ chế, mục đích khác nhau thì cách xử lý khác nhau. `AnalysisPhoto` cũng có id gán tay nhưng không cần `Persistable`: id đó là id của một job vừa tạo, không thể trùng, nên cái giá chỉ là thêm một câu `SELECT`.
</details>

❓ **Đoán trước 4.2:** `RefreshToken` có `@ManyToOne User user`, nhưng `Meal` lại **không có** `@ManyToOne User`. Vì sao?

<details><summary>▶ Đáp án</summary>

`User` là entity của identity-service, và **không tồn tại** trong code hay DB của meal. Meal chỉ biết `UUID userId`, lấy từ token. Trong một microservice, chỉ được map quan hệ JPA tới những entity **cùng service, cùng DB**.

Còn `fetch = LAZY` ở `RefreshToken`: mặc định của `@ManyToOne` là `EAGER`. Đọc token mà không đặt `LAZY` thì Hibernate luôn kéo theo `User`, kể cả khi không cần.
</details>

🥊 **4.3 Phương án của bạn:** "Thêm setter cho mọi field của `Meal`, giống `User` ở identity, cho dễ dùng."

<details><summary>▶ Phản biện</summary>

Thử viết `Meal.update()` lại bằng setter:
```java
meal.setType(t); meal.setEdited(true); meal.getItems().clear(); meal.getItems().addAll(newItems);
```
Đoạn này có **hai lỗi**:
1. `clear()` rồi `addAll()` vi phạm `UNIQUE(meal_id, position)`, vì Hibernate `INSERT` các dòng mới trước khi `DELETE` các dòng cũ.
2. Nơi khác gọi lại đoạn này mà quên `setEdited(true)`, thế là cờ "đã sửa" sai.

Gói logic vào một hàm `update()` thì cách sửa đúng chỉ nằm ở **một chỗ**. Setter thì để mọi nơi tự sửa theo ý mình. Identity vẫn ổn khi dùng setter vì `User` hầu như chỉ được tạo một lần rồi không đổi. Nhưng `User.setId()` là `public` trong khi id do Hibernate sinh: đó là một cửa không nên mở (🐞 4.A).
</details>

🥊 **4.4 Phương án của bạn:** "Làm quan hệ **hai chiều**: `MealItem` có field `Meal meal`, để từ một món đi ngược lại được bữa ăn chứa nó."

<details><summary>▶ Phản biện</summary>

| | Một chiều (hiện tại) | Hai chiều |
|---|---|---|
| Có cần đi từ món sang bữa không? | Không có use case nào cần | |
| Chuyển sang JSON | Không có vòng lặp | `Meal → items → meal → items…` lặp vô hạn, phải thêm `@JsonIgnore` |
| Thêm một món | `items.add(x)` | `items.add(x)` **và** `x.setMeal(this)`. Quên vế sau là `meal_id = NULL` |
| SQL | Cần `@JoinColumn(nullable = false)` để không tạo thêm câu `UPDATE` | Tối ưu hơn một chút |

Chỉ thêm chiều ngược lại khi có một **câu query thật** cần đi từ con sang cha.
</details>

🔤 **Syntax JPA** (✓ = service có dùng)

| Annotation | identity | profile | meal | analysis | Ghi nhớ |
|---|---|---|---|---|---|
| `@Entity @Table(name)` | ✓ | ✓ | ✓ | ✓ | Thiếu `@Table` thì tên bảng lấy theo tên class |
| `@Id @GeneratedValue(strategy = UUID)` | ✓ | | item | job | Hibernate sinh UUID **trong Java**, trước khi `INSERT` |
| `@Column(name, nullable, length, updatable)` | ✓ | ✓ | ✓ | ✓ | Với `validate`, chủ yếu để người đọc biết ràng buộc. `updatable = false` thì **thật sự** bị bỏ khỏi câu `UPDATE` |
| `@Enumerated(EnumType.STRING)` | | ✓ | ✓ | ✓ | Mặc định là `ORDINAL`, rất nguy hiểm |
| `@OneToMany(cascade = ALL, orphanRemoval = true)` | | | ✓ | ✓ | `cascade`: lưu cha là lưu con. `orphanRemoval`: con bị bỏ khỏi list thì bị `DELETE` |
| `@JoinColumn(name, nullable = false)` | ✓ | | ✓ | ✓ | Với `@OneToMany` một chiều: `INSERT` con kèm FK luôn |
| `@OrderBy("position")` | | | ✓ | ✓ | Tên **field** Java, không phải tên cột |
| `@ManyToOne(fetch = LAZY, optional = false)` | ✓ | | | | Mặc định là `EAGER` |
| `@PrePersist` / `@PreUpdate` | ✓ | ✓ | ✓ | | Chạy ngay trước `INSERT` / `UPDATE` |
| `@PostPersist` / `@PostLoad` | | | ✓ | | Chạy sau khi lưu / sau khi đọc lên |
| `@Transient` | | | ✓ | | Field **không** có cột trong DB |
| `implements Persistable<UUID>` | | | ✓ | | Tự quyết định `isNew()` |
| Constructor `protected` không tham số | | ✓ | ✓ | ✓ | Bắt buộc với JPA. `protected` để code khác không tạo được object rỗng |
| Wrapper (`Integer`, `Double`) cho cột nullable | | | `confidence` | `centerX`, `queueMs` | Kiểu nguyên thủy không chứa được `null` |

🐞 **Bug 4.A (dễ):** `User.setId()` để `public`, trong khi id do Hibernate sinh. Gọi `setId()` trên một user mới sẽ làm Spring Data tưởng user đó đã tồn tại và gọi `merge`. Xoá setter này đi, rồi chạy lại test.
🐞 **Bug 4.B (trung bình):** `AnalysisJob.isTerminal()` không được dùng ở đâu cả. `complete()` và `fail()` không kiểm tra trạng thái hiện tại. Thêm các điều kiện kiểm tra, và viết một unit test **không cần Spring** cho từng bước chuyển trạng thái **không hợp lệ**.

📌 **Rút kinh nghiệm**
- Entity nào có quy tắc nghiệp vụ thì viết **hàm có tên nghiệp vụ** cho nó, đừng mở setter.
- Id gán tay thì tự hỏi: "lưu trùng thì tôi muốn **ghi đè** hay **báo lỗi**?" Muốn báo lỗi thì dùng `Persistable`.
- Chỉ map quan hệ JPA trong cùng một service. Dữ liệu nặng thì chỉ nối qua id.

---

## 5. DTO (package `dto/`)

| Khía cạnh | identity | profile | meal | analysis |
|---|---|---|---|---|
| DTO cho request | `RegisterRequest`, `LoginRequest`, `RefreshTokenRequest` | **`ProfileDto` dùng cho cả hai chiều** | `SaveMealRequest`, `UpdateMealRequest` | **Không có**: multipart dùng `@RequestParam` |
| DTO cho response | `AuthResponse`, `ErrorResponse` | `ProfileDto` | `MealDto`, `DayDto`, `StreakDto` | `JobDto` (có record lồng `Timings`, `Region`…) |
| Chuyển entity → DTO | Viết trong service | `ProfileDto.from(entity)` | `MealDto.from`, `DayDto.of` | `JobDto.from` |
| Chuyển DTO → entity | Viết trong service (bằng setter) | `profile.update(dto.x()…)` | `request.toMeal(userId)` | Constructor của entity |
| Trường do server tính | `tokenType = "Bearer"` | | `kcal`, `proteinG`… (tổng) | `timings` |
| Kiểu field | `String` | `Integer` + `@NotNull` | `Integer`/`Double` + `@NotNull`, `boolean isEdited` | Kiểu nguyên thủy, `null` cho các giá trị chưa có |

❓ **Đoán trước 5.1:** `ProfileDto` dùng `@NotNull Integer ageYears`. Đổi thành `int ageYears` thì sao, nếu app gửi lên JSON **thiếu** field `ageYears`?

<details><summary>▶ Đáp án</summary>

Jackson gán **mặc định là `0`** cho `int` khi thiếu field. `@NotNull` không có tác dụng gì trên `int` (vì `int` không bao giờ là `null`), còn `@Min(14)` thì báo "tuổi phải ≥ 14". User nhận về một thông báo **sai nguyên nhân**: họ không hề gửi tuổi 0, họ **quên gửi** tuổi. Dùng `Integer` thì phân biệt được "không gửi" (`null`, bị `@NotNull` chặn) với "gửi sai" (bị `@Min` chặn).
</details>

❓ **Đoán trước 5.2:** cùng record `SaveMealRequest`, vì sao `boolean isEdited` dùng kiểu nguyên thủy được, còn `Integer grams` thì phải là wrapper?

<details><summary>▶ Đáp án</summary>

Hãy hỏi: "nếu thiếu field này, giá trị mặc định có **an toàn và đúng** không?"
- Thiếu `isEdited` thì mặc định `false`, nghĩa là "chưa sửa". Hợp lý.
- Thiếu `grams` thì mặc định `0`, tức là một món 0 gram, và tổng kcal sai. **Phải từ chối** request này.
</details>

❓ **Đoán trước 5.3:** vì sao `SaveMealRequest` **không có** field `userId`, mà `toMeal(UUID userId)` lại nhận `userId` qua tham số?

<details><summary>▶ Đáp án</summary>

Nếu body có `userId`, kẻ xấu chỉ cần gửi `"userId": "<id của người khác>"` là ghi được bữa ăn vào nhật ký của người đó. Lỗi này gọi là **mass assignment / IDOR**. `userId` **chỉ** được lấy từ token đã kiểm tra chữ ký, rồi controller truyền vào `toMeal(userId)`. Nhờ vậy, DTO **về mặt kiểu dữ liệu** không thể chứa thông tin "là ai".
</details>

❓ **Đoán trước 5.4:** profile dùng **một** DTO cho cả request lẫn response, còn meal tách thành 3 (`SaveMealRequest` / `UpdateMealRequest` / `MealDto`). Khi nào thì gộp làm một được?

<details><summary>▶ Đáp án</summary>

Gộp được khi **hai chiều có đúng cùng một tập field**. Profile gửi lên 6 field, nhận về đúng 6 field đó.

Meal thì khác:
- **Response có thêm** `items[].id` và các tổng `kcal`… do server sinh ra hoặc tính ra.
- **Request tạo mới có thêm** `id`, `jobId`, `loggedAt`, `photoUrl`, nhưng **request sửa** thì không được đổi các field này.

Nếu gộp lại thành một, client sẽ tưởng mình được gửi `kcal` lên, hoặc được đổi `loggedAt` khi sửa bữa. Tách DTO là cách **viết luật nghiệp vụ vào kiểu dữ liệu**: `UpdateMealRequest` **không có** `loggedAt`, nên user không thể đổi giờ ăn qua API sửa.
</details>

🥊 **5.5 Phương án của bạn:** "Bỏ hết DTO đi, controller trả thẳng entity `Meal`, Jackson tự chuyển sang JSON."

<details><summary>▶ Phản biện</summary>

Liệt kê điều gì sẽ hỏng trước khi mở danh sách bên dưới. Có ít nhất 4 thứ:
1. **Lazy loading ngoài session**: `open-in-view=false`, nên Jackson gọi `getItems()` sau khi transaction đã đóng (nếu không có `@EntityGraph`) và nhận `LazyInitializationException`.
2. **Lộ thông tin nội bộ**: `userId`, và cả `isNew()`, mà Jackson coi là property **`"new": false`**.
3. **API dính chặt vào schema DB**: đổi tên một cột là app hỏng.
4. **Không có chỗ cho các trường tính toán** như `kcal`, trừ khi nhét getter vào entity.
5. Với `RefreshToken` thì nguy hiểm hơn nữa: `getUser()` → `User` → **`getPasswordHash()`** bị trả ra ngoài.
</details>

🔤 **Syntax validation** (chung cho identity, profile, meal)

| Annotation | Áp dụng cho | `null` có qua không? | Ví dụ trong repo |
|---|---|---|---|
| `@NotNull` | Mọi kiểu | ✗ | `@NotNull UUID id` |
| `@NotBlank` | `String` | ✗ (chặn cả `""` và `"  "`) | `@NotBlank String name` |
| `@NotEmpty` | `String`, `Collection` | ✗ (chặn `""` và `[]`) | |
| `@Size(min, max)` | `String`, `Collection` | ✓ | `@Size(min = 1, max = 64) String jobId` (cho phép `null`, chặn `""`) |
| `@Min` / `@Max` | Số nguyên (`int`, `Integer`, `long`…) | ✓ | `@Min(0) @Max(100) Integer confidence` (cho phép `null`) |
| `@DecimalMin("0")` / `@DecimalMax("900")` | `Double`, `BigDecimal` | ✓ | `Per100g.kcal`. Theo đặc tả, `@Min` không dành cho số thực vì sai số làm tròn |
| `@Email` | `String` | ✓ | `LoginRequest.email` |
| `@Valid` | Object / phần tử của list | | Validate **xuống bên trong** object lồng nhau. Thiếu nó thì các annotation trong `Item` **bị bỏ qua** |
| `List<@NotNull @Valid Item>` | Type-use | | Annotation đặt trên **từng phần tử** của list |

Quy tắc ghi nhớ: **mọi annotation đều cho `null` đi qua, trừ nhóm `@NotNull`, `@NotBlank`, `@NotEmpty`.** Muốn bắt buộc phải có giá trị thì luôn ghép thêm `@NotNull`.

Lỗi cú pháp bạn đã gặp: `List<@Valid SaveMealRequest.Item>` báo *scoping construct cannot be annotated*. Annotation type-use phải đứng **sát tên kiểu**: viết `SaveMealRequest.@Valid Item`, hoặc `import` `Item` rồi viết `List<@Valid Item>`.

🔤 **Syntax record** (chung)

```java
public record MealDto(UUID id, ..., List<Item> items) {        // field final, tự có constructor, getter id(), equals, hashCode
    public record Item(UUID id, String name, ...) {            // record lồng: mặc định static
        static Item from(MealItem item) { ... }                // factory, mức truy cập package
    }
    public static MealDto from(Meal meal) { ... }              // quy ước: from(entity) cho response
}
```
- Getter của record là `id()`, **không phải** `getId()`.
- Field của record có kiểu `boolean` tên `isEdited` thì JSON là `"isEdited"`. Jackson dùng đúng tên thành phần của record, khác với getter `isEdited()` của class thường, vốn cho ra `"edited"`.
- `AuthResponse.bearer(...)` là factory method **đặt tên theo ý nghĩa**, để `"Bearer"` được viết cứng ở đúng một chỗ.

🐞 **Bug 5.A (trung bình):** `RegisterRequest` có `@Size(max = 72) String password`. BCrypt chỉ xử lý tối đa **72 byte**, nhưng `@Size` đếm **ký tự**: mật khẩu tiếng Việt có dấu chiếm 2–3 byte cho mỗi ký tự. Viết test với một mật khẩu 72 ký tự có dấu, rồi quyết định nên chặn ở đâu và chặn thế nào.

📌 **Rút kinh nghiệm**
- Request và response khác nhau thì **tách DTO**. Field nào client không được gửi thì **không có** trong DTO request.
- Định danh của chủ sở hữu (`userId`) **không bao giờ** nằm trong body.
- Field bắt buộc dùng wrapper + `@NotNull`. Chỉ dùng kiểu nguyên thủy khi giá trị mặc định thật sự đúng.
- Giới hạn trong DTO **trùng khớp** với `CHECK` trong DB, và ghi rõ điều đó trong Javadoc.

---

## 6. Repository (package `repository/`)

| | identity | profile | meal | analysis |
|---|---|---|---|---|
| Kế thừa | `JpaRepository<User, UUID>` | `JpaRepository<BodyProfile, UUID>` | `JpaRepository<Meal, UUID>` | 2 repository |
| Hàm tự viết | `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`, `findByTokenHashAndRevokedAtIsNull` | **Không có** (`findById` là đủ) | `findWithItemsById`, `findByIdAndUserId`, `findByUserIdAndJobId` + 2 `@Query` | `findByIdAndUserId`, `findByStageIn` |
| Tải quan hệ con | Lazy, đọc trong `@Transactional` | | **`@EntityGraph(attributePaths = "items")`** | Lazy, đọc trong `tx.execute` |
| Chỉ lấy một phần dữ liệu (projection) | | | `select m.loggedAt …`, trả `List<LocalDateTime>` | |

❓ **Đoán trước 6.1:** Spring Data sinh ra câu SQL nào cho `findByTokenHashAndRevokedAtIsNull(String hash)`?

<details><summary>▶ Đáp án</summary>

```sql
select … from refresh_tokens where token_hash = ? and revoked_at is null
```
Cấu tạo của tên hàm: `find` + `By` + `TokenHash` (so sánh `=`) + `And` + `RevokedAt` + `IsNull` (không cần tham số). Mỗi đoạn ứng với một **tên field Java**, không phải tên cột.
</details>

❓ **Đoán trước 6.2:** `MealService.days()` **không có** `@Transactional`, nhưng vẫn đọc `meal.getItems()` khi tạo `MealDto` mà không gặp `LazyInitializationException`. Trong khi đó `AnalysisService.get()` lại phải bọc trong `tx.execute`. Vì sao hai chỗ khác nhau?

<details><summary>▶ Đáp án</summary>

| | meal `findLoggedBetween` | analysis `findByIdAndUserId` |
|---|---|---|
| Có `@EntityGraph`? | ✓ Các món được tải **trong cùng một câu query** (`LEFT JOIN`) | ✗ `detections` vẫn là lazy |
| Đọc `getItems()` khi session đã đóng | Dữ liệu đã có sẵn, không cần thêm query | Cần thêm một query, nhưng session đã đóng nên **lỗi** |
| Cách giải quyết | Không cần transaction | Giữ session mở bằng `tx.execute(...)` cho tới khi `JobDto.from` chạy xong |

Cả hai cách đều đúng. `@EntityGraph` tốt hơn khi đọc **nhiều** bữa ăn, vì tránh được N+1 query (một query cho danh sách, rồi thêm N query cho các món của từng bữa).
</details>

❓ **Đoán trước 6.3:** gọi `findByUserIdAndJobId(userId, null)` thì sinh ra SQL gì? Vì sao `MealService.findByJob` phải chặn trường hợp `jobId == null`?

<details><summary>▶ Đáp án</summary>

Spring Data đổi tham số `null` thành `job_id IS NULL`, và câu này khớp với **mọi bữa ăn tự nhập**. Nếu không chặn, lưu một bữa tự nhập mới sẽ "tìm thấy" một bữa tự nhập cũ, rồi trả `200` với bữa cũ đó. Bữa mới **không bao giờ được lưu**.
</details>

🥊 **6.4 Phương án của bạn:** "Thêm `List<Meal> findByUserId(UUID userId)`, lấy hết bữa ăn của user lên rồi lọc theo ngày bằng stream trong Java. Đơn giản hơn viết `@Query`."

<details><summary>▶ Phản biện</summary>

Một user dùng app 2 năm thì có khoảng 2.000 bữa và 8.000 món, mà mỗi lần mở dashboard **chỉ cần 7 ngày**. Lọc trong Java nghĩa là:
- mỗi request kéo 10.000 dòng qua mạng;
- index `(user_id, logged_at)` trở nên vô dụng;
- bộ nhớ dùng tăng theo **tuổi tài khoản**.

Quy tắc: **lọc, sắp xếp, giới hạn số dòng ở phía DB**. Java chỉ làm những việc SQL không làm được, như gom theo ngày (kể cả ngày trống) hay đếm streak.
</details>

🥊 **6.5 Phương án của bạn:** "Bỏ `findByIdAndUserId`, chỉ dùng `findById` rồi kiểm tra `meal.getUserId().equals(userId)` trong service."

<details><summary>▶ Phản biện</summary>

Cả hai cách đều **an toàn** nếu bạn không quên kiểm tra. Khác nhau ở chỗ:

| | `findByIdAndUserId` | `findById` + `if` |
|---|---|---|
| Quên kiểm tra | Không thể quên: điều kiện nằm ngay trong câu query | Quên dòng `if` một lần là **lộ dữ liệu** |
| Phân biệt "không tồn tại" với "của người khác" | Không phân biệt được (cả hai đều 404). Đây chính là điều ta muốn | Phân biệt được |

Meal dùng **cả hai**, có chủ đích:
- `update` và `delete` dùng `findByIdAndUserId`, vì chỉ cần 404.
- `save` dùng `findWithItemsById` (không lọc theo user), vì **cần biết** id đó đã bị người khác dùng, để trả `409` thay vì tạo trùng.
</details>

🔤 **Syntax Spring Data** (chung)

| Thành phần | Ví dụ | SQL tương ứng |
|---|---|---|
| `find…By`, `exists…By`, `count…By`, `delete…By` | `existsByEmailIgnoreCase` | `select … fetch first 1 rows only`: chỉ cần biết có hay không |
| `And`, `Or` | `findByIdAndUserId` | `id = ? and user_id = ?` |
| `IgnoreCase` | `findByEmailIgnoreCase` | `upper(email) = upper(?)` |
| `IsNull`, `IsNotNull` | `RevokedAtIsNull` | `revoked_at is null` |
| `In` | `findByStageIn(Collection)` | `stage in (?, ?)` |
| Đoạn giữa `find` và `By` | `findWithItemsById` | Bị bỏ qua. Chỉ là tên gợi nhớ, ở đây để báo "có `@EntityGraph`" |
| Kiểu trả về | `Optional<T>`, `List<T>`, `boolean` | 0–1 dòng, nhiều dòng, có tồn tại hay không |
| `@Query("select m from Meal m where …")` | JPQL: dùng **tên entity và tên field**, không phải tên bảng | |
| `@EntityGraph(attributePaths = "items")` | Tải `items` trong cùng một query | `left join meal_items` |

🐞 **Bug 6.A (trung bình, về hiệu năng):** `findByEmailIgnoreCase` sinh ra `upper(email) = upper(?)`. Một index trên `email` **không dùng được** cho biểu thức `upper(email)`, nên mỗi lần đăng nhập là quét cả bảng `users`. Mà code đã chuẩn hoá email về chữ thường **trước khi lưu**. Hãy chọn một trong hai cách: (a) đổi thành `findByEmail(normalized)`, hoặc (b) tạo index trên biểu thức `lower(email)`. Giải thích lựa chọn của bạn.
🐞 **Bug 6.B (thấp):** lúc khởi động, `resumeUnfinished` gọi `findByStageIn`. Câu này quét cả bảng (không có index trên `stage`), rồi `requeue()` gọi `detections.clear()`, nên mỗi job lại tạo thêm một query tải món (N+1). Hiện tại chưa đáng lo, nhưng hãy viết ra cách sửa.

📌 **Rút kinh nghiệm**
- Hàm nào trả dữ liệu thuộc về một user thì **có `userId` ngay trong điều kiện query**.
- Đọc entity kèm danh sách con để trả ra API thì dùng `@EntityGraph`. Chỉ cần vài cột thì dùng projection.
- Kiểm tra xem câu SQL sinh ra có **dùng được index** không (`IgnoreCase`, `lower()`, `LIKE '%x'` đều phá index).

---

## 7. Service và transaction (phần khó nhất)

| | identity | profile | meal | analysis |
|---|---|---|---|---|
| Cách quản lý transaction | `@Transactional` trên mọi hàm public | `readOnly` cho đọc, `@Transactional` cho ghi | `save` **cố ý không có** · `update`, `delete` có · `days`, `streak` không có (mỗi hàm chỉ một query) | **`TransactionTemplate`** (tự viết bằng code) |
| Chống ghi trùng | `existsBy…` rồi `save` (*check-then-act*) | `findById` rồi `save` (ghi chú `ponytail`) | Thử `INSERT`, bắt lỗi trùng, đọc lại | `start()` + `queueMs` |
| Báo lỗi | `ApiException(status)` | `Optional`, để controller trả 404 | `Optional` / `boolean` / `ResponseStatusException(409)` | `Optional` / `ResponseStatusException(409)` |
| Kiểu trả về | DTO | `Optional<ProfileDto>` | `record Saved(meal, created)` | `Optional<JobDto>` |

### 7.1 Một mẫu lỗi xuất hiện ở khắp nơi: *check-then-act*

❓ **Đoán trước:** cả 6 chỗ dưới đây đều có cùng một dạng *"kiểm tra → khe hở → làm"*. Với mỗi chỗ, điền vào hai cột cuối **trước khi** mở đáp án.

| # | Ở đâu | Kiểm tra | Làm | Hai request cùng lúc thì sao? | Đã xử lý chưa? |
|---|---|---|---|---|---|
| 1 | identity `register` | `existsByEmail` | `save(user)` | ? | ? |
| 2 | identity `rotate` | `findByTokenHash…IsNull` | `revoke` + `issue` mới | ? | ? |
| 3 | profile `upsert` | `findById` | `save` | ? | ? |
| 4 | meal `save` | `findSaved` | `saveAndFlush` | ? | ? |
| 5 | analysis `process` | `start()` đọc `queueMs == null` | Gọi Claude | ? | ? |
| 6 | analysis `ClaudeDishRecognizer.client()` | `client == null` | Tạo client | ? | ? |

<details><summary>▶ Đáp án</summary>

| # | Hai request cùng lúc thì sao? | Đã xử lý chưa? |
|---|---|---|
| 1 | Cả hai thấy email chưa tồn tại, cả hai `INSERT`. `UNIQUE(email)` chặn request thứ hai, nhưng `DataIntegrityViolationException` rơi vào handler `Exception` chung, nên trả **500** | 🐞 Nên bắt lỗi trùng và trả `409` |
| 2 | Hai lần refresh với **cùng một** token: cả hai thấy token chưa bị thu hồi, nên **cả hai nhận được token mới**. Một token bị đánh cắp vẫn dùng được song song với token thật | 🐞 Cần `@Version`, hoặc `UPDATE … WHERE revoked_at IS NULL` rồi đếm số dòng bị ảnh hưởng |
| 3 | Lần lưu đầu tiên chạy song song: cả hai `INSERT` nên vi phạm khoá chính, và request thứ hai nhận **500** | 🟡 Đã ghi `ponytail`: app chỉ gửi một request mỗi lần |
| 4 | Request thua cuộc đua bị khoá chính hoặc `UNIQUE(user_id, job_id)` chặn, bắt lỗi, đọc lại, trả `200` | ✅ Có test 8 luồng chứng minh |
| 5 | Hai worker cùng thấy `queueMs == null`, nên Claude bị gọi hai lần (tốn tiền gấp đôi) | 🐞 Xảy ra khi có **hai lệnh retry chạy cùng lúc** |
| 6 | Hai luồng cùng tạo client | ✅ `synchronized` (vì đây là dữ liệu trong bộ nhớ, khoá trong Java là đủ) |

**Bài học quan trọng nhất của cả tài liệu:** dữ liệu nằm trong DB thì **để DB làm trọng tài** (`PK`, `UNIQUE`, `UPDATE … WHERE` có điều kiện, `@Version`). Dữ liệu nằm trong bộ nhớ của một JVM thì dùng `synchronized`, `AtomicReference`…
</details>

### 7.2 Các câu hỏi về transaction

❓ **7.2.1** Vì sao `MealService.save()` **cố ý không có** `@Transactional`?

<details><summary>▶ Đáp án</summary>

Khi một câu lệnh vi phạm ràng buộc, Postgres đánh dấu **cả transaction** là hỏng: *"current transaction is aborted, commands ignored until end of transaction block"*. Nếu `save()` là một transaction duy nhất, thì câu `SELECT` trong khối `catch` (để đọc lại bữa của người thắng) cũng bị từ chối, và request trả **500**. Khi không có `@Transactional`, mỗi lời gọi repository là một transaction riêng: câu `INSERT` thất bại được rollback **riêng**, và câu `SELECT` sau đó chạy trong một transaction mới.
</details>

❓ **7.2.2** Vì sao analysis dùng `TransactionTemplate`, mà không gắn `@Transactional` lên `process()` và `update()`? Có **3 lý do**.

<details><summary>▶ Đáp án</summary>

1. **`@Transactional` hoạt động qua proxy.** `process()` và `update()` là hàm `private`, và được gọi **từ bên trong chính class đó** (`this.update(...)`). Lời gọi không đi qua proxy, nên annotation **bị bỏ qua hoàn toàn**, không có lỗi nào báo ra.
2. **Không được giữ transaction trong lúc gọi Claude.** Nếu cả `process()` là một transaction, mỗi job giữ **một kết nối DB** trong khoảng 15 giây. Pool kết nối Hikari mặc định chỉ có **10** kết nối, nên 10 job chạy cùng lúc là hết kết nối, và cả những request `GET` đơn giản cũng bị treo. `TransactionTemplate` cho phép mở các transaction ngắn: một cái để `start`, rồi **gọi Claude khi không có transaction nào**, rồi một cái để `recognized`, một cái để `complete`.
3. **Mỗi bước phải được commit riêng**, để app đang polling thấy được `CALCULATING` trước khi job `COMPLETED`.
</details>

❓ **7.2.3** Trong `submit()`, hàm `dispatch(job.id())` được gọi **sau khi** `tx.execute(...)` đã trả về. Nếu chuyển `dispatch` vào **bên trong** khối `tx.execute` thì sao?

<details><summary>▶ Đáp án</summary>

Worker chạy trên một luồng khác, có thể bắt đầu **trước khi** transaction kịp commit. Lúc đó `jobs.findById(jobId)` trả về rỗng (vì dòng dữ liệu chưa commit thì transaction khác không nhìn thấy), nên `started = false` và worker dừng. Job **kẹt ở RECOGNIZING mãi mãi**: không ai chạy nó, cũng không có lỗi nào. Lỗi này lúc có lúc không, tuỳ tốc độ máy. Quy tắc: **chỉ giao việc cho luồng khác sau khi đã commit.**
</details>

❓ **7.2.4** Trong `RefreshTokenService.rotate()`:
```java
if (!stored.isActive()) {
    stored.setRevokedAt(Instant.now());
    refreshTokenRepository.save(stored);
    throw new ApiException(HttpStatus.UNAUTHORIZED, "...");
}
```
Dòng `setRevokedAt` có thật sự được lưu xuống DB không?

<details><summary>▶ Đáp án</summary>

**Không.** `ApiException` kế thừa `RuntimeException`, mà `@Transactional` mặc định **rollback khi gặp `RuntimeException`**. Câu `UPDATE` bị huỷ. Hai dòng `setRevokedAt` và `save` này vô tác dụng (🐞 7.A). Hậu quả thực tế không lớn, vì token đã hết hạn nên đằng nào cũng không dùng được. Nhưng đây là ví dụ điển hình của việc **code trông như đã làm một việc mà thực ra không làm**.

Để tham khảo: exception kiểu *checked* (`extends Exception`) thì mặc định **không** gây rollback. Muốn rollback thì phải ghi `@Transactional(rollbackFor = …)`.
</details>

🥊 **7.3 Phương án của bạn:** "Gắn `@Transactional` lên **cả class** `MealService` cho chắc."

<details><summary>▶ Phản biện</summary>

Làm vậy thì `save()` cũng có transaction, và nhánh `catch` hỏng như ở câu 7.2.1. Test `concurrentRetriesOfOneMealKeepOneMeal` sẽ **đỏ** ngay. Hãy thử thật một lần để thấy tận mắt. Đặt `@Transactional` ở cấp class là một thói quen phổ biến, nhưng nó che đi những hàm **cần** ngoại lệ.
</details>

🥊 **7.4 Phương án của bạn:** "Thêm `synchronized` vào `MealService.save()` để chống lưu trùng. Đơn giản hơn nhiều so với bắt `DataIntegrityViolationException`."

<details><summary>▶ Phản biện</summary>

| | `synchronized` | DB làm trọng tài |
|---|---|---|
| Chạy 2 instance của meal-service (scale ngang) | ❌ Mỗi JVM có khoá riêng, nên vẫn lưu trùng | ✅ DB là nơi duy nhất quyết định |
| 1.000 user cùng lưu | ❌ **Mọi user xếp một hàng**, dù họ không liên quan tới nhau | ✅ Chỉ các request trùng nhau mới đụng độ |
| Đúng khi service restart giữa chừng | Khoá biến mất | `UNIQUE` vẫn còn |

`synchronized` chỉ hợp với tài nguyên **nằm trong một JVM** (như câu 6 ở bảng 7.1).
</details>

🔤 **Syntax hay gặp ở tầng service**

| Cú pháp | Ở đâu | Ý nghĩa |
|---|---|---|
| `@Transactional(readOnly = true)` | profile `get` | Gợi ý cho Hibernate bỏ qua dirty checking, và cho DB biết đây là transaction chỉ đọc |
| `tx.execute(status -> { …; return x; })` | analysis | Transaction viết bằng code, **có trả về** giá trị |
| `tx.executeWithoutResult(status -> …)` | analysis `update` | Không trả về giá trị |
| `Optional.map(f).orElseGet(() -> …)` | profile `upsert` | `orElseGet` chỉ chạy lambda **khi rỗng**. `orElse(new X())` thì **luôn** tạo object |
| `.orElseThrow(() -> e)` | meal `save` | Ném lại **đúng exception gốc** |
| `Collectors.groupingBy(meal -> meal.getLoggedAt().toLocalDate())` | meal `days` | `Map<LocalDate, List<Meal>>` |
| `from.datesUntil(to.plusDays(1))` | meal `days` | Stream các ngày từ `from` tới `to`, **không** gồm ngày cuối, nên phải `+1` |
| `record Saved(MealDto meal, boolean created)` | meal | Trả về **hai** giá trị mà không cần viết class riêng |
| `Consumer<AnalysisJob> change` | analysis `update` | Truyền "việc cần làm với job" vào như một tham số |
| `executor.execute(() -> …)` | analysis `dispatch` | Đẩy việc sang luồng nền. Không có `try/catch` thì exception **biến mất** |

🐞 **Bug 7.A (dễ):** xoá hai dòng vô tác dụng trong nhánh `!stored.isActive()` của `rotate()`, hoặc làm cho chúng có tác dụng thật. Bạn chọn cách nào, và vì sao?
🐞 **Bug 7.B (trung bình):** identity `register` chạy song song thì một request nhận 500. Viết một test 8 luồng giống `assertOneMealSurvives`, cho nó **đỏ**, rồi sửa để trả `409`.
🐞 **Bug 7.C (khó):** chặn worker thứ hai của analysis bằng một câu `@Modifying @Query("update AnalysisJob j set j.queueMs = :ms where j.id = :id and j.queueMs is null")`, kiểm tra số dòng trả về có bằng 1 không. Vì sao **không nên** dùng `@Version` cho trường hợp này? (Gợi ý: `OptimisticLockException` sẽ rơi vào khối `catch` nào trong `dispatch`?)

📌 **Rút kinh nghiệm**
- Mặc định gắn `@Transactional` cho hàm ghi. Ngoại lệ phải **có comment giải thích** (như `save()` của meal).
- `@Transactional` **không** có tác dụng với hàm `private`, và với lời gọi từ bên trong chính class đó.
- Không giữ transaction trong lúc gọi mạng ra ngoài hoặc chạy việc chậm.
- Chỉ giao việc cho luồng khác **sau khi** đã commit.
- Trước mỗi đoạn "kiểm tra rồi làm", hỏi: "hai request đến cùng lúc thì sao?"

---

## 8. Controller

| Endpoint | Status code | Vì sao |
|---|---|---|
| identity `POST /auth/register` | `201` (`@ResponseStatus`) · `409` email đã tồn tại | Tạo ra một tài nguyên mới |
| identity `POST /auth/login`, `/refresh` | `200` | Không tạo tài nguyên nào |
| identity `POST /auth/logout` | `204` | Thành công, không có body |
| profile `GET /profile/me` | `200` / `404` (`ResponseEntity.of`) | `404` = user chưa hoàn tất onboarding |
| profile `PUT /profile/me` | `200` | Upsert |
| meal `POST /meals` | `201` mới tạo / `200` đã có từ trước / `409` id thuộc người khác | Idempotency |
| meal `PUT /meals/{id}` | `200` / `404` | |
| meal `DELETE /meals/{id}` | `204` / `404` | |
| meal `GET /meals?from&to` | `200` / `400` (khoảng ngày quá 92 ngày) | |
| analysis `POST /analysis` | **`202`** | Đã nhận, **chưa xử lý xong** |
| analysis `GET /analysis/{id}` | `200` / `404` | |
| analysis `POST /analysis/{id}/retry` | `202` / `409` (job chưa thất bại) / `404` | |

| Khía cạnh | identity | profile | meal | analysis |
|---|---|---|---|---|
| Lấy user | `@AuthenticationPrincipal UserDetails` (email) | `@AuthenticationPrincipal Jwt`, rồi `userId(jwt)` | = | = |
| Nhận dữ liệu | `@Valid @RequestBody` | = | `@Valid @RequestBody`, `@PathVariable UUID`, `@RequestParam @DateTimeFormat` | `@RequestParam MultipartFile` + 3 `@RequestParam` |
| Logic trong controller | Không có | Không có | Kiểm tra khoảng ngày · chọn 201 hay 200 | Kiểm tra định dạng ảnh, `photoRef` |

❓ **Đoán trước 8.1:** `DELETE /api/v1/meals/abc` (id không phải UUID) trả về gì? Code của bạn có chạy không?

<details><summary>▶ Đáp án</summary>

Trả `400`. Spring không đổi được `"abc"` thành `UUID`, nên ném `MethodArgumentTypeMismatchException` **trước khi** gọi controller. Nhờ problem details, body lỗi theo đúng chuẩn. Đây là lợi ích của việc khai báo `@PathVariable UUID id` thay vì `String id`: kiểu dữ liệu chính là một lớp validation.
</details>

❓ **Đoán trước 8.2:** identity trả `409 "Email is already registered"`, trong khi meal và analysis trả `404` cho dữ liệu của người khác **để không lộ là nó tồn tại**. Như vậy có mâu thuẫn không?

<details><summary>▶ Đáp án</summary>

Có mâu thuẫn, và cũng có đánh đổi. `409` cho phép bất kỳ ai **dò xem một email đã đăng ký hay chưa** (*user enumeration*). Đổi lại, người dùng thật nhận được thông báo dễ hiểu. Cách làm an toàn hơn là luôn trả "đã gửi email xác nhận" và gửi email cho chủ thật. Cách này cần hạ tầng email, nên hiện tại chấp nhận `409`, nhưng bạn phải **biết** đây là một lựa chọn có chủ đích, không phải mặc định đúng.
</details>

🥊 **8.3 Phương án của bạn:** "`PUT /profile/me` nên trả `201` ở lần tạo đầu tiên và `200` ở các lần sau, cho đúng chuẩn HTTP."

<details><summary>▶ Phản biện</summary>

Theo đặc tả HTTP, cách này **đúng**: `PUT` tạo mới tài nguyên thì nên trả `201`. Nhưng hãy hỏi: **app có làm gì khác đi** khi nhận `201` thay vì `200` không? Không: app chỉ cần biết hồ sơ đã được lưu. Meal thì khác: app **cần** phân biệt `201` với `200` (lưu mới hay lưu lại), nên ở đó việc phân biệt có giá trị. Quy tắc thực tế: chỉ phân biệt các status code mà **client có hành xử khác nhau** với từng cái. Muốn làm theo đề xuất của bạn thì service phải trả thêm thông tin "vừa tạo" (giống `Saved.created()` của meal).
</details>

🥊 **8.4 Phương án của bạn:** "Thêm `@RequestParam UUID userId` vào các API của meal, để test bằng Postman dễ hơn."

<details><summary>▶ Phản biện</summary>

Bất kỳ ai cũng đổi được `userId` trong URL để đọc nhật ký của người khác (IDOR, xem câu 5.3). Muốn test dễ thì **tạo token cho user test** (như `bearerFor(UUID)` trong test). Nguyên tắc: định danh của người gọi **chỉ** đến từ token đã kiểm tra chữ ký.
</details>

🔤 **Syntax controller**

| Cú pháp | Ý nghĩa |
|---|---|
| `@RestController` = `@Controller` + `@ResponseBody` | Giá trị trả về được chuyển thành JSON |
| `@RequestMapping("/api/v1/meals")` trên class | Tiền tố chung cho mọi endpoint |
| `ResponseEntity.of(optional)` | Có giá trị thì `200` kèm body, rỗng thì `404` |
| `ResponseEntity.status(x).body(y)` · `.accepted()` · `.noContent()` | Tự chọn status code |
| `@ResponseStatus(HttpStatus.CREATED)` | Status cố định, dùng khi hàm trả thẳng một object |
| `@RequestParam @DateTimeFormat(iso = DATE) LocalDate from` | `?from=2026-10-19` |
| `@RequestParam MultipartFile photo` | Một phần (part) của `multipart/form-data` |
| `@PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)` | Từ chối các `Content-Type` khác với `415` |
| `private static UUID userId(Jwt jwt)` | Lặp lại ở 3 service, xem 9.4 |

📌 **Rút kinh nghiệm**
- Controller mỏng: lấy `userId` từ token, kiểm tra dữ liệu đầu vào, gọi service, rồi **chọn status code**. Service không biết gì về HTTP, ngoại trừ `ResponseStatusException` (thoả hiệp có chủ đích để code gọn).
- Dùng kiểu dữ liệu chặt (`UUID`, `LocalDate`, enum) cho tham số: Spring tự trả `400` khi sai.
- Dữ liệu của người khác thì trả `404`, trừ khi có lý do nghiệp vụ để làm khác (như `409` của meal: id do client sinh ra).

---

## 9. Bảo mật

| | identity | profile · meal · analysis |
|---|---|---|
| Vai trò | **Phát** token | **Kiểm tra** token |
| Cơ chế | `JwtAuthenticationFilter extends OncePerRequestFilter` tự viết + jjwt | `.oauth2ResourceServer(jwt)` + `NimbusJwtDecoder` |
| Kiểm tra | Chữ ký, hạn dùng, **user còn tồn tại trong DB** (mỗi request một query) | Chữ ký HS256, `exp`/`nbf`, claim `uid` là UUID. **Không** truy cập DB |
| Principal | `UserDetails` (email) | `Jwt` (đọc claim `uid`) |
| Endpoint công khai | `register`, `login`, `refresh`, `logout`, `health` | `health`, `info` |
| Thiếu token | **403** (🐞) | **401** |
| `SecurityConfig` | Riêng | **Ba file giống hệt nhau** |

❓ **Đoán trước 9.1:** vì sao profile không gọi sang identity để hỏi "token này có hợp lệ không"?

<details><summary>▶ Đáp án</summary>

| | Tự kiểm tra (hiện tại) | Gọi sang identity |
|---|---|---|
| Độ trễ | Chỉ tính toán trong bộ nhớ, dưới 1 ms | Mỗi request phải chờ thêm một request HTTP |
| identity sập | Các service khác **vẫn chạy** | **Mọi** service đều sập theo |
| Thu hồi token trước khi hết hạn | ❌ Không làm được | ✅ Làm được |

Cái giá của việc tự kiểm tra: không thu hồi được access token. Vì vậy access token chỉ sống **1 giờ**, còn việc thu hồi được làm trên **refresh token** (nằm trong DB của identity).
</details>

❓ **Đoán trước 9.2:** một user bị xoá khỏi bảng `users`, nhưng access token của họ vẫn còn 30 phút. Họ còn gọi được identity không? Còn gọi được meal không?

<details><summary>▶ Đáp án</summary>

- **identity: không.** Filter gọi `loadUserByUsername`, không tìm thấy user, nên không xác thực được.
- **meal: được**, cho tới khi token hết hạn. Meal không có bảng `users`, chỉ tin vào chữ ký.

Đây là hệ quả trực tiếp của câu 9.1, và cũng là lý do sự kiện `UserDeleted` (D6) quan trọng.
</details>

🥊 **9.3 Phương án của bạn:** "Đổi HS256 sang RS256."

<details><summary>▶ Phản biện (lần này đồng ý, kèm điều kiện)</summary>

| | HS256 (hiện tại) | RS256 |
|---|---|---|
| Khoá | **Một** secret dùng chung cho cả 4 service | identity giữ **khoá bí mật**, các service khác chỉ có **khoá công khai** |
| Một service bị lộ secret thì | Kẻ tấn công **tự ký** được token cho bất kỳ user nào | Chỉ đọc được, **không ký được** |
| Phân phối khoá | Copy chung vào `.env` | identity mở endpoint JWKS (`/.well-known/jwks.json`), các service khác dùng `NimbusJwtDecoder.withJwkSetUri(...)` |
| Đổi khoá định kỳ | Phải đổi ở 4 nơi cùng một lúc | Header `kid` cho phép khoá cũ và khoá mới cùng tồn tại một thời gian |

Có càng nhiều service, RS256 càng đáng làm. Với 4 service và một người phát triển, HS256 vẫn chấp nhận được, **miễn là** secret không bao giờ nằm trong git (2.3).
</details>

🥊 **9.4 Phương án của bạn:** "Tạo một thư viện dùng chung `visionfit-common-security` cho 3 file `SecurityConfig` giống nhau, cùng hàm `userId(jwt)`."

<details><summary>▶ Phản biện</summary>

| | Copy (hiện tại) | Thư viện dùng chung |
|---|---|---|
| Sửa một lỗi bảo mật | Phải sửa 3 nơi, dễ sót | Sửa 1 nơi, rồi **build lại cả 3 service** |
| Độc lập | Mỗi service tự quyết, ví dụ analysis cấu hình multipart khác | Mọi service bị buộc vào cùng một phiên bản thư viện |
| Khi nào nên làm | Dưới khoảng 3 bản sao, code ít đổi | Code bảo mật **thay đổi thường xuyên**, hoặc có nhiều service |

Microservice có câu: *"a little copying is better than a little dependency"* (copy một ít còn hơn phụ thuộc một ít). Nhưng với code **bảo mật**, việc sửa sót một nơi rất nguy hiểm. Lựa chọn hợp lý ở đây: giữ nguyên bản copy, và thêm **test giống nhau** cho cả 3 service (`rejectsMissingForgedExpiredAndUidlessTokens`), để một bản copy bị lệch sẽ lộ ra ngay.
</details>

🔤 **Syntax Spring Security**

| Cú pháp | Ở đâu | Ý nghĩa |
|---|---|---|
| `.csrf(AbstractHttpConfigurer::disable)` | Cả 4 service | API dùng Bearer token, không dùng cookie, nên không cần chống CSRF |
| `SessionCreationPolicy.STATELESS` | Cả 4 service | Không tạo `HttpSession`. Mỗi request tự mang token theo |
| `.requestMatchers(...).permitAll()` · `.anyRequest().authenticated()` | Cả 4 service | Thứ tự rất quan trọng: luật cụ thể đặt trước |
| `.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` | identity | Chèn filter tự viết vào chuỗi filter |
| `.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))` | 3 service còn lại | Dùng bean `JwtDecoder` mà bạn tự khai báo |
| `new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), new JwtClaimValidator<String>("uid", SecurityConfig::isUuid))` | 3 service còn lại | Kiểm tra mặc định (`exp`, `nbf`, sai lệch đồng hồ 60 giây) **cộng** kiểm tra `uid` |
| `SecurityConfig::isUuid` | | Method reference, tương đương lambda `v -> isUuid(v)` |

🐞 **Bug 9.A (dễ):** identity trả `403` khi request thiếu token, vì chưa cấu hình `authenticationEntryPoint`. Thêm `.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`, kèm một test.
🐞 **Bug 9.B (dễ):** secret JWT mặc định nằm trong git (2.3). Bỏ giá trị mặc định ở `main`, rồi kiểm tra xem test vẫn chạy được vì sao.

---

## 10. Xử lý lỗi

| | identity | profile · meal · analysis |
|---|---|---|
| Định dạng body | `ErrorResponse(timestamp, status, error, message, path)` tự định nghĩa | RFC 9457 problem details `{type, title, status, detail, instance}` |
| Cách ném lỗi | `ApiException(HttpStatus, msg)` + `@RestControllerAdvice` | `ResponseStatusException` |
| Lỗi validation | Handler tự gom lại thành `"field: message; …"` | Spring tự xử lý (`400` problem details) |
| Exception không lường trước | `@ExceptionHandler(Exception.class)` chung, trả `500` | Spring mặc định |

❓ **Đoán trước 10.1:** gửi `POST /api/v1/auth/login` với body là JSON **hỏng** (`{"email":`). Identity trả về gì? Còn gửi một body hỏng tương tự tới meal thì sao?

<details><summary>▶ Đáp án</summary>

- **meal:** Spring ném `HttpMessageNotReadableException`, được xử lý mặc định thành **`400`**.
- **identity:** cũng exception đó, nhưng `@ExceptionHandler(Exception.class)` **bắt mọi thứ** trước khi Spring kịp xử lý theo mặc định, nên trả **`500 "Unexpected error"`**. Gửi `Content-Type: text/plain` (lẽ ra là `415`) cũng bị biến thành `500` (🐞 10.A).

Bài học: một handler "bắt tất cả" sẽ **nuốt luôn** các lỗi mà Spring vốn đã xử lý đúng.
</details>

🥊 **10.2 Phương án của bạn:** "Giữ nguyên hai định dạng lỗi như hiện tại, vì mỗi service độc lập với nhau mà."

<details><summary>▶ Phản biện</summary>

Các service độc lập về **cách triển khai bên trong**, nhưng app (client) thì **dùng chung**. App đang phải hiểu hai kiểu body lỗi khác nhau. Định dạng lỗi là một phần của **hợp đồng API**, nên phải **thống nhất**. Hãy đưa identity về problem details: bật `problemdetails`, đổi `ApiException` thành `ResponseStatusException` (hoặc giữ `ApiException` nhưng trả `ProblemDetail`), và bỏ handler `Exception` chung.
</details>

🐞 **Bug 10.A (trung bình):** chuyển identity sang problem details. Viết test cho 3 trường hợp: JSON hỏng (`400`), sai mật khẩu (`401`), email đã tồn tại (`409`).

---

## 11. Test

| | identity | profile | meal | analysis |
|---|---|---|---|---|
| Số test | 2 | 4 | 16 | 10 |
| Kiểu test | Gọi thẳng `AuthService`, **không qua HTTP** | `MockMvc` qua toàn bộ tầng HTTP | `MockMvc` | `MockMvc` + unit test (`ToDetectionsTest`) |
| Schema trong test | **`create-drop`, tắt Flyway** | **`create-drop`, tắt Flyway** | **Chạy migration thật** trên H2 | **Chạy migration thật** |
| Token | Đi qua `register` thật | Ký bằng `NimbusJwtEncoder` với cùng secret | = | = |
| Thành phần bên ngoài | | | | `DishRecognizer` giả (lambda) + `SyncTaskExecutor` |
| Test chạy đồng thời | | | ✓ 8 luồng + `CountDownLatch` | |

❓ **Đoán trước 11.1:** test của profile **xanh hết**, nhưng lên production thì service **không khởi động được**. Chuyện đó xảy ra được không, và vì sao?

<details><summary>▶ Đáp án</summary>

Được. Test dùng `create-drop`, tức Hibernate **tự tạo bảng từ entity**, và file migration **không bao giờ được chạy**. Nếu migration có lỗi cú pháp, hoặc lệch với entity (sai tên cột, sai kiểu), thì test vẫn xanh. Lên production, `validate` phát hiện chỗ lệch và service dừng. Ngoài ra, các `CHECK` của profile (tuổi 14–90…) **chưa từng được test**. Meal và analysis chạy migration thật nên không gặp vấn đề này (🐞 11.A).
</details>

❓ **Đoán trước 11.2:** analysis dùng `SyncTaskExecutor` trong test. Nếu dùng executor thật (chạy nền), test `uploadAnswersAtOnceAndTheJobCompletesInTheBackground` sẽ gặp vấn đề gì?

<details><summary>▶ Đáp án</summary>

Ngay sau khi `POST` trả về, test gọi `GET` và chờ `COMPLETED`. Nếu việc phân tích chạy trên luồng khác, có thể nó **chưa xong** ở thời điểm đó, nên test lúc xanh lúc đỏ (*flaky*). `SyncTaskExecutor` chạy `dispatch` ngay trên luồng đang gọi, nên khi `POST` trả về thì job **chắc chắn đã xong**. Đánh đổi: test này không còn kiểm tra được hành vi của luồng nền thật (ví dụ lỗi "giao việc trước khi commit" ở 7.2.3).
</details>

❓ **Đoán trước 11.3:** profile, meal và analysis tạo token trong test **mà không chạy identity**. Làm cách nào?

<details><summary>▶ Đáp án</summary>

Dùng `NimbusJwtEncoder` với **cùng secret HS256** có trong `test/application.properties`, đặt claim `uid` và `exp`. Service không phân biệt được token này với token do identity ký: đó chính là ý nghĩa của "kiểm tra tại chỗ". Test của profile còn thử thêm token **giả mạo** (sai secret), **hết hạn**, **thiếu `uid`**, và **`uid` sai định dạng**.
</details>

🔤 **Syntax test**

| Cú pháp | Ý nghĩa |
|---|---|
| `@SpringBootTest` + `@AutoConfigureMockMvc` | Khởi động toàn bộ ứng dụng, gọi HTTP mà không cần mở cổng mạng thật |
| `mvc.perform(post(...).header("Authorization", bearer).contentType(JSON).content(body))` | Tạo một request |
| `.andExpect(status().isCreated())` · `.andExpect(jsonPath("$.items[0].name").value("Cơm"))` | Kiểm tra kết quả |
| `@TestConfiguration` + `@Bean @Primary` | Thay một bean thật bằng bean giả, chỉ trong test |
| `CountDownLatch(1)`: các luồng gọi `await()`, rồi `countDown()` | Cho 8 luồng **xuất phát cùng một lúc** |
| `"""…"""` (text block) | JSON nhiều dòng ngay trong code test |

🐞 **Bug 11.A (trung bình):** cho profile và identity chạy migration thật trong test, bằng `ddl-auto=validate` và `flyway.enabled=true` (bỏ `create-drop`). Có test nào đỏ không? Thêm một test chứng minh `CHECK` tuổi ≤ 90 hoạt động.

---

## 12. Mẫu cho service thứ 5: checklist rút kinh nghiệm

Khi viết service tiếp theo (hoặc làm lại một service đã có), đi theo thứ tự sau. **Mỗi dòng là một câu hỏi** bạn phải tự trả lời được.

| # | Bước | Câu hỏi phải trả lời được |
|---|---|---|
| 1 | Luật nghiệp vụ | Luật nào phải **luôn đúng**? ("mỗi user một…", "không được trùng…") |
| 2 | Phạm vi | Dữ liệu nào thuộc service này? Dữ liệu nào **chỉ được tham chiếu bằng id**? |
| 3 | API | Status code nào cho từng kết quả? **Client có hành xử khác nhau** với từng status không? |
| 4 | `settings.gradle`, `build.gradle` | Service này chỉ đọc token (resource server) hay phát token? Dependency nào là thừa? |
| 5 | `docker-compose` + `.env` + `.env.example` | Cổng DB, cổng app, user DB riêng. Secret **không** có giá trị mặc định cho production |
| 6 | `application.properties` | Có cột `LocalDateTime` không (nếu có thì không đặt `time_zone`)? Đã bật `problemdetails` chưa? Đã có `config.import` chưa? |
| 7 | Migration `V1` | Mỗi luật ở bước 1 ứng với ràng buộc nào? Index nào phục vụ query nào? |
| 8 | Entity | Có hàm nghiệp vụ thay cho setter chưa? Id gán tay thì có cần `Persistable` không? Cột nullable có dùng wrapper không? |
| 9 | DTO | Đã tách request và response chưa? Có `userId` lọt vào body không? Wrapper + `@NotNull`? Giới hạn có trùng với `CHECK` không? |
| 10 | Repository | Có `userId` trong điều kiện query chưa? Có tránh được N+1 không? Câu SQL có dùng được index không? |
| 11 | Service | Transaction mở ở đâu, đóng ở đâu? Hai request cùng lúc thì sao? Có gọi ra ngoài trong lúc giữ transaction không? |
| 12 | Controller | `userId` chỉ lấy từ token? Tham số có kiểu chặt không? Dữ liệu của người khác thì trả 404? |
| 13 | `SecurityConfig` | Copy từ service khác sang, kèm theo **test bảo mật** giống nhau |
| 14 | Test | Có chạy migration thật không? Có token giả mạo và hết hạn không? Có test chạy đồng thời cho các luật "không được trùng" không? Thành phần bên ngoài đã được thay bằng bản giả chưa? |

### 10 quy tắc rút ra từ 4 service

1. **Định danh người gọi chỉ đến từ token.**
2. **Mỗi luật nghiệp vụ là một ràng buộc trong DB**, và DTO kiểm tra lại cùng giới hạn đó để báo lỗi dễ hiểu.
3. **DB làm trọng tài** khi có tranh chấp. `synchronized` chỉ dùng cho dữ liệu nằm trong bộ nhớ.
4. **Dữ liệu của người khác thì trả 404**, giống như dữ liệu không tồn tại.
5. **Không lưu dữ liệu suy ra được.** Dữ liệu lớn để ở bảng riêng.
6. **Thời điểm dùng `Instant`, giờ theo đồng hồ của user dùng `LocalDateTime`.** Không bao giờ trộn lẫn hai loại.
7. **Transaction ngắn**, không bao giờ gọi mạng ra ngoài trong lúc giữ transaction. Giao việc cho luồng khác sau khi đã commit.
8. **Entity có hàm nghiệp vụ, không mở setter tuỳ tiện.**
9. **Thống nhất hợp đồng API giữa các service**: định dạng lỗi, cách đặt tên, giới hạn trùng khớp nhau.
10. **Test chạy migration thật, token thật, và AI giả.**

---

## 13. Bảng tổng hợp bug để luyện tập

Làm theo thứ tự từ dễ tới khó. Với mỗi bug: **viết test cho nó đỏ trước**, sửa, test xanh, rồi gửi vào chat để review.

| Mã | Service | Mức độ | Mô tả ngắn | Kiến thức được luyện |
|---|---|---|---|---|
| 1.A | analysis | Dễ | Dependency validation thừa | Gradle |
| 3.C | identity | Dễ | Index email thừa → viết `V3` | Flyway: không sửa `V1` |
| 4.A | identity | Dễ | `User.setId()` để `public` | Spring Data `isNew` |
| 7.A | identity | Dễ | `setRevokedAt` bị rollback | Rollback mặc định |
| 9.A | identity | Dễ | Thiếu token trả 403 thay vì 401 | Spring Security entry point |
| 9.B | tất cả | Dễ | Secret JWT mặc định nằm trong git | Cấu hình, fail fast |
| 3.A | meal, analysis | Dễ | Tên món chỉ gồm khoảng trắng | `CHECK` + test |
| 3.B | analysis | Trung bình | `stage` và `failure` mâu thuẫn | `CHECK` nhiều cột |
| 5.A | identity | Trung bình | BCrypt chỉ dùng 72 byte | Validation: byte khác ký tự |
| 6.A | identity | Trung bình | `IgnoreCase` làm index vô dụng | Index và biểu thức |
| 10.A | identity | Trung bình | Handler chung biến 400 thành 500 | Problem details |
| 11.A | identity, profile | Trung bình | Test không chạy migration | Cấu hình test |
| 7.B | identity | Trung bình | `register` chạy song song trả 500 | Test đồng thời, bắt lỗi trùng |
| 4.B | analysis | Trung bình | Bước chuyển trạng thái không được kiểm tra | State machine, unit test |
| 6.B | analysis | Thấp | N+1 khi khởi động | `@EntityGraph`, `@Modifying` |
| 7.C | analysis | Khó | Hai worker cùng chạy một job | Cập nhật có điều kiện |
| 2 (bảng 7.1) | identity | Khó | Refresh token rotation chạy song song | Cập nhật có điều kiện, reuse detection |

> Học tới đâu thì ghi lại câu trả lời của bạn cho các câu ❓ và 🥊 vào chat. Câu nào đáp án làm bạn bất ngờ là câu đáng ôn lại nhất.
