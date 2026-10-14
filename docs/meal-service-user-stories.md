# `meal-service`: chức năng và user story

> Ngày lập: 2026-10-14. Service thứ 3, sau `identity-service` và `profile-service`.
> Nguồn: interface `MealRepository` và các màn Dashboard, History, Review trong app `BaoFit`.

---

## 1. Phạm vi

`meal-service` lưu **nhật ký ăn uống**: các bữa ăn đã được user xác nhận (`ConfirmedMeal`) và danh sách món trong mỗi bữa (`FoodItem`).

| Thuộc `meal-service` | Không thuộc `meal-service` |
|---|---|
| Bữa ăn đã xác nhận + các món | Bữa đang phân tích (`PendingMeal`), job AI → `analysis-service` (sau) |
| Đường dẫn ảnh của bữa (chỉ lưu URL) | Upload ảnh → `analysis-service` |
| Lọc bữa ăn theo ngày / khoảng ngày | Mục tiêu kcal → app tính từ profile |
| | Tổng kcal/macro theo ngày, streak → app tính (`SummarizeDayUseCase`, `CalculateStreakUseCase`) |

Server trả dữ liệu thô, app tự tính tổng. App đã có sẵn logic này, server không cần làm lại.

---

## 2. User story

### Epic A: Ghi bữa ăn

**US-01: Lưu bữa ăn sau khi xem lại kết quả AI**
Là user, sau khi AI nhận diện món trong ảnh và tôi đã sửa tên/khối lượng cho đúng, tôi muốn lưu bữa ăn vào nhật ký, để nó được tính vào tổng calo trong ngày.

- [x] Bữa ăn gồm: loại bữa (`BREAKFAST`, `LUNCH`, `SNACK`, `DINNER`), thời điểm ăn, ảnh (có thể không có), danh sách món, cờ "đã chỉnh sửa".
- [x] Mỗi món gồm: tên, khối lượng (gram), dinh dưỡng trên 100 g (kcal, protein, carbs, fat), độ tin cậy AI (0–100).
- [x] Phải có ít nhất 1 món. Tên món không được trống. Khối lượng phải > 0. Sai thì trả `400`.
- [x] Lưu thành công trả về bữa ăn vừa tạo, có `id`.
- [x] Nếu user đã sửa kết quả AI thì `isEdited = true`, app hiện nhãn "đã sửa".

**US-02: Tự nhập món khi AI thất bại**
Là user, khi AI không nhận ra món (bấm "Tự nhập món ăn"), tôi muốn tự gõ tên và khối lượng món, để vẫn ghi được bữa ăn.

- [x] Dùng chung API với US-01.
- [x] Món tự thêm có `confidence = null`.
- [x] Một bữa có thể vừa có món AI nhận ra, vừa có món tự thêm.

**US-03: Không bị lưu trùng bữa ăn**
Là user, khi mạng chập chờn hoặc tôi lỡ bấm "Lưu" hai lần, tôi không muốn bữa ăn xuất hiện hai lần trong nhật ký.

- [x] Bữa ăn đến từ một job phân tích thì gửi kèm `jobId`.
- [x] Gửi lại cùng `jobId` thì server trả về bữa đã lưu trước đó (`200`), không tạo bữa mới.
- [x] Bảo đảm bằng ràng buộc `UNIQUE (user_id, job_id)` trong database, kể cả khi 2 request đến cùng lúc.

### Epic B: Xem nhật ký

**US-04: Xem bữa ăn hôm nay trên Dashboard**
Là user, tôi muốn thấy hôm nay tôi đã ăn những bữa nào và tổng calo/macro so với mục tiêu, để biết còn được ăn bao nhiêu.

- [x] Lấy được mọi bữa ăn của một ngày, bữa mới nhất đứng đầu.
- [x] Ngày chưa có bữa nào thì trả danh sách rỗng, không trả lỗi.

**US-05: Xem nhật ký của một ngày bất kỳ (History)**
Là user, tôi muốn chọn một ngày trong 7 ngày gần nhất hoặc chọn ngày trên lịch, để xem lại hôm đó tôi đã ăn gì.

- [x] Dùng cùng API với US-04, truyền ngày cần xem.

**US-06: Xem dải 7 ngày trong tuần**
Là user, tôi muốn thấy 7 ngày gần nhất được đánh dấu "trong mục tiêu", "vượt mục tiêu" hoặc "chưa có dữ liệu", để nắm nhanh cả tuần.

- [x] Lấy được bữa ăn của cả một khoảng ngày trong một lần gọi.
- [x] App tự nhóm theo ngày và tự tính trạng thái.

**US-07: Xem chuỗi ngày ghi nhật ký liên tiếp (streak)**
Là user, tôi muốn thấy mình đã ghi bữa ăn liên tục bao nhiêu ngày, để có động lực duy trì.

- [x] Dashboard lấy bữa ăn của 60 ngày gần nhất + hôm nay (61 ngày) trong một lần gọi.
- [x] Khoảng ngày tối đa của API phải ≥ 61 ngày.

**US-08: Biết còn bao nhiêu calo trước khi lưu bữa**
Là user, trên màn Review tôi muốn thấy "còn X kcal" trước và sau bữa này, để quyết định có ăn hết hay không.

- [x] Dùng API của US-04 với ngày của bữa ăn đang xem lại.

### Epic C: Riêng tư

**US-09: Chỉ tôi thấy nhật ký của tôi**
Là user, tôi muốn chỉ mình tôi đọc và ghi được nhật ký ăn uống của mình.

- [x] Mọi endpoint yêu cầu access token. Không có token hoặc token sai thì trả `401`.
- [x] `user_id` luôn lấy từ claim `uid` của token, không bao giờ lấy từ request.
- [x] Mọi truy vấn đều lọc theo `user_id`. User A không thể đọc hay ghi đè bữa ăn của user B.

### Epic D: Chi tiết bữa ăn (thêm ngày 2026-10-14)

**US-10: Xem lại và sửa bữa ăn đã lưu**
Là user, khi bấm vào một bữa đã lưu trên Dashboard hoặc History, tôi muốn xem lại các món và sửa tên, khối lượng, calo, thêm/bớt món hoặc đổi loại bữa, để nhật ký đúng với thực tế.

- [x] Gửi loại bữa + toàn bộ danh sách món mới. Thời điểm ăn, ảnh và `jobId` không đổi.
- [x] Server đánh dấu `isEdited = true`.
- [x] Quy tắc kiểm tra món giống US-01. Sai thì trả `400`.
- [x] Bữa không tồn tại hoặc của người khác thì trả `404` (không phân biệt hai trường hợp).

**US-11: Xóa bữa ăn**
Là user, tôi muốn xóa một bữa ghi nhầm, để nó không còn được tính vào tổng calo.

- [x] Xóa thành công trả `204`. Xóa lần nữa trả `404`; app coi cả hai là "đã xóa".
- [x] Xóa luôn các món của bữa (`ON DELETE CASCADE`).
- [x] Sau khi xóa, `jobId` của bữa đó được dùng lại được.

---

## 3. Chưa làm lần này

| Chức năng | Lý do | Làm khi |
|---|---|---|
| Đổi thời điểm ăn của bữa đã lưu | App chưa có bộ chọn giờ trên màn chi tiết | Có nhu cầu |
| Bữa đang phân tích (`PendingMeal`) | Thuộc job AI | Làm `analysis-service` |
| Upload ảnh | Thuộc `analysis-service`. Meal chỉ lưu URL | Làm `analysis-service` |
| Xóa bữa ăn khi user bị xóa | Cần event giữa các service (giống Phase C của profile) | Có message broker |
| Tính tổng theo ngày ở server | App đã tính rồi | Có client thứ 2 cần |

---

## 4. API

Base URL: `http://localhost:8083/api/v1/meals`. Mọi request gửi header `Authorization: Bearer <accessToken>`.

| `MealRepository` (app) | Endpoint |
|---|---|
| `saveMeal(...)` | `POST /api/v1/meals` |
| `observeDay(date)` | `GET /api/v1/meals?from=date&to=date` |
| `observeRange(from, to)` | `GET /api/v1/meals?from=...&to=...` |
| `updateMeal(id, type, items)` | `PUT /api/v1/meals/{id}` |
| `deleteMeal(id)` | `DELETE /api/v1/meals/{id}` |

### `PUT /api/v1/meals/{id}`: sửa bữa ăn (US-10)

```json
{
  "type": "DINNER",
  "items": [
    { "name": "Phở bò", "grams": 400, "per100g": { "kcal": 110, "proteinG": 7, "carbsG": 14, "fatG": 3 }, "confidence": 94 }
  ]
}
```

| Kết quả | Khi nào |
|---|---|
| `200 OK` + bữa ăn sau khi sửa | Thành công. `isEdited` luôn là `true` |
| `400 Bad Request` | Sai dữ liệu (cùng quy tắc với `POST`), hoặc `id` không phải UUID |
| `404 Not Found` | Không có bữa này, hoặc bữa của người khác |

Món được ghi đè theo vị trí, không xóa hết rồi thêm lại: Hibernate chạy `INSERT` trước `DELETE`, nên thêm dòng mới trùng `position` sẽ vi phạm `UNIQUE (meal_id, position)`.

### `DELETE /api/v1/meals/{id}`: xóa bữa ăn (US-11)

`204 No Content` khi đã xóa; `404` khi không có bữa này (đã xóa trước đó, hoặc của người khác).

### `POST /api/v1/meals`: lưu bữa ăn (US-01, 02, 03)

```json
{
  "jobId": "job-7f3a",
  "type": "LUNCH",
  "loggedAt": "2026-10-14T12:05:00",
  "photoUrl": "https://cdn.example/meals/7f3a.jpg",
  "isEdited": true,
  "items": [
    {
      "name": "Cơm tấm",
      "grams": 250,
      "per100g": { "kcal": 130, "proteinG": 2.7, "carbsG": 28, "fatG": 0.3 },
      "confidence": 94
    },
    {
      "name": "Sườn nướng",
      "grams": 120,
      "per100g": { "kcal": 290, "proteinG": 24, "carbsG": 4, "fatG": 20 },
      "confidence": null
    }
  ]
}
```

| Kết quả | Khi nào |
|---|---|
| `201 Created` + bữa ăn | Bữa mới |
| `200 OK` + bữa ăn đã có | Đã có bữa với cùng `jobId` (US-03) |
| `400 Bad Request` | Sai dữ liệu (xem quy tắc kiểm tra bên dưới) |
| `401 Unauthorized` | Thiếu token hoặc token sai |

### `GET /api/v1/meals?from=2026-10-08&to=2026-10-14`: xem nhật ký (US-04 → 08)

- `from`, `to` có dạng `YYYY-MM-DD`, tính cả 2 đầu. Xem 1 ngày thì truyền `from = to`.
- Trả về mảng bữa ăn trong khoảng ngày, **mới nhất trước** (theo `loggedAt`). App tự nhóm theo ngày và tự thêm ngày rỗng.
- `400` khi `from > to` hoặc khoảng ngày dài hơn 92 ngày.

```json
[
  {
    "id": "5b0c9a3e-1d2f-4c6b-9a8e-2f1e0d3c4b5a",
    "jobId": "job-7f3a",
    "type": "LUNCH",
    "loggedAt": "2026-10-14T12:05:00",
    "photoUrl": "https://cdn.example/meals/7f3a.jpg",
    "isEdited": true,
    "items": [
      {
        "id": "a1b2c3d4-0000-4000-8000-000000000001",
        "name": "Cơm tấm",
        "grams": 250,
        "per100g": { "kcal": 130, "proteinG": 2.7, "carbsG": 28, "fatG": 0.3 },
        "confidence": 94
      }
    ]
  }
]
```

### Quy tắc kiểm tra dữ liệu (cả DTO lẫn `CHECK` trong database)

| Trường | Quy tắc |
|---|---|
| `type` | Một trong `BREAKFAST`, `LUNCH`, `SNACK`, `DINNER` |
| `loggedAt` | Bắt buộc, dạng `YYYY-MM-DDTHH:mm:ss` |
| `jobId` | Không bắt buộc, tối đa 64 ký tự |
| `photoUrl` | Không bắt buộc, tối đa 2048 ký tự |
| `items` | 1 → 30 món |
| `items[].name` | Không trống sau khi bỏ khoảng trắng, tối đa 100 ký tự |
| `items[].grams` | 1 → 5000 |
| `items[].per100g.kcal` | 0 → 900 |
| `items[].per100g.proteinG / carbsG / fatG` | 0 → 100 |
| `items[].confidence` | `null` hoặc 0 → 100 |

---

## 5. Database (`meal-db`, cổng 5435)

Schema đầy đủ nằm ở [`V1__create_meals_tables.sql`](../meal-service/src/main/resources/db/migration/V1__create_meals_tables.sql).

| Bảng | Cột chính | Ràng buộc |
|---|---|---|
| `meals` | `id`, `user_id` (claim `uid`), `job_id`, `type`, `logged_at` (`TIMESTAMP`, không có múi giờ), `photo_url`, `is_edited`, `created_at` | `UNIQUE (user_id, job_id)` cho US-03; index `(user_id, logged_at)` cho truy vấn theo ngày |
| `meal_items` | `id`, `meal_id`, `position`, `name`, `grams`, `*_per_100g` (`DOUBLE PRECISION`), `confidence` | `REFERENCES meals ON DELETE CASCADE`; `CHECK` giống quy tắc kiểm tra ở mục 4 |

Không có khóa ngoại tới bảng users vì users nằm ở `identity-db`.

---

## 6. Quyết định thiết kế

1. **`loggedAt` là giờ địa phương, không có múi giờ.** App dùng `LocalDateTime`. Lọc theo ngày bằng `logged_at >= from 00:00 AND logged_at < (to + 1) 00:00`. Giới hạn: user đổi múi giờ thì bữa ăn có thể nằm sai ngày. Thêm cột múi giờ khi cần.
2. **Lọc theo khoảng ngày thay cho phân trang.** Khoảng ngày tối đa 92 ngày, tức khoảng 400 bữa nếu ăn 4 bữa mỗi ngày, đủ nhỏ để trả một lần. Dashboard cần 61 ngày. Thêm phân trang khi có màn "toàn bộ lịch sử".
3. **Lưu dinh dưỡng trên 100 g + khối lượng, không lưu kcal tổng.** Giống domain của app: đổi khối lượng thì calo tự tính lại, không bao giờ lệch nhau.
4. **Không lưu tiêu đề bữa ăn.** App tự tạo tiêu đề từ tên món bằng `mealTitleFor(items)`.
5. **Chống lưu trùng bằng ràng buộc database** (US-03). Service tìm bữa theo `jobId` trước. Nếu 2 request đến cùng lúc, ràng buộc `UNIQUE` chặn bản thứ hai và service trả về bữa đầu tiên (`200`) thay vì lỗi `500`.
6. **Quan hệ 1-nhiều `meals` → `meal_items`**, xóa bữa ăn thì xóa luôn các món (`ON DELETE CASCADE`). Món được lấy cùng bữa trong 1 câu SQL (`@EntityGraph`), không bị N+1.
7. **Dinh dưỡng dùng `DOUBLE PRECISION`**, giống kiểu `Double` của app, không phải chuyển đổi qua lại.

---

## 7. Hạ tầng

Giống `profile-service`:

- Module `meal-service` trong `settings.gradle`, Spring Boot cổng **8083**.
- `meal-db` (PostgreSQL) trong `docker-compose.yml`, cổng **5435**, chỉ mở cho `127.0.0.1`.
- Biến `MEAL_DB_*`, `MEAL_SERVICE_PORT` trong `.env.example`.
- Kiểm tra JWT bằng `spring-boot-starter-security-oauth2-resource-server` với cùng `JWT_SECRET`. Không gọi `identity-service`.
- Flyway quản lý schema, Hibernate `ddl-auto=validate`.
