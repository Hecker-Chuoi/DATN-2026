# 9. Kiểm định Phân quyền (Authorization Audit) & Kế hoạch chuẩn hóa theo mô hình Jira

> Ngày rà soát: 09/09/2026. Phạm vi: `identity-service`, `api-gateway`, `work-service`, `frontend`, tài liệu thiết kế `Keycloak (author + authen).docx`.

## Trạng thái triển khai (cập nhật 09/09/2026)

| Giai đoạn | Trạng thái | Ghi chú |
|---|---|---|
| 0 – Hotfix lộ dữ liệu | ✅ Hoàn thành | C1–C6 đã sửa; `GET /projects` lọc theo thành viên; guard fail-closed. |
| 1 – Thống nhất backend | ✅ Hoàn thành | Bỏ `ProjectSecurity`/`role_permissions`; `ProjectAccessService`, `TeamSecurity`; user id = Keycloak `sub`; `PermissionKey` 15 quyền; Flyway `V2__authz_hardening.sql` (chuẩn hóa id + seed quyền mới + drop bảng cũ). |
| 2 – Frontend | ✅ Hoàn thành | `PermissionProvider` mount ở `App.jsx`; hook `useProjectPermission` gọi `permissions/my`; trang 403/404 (`AccessDenied`); tab Members dùng tìm kiếm user; modal issue ẩn/hiện theo quyền; admin portal dùng admin token theo đường dẫn trang. |
| 3 – Kiểm thử & tài liệu | 🟡 Một phần | Unit test backend đã cập nhật/bổ sung (outsider, viewer, OWN/ALL, role khác dự án, lọc danh sách). Còn lại: integration test MockMvc + JWT, cập nhật file `.docx`. |

**Việc cần làm thủ công sau khi deploy:**
1. Vào Keycloak Admin Console → Users → gỡ realm role `ADMIN` khỏi các tài khoản test đã đăng ký trước đây (hoặc dùng `PUT /identity/api/v1/admin/users/{id}/roles`). Chỉ giữ ADMIN cho tài khoản quản trị thật.
2. Khởi động lại `work-service` để Flyway chạy `V2__authz_hardening.sql` (chuẩn hóa `user_id`, bổ sung quyền, gán role cho thành viên team/lead chưa có).
3. Đăng nhập lại trên frontend để token mới không còn role ADMIN.

---

## 0. Kết luận nhanh

Hệ thống **đã có** nền tảng phân quyền đúng hướng Jira (vai trò theo từng dự án `project_roles` + ma trận `permission_scheme_entries` + annotation `@RequireProjectPermission` / `@RequireTaskPermission`). Tuy nhiên hiện tại **phân quyền chưa thực sự có hiệu lực** vì 4 nguyên nhân gốc:

| # | Nguyên nhân gốc | Hệ quả người dùng nhìn thấy |
|---|---|---|
| R1 | Mọi tài khoản đăng ký mới được gán realm role **ADMIN** trong Keycloak (`UserService.register`) | Mọi user là Global Admin → guard bypass toàn bộ. Đây rất có thể là lý do "user B không trong dự án vẫn vào xem được". |
| R2 | `hasPermission()` trả `true` cho `PROJECT_VIEW` với **bất kỳ user đã đăng nhập** không có role trong dự án | Kể cả khi không phải admin, ai cũng xem được mọi dự án, board, issue, comment, member. |
| R3 | `GET /projects` không lọc theo thành viên | Trang Projects liệt kê **toàn bộ** dự án trong hệ thống. |
| R4 | Frontend: `PermissionProvider` chưa được mount, hook `useProjectPermission` bị dùng sai kiểu, API `permissions/my` không được gọi | UI không ẩn/hiện theo quyền: chủ dự án không thấy nút "Thêm thành viên", nhưng người ngoài lại thấy nút "Tạo công việc", "Xóa comment"... |

Ngoài ra còn tồn tại **hai mô hình phân quyền chạy song song** (mô hình "team_members + role_permissions" theo docx và mô hình "project_roles + permission_scheme_entries" kiểu Jira) với bộ mã quyền không khớp nhau, và **định danh user không nhất quán** (DB id vs Keycloak sub).

---

## 1. Đối chiếu thiết kế (docx) và code hiện tại

| Khía cạnh | Thiết kế trong docx | Code hiện tại | Nhận xét |
|---|---|---|---|
| Global role | `ADMIN`, `USER` (Keycloak realm role) | Có trong `realm-export.json`; `SecurityUtils.isGlobalAdmin()` đọc `realm_access.roles` | Đúng thiết kế, nhưng R1 làm mọi người thành ADMIN. |
| Project role | `OWNER / MEMBER / OBSERVER` lưu ở `team_members.role_in_team` | **Hai bộ**: `TeamRole {LEADER, MEMBER, VIEWER}` (team) và `project_roles {ADMINISTRATOR, MEMBER, VIEWER}` (per project) | Code đi theo hướng Jira (role per project) hợp lý hơn docx. Cần cập nhật docx cho khớp. |
| Mã quyền | 15 mã: `PROJECT_*`, `MEMBER_*`, `TASK_*`, `COMMENT_CREATE/DELETE_OWN/DELETE_ALL` | `PermissionKey` 10 mã: `PROJECT_ADMIN, PROJECT_VIEW, ISSUE_*, SPRINT_MANAGE, RELEASE_MANAGE, COMMENT_CREATE` | Thiếu `COMMENT_DELETE_OWN/ALL`, `MEMBER_*`; `ISSUE_ASSIGN`, `SPRINT_MANAGE`, `RELEASE_MANAGE` khai báo nhưng chưa enforce ở endpoint nào. |
| Bảng lưu ma trận | `role_permissions (role_name, permission_code)` tĩnh toàn hệ thống | Cả `role_permissions` (dùng bởi `ProjectSecurity`) **và** `permission_scheme_entries` (dùng bởi `ProjectMemberServiceImpl`) | Trùng lặp. `init_rbac_schema.sql` không được mount vào Docker, Flyway `V1` cũng không tạo → bảng `role_permissions` do Hibernate tạo **rỗng**. |
| Cơ chế check | Tầng 1 RBAC (ADMIN? ACTIVE?) → Tầng 2 scope (thuộc team? role_in_team?) | `ProjectPermissionGuard` (AOP) → `ProjectMemberService.hasPermission` | Đúng tinh thần 2 tầng. Thiếu kiểm tra `ACTIVE`/`deletedAt` ở vài chỗ. |
| FE ẩn/hiện | `/users/me` trả `projectPermissions{projectId: [...]}` + `useProjectPermission(projectId)` + `<Can>` | Có `PermissionContext`, `useProjectPermission`, `Can`, endpoint `GET /projects/{id}/permissions/my` | Đã có khung nhưng **chưa nối dây** (xem mục 2.3). |

Kết luận: giữ mô hình Jira trong code làm chuẩn, bỏ mô hình `role_permissions`/`ProjectSecurity`, và cập nhật lại docx.

---

## 2. Danh sách lỗi phát hiện

Mức độ: 🔴 Nghiêm trọng (lộ dữ liệu / leo thang quyền), 🟠 Trung bình (sai logic, dễ gây lỗi), 🟡 Nhẹ (UX / code smell).

### 2.1 Backend – 🔴 Nghiêm trọng

| ID | Mô tả | Vị trí |
|---|---|---|
| C1 | Đăng ký user mới gán realm role **ADMIN** (comment `//DEFAULT_ROLES` cho thấy là hack dev để quên). DB lưu `USER` nhưng JWT có `ADMIN` → FE tưởng là user thường, BE bypass mọi guard. | `identity-service/.../service/UserService.java:67` |
| C2 | User không có `role_assignments` trong dự án vẫn được cấp `PROJECT_VIEW` ("Allow general view for any authenticated user"). `getMyPermissions` cũng trả `[PROJECT_VIEW]` cho người ngoài. Unit test hiện tại **khẳng định** hành vi sai này (`testOutsiderHasNoPermissions` assert `PROJECT_VIEW == true`). | `work-service/.../service/impl/ProjectMemberServiceImpl.java:60-63, 98-100`; `src/test/.../security/ProjectPermissionGuardTest.java:132` |
| C3 | `GET /api/v1/work/projects` trả về mọi dự án (chỉ lọc workspace/team/search), không lọc theo user. | `ProjectController.java:49-61`, `ProjectServiceImpl.getAllProjects:114-137`, `ProjectSpecification.java` |
| C4 | Endpoint đọc/ghi **không có guard dự án** (chỉ cần đăng nhập): <br>• `GET /projects/key/{key}` <br>• `GET /items/code/{code}` <br>• `GET /attachments/{id}/download` (tải file của bất kỳ dự án nào) <br>• `GET /items/{id}/changelogs` <br>• `POST /statuses` (tạo status toàn cục) <br>• `GET /workflows/{id}` <br>• `POST /workflows/{id}/transitions` và `DELETE /workflows/{id}/transitions/{tid}` (**sửa workflow của dự án khác**) | `ProjectController.java:81`, `WorkItemController.java:75`, `WorkItemAttachmentController.java:69`, `WorkItemChangelogController.java:22`, `WorkflowController.java:89, 101, 112, 125` |
| C5 | Team endpoints mở cho mọi user: `GET /teams` (toàn bộ team hệ thống), `GET /teams/{id}`, `GET /teams/{id}/members` (đọc danh sách thành viên team bất kỳ, kèm side-effect ghi DB trong GET). | `TeamController.java:50-54, 79-84, 206-222` |
| C6 | `POST /projects` nhận `teamId`, `leadUserId`, `workspaceId` tùy ý không kiểm tra người tạo có thuộc team đó không → user B tạo project gắn vào team của A và **tự động kéo toàn bộ thành viên team A vào**. `PUT /projects/{id}` cũng cho đổi `teamId`/`leadUserId` tự do; `leadUserId` được bypass toàn quyền. | `ProjectServiceImpl.java:69-85, 158-169, 423-444` |

### 2.2 Backend – 🟠 Trung bình

| ID | Mô tả | Vị trí |
|---|---|---|
| M1 | **Hai mô hình song song.** `ProjectSecurity.hasTeamPermission` tra `role_permissions` với `role_name = TeamRole` (`LEADER/MEMBER/VIEWER`) trong khi SQL seed dùng `ADMIN/OWNER/MEMBER/OBSERVER` (và bảng thực tế rỗng vì SQL không được chạy). Hệ quả: `@PreAuthorize("@projectSecurity.hasTeamPermission(#teamId,'MEMBER_INVITE')")` chặn **LEADER không phải owner** thêm/đổi role thành viên (403) dù logic bên trong cho phép. | `security/ProjectSecurity.java:102-122`, `TeamController.java:292, 403`, `environment/database/init_rbac_schema.sql`, `docker-compose*.yml` (không mount) |
| M2 | **Định danh user không nhất quán.** JWT `sub` = Keycloak ID; realm export **không có protocol mapper** cho attribute `user_id` nên `SecurityUtils.getCurrentUserId()` luôn dùng `sub`. Team add-member đã canonical hóa sang Keycloak ID, nhưng tab "Members" của project gửi `u.id` (DB id từ `userApi.listAll`) → `role_assignments.user_id` = DB id → khi user đó đăng nhập, `hasPermission(projectId, sub)` không tìm thấy assignment → chỉ được view (sau khi fix C2 sẽ bị chặn hoàn toàn). `TeamController` phải chống chế bằng `getUserAliasIds()` gọi identity nhiều lần mỗi request. | `security/SecurityUtils.java:30-50`, `TeamController.java:343-348, 575-594`, `ProjectMembersTab.jsx:150-175`, `identity-service/.../UserResponse.java:19` |
| M3 | Guard **fail-open**: nếu không resolve được `projectId`/`taskId` từ tham số thì bỏ qua check. Fallback "UUID đầu tiên trong tham số" có thể check nhầm resource nếu sau này thêm endpoint có UUID khác đứng trước. Lỗi bất ngờ trong guard bị bọc thành `RuntimeException` → 500 thay vì 403. | `security/ProjectPermissionGuard.java:63-64, 80-81, 139-144, 97-99` |
| M4 | Bộ quyền chưa đủ hạt để làm Jira-like: sửa/xóa comment dùng `COMMENT_CREATE`; xóa attachment dùng `ISSUE_EDIT` (member xóa được file của người khác, admin **không** xóa được comment người khác); `ISSUE_ASSIGN` không enforce khi đổi assignee; `SPRINT_MANAGE/RELEASE_MANAGE` chưa dùng. Lỗi "không phải chủ comment" ném `UnauthorizedException` → HTTP **401** (FE hiểu nhầm là hết hạn token và thử refresh). | `WorkItemCommentController.java:54, 67`, `WorkItemCommentServiceImpl.java:159, 199`, `WorkItemAttachmentController.java:57`, `WorkItemServiceImpl.java:273` |
| M5 | Quản lý thành viên dự án thiếu ràng buộc: `addMemberToProject`/`updateMemberRole` không kiểm tra `role.projectId == projectId` (gán role của dự án khác); `removeMemberFromProject` chỉ bảo vệ `leadUserId`, không chặn xóa `createdBy`/admin cuối cùng/chính mình; `GET /members` có side-effect ghi DB (`syncTeamMembersToProject`). | `ProjectMemberServiceImpl.java:122-126, 214-215, 260-261, 286-296` |
| M6 | `GET /projects/{id}/permissions/my` không có guard, không kiểm tra `deletedAt`, trả `PROJECT_VIEW` cho người ngoài. Nên trả thêm `role` để FE hiển thị. | `ProjectMemberController.java:31-36` |
| M7 | `identity-service` mở `/internal/**` không xác thực; gateway không route nhóm này nhưng port 8082 vẫn truy cập trực tiếp được trên máy. | `identity-service/.../config/SecurityConfig.java:33` |

### 2.3 Frontend

| ID | Mô tả | Vị trí |
|---|---|---|
| F1 🔴 | `PermissionProvider` **chưa được mount** ở `main.jsx`/`App.jsx`. `usePermission()` khi thiếu provider trả `hasPermission: () => true` → `<Can>` luôn cho phép. | `context/PermissionContext.jsx:35-45`, `App.jsx`, `main.jsx` |
| F2 🔴 | `useProjectPermission(projectId)` trả về **một function**, nhưng `ProjectDetailPage` destructure `{ isAdmin, canCreateIssue, canManageSprint, permissions }` → tất cả `undefined`. Hệ quả: chủ dự án **không thấy** nút "Thêm thành viên", đổi role, "Thêm bước chuyển"; trong khi nút "Tạo công việc" ở header không có guard nên **ai cũng thấy**. | `hooks/useProjectPermission.js:8-11`, `pages/ProjectDetailPage.jsx:43, 173-195, 279, 314, 320` |
| F3 🔴 | `projectApi.getMyPermissions` được khai báo nhưng **không nơi nào gọi**; `PermissionProvider` không bao giờ có dữ liệu `projectPermissions`. | `api.js:286`, toàn bộ `src/` |
| F4 🟠 | Không xử lý 403/404 ở cấp trang: `ProjectDetailPage.fetchProject` chỉ `console.warn`, vẫn render đủ tab với `project = null`; interceptor chỉ toast lỗi. Không có trang "Bạn không có quyền truy cập" hay redirect về `/projects`. | `pages/ProjectDetailPage.jsx:45-63`, `api.js:210-215` |
| F5 🟠 | `ProjectMembersTab` lấy danh sách user bằng `userApi.listAll()` = endpoint `/admin/users` (yêu cầu ADMIN, và interceptor gắn **admin token**) → chủ dự án thường không load được danh sách để thêm thành viên. Prop `project` không được truyền từ `ProjectDetailPage` → `isLead` luôn `false`. | `components/project/ProjectMembersTab.jsx:150, 199, 328`, `api.js:101-105` |
| F6 🟠 | `WorkItemDetailModal`: nút "Chỉnh sửa"/"Xóa" comment và "Xóa tệp" hiển thị cho mọi người, không so `authorId`/`uploadedBy` với user hiện tại và không theo quyền → bấm rồi nhận toast lỗi. Dropdown trạng thái, sửa summary/description cũng không theo quyền. | `components/items/WorkItemDetailModal.jsx:1307-1321, 1565-1577` |
| F7 🟡 | Admin portal (`/admin`) gọi work-service (`teamApi.listAll`, `projectApi.listAll`) bằng **user token** (interceptor chỉ coi `/identity/api/v1/admin/` là admin route) → admin chưa đăng nhập portal user sẽ bị 401. | `api.js:101-105`, `components/admin/AdminProjectManagement.jsx:70, 101, 129` |
| F8 🟡 | `TeamDetailPage` tự tính `isCurrentUserLeader/Owner` bằng so sánh id (phải so cả `id` lẫn `keycloakId`) – hệ quả của M2; khi có contract `permissions/my` nên dùng chung cơ chế. | `pages/TeamDetailPage.jsx:66-91` |

---

## 3. Mô hình phân quyền mục tiêu (Jira-like)

### 3.1 Nguyên tắc

1. **Deny by default**: mọi endpoint có `projectId`/`itemId`/`teamId` đều phải qua guard; không resolve được id ⇒ 403.
2. **Membership là điều kiện tiên quyết**: không có `role_assignments` trong dự án ⇒ không có quyền nào, kể cả xem (trừ Global ADMIN).
3. **Một nguồn sự thật**: `project_roles` + `permission_scheme_entries` + `role_assignments`. Bỏ `role_permissions`, `permissions`, `ProjectSecurity`.
4. **Một định danh user**: Keycloak `sub` ở mọi bảng của work-service (`role_assignments.user_id`, `team_members.user_id`, `created_by`, `assignee_id`, `author_id`...). Identity trả `keycloakId` và FE gửi `keycloakId` khi thêm thành viên.
5. **FE chỉ ẩn/hiện, BE quyết định**: FE tải `permissions/my` một lần khi mở dự án; mọi nút bấm bọc `<Can>`; 403 từ BE được xử lý thành trang/thông báo rõ ràng.

### 3.2 Vai trò

| Phạm vi | Vai trò | Nguồn |
|---|---|---|
| Hệ thống | `ADMIN` (bypass mọi check, chỉ gán thủ công), `USER` (mặc định khi đăng ký) | Keycloak realm role |
| Team | `LEADER`, `MEMBER`, `VIEWER` | `team_members.role` |
| Dự án | `ADMINISTRATOR`, `MEMBER`, `VIEWER` (mặc định khi seed; có thể thêm role tùy chỉnh sau) + `leadUserId` (luôn là ADMINISTRATOR) | `project_roles`, `role_assignments` |

Đồng bộ Team → Project: `LEADER→ADMINISTRATOR`, `MEMBER→MEMBER`, `VIEWER→VIEWER` (đã có trong `syncMemberToTeamProjects`, giữ nguyên nhưng chuyển vào service và chỉ chạy khi có sự kiện thay đổi, không chạy trong GET).

### 3.3 Bộ quyền đề xuất (mở rộng `PermissionKey`, giữ tên cũ để giảm thay đổi)

| Nhóm | Key | ADMINISTRATOR | MEMBER | VIEWER | Enforce tại |
|---|---|:---:|:---:|:---:|---|
| Dự án | `PROJECT_VIEW` | ✓ | ✓ | ✓ | Mọi GET trong dự án, `GET /projects/{id}`, `/key/{key}`, `/members`, `/roles`, workflow/statuses GET, changelogs, download attachment |
| | `PROJECT_ADMIN` | ✓ | | | `PUT/DELETE /projects/{id}`, `restore`, thêm/xóa/đổi role thành viên, `PUT workflow/scheme` |
| | `WORKFLOW_MANAGE` (mới) | ✓ | | | `POST/DELETE /workflows/{id}/transitions`, `POST /statuses` (hoặc gộp vào `PROJECT_ADMIN`) |
| Issue | `ISSUE_CREATE` | ✓ | ✓ | | `POST /projects/{id}/items`, `POST /items/{id}/subtasks` |
| | `ISSUE_EDIT` | ✓ | ✓ | | `PUT /items/{id}`, `PATCH rank`, links, upload attachment |
| | `ISSUE_ASSIGN` | ✓ | ✓ | | Khi `assigneeId` thay đổi trong `updateWorkItem`/`createWorkItem` |
| | `ISSUE_TRANSITION` | ✓ | ✓ | | `POST /work-items/{id}/transitions` |
| | `ISSUE_DELETE` | ✓ | | | `DELETE /items/{id}` |
| Sprint/Release | `SPRINT_MANAGE`, `RELEASE_MANAGE` | ✓ | | | Khi triển khai sprint/version APIs |
| Comment | `COMMENT_CREATE` | ✓ | ✓ | ✓* | `POST comments` (*Viewer có được comment hay không tùy quyết định; docx cho phép OBSERVER comment) |
| | `COMMENT_EDIT_OWN`, `COMMENT_DELETE_OWN` (mới) | ✓ | ✓ | ✓* | `PUT/DELETE comments/{cid}` khi `authorId == currentUser` |
| | `COMMENT_DELETE_ALL` (mới) | ✓ | | | `DELETE comments/{cid}` bất kỳ |
| Attachment | `ATTACHMENT_DELETE_OWN` (mới) | ✓ | ✓ | | `DELETE attachments/{aid}` khi `uploadedBy == currentUser` |
| | `ATTACHMENT_DELETE_ALL` (mới) | ✓ | | | `DELETE attachments/{aid}` bất kỳ |

Quy tắc "OWN vs ALL" xử lý trong service: `if (isOwner && has(OWN)) ok; else if (has(ALL)) ok; else 403` (ném `AccessDeniedException`, không phải `UnauthorizedException`).

### 3.4 Contract API cho frontend

```json
GET /api/v1/work/projects/{projectId}/permissions/my   (guard: thành viên hoặc ADMIN, ngược lại 403)
{
  "projectId": "…",
  "roleName": "MEMBER",
  "isLead": false,
  "isGlobalAdmin": false,
  "permissions": ["PROJECT_VIEW", "ISSUE_CREATE", "ISSUE_EDIT", "ISSUE_TRANSITION", "ISSUE_ASSIGN", "COMMENT_CREATE", "COMMENT_EDIT_OWN", "COMMENT_DELETE_OWN", "ATTACHMENT_DELETE_OWN"]
}

GET /api/v1/work/projects          → chỉ dự án user là thành viên (hoặc tất cả nếu ADMIN), thêm field "myRole"
GET /identity/api/v1/users/me      → giữ nguyên, bổ sung "globalRoles" lấy từ JWT realm_access (không từ DB) để FE biết có phải ADMIN
```

---

## 4. Kế hoạch triển khai

### Giai đoạn 0 – Hotfix lộ dữ liệu (≈ 1 ngày)

| Bước | Việc | File |
|---|---|---|
| 0.1 | Đổi `Set.of("ADMIN")` → `DEFAULT_ROLES`; vào Keycloak Admin Console gỡ role ADMIN khỏi các user test đã tạo (hoặc dùng `PUT /admin/users/{id}/roles`). | `identity-service/.../UserService.java:67` |
| 0.2 | `hasPermission`: khi `assignments.isEmpty()` → `return false`. `getMyPermissions`: người ngoài → `List.of()`. Sửa test `testOutsiderHasNoPermissions` assert `PROJECT_VIEW == false`. | `ProjectMemberServiceImpl.java:60-63, 98-100`; `ProjectPermissionGuardTest.java:132` |
| 0.3 | `getAllProjects`: thêm `currentUserId`, join `role_assignments` (Specification `root.get("id").in(subquery projectIds của user)`), ADMIN thì bỏ lọc. | `ProjectServiceImpl.java:114`, `ProjectSpecification.java`, `ProjectController.java:49`, `RoleAssignmentRepository` (thêm `findProjectIdsByUserId`) |
| 0.4 | Gắn guard cho các endpoint ở C4: `getProjectByKey` (resolve project rồi check `PROJECT_VIEW` trong service hoặc dùng `@PreAuthorize("@projectMemberService.hasPermissionByKey(#key, 'PROJECT_VIEW')")`), `getWorkItemByCode`, `downloadAttachment` (tra `attachment → workItem → project`), `getChangelogs` (`@RequireTaskPermission(PROJECT_VIEW)`), `createStatus`/`getWorkflowById`/`createTransition`/`deleteTransition` (tra `workflow.projectId` → `PROJECT_ADMIN`). | Các controller nêu ở C4 |
| 0.5 | Team: `GET /teams` chỉ ADMIN; `GET /teams/{id}` và `/members` yêu cầu là thành viên/owner; bỏ side-effect ghi DB trong GET (chuyển vào `createTeam`). | `TeamController.java:50, 79, 206` |
| 0.6 | `createProject`: nếu `teamId != null` phải là thành viên team đó (hoặc ADMIN); `leadUserId` mặc định = người tạo, nếu khác phải là thành viên team; `updateProject` tương tự. | `ProjectServiceImpl.java:69-85, 158-169` |
| 0.7 | FE: sửa `useProjectPermission` (xem 2.1 bên dưới) để chủ dự án thấy lại các nút admin. | `hooks/useProjectPermission.js`, `pages/ProjectDetailPage.jsx:43` |

Tiêu chí nghiệm thu: user B (không ADMIN, không thuộc dự án) gọi `GET /projects/{id}` → 403; `GET /projects` không thấy dự án của A; A vẫn thấy đủ nút quản trị.

### Giai đoạn 1 – Thống nhất mô hình backend (≈ 2–3 ngày)

| Bước | Việc | File |
|---|---|---|
| 1.1 | Xóa `ProjectSecurity`, `RolePermission`, `Permission`, `RolePermissionRepository`, `init_rbac_schema.sql`; thay 2 `@PreAuthorize("@projectSecurity.hasTeamPermission…")` bằng `@PreAuthorize("@teamSecurity.isLeader(#teamId)")` (bean mới, kiểm tra owner hoặc `team_members.role == LEADER`). Chuyển toàn bộ logic trong `TeamController` xuống `TeamService`. | `security/ProjectSecurity.java`, `TeamController.java` |
| 1.2 | Chuẩn hóa user id: (a) tất cả nơi ghi `user_id` trong work-service dùng `SecurityUtils.getCurrentUserId()` (bỏ `extractUserId`/`resolveUserId` riêng lẻ, bỏ fallback `0000…0001`); (b) `AssignProjectRoleRequest.userId` được service canonical hóa qua `identityClient.getUserById` → `keycloakId`; (c) `SecurityUtils` chỉ dùng `sub` (bỏ nhánh `user_id` claim) hoặc thêm mapper `user_id` vào realm và dùng thống nhất – **chọn một**; (d) script migration dữ liệu cũ: `UPDATE role_assignments/team_members SET user_id = keycloak_id WHERE user_id = db_id`. | `SecurityUtils.java`, `WorkItemController.java:138`, `WorkItem*Controller.java`, `ProjectMemberServiceImpl.addMemberToProject`, `TeamController.getUserAliasIds` (xóa) |
| 1.3 | Guard fail-closed: `projectId == null` hoặc `taskId == null` ⇒ `AccessDeniedException`; bỏ fallback "UUID đầu tiên"; cho phép chỉ định tên tham số qua thuộc tính annotation `@RequireProjectPermission(value=…, idParam="projectId")`. Exception khác trong guard → log + 403. | `security/ProjectPermissionGuard.java` |
| 1.4 | Mở rộng `PermissionKey` theo mục 3.3; cập nhật seed trong `seedProjectEcosystem`; viết Flyway `V2__extend_permission_scheme.sql` thêm entries cho dự án đã tồn tại. | `enums/PermissionKey.java`, `ProjectServiceImpl.java:370-408`, `db/migration/V2__*.sql` |
| 1.5 | Comment/Attachment OWN vs ALL trong service; đổi `UnauthorizedException` → `AccessDeniedException`; `ISSUE_ASSIGN` khi assignee đổi. | `WorkItemCommentServiceImpl.java`, `WorkItemAttachmentServiceImpl.java`, `WorkItemServiceImpl.java:273` |
| 1.6 | Ràng buộc quản lý thành viên: role phải thuộc dự án; không xóa/hạ cấp admin cuối cùng; không tự xóa mình (dùng API "leave"); bỏ `syncTeamMembersToProject` khỏi `getProjectMembers`, gọi khi team thay đổi (event/service). | `ProjectMemberServiceImpl.java` |
| 1.7 | `GET /permissions/my`: guard thành viên, trả `roleName/isLead/isGlobalAdmin/permissions`; `GET /projects` trả thêm `myRole`. | `ProjectMemberController.java`, DTO mới `MyProjectPermissionsResponse` |
| 1.8 | Kiểm tra trạng thái user: ADMIN xóa user → Keycloak `enabled=false` đã có; thêm `deleteRoleAssignmentsByUserId` để gỡ khỏi dự án (hiện chỉ gỡ team). | `identity-service/.../UserService.deleteUser` (gọi work-service hoặc event Kafka) |

### Giai đoạn 2 – Frontend nối dây phân quyền (≈ 2–3 ngày)

| Bước | Việc | File |
|---|---|---|
| 2.1 | Sửa hook: `useProjectPermission(projectId)` gọi `projectApi.getMyPermissions` (cache theo projectId trong `PermissionContext`), trả `{ loading, role, isAdmin, isLead, can(key), permissions, error }`. `isAdmin = isGlobalAdmin \|\| permissions.includes('PROJECT_ADMIN')`. | `hooks/useProjectPermission.js`, `context/PermissionContext.jsx` |
| 2.2 | Mount `PermissionProvider` trong `App.jsx` bao `WorkspaceLayout`; nạp `globalRoles` từ `/users/me`; bỏ fallback `() => true` (ngoài provider ⇒ `false`). | `App.jsx`, `context/PermissionContext.jsx:35-45` |
| 2.3 | `ProjectDetailPage`: bọc "Tạo công việc" bằng `<Can perform="ISSUE_CREATE">`; tab Members/Workflow nhận `isAdmin` thật; truyền `project` xuống `ProjectMembersTab`; khi `getById` trả 403/404 → render `<AccessDenied />` với nút quay lại `/projects` (không render tabs). | `pages/ProjectDetailPage.jsx` |
| 2.4 | Trang/route guard: component `ProjectGuard` (hoặc xử lý trong `useProjectPermission`) + trang `ForbiddenPage.jsx`; interceptor: 403 → không toast trùng nếu trang đã xử lý; 401 chỉ refresh khi thật sự hết hạn. | `components/common/ProjectGuard.jsx`, `pages/ForbiddenPage.jsx`, `api.js` |
| 2.5 | `ProjectMembersTab`: dùng `userApi.search` (autocomplete như `TeamDetailPage`) thay `listAll`; gửi `keycloakId`; ẩn select/xóa với admin cuối cùng. | `components/project/ProjectMembersTab.jsx` |
| 2.6 | `WorkItemDetailModal`: nhận `projectId` + `currentUser`; chỉ hiện "Chỉnh sửa/Xóa" comment khi `(authorId === me && can(COMMENT_EDIT_OWN/DELETE_OWN)) \|\| can(COMMENT_DELETE_ALL)`; tương tự attachment; ẩn sửa summary/description/priority/assignee/status nếu thiếu `ISSUE_EDIT`/`ISSUE_ASSIGN`/`ISSUE_TRANSITION`; ẩn ô nhập comment nếu thiếu `COMMENT_CREATE`. | `components/items/WorkItemDetailModal.jsx` |
| 2.7 | `ProjectBoardView`: `canCreate` từ hook; nếu thiếu `ISSUE_TRANSITION` thì tắt kéo thả (chỉ xem). | `components/boards/ProjectBoardView.jsx` |
| 2.8 | `ProjectListPage`: hiển thị badge `myRole`; `Sidebar`/`CreateProjectModal` chỉ liệt kê team user thuộc (đã đúng). | `pages/ProjectListPage.jsx` |
| 2.9 | Admin portal: interceptor chọn admin token theo **đường dẫn trang** (`window.location.pathname.startsWith('/admin')`) thay vì theo URL API. | `api.js:101-105` |

### Giai đoạn 3 – Kiểm thử, dọn dẹp, tài liệu (≈ 1–2 ngày)

| Bước | Việc |
|---|---|
| 3.1 | Unit test ma trận quyền (mục 5) cho `ProjectMemberServiceImpl.hasPermission` và guard; test `getAllProjects` lọc theo thành viên. |
| 3.2 | Integration test (MockMvc + JWT giả) cho từng endpoint × vai trò: outsider 403, viewer chỉ GET, member không DELETE issue, admin đầy đủ, global ADMIN bypass. |
| 3.3 | Cập nhật `Keycloak (author + authen).docx` và `docs/services/work_service/2_WORK_DATABASE_DESIGN.md`: bỏ `role_permissions`, mô tả `project_roles/permission_scheme_entries`, bộ quyền mới, luồng FE. |
| 3.4 | Gỡ code chết: `backend/environment/database/init_rbac_schema.sql`, `Permission`, `RolePermission`, `getUserAliasIds`, `frontend/src/api/workServiceApi.js` (trùng `api.js`). |

---

## 5. Ma trận kiểm thử (dùng cho cả unit test và test tay)

Ký hiệu: ✓ = 200/201, ✗ = 403, `—` = không áp dụng. Vai trò: **O** = Outsider (đã đăng nhập, không thuộc dự án), **V** = Viewer, **M** = Member, **A** = Project Administrator/Lead, **G** = Global ADMIN.

| Endpoint | O | V | M | A | G |
|---|:-:|:-:|:-:|:-:|:-:|
| `GET /projects` (thấy dự án X trong danh sách) | ✗ | ✓ | ✓ | ✓ | ✓ |
| `GET /projects/{X}`, `/key/{key}`, `/members`, `/roles`, `/workflow*`, `/statuses` | ✗ | ✓ | ✓ | ✓ | ✓ |
| `GET /projects/{X}/permissions/my` | ✗ | ✓ | ✓ | ✓ | ✓ |
| `PUT/DELETE /projects/{X}`, `POST /members`, `PUT/DELETE /members/{u}`, `PUT /workflow/scheme` | ✗ | ✗ | ✗ | ✓ | ✓ |
| `POST/DELETE /workflows/{wf của X}/transitions`, `POST /statuses` | ✗ | ✗ | ✗ | ✓ | ✓ |
| `GET /projects/{X}/items`, `GET /items/{i}`, `/code/{c}`, `/subtasks`, `/comments`, `/attachments`, `/links`, `/changelogs`, `/transitions`, download | ✗ | ✓ | ✓ | ✓ | ✓ |
| `POST /projects/{X}/items`, `POST /items/{i}/subtasks` | ✗ | ✗ | ✓ | ✓ | ✓ |
| `PUT /items/{i}`, `PATCH rank`, `POST links`, `POST attachments` | ✗ | ✗ | ✓ | ✓ | ✓ |
| `POST /work-items/{i}/transitions` | ✗ | ✗ | ✓ | ✓ | ✓ |
| `DELETE /items/{i}` | ✗ | ✗ | ✗ | ✓ | ✓ |
| `POST comments` | ✗ | ✓* | ✓ | ✓ | ✓ |
| `PUT/DELETE comments/{own}` | ✗ | ✓* | ✓ | ✓ | ✓ |
| `DELETE comments/{của người khác}` | ✗ | ✗ | ✗ | ✓ | ✓ |
| `DELETE attachments/{own}` | ✗ | ✗ | ✓ | ✓ | ✓ |
| `DELETE attachments/{của người khác}` | ✗ | ✗ | ✗ | ✓ | ✓ |
| `POST /projects` với `teamId` = team user không thuộc | ✗ | — | — | — | ✓ |
| `GET /teams` | ✗ | ✗ | ✗ | ✗ | ✓ |
| `GET /teams/{T}`, `/members` (không thuộc team T) | ✗ | ✗ | ✗ | ✗ | ✓ |
| `POST /teams/{T}/members` (LEADER không phải owner) | — | — | — | ✓ | ✓ |

(*) tùy quyết định có cho Viewer bình luận hay không; docx đang cho phép.

Kịch bản UX cần tự kiểm tra trên FE sau giai đoạn 2:

1. B mở link `/projects/{X}` của A → thấy trang "Bạn không có quyền truy cập dự án này" và nút quay lại, không thấy tên dự án/board.
2. Viewer mở dự án → thấy board, không thấy "Tạo công việc", không kéo thả được, không thấy nút sửa/xóa trong modal issue, không thấy tab thao tác admin.
3. Member → tạo/sửa/chuyển trạng thái issue, xóa comment của mình, không xóa comment người khác, không xóa issue.
4. Chủ dự án → thấy "Thêm thành viên", đổi role, "Thêm bước chuyển", xóa mọi comment/attachment.
5. Global ADMIN đăng nhập portal user → thấy mọi dự án, mọi nút.
