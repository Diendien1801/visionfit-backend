# Kỹ năng Java backend theo level và kế hoạch lên Middle

> Dựa trên 100 JD Java backend tại Việt Nam, đọc ngày 2026-11-06. Số trong ngoặc là số JD **bắt buộc** kỹ năng đó, trên tổng số JD đã đọc ở level đó.
> Mỗi level chỉ ghi **phần thêm vào** so với level trước.

**Giới hạn của số liệu:** đây là mẫu đọc được, không ngẫu nhiên (TopCV, JobsGo, Indeed chặn truy cập; VietnamWorks không hiện danh sách). Middle trở lên chủ yếu lấy từ ITviec, còn Junior trở xuống chủ yếu từ Joboko và LinkedIn, nên khác biệt giữa các level có thể lẫn với khác biệt giữa các trang. JD cũng không cho biết phỏng vấn hỏi gì. Các con số chỉ cho thấy bậc thô.

## Kỹ năng theo level

### Intern/Fresher (25 JD): sinh viên hoặc 0–12 tháng
**Cần có:**
- Java (24), Core Java (9)
- OOP (10)
- SQL (12)
- Cấu trúc dữ liệu và giải thuật (7)
- REST API cơ bản (5)
- Git (4)
- Spring (4)
- Thái độ: ham học hỏi (19)

**Điểm cộng:** Spring (8), Git (5), microservices (2).

**Tuỳ nơi:** frontend (7), đọc được tài liệu tiếng Anh.

### Junior (17 JD): khoảng 1–2 năm
**Thêm:**
- Spring/Spring Boot thành nền (11)
- REST API dùng trong công việc thật (10)
- Dùng Git hằng ngày (8)
- JPA/Hibernate (5)
- Microservices (5)
- Multithreading cơ bản (4)
- Design patterns (4)
- Viết test (3), CI/CD (3)
- Làm việc nhóm (12)

**Điểm cộng:** messaging (4), Redis (3), Docker (2), Kubernetes (2).

### Middle (21 JD): thường từ 3 năm
**Thêm:**
- Cơ sở dữ liệu vững hơn (17)
- Microservices hoặc hệ phân tán (8)
- OOP, SOLID, design patterns dùng thành thạo (8)
- Messaging: Kafka, RabbitMQ (7)
- Testing (5)
- Redis, caching (4)
- Công cụ AI coding (4)
- Tiếng Anh làm việc (10)

**Điểm cộng:** Docker (5), cloud (5), kiến thức tài chính (5), Kubernetes (4), observability (4), mentoring (4), tối ưu SQL (2).

### Senior (21 JD): khoảng 4–5 năm
**Thêm về kỹ thuật:**
- Thiết kế hệ thống (9 bắt buộc, thêm 11 JD nêu là nhiệm vụ)
- CI/CD (8)
- Cloud (6)
- Kubernetes (5)
- Security (5)
- Concurrency (3)
- Xử lý sự cố production, tìm nguyên nhân gốc (thấy ở một số JD, chưa đếm riêng)

**Thêm ngoài code:**
- Code review (9)
- Tham gia quyết định kiến trúc (8)
- Trao đổi với các bên liên quan (7)
- Mentoring (5)

**Điểm cộng:** Kubernetes (7), cloud (6), messaging (6). JVM tuning rất hiếm: chỉ 1 trong 41 JD Senior và Lead.

### Lead/Principal (20 JD): khoảng 7 năm trở lên
**Thêm về kỹ thuật:**
- Thiết kế hệ thống gần như bắt buộc (16)
- Cloud (11), security (9), Kubernetes (8)
- Hệ phân tán chuyên sâu (7)
- Observability (5)

**Thêm ngoài code:**
- Đã từng giữ vai trò dẫn dắt (14)
- Quyết định và chịu trách nhiệm về kiến trúc (18)
- Đặt chuẩn kỹ thuật cho team (12)
- Mentoring (11), làm việc với các bên liên quan (11)
- Dẫn dắt team hoặc delivery (7, đều ở nhóm Tech Lead)
- Tiếng Anh (12)

### Lưu ý khi đọc
- Lên cao thì danh sách công nghệ không đổi nhiều, mà **phạm vi trách nhiệm** mới là thứ tăng.
- Ranh giới Junior và Middle rất mờ: nhiều JD ghép "Middle/Senior" hoặc đòi kỹ năng gần như nhau. Điều chắc nhất giữa hai level là số năm.
- "Level cao có sẵn kỹ năng level trước" là giả định. JD cấp cao không phải lúc nào cũng ghi lại kỹ năng nền, và phỏng vấn vẫn có thể hỏi.

## Kế hoạch lên Middle

Phần này là **nhận định**, không phải kết luận từ dữ liệu JD.

| # | Việc | Ghi chú |
|---|---|---|
| 1 | Đào sâu VisionFit | Đạt 4 tiêu chí ở dưới |
| 2 | Redis | Áp dụng vào project thật |
| 3 | RabbitMQ | Tự viết thêm một event mới, đưa lên production |
| 4 | Làm chức năng message | Chưa chốt: chat giữa người dùng, hay event qua RabbitMQ |
| 5 | Core Java, OOP, DSA, concurrency | Làm song song ngay từ đầu, vì đây là thứ quyết định việc qua vòng lọc |
| 6 | Kafka: khái niệm và một demo nhỏ | 36 JD nêu Kafka, 11 JD nêu RabbitMQ, và cả 11 đều nêu Kafka |

### Tiêu chí "nắm vững VisionFit" (không dùng AI)
- Viết lại từ đầu một service nhỏ, gồm cả auth và test.
- Giải thích được vì sao chọn từng thứ và nhược điểm của lựa chọn đó.
- Thêm một tính năng mới xuyên qua 2 service.
- Khi có lỗi 500 trên server, tự tìm ra nguyên nhân từ log.

### Phần học không thay được
Sáu việc trên đưa tới mức **kiến thức tiệm cận Middle**, chưa phải Middle. JD Middle thường đòi từ 3 năm, và dự án cá nhân không có những trải nghiệm sau:
- Làm trong team: code review, tranh luận thiết kế.
- Đọc và sửa code cũ của người khác.
- Dữ liệu lớn và tải thật.
- Sự cố production ảnh hưởng tới khách hàng.
- Tự ước lượng và giao việc đúng hạn.

Vì vậy, mục tiêu trước mắt là **có việc Fresher hoặc Junior sàn 0–1 năm**, rồi lấy kinh nghiệm làm việc thật để lên Middle.
