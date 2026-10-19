# `analysis-service`: phân tích ảnh bữa ăn bằng AI

> Ngày lập: 2026-10-19. Đây là service thứ 4 và là **service nghiệp vụ cuối cùng**, sau `identity`, `profile` và `meal`.
> Nguồn: interface `MealAnalysisRepository` và các màn Camera → Analysis → Review trong app `BaoFit`.

---

## 1. Có phải service cuối cùng không?

Đây là các repository trong `AppContainer` sau khi có meal-service:

| Repository | Trạng thái | Cần backend? |
|---|---|---|
| `AuthRepository` | identity-service ✓ | |
| `ProfileRepository` | profile-service ✓ | |
| `MealRepository` | meal-service ✓ | |
| **`MealAnalysisRepository`** | **Mock → analysis-service (lần này)** | **Có, đây là service cuối** |
| `CameraSource` | Mock (luôn trả ảnh demo) | Không. Phần này là **camera của thiết bị** (CameraX trên Android, AVFoundation trên iOS, chọn file trên desktop) |
| `NetworkMonitor` | Mock | Không. Phần này là **trạng thái mạng của thiết bị** (ConnectivityManager / NWPathMonitor) |

Vậy đây là service nghiệp vụ cuối cùng. Sau đó không còn service mới, chỉ còn **hạ tầng**: API Gateway, app tự refresh token, lưu token xuống máy, và event để đồng bộ dữ liệu giữa các service. Phía app còn camera thật và theo dõi mạng thật.

---

## 2. Phạm vi

| Thuộc analysis-service | Không thuộc |
|---|---|
| Nhận ảnh upload, lưu ảnh để có thể phân tích lại (retry) | Lưu bữa ăn đã xác nhận → meal-service |
| Job bất đồng bộ: `RECOGNIZING` → `CALCULATING` → `COMPLETED` / `FAILED` | Tổng kcal trong ngày, mục tiêu → app |
| Gọi Claude (vision) nhận diện món, ước lượng gram, dinh dưỡng trên 100 g, vị trí trên ảnh | Hiển thị ảnh: app vẫn dùng `photoRef` của nó |
| Phân tích lại khi job thất bại | |

## 3. User story

- **US-01: Chụp xong là có kết quả.** Gửi ảnh thì nhận `jobId` ngay (`202`), không phải chờ AI. App theo dõi job tới khi xong.
- **US-02: Thấy AI đang làm gì.** Job có `stage` và thời gian từng bước (xếp hàng, nhận diện, tính toán), để màn Analysis hiện tiến độ.
- **US-03: Sửa được kết quả.** Mỗi món có tên tiếng Việt, gram, dinh dưỡng trên 100 g (đổi gram thì kcal tự đổi theo), độ tin cậy 0–100, và vị trí trên ảnh để vẽ marker.
- **US-04: AI không thấy món nào.** Job `FAILED` với lý do `NO_FOOD_DETECTED`, app gợi ý chụp lại hoặc tự nhập.
- **US-05: AI lỗi thì thử lại được.** Job `FAILED` với lý do `INVALID_AI_RESPONSE` (AI lỗi, quá tải, trả về dữ liệu hỏng). `POST /retry` phân tích lại **chính ảnh đã lưu**, không cần upload lại.
- **US-06: Riêng tư.** Chỉ chủ job mới xem và retry được job của mình (user lấy từ claim `uid`). Người khác gọi thì nhận `404`, không phải `403`, để không lộ là job có tồn tại.
- **US-07: Service khởi động lại không làm mất job.** Job đang chạy dở khi service tắt sẽ được chạy lại lúc khởi động.

## 4. API

| Method | Path | Body | Phản hồi |
|---|---|---|---|
| `POST` | `/api/v1/analysis` | multipart: `photo` (jpeg/png/webp, ≤ 5 MB), `capturedAt` (ISO `LocalDateTime`), `mealType`, `photoRef` | `202` JobDto · `400` sai định dạng · `401` |
| `GET` | `/api/v1/analysis/{id}` | | `200` JobDto · `404` |
| `POST` | `/api/v1/analysis/{id}/retry` | | `202` JobDto · `409` job không ở trạng thái `FAILED` · `404` |

```json
{
  "id": "…uuid…", "mealType": "DINNER", "capturedAt": "2026-10-19T19:05:00",
  "photoRef": "bundled://family_tray", "photoSizeBytes": 166029,
  "stage": "COMPLETED", "failure": null,
  "timings": { "queueMs": 12, "recognitionMs": 8400, "calculationMs": 3 },
  "detections": [
    { "id": "…", "name": "Thịt kho trứng", "grams": 150, "confidence": 92,
      "per100g": { "kcal": 230, "proteinG": 14, "carbsG": 4, "fatG": 17 },
      "region": { "centerX": 0.31, "centerY": 0.42, "radius": 0.16 } }
  ]
}
```

- **App tự tính `mealType`** (`MealType.forTime`) rồi gửi lên, để quy tắc này chỉ nằm ở một chỗ.
- **`photoRef`** là tham chiếu ảnh của app (`bundled://…`, sau này là đường dẫn file của camera). Server lưu lại nguyên văn để app hiển thị. Ảnh thật dùng để phân tích là phần `photo`.
- **Thời gian upload** do app tự đo (server không biết), phần còn lại server đo.

## 5. Thiết kế

```
App ──POST multipart──► AnalysisController ──► AnalysisService.submit()
                                                 │ lưu job (RECOGNIZING) + ảnh (bytea)
                                                 │ trả 202 ngay
                                                 └─► executor (thread pool) ──► AnalysisWorker.process(jobId)
                                                        ├─ DishRecognizer.recognize(ảnh)   ← Claude vision (claude-opus-5-5)
                                                        ├─ không có món → FAILED NO_FOOD_DETECTED
                                                        ├─ lỗi AI       → FAILED INVALID_AI_RESPONSE
                                                        └─ CALCULATING (lưu món) → COMPLETED
App ──GET /{id} mỗi giây──► đọc trạng thái job (polling)
```

- **Claude:** dùng Java SDK `com.anthropic:anthropic-java`, model `claude-opus-5-5`, effort `medium`, và **structured output** (`outputConfig(AiResult.class)`) nên câu trả lời luôn đúng schema JSON. Ảnh gửi dạng base64. Có bật refusal fallback phía server (`fallbacks: "default"`).
- **`DishRecognizer` là interface** với hai bản: `ClaudeDishRecognizer` cho môi trường thật, và bản giả trong test, để test không tốn tiền và không cần mạng.
- **Ảnh lưu trong Postgres (`bytea`).** Ảnh demo chỉ khoảng 70–170 KB. `ponytail:` khi có camera thật và nhiều user thì chuyển sang object storage (S3/MinIO).
- **Kiểm tra lại số liệu AI trả về:** gram, dinh dưỡng, độ tin cậy và vị trí đều được ép vào giới hạn mà meal-service chấp nhận (`CHECK` của `meal_items`). Nhờ vậy món AI tìm ra luôn lưu được.
- **Polling 1 giây** thay vì WebSocket/SSE. `ponytail:` chuyển sang SSE khi có nhiều user đồng thời.

## 6. Phía app

| File | Việc |
|---|---|
| `data/remote/ApiConfig.kt` | `ApiUrls.analysis` (cổng 8084) |
| `data/remote/PhotoBytes.kt` | Đọc byte ảnh: `bundled://x` → `Res.readBytes("drawable/meal_x.jpg")` |
| `data/remote/dto/AnalysisDtos.kt` | DTO ↔ `AnalysisJob` |
| `data/repository/RemoteMealAnalysisRepository.kt` | Upload multipart, polling tới khi job kết thúc, ghi `PendingMeal` vào `store` (như bản Mock), retry |
| `di/AppContainer.kt` | Có backend thì dùng Remote, không có thì dùng Mock |

Có thêm phần chọn ảnh thật từ máy (xem mục 9).

`ponytail:` các bữa đang phân tích chỉ nằm trong bộ nhớ app, như bản Mock. Tắt app thì danh sách chờ trong nhật ký mất, dù job vẫn còn trên server. Khi cần giữ lại sau khi mở lại app: thêm `GET /api/v1/analysis?from&to`, rồi lọc bỏ những job đã có bữa ăn trong meal-service (so theo `jobId`).

## 7. Chạy local

```bash
# .env: thêm ANALYSIS_* (xem .env.example) và ANTHROPIC_API_KEY=...
docker compose up -d analysis-db
sh ./gradlew :analysis-service:bootRun
```

Không có `ANTHROPIC_API_KEY` thì service vẫn chạy, nhưng mọi job sẽ `FAILED` (`INVALID_AI_RESPONSE`) và app hiện nút "Thử lại" / "Tự nhập món".

---

## 8. Kết quả triển khai (2026-10-19)

### Đã kiểm tra

- [x] `:analysis-service:test`: **9/9 xanh**
  - `AnalysisApiTest` (7 test, token thật, migration thật trên H2, model giả, job chạy đồng bộ): 401 khi thiếu token · upload trả `202` rồi job `COMPLETED` với đủ dữ liệu · không có món thì `NO_FOOD_DETECTED`, retry thì `COMPLETED`, retry lần nữa thì `409` · model lỗi thì `INVALID_AI_RESPONSE` · user khác nhận `404` · upload sai thì `400` · job bị ngắt do restart được chạy lại khi khởi động
  - `ToDetectionsTest` (2 test): ép số liệu của AI vào giới hạn và bỏ món không dùng được
- [x] App `:composeApp:jvmTest`: **79/79 xanh**, trong đó `RemoteMealAnalysisRepositoryTest` có 6 test: upload multipart + polling tới khi xong · retry · retry không ném lỗi · mở lại job sau khi khởi động lại app · upload lỗi thì không tạo mục chờ · đăng xuất thì quên hết job. iOS và Android compile được
- [x] Chạy thật (Postgres + identity-service + analysis-service + `AppContainer` của app, test tạm thời đã xóa):
  - đăng ký thành công
  - app upload **166.029 byte** của ảnh demo thật, server lưu đủ 166.029 byte
  - job chạy nền, app polling tới khi kết thúc
  - retry phân tích lại đúng ảnh đã lưu
  - Request **có tới Claude API** và nhận `401 authentication_error` vì máy chạy thử chưa có `ANTHROPIC_API_KEY`
- [ ] **Chưa kiểm tra:** câu trả lời thật của Claude (nhận diện món, structured output, `fallbacks`). Cần thêm `ANTHROPIC_API_KEY` vào `.env` rồi chụp một ảnh demo trong app

### Lưu ý

- **`main` hiện không build được meal-service:** commit `2759bc7` dùng `com.visionfit.meal.dto.UpdateMealRequest`, nhưng file đó **không có trong repo** (có thể quên `git add`). analysis-service không phụ thuộc vào file này.
- Phần gọi Claude dùng model `claude-opus-5-5`, effort `medium`, structured output. Refusal fallback phía server được bật (`fallbacks: "default"`, beta `server-side-fallback-2026-07-01`). Muốn tắt thì xóa 2 dòng `putAdditional…` trong `ClaudeDishRecognizer`.

---

## 9. Chọn ảnh thật từ máy (2026-10-19)

Camera vẫn là Mock: khung ngắm và nút chụp chỉ "thấy" ảnh demo. Ở chế độ **Thư viện**, ô đầu tiên là **"Chọn từ máy"**, mở hộp chọn ảnh của từng nền tảng:

| Nền tảng | Hộp chọn ảnh | Lưu ảnh ở | Quyền truy cập |
|---|---|---|---|
| Desktop | `java.awt.FileDialog` (jpg, png, webp, bmp, gif) | `~/.visionfit/photos` | Không cần |
| Android | System Photo Picker (`PickVisualMedia.ImageOnly`) | `filesDir/photos` | Không cần |
| iOS | `PHPickerViewController`, xin dữ liệu dạng `public.jpeg` để iOS tự chuyển HEIC | `Documents/photos` | Không cần (chạy ngoài tiến trình app) |

Luồng xử lý:
```
Screen: bấm "Chọn từ máy" → PickFromDeviceClicked
ViewModel: → effect OpenPhotoPicker → Route mở hộp chọn của nền tảng
storeDevicePhoto(): giải mã → thu nhỏ còn cạnh dài ≤ 1568 px → JPEG chất lượng 85 → lưu file
  → DevicePhotoPicked(MealPhoto("file://…")) → ảnh lên đầu thư viện và được chọn sẵn
  → null: không phải ảnh đọc được → snackbar "Không đọc được ảnh này…"
Gửi: readPhotoBytes("file://…") → upload multipart; app hiển thị ảnh bằng rememberMealPhotoPainter (đọc file, giải mã ở luồng nền)
```

Vì sao phải chuyển định dạng và thu nhỏ: ảnh điện thoại 12 MP thường quá 5 MB (server từ chối), ảnh iPhone mặc định là HEIC (Claude không đọc được), và ảnh có cạnh dài hơn 1568 px chỉ làm tốn thêm token mà không giúp nhận diện tốt hơn.

**Đã kiểm tra:**
- `:composeApp:jvmTest`: **87/87 xanh**, trong đó có `DevicePhotosTest` (4 test với JPEG/PNG thật trên Skia: ảnh nhỏ giữ nguyên kích thước, ảnh 4000×3000 → 1568×1176 JPEG, dữ liệu rác → `null`, file bị xóa → `null`) và `DevicePhotoPickTests` (3 test cho luồng MVI)
- Ảnh chụp màn hình `05c-camera-library-picked`
- Chạy thật: PNG 4000×3000 → JPEG lưu trên máy → upload → server lưu đủ số byte, kiểu `image/jpeg`, giữ nguyên `photoRef` dạng `file://`
- Desktop, Android và iOS đều compile được

**Chưa kiểm tra được:**
- Bấm trên hộp chọn ảnh thật của từng nền tảng (FileDialog, Photo Picker, PHPicker), vì không có công cụ điều khiển giao diện của chúng
- Chạy trên máy Android hoặc iOS thật

`ponytail:` ảnh nằm trên máy đã chọn ảnh. Bữa ăn xem trên máy khác sẽ hiện ảnh giữ chỗ. Khi cần: cho analysis-service phục vụ ảnh (`GET /api/v1/analysis/{id}/photo`) và lưu URL đó vào meal-service.
