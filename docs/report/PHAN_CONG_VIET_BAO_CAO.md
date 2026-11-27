# Phân công viết báo cáo DATN-2026 (3 thành viên)

> Mục đích: 3 người viết **song song**, không sửa chung một chỗ, không phải chờ nhau.
> Đề mục bên dưới đã được **chuyển từ khung tham khảo (đề tài chấm bài UML) sang đề tài của mình** (hệ thống quản lý công việc kiểu Jira). Phần nào thiếu/thừa so với thực tế thì sửa ở bảng §3 trước rồi mới viết.

---

## 1. Nguyên tắc chia

1. **Chia dọc theo phân hệ, không chia ngang theo chương.** Nếu chia "mỗi người một chương" thì người viết Chương 4 phải chờ Chương 3, người viết Chương 3 lại phải hỏi người code từng phần. Ở đây mỗi người nhận **trọn một nhóm phân hệ mình hiểu nhất** và viết nó xuyên suốt: giải pháp (Ch.2) → đặc tả, phân tích, thiết kế (Ch.3) → test case, demo (Ch.4).
2. **Phần dùng chung có đúng 1 chủ.** Kiến trúc tổng thể, actor, ERD tổng, mục lục… mỗi thứ chỉ một người sửa. Người khác góp ý qua comment hoặc gửi nội dung cho chủ phần đó, không tự sửa.
3. **Mỗi người một file riêng.** Không ai mở `Khung_DATN.docx` để viết cho đến buổi ghép (xem §6). File Word là file nhị phân, hai người cùng sửa thì Git không gộp được.
4. **Chốt "hợp đồng" trước khi viết** (§4): danh sách actor, mã use case, sơ đồ kiến trúc, quy ước đặt tên. Chốt xong thì cả 3 viết độc lập.

---

## 2. Ba vai trò

| Vai | Phạm vi phân hệ | Phần dùng chung làm chủ | Gợi ý người (theo git log, đổi tùy ý) |
|---|---|---|---|
| **A: Lõi nghiệp vụ công việc** | Dự án, công việc (Work Item), Workflow, xác nhận hoàn thành, Backlog/LexoRank, Sprint, Board, Dự án mẫu/nhân bản | Chương 2 (lời dẫn, tổng quan 2.0), thiết kế giao diện 3.3.5, khung kiểm thử đơn vị 4.4, triển khai CI/CD | Trần Xuân Sơn |
| **B: Nền tảng, bảo mật và thông báo** | Identity (đăng ký, đăng nhập thường + Google, xác thực email, quên mật khẩu), Keycloak, API Gateway, mô hình phân quyền, Notification (Outbox, Kafka, WebSocket, email) | Kiến trúc tổng thể 3.3.1, công nghệ 1.2, cài đặt 4.1, Phụ lục A (API) | Nguyễn Viết Tuấn Anh |
| **C: Tổ chức, tiến độ và đóng góp, biên tập** | Workspace, thành viên, lời mời, trang quản trị, hồ sơ cá nhân, **Quản lý tiến độ** (burndown, velocity, CFD, báo cáo sprint), **Quản lý đóng góp** (sổ ghi nhận đóng góp, snapshot theo sprint) | Bài toán và khảo sát 1.1, đặc tả chung 3.1.1–3.1.4, ERD tổng 3.3.3, khung kiểm thử hệ thống 4.3, demo 4.5, **ghép file, định dạng, mục lục, Kết luận, TLTK** | Nguyễn Hữu Tiến |

> C nhận thêm việc biên tập và ghép file, là việc tốn thời gian nhất ở tuần cuối. Vì vậy thiết kế giao diện (3.3.5) và khung kiểm thử đơn vị (4.4) chuyển sang A.
>
> Lưu ý ranh giới A–C: code tiến độ và đóng góp nằm trong work-service, dùng dữ liệu Sprint/Work Item/lịch sử trạng thái của A. C **chỉ mô tả cách tính và hiển thị báo cáo**. Mô hình Sprint, Work Item và cách ghi lịch sử do A viết, C trích dẫn chứ không viết lại.

---

## 3. Đề mục và người phụ trách

Ký hiệu: **A / B / C** là chủ mục. `A+B+C` nghĩa là mỗi người viết phần thuộc phân hệ của mình, chủ mục (in đậm) ghép lại.
Cột *Nguồn* là tài liệu có sẵn trong repo để lấy nội dung, đỡ phải viết lại từ đầu.

### CHƯƠNG 1. GIỚI THIỆU BÀI TOÁN VÀ CÔNG NGHỆ LIÊN QUAN (~10 trang)

| Mục | Nội dung | Chủ | Nguồn |
|---|---|---|---|
| 1.1.1 | Giới thiệu bài toán quản lý công việc nhóm | C | `formal_doc/00_MUC_LUC.md` |
| 1.1.2 | Khảo sát hệ thống tương tự (Jira, Trello, ClickUp…) và điểm khác biệt của đề tài | C | tự khảo sát |
| 1.1.3 | Mục tiêu và phạm vi (ghi rõ phần đã làm / phần định hướng: Community, Trợ lý AI) | C | `formal_doc/08_*`, `09_*`, `docs/services/ai_service/` |
| 1.1.4 | Đối tượng người dùng | C | |
| 1.2.1 | Kiến trúc Microservice, API Gateway | B | `docs/services/0_SERVICES_ARCHITECTURE.md`, `GATEWAY-DOC.md` |
| 1.2.2 | Frontend: React 19, Vite | C | `frontend/package.json` |
| 1.2.3 | Backend: Java 21, Spring Boot 3.5, Spring Cloud Gateway, OpenFeign, Spring Security | B | `backend/pom.xml` |
| 1.2.4 | Xác thực và ủy quyền: Keycloak, OAuth2/OIDC, JWT | B | `docs/NORMAL_LOGIN_FLOW.md`, `GOOGLE_LOGIN_FLOW.md` |
| 1.2.5 | Dữ liệu: PostgreSQL, Flyway, Redis, MinIO | B | `docs/DATABASE_DESIGN.md` |
| 1.2.6 | Bất đồng bộ và thời gian thực: Kafka, WebSocket/STOMP, SMTP | B | `docs/services/notification/2_*` |
| 1.2.7 | Container và CI/CD: Docker, Kubernetes, Helm, GitHub Actions/Jenkins | A | `docs/cicd/*` |

### CHƯƠNG 2. CÁC GIẢI PHÁP KỸ THUẬT TRỌNG TÂM (~20 trang)

> Tương ứng "Chương 2 – giải pháp chấm UML" của khung mẫu: đây là chương **thể hiện đóng góp kỹ thuật**. Mỗi giải pháp viết theo cùng một khuôn: *Bài toán → Thách thức → Giải pháp → Cài đặt chính → Đánh giá/hạn chế*.

| Mục | Nội dung | Chủ | Nguồn |
|---|---|---|---|
| 2.0 | Tổng quan các giải pháp, lý do chọn (½ trang, viết sau cùng) | **A** | |
| 2.1 | **Phân quyền đa tầng, mặc định từ chối (fail-closed)** | B | `docs/AUTHORIZATION.md`, `workspace_service/WORKSPACE_AUTHZ_REVIEW.md` |
| 2.1.1 | Bài toán và thách thức (Global role, Workspace, Project) | B | |
| 2.1.2 | Mô hình PermissionKey, ProjectRole, PermissionScheme, RoleAssignment | B | |
| 2.1.3 | Ba đường kiểm tra quyền: annotation + AOP, cấp workspace, quyền theo chủ sở hữu | B | `ProjectPermissionGuard` |
| 2.1.4 | Thống nhất định danh người dùng theo Keycloak `sub` | B | CLAUDE.md, `AUTHORIZATION.md` §5.2 |
| 2.2 | **Workflow và quy trình xác nhận hoàn thành** | A | `work_service/8_*`, `7_*` |
| 2.2.1 | Workflow engine: trạng thái, nhóm trạng thái, chuyển trạng thái | A | |
| 2.2.2 | Cơ chế "tạm hoàn thành" và duyệt (WorkItemCompletionGate) | A | |
| 2.2.3 | Sắp xếp backlog bằng LexoRank | A | |
| 2.2.4 | Sprint và Board: vòng đời sprint, cột board ánh xạ trạng thái | A | `work_service/7_*`, `service/board/*` |
| 2.3 | **Thông báo hướng sự kiện** | B | `docs/services/notification/*` |
| 2.3.1 | Bài toán: không mất sự kiện, không gửi trùng | B | |
| 2.3.2 | Transactional Outbox, Kafka, Dead Letter Topic | B | |
| 2.3.3 | Xác định người nhận, danh mục nội dung, cài đặt kênh và tắt tiếng | B | `1_DANH_MUC_LOAI_THONG_BAO.md` |
| 2.3.4 | Đẩy realtime qua WebSocket, chạy nhiều instance (Redis Pub/Sub, khóa job) | B | |
| 2.4 | **Dự án mẫu và nhân bản dự án** | A | `formal_doc/04_*`, `work_service/template/` |
| 2.4.1 | Bài toán sao chép sâu: ánh xạ ID, dời ngày | A | |
| 2.4.2 | Blueprint, Loader, ProjectCloneEngine, TemplateIntegrityValidator | A | |
| 2.5 | **Quản lý tiến độ và đóng góp** | C | |
| 2.5.1 | Bài toán: đo tiến độ sprint/dự án và ghi nhận công sức từng thành viên | C | `formal_doc/02_*` (phần Báo cáo Agile) |
| 2.5.2 | Báo cáo tiến độ: burndown (có/không tính cuối tuần), velocity, CFD | C | `AgileReportServiceImpl`, `BurndownCalculator` |
| 2.5.3 | Dựng lại trạng thái theo lịch sử và chụp snapshot báo cáo sprint | C | `WorkItemStateReplayer`, `SprintReportSnapshotEngine` |
| 2.5.4 | Ghi nhận đóng góp: sổ ghi nhận (ledger), snapshot đóng góp theo sprint, báo cáo theo thành viên | C | `ContributionReportServiceImpl`, migration V9, `4_CONTRIBUTION_PRESENTATION.html` |
| 2.6 | Đánh giá thực nghiệm các giải pháp | **A**, B+C góp | `docs/scenario_test/*` |

### CHƯƠNG 3. ĐẶC TẢ, PHÂN TÍCH, THIẾT KẾ HỆ THỐNG (~25 trang)

| Mục | Nội dung | Chủ | Ghi chú |
|---|---|---|---|
| 3.1.1 | Yêu cầu chức năng (bảng tổng hợp) | **C**, A+B gửi dòng của mình | |
| 3.1.2 | Yêu cầu phi chức năng | **C**, B góp phần bảo mật/hiệu năng | |
| 3.1.3 | Actor | **C** | **Chốt tuần 1** |
| 3.1.4 | Sơ đồ use case tổng quát | **C** | **Chốt tuần 1** |
| 3.1.5 | Use case chi tiết theo phân hệ | A+B+C | xem bảng phân hệ bên dưới |
| 3.2.1 | Kịch bản chính | A+B+C | |
| 3.2.2 | Phân tích luồng nghiệp vụ (tuần tự, hoạt động) | A+B+C | `work_service/3_SO_DO_TUAN_TU_NGHIEP_VU.md` |
| 3.3.1 | Kiến trúc tổng thể (sơ đồ service, gateway, Kafka, DB) | **B** | **Chốt tuần 1** |
| 3.3.2 | Thiết kế lớp theo service | A (work, phần lõi), B (identity, gateway, notification), C (workspace + gói báo cáo/đóng góp của work) | |
| 3.3.3 | Thiết kế CSDL: ERD tổng + bảng theo cụm | **C** ghép, A+B+C viết bảng của mình | `docs/diagrams/database-erd.dbml`, `formal_doc/01_*` |
| 3.3.4 | Thiết kế API và định tuyến Gateway | **B** | `docs/API_DOCUMENTATION.md` |
| 3.3.5 | Thiết kế giao diện | **A** | `docs/design/*`, `work_service/5_*` |

**Phân hệ trong 3.1.5 / 3.2 / 3.3.2 / 3.3.3:**

| Phân hệ | Chủ | Tiền tố mã UC |
|---|---|---|
| Tài khoản và xác thực (đăng ký, xác thực email, đăng nhập thường + Google, quên mật khẩu) | B | `UC-AUTH-xx` |
| Thông báo (chuông, trang thông báo, cài đặt, email) | B | `UC-NTF-xx` |
| Workspace, thành viên, lời mời, quản trị hệ thống, hồ sơ | C | `UC-WS-xx`, `UC-ADM-xx` |
| Dự án, phân quyền dự án, Workflow | A | `UC-PRJ-xx` |
| Công việc, bình luận, đính kèm, giao việc, xác nhận hoàn thành | A | `UC-WI-xx` |
| Backlog, Sprint, Board | A | `UC-SPR-xx` |
| Dự án mẫu, nhân bản | A | `UC-TPL-xx` |
| Báo cáo tiến độ (burndown, velocity, CFD, báo cáo sprint) | C | `UC-RPT-xx` |
| Quản lý đóng góp thành viên | C | `UC-CTB-xx` |

### CHƯƠNG 4. CÀI ĐẶT, KIỂM THỬ VÀ ĐÁNH GIÁ (~20 trang)

| Mục | Nội dung | Chủ | Nguồn |
|---|---|---|---|
| 4.1.1 | Môi trường và công cụ phát triển | B | CLAUDE.md |
| 4.1.2 | Cài hạ tầng bằng Docker Compose | B | `infrastructure/docker-setup.md`, `keycloak-setup.md` |
| 4.1.3 | Chạy backend và frontend | B | `start-dev.ps1` |
| 4.2 | Triển khai CI/CD lên Kubernetes | A | `docs/cicd/*` |
| 4.3.1–4.3.2 | Kiểm thử hệ thống: môi trường, chức năng được kiểm thử | **C** | |
| 4.3.3 | Test case hệ thống | A+B+C (mỗi người ~5 TC của phân hệ mình) | `docs/scenario_test/*`, `tests/e2e` |
| 4.4.1–4.4.2 | Kiểm thử đơn vị: đơn vị được chọn, phương pháp (JUnit 5, Mockito, AssertJ) | **A** | |
| 4.4.3–4.4.4 | Test case chính, kịch bản mẫu | A+B+C (lấy từ `src/test` phân hệ mình) | ~60 class test trong 4 service |
| 4.4.5–4.4.6 | Kết quả và độ phủ (JaCoCo) | **A**, B chạy hộ (máy có Docker build) | |
| 4.5 | Demo giao diện | **C** ghép, A+B+C mỗi người tự chụp và chú thích màn hình phân hệ mình | `frontend/src/pages/*` (C: `ProjectReportsPage`, trang đóng góp, workspace) |
| 4.6 | Đánh giá giao diện và tính năng | **C** | |

### PHẦN CUỐI

| Mục | Chủ |
|---|---|
| Lời cảm ơn, Danh mục viết tắt / bảng / hình, Mục lục | C |
| Kết luận (kết quả, hạn chế, hướng phát triển: Community, Trợ lý AI…) | **C**, mỗi người gửi 3–5 gạch đầu dòng |
| Tài liệu tham khảo | C (gộp từ sổ đăng ký §5) |
| Phụ lục A: Tài liệu API | B |
| Phụ lục B: Danh mục 53 loại thông báo | B |
| Phụ lục C: Phân chia công việc các thành viên | C |

### Ước lượng khối lượng

| Vai | Ch.1 | Ch.2 | Ch.3 | Ch.4 | Phần cuối, biên tập | Tổng (trang) |
|---|---|---|---|---|---|---|
| A | 1 | 8 | 9 | 8 | 0 | ~26 |
| B | 6 | 8 | 8 | 4 | 3 | ~29 |
| C | 4 | 5 | 9 | 5 | 5 + ghép | ~28 + ghép |

> Nếu B quá tải: chuyển 1.2.5–1.2.6 sang A, hoặc chuyển 4.1 sang C.

---

## 4. Hợp đồng chung (chốt trong tuần 1, sau đó không đổi đơn phương)

| Thứ cần chốt | Người làm | Ai dùng | Hạn |
|---|---|---|---|
| Danh sách actor + mô tả 1 dòng | C | A, B | Tuần 1 |
| Danh sách mã UC (chỉ tên và mã, chưa cần đặc tả) | A, B, C tự liệt kê phân hệ mình, C gộp | cả 3 | Tuần 1 |
| Sơ đồ kiến trúc tổng thể (1 hình) | B | cả 3 (A, C trích dẫn, không vẽ lại) | Tuần 1 |
| Danh sách bảng DB theo cụm | A, B, C | C ghép ERD | Tuần 2 |
| Mô tả ngắn mô hình Sprint, nhóm trạng thái, lịch sử thay đổi Work Item (½ trang + sơ đồ lớp) | A | C (viết 2.5 dựa trên đây) | Tuần 2 |
| Bảng thuật ngữ, cách viết (Work Item = "công việc", Sprint giữ nguyên…) | C | cả 3 | Tuần 1, dựa trên `docs/services/language/1_*` |

Muốn đổi thứ đã chốt: nhắn nhóm, chủ của thứ đó sửa, những người còn lại cập nhật phần mình.

---

## 5. Quy ước để ghép không vỡ

**Thư mục**

```
docs/report/
├── Khung_DATN.docx            # bản ghép, CHỈ C sửa ở buổi ghép
├── PHAN_CONG_VIET_BAO_CAO.md  # file này
├── parts/
│   ├── A_noi_dung.docx        # chỉ A sửa
│   ├── B_noi_dung.docx        # chỉ B sửa
│   └── C_noi_dung.docx        # chỉ C sửa
├── diagrams/
│   ├── A/  B/  C/             # mã nguồn sơ đồ (.puml / .drawio) + ảnh .png xuất ra
└── shared/
    ├── tai_lieu_tham_khao.md  # sổ đăng ký TLTK, chỉ thêm dòng
    └── viet_tat.md            # sổ đăng ký từ viết tắt, chỉ thêm dòng
```

**Định dạng trong file riêng**: copy style từ `Khung_DATN.docx` (Heading 1/2/3, Normal, Caption) để lúc ghép không phải chỉnh lại. Đặt tiêu đề mục **đúng số mục trong §3** để C biết dán vào đâu.

**Hình và bảng**: dùng *Insert Caption* của Word (đánh số tự động), không gõ tay "Hình 3.5". Khi tham chiếu trong bài, tạm ghi `[Hình: A-kien-truc-workflow]`. Lúc ghép, C đổi thành cross-reference.

**Sơ đồ**: thống nhất một công cụ (đề xuất PlantUML, hoặc draw.io), cùng font, cùng màu. Lưu cả file nguồn để sửa được. Sơ đồ dùng chung (kiến trúc, actor, ERD) chỉ chủ của nó vẽ, người khác chèn lại.

**Tài liệu tham khảo**: ai trích nguồn nào thì thêm 1 dòng vào `shared/tai_lieu_tham_khao.md` dạng `[key] Tác giả, Tên, Năm, URL`. Trong bài ghi `[key]`, C đánh số lại khi ghép. File markdown chỉ thêm dòng nên nếu Git báo xung đột thì giữ cả hai bên.

**Mã định danh**: UC `UC-<PHÂN HỆ>-<số>`, test case `TC-<PHÂN HỆ>-<số>`, yêu cầu `FR-<PHÂN HỆ>-<số>` / `NFR-<số>`. Không ai đánh số trùng tiền tố của người khác.

**Git**: mỗi người commit file của mình, commit message tiếng Việt, ví dụ `docs(report): A viết 2.2 workflow`. Nếu nhóm thích viết trên Google Docs/Word Online thì vẫn giữ nguyên tắc 1 người 1 file, chỉ bỏ phần Git.

**Nội dung phải khớp code**: chỉ viết tính năng đã có code. Phần chỉ có trong docs (Community, Trợ lý AI, integration-notification) đưa vào *Hướng phát triển*, không mô tả như đã làm.

---

## 6. Lịch làm việc (5 tuần, đổi theo hạn nộp thật)

| Tuần | A | B | C |
|---|---|---|---|
| 1: Chốt khung | Liệt kê UC phân hệ mình, dàn ý Ch.2 | Vẽ kiến trúc tổng thể, liệt kê UC | Actor, UC tổng quát, bảng thuật ngữ, tạo thư mục và 3 file `parts/` |
| 2: Viết | 2.2, 2.4 | 1.2, 2.1 | 1.1, 1.2.2, 3.1.1–3.1.4 |
| 3: Viết | Ch.3 phân hệ A, 1.2.7, 3.3.5 | 2.3, Ch.3 phân hệ B, 3.3.1, 3.3.4 | 2.5, Ch.3 phân hệ C, ERD |
| 4: Viết và review chéo | 4.2, 4.4 khung + JaCoCo, TC phân hệ A, 2.0, 2.6. Review phần **B** | 4.1, TC + unit test phân hệ B, Phụ lục A/B. Review phần **C** | 4.3, 4.5, 4.6, TC + unit test phân hệ C. Review phần **A** |
| 5: Ghép | Sửa theo góp ý | Sửa theo góp ý | Ghép vào `Khung_DATN.docx`, đánh số hình/bảng, mục lục, TLTK, Kết luận, Phụ lục C |

**Review chéo theo vòng A → B → C → A**: người review kiểm tra (1) có khớp code không, (2) thuật ngữ đúng bảng chung chưa, (3) mã UC/TC đúng quy ước chưa. Góp ý bằng comment trong file Word, không sửa thẳng.

**Họp ngắn 15 phút mỗi tuần**: mỗi người báo mục nào xong và vướng gì. Nếu cần đổi hợp đồng ở §4 thì chốt luôn trong buổi họp.

---

## 7. Checklist trước khi giao file cho C ghép

- [ ] Tiêu đề mục đúng số trong §3, dùng đúng style Heading.
- [ ] Mọi hình, bảng đều có caption Word, nguồn sơ đồ đã đẩy lên `diagrams/<vai>/`.
- [ ] Không vẽ lại sơ đồ chung, chỉ chèn bản của chủ.
- [ ] Thuật ngữ theo bảng chung, không lẫn Anh–Việt tùy tiện.
- [ ] Nguồn trích dẫn đã thêm vào `shared/tai_lieu_tham_khao.md`.
- [ ] Không mô tả tính năng chưa có code như đã hoàn thành.
- [ ] Đã qua review chéo.
