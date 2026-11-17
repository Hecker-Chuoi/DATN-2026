# 04. Kế Hoạch Triển Khai Chi Tiết: Loại Bỏ Team, Giữ Workspace & Project

> **Đồ án tốt nghiệp DATN-2026**  
> **Địa điểm lưu trữ:** `D:\DATN-2026\docs\services\work_service\new structure/`  
> **Mục tiêu:** Thực hiện refactor toàn bộ mã nguồn backend, database migration scripts, frontend UI/State, và unit tests theo từng bước có kiểm thử độc lập.

---

## 1. Nguyên Tắc Triển Khai
1. **Làm đến đâu, kiểm thử đến đó (Step-by-step Verification):**
   - Mỗi giai đoạn thay đổi phải thực hiện build, compile, chạy test hoặc lint để bảo đảm không làm gãy mã nguồn.
2. **Không phụ thuộc vào Database đang chạy:**
   - Tạo sẵn các script Flyway migration chuẩn (`V8__...` và `V5__...`) trong source code.
   - Refactor code không dựa vào kết nối DB runtime (Unit tests backend dùng Mockito/JUnit 5 thuần, frontend kiểm tra build và lint).
3. **Ghi chép nhật ký xuyên suốt:**
   - Cập nhật từng bước, từng file đã sửa/xóa và kết quả kiểm thử vào duy nhất một tài liệu log:
     `D:\DATN-2026\docs\services\work_service\new structure\NHAT_KY_THUC_THI_VA_KIEM_THU_REFACTOR.md`.

---

## 2. Các Giai Đoạn Thực Hiện

```
┌────────────────────────────────────────────────────────────────────────┐
│ GIAI ĐOẠN 1: Chuẩn bị Script Flyway Migration (V8 work, V5 workspace)   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ GIAI ĐOẠN 2: Refactor Backend (work-service, workspace, identity)      │
│   • Xóa các class Team, bảo vệ, controller, DTO thừa                   │
│   • Sửa ProjectServiceImpl, ProjectMemberService, InvitationService    │
│   • Test: mvn test-compile, mvn test                                   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ GIAI ĐOẠN 3: Refactor Frontend React 19                                │
│   • Xóa TeamDetailPage, CreateTeamModal                               │
│   • Sửa Sidebar, WorkspaceLayout, CreateProjectModal, ProjectListPage  │
│   • Dọn dẹp api.js, hooks, utils                                       │
│   • Test: npm run lint, npm run build                                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ GIAI ĐOẠN 4: Tổng kết & Cập nhật Nhật ký Refactor                      │
│   • Rà soát toàn bộ hệ thống                                           │
│   • Hoàn thiện NHAT_KY_THUC_THI_VA_KIEM_THU_REFACTOR.md                      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Chi Tiết Từng Giai Đoạn

### Giai đoạn 1: Database Migration Scripts
- Tạo `backend/work-service/src/main/resources/db/migration/V8__drop_team_and_restructure_projects.sql`:
  - Di trú `team_members` sang `role_assignments`.
  - Dọn dẹp `invitations` type `TEAM`, xóa `team_role`.
  - Drop column `team_id` trên `projects`.
  - Drop tables `team_members`, `teams`.
- Tạo `backend/workspace-service/src/main/resources/db/migration/V5__drop_teams_fk.sql`:
  - Dọn dẹp ràng buộc `fk_teams_workspace` an toàn.

### Giai đoạn 2: Refactor Backend Java
1. **Xóa các thành phần Team trong `work-service`**:
   - `TeamController.java`, `Team.java`, `TeamMember.java`, `TeamRole.java`, `TeamRepository.java`, `TeamMemberRepository.java`.
   - `TeamSecurity.java`, `TeamRoleGuard.java`, `RequireTeamRole.java`.
   - 8 file DTO: `AddTeamMemberRequest`, `AssignTeamLeaderRequest`, `CreateTeamRequest`, `UpdateTeamMemberRoleRequest`, `UpdateTeamRequest`, `TeamDetailResponse`, `TeamMemberResponse`, `TeamResponse`.
   - 3 file Unit test: `TeamControllerTest`, `TeamRoleGuardTest`, `TeamSecurityTest`.
2. **Cập nhật `work-service` core**:
   - `Project.java`: Bỏ `teamId`.
   - `ProjectRepository.java`: Bỏ `findByTeamId`.
   - `ProjectServiceImpl.java`: Bỏ dependency team, gỡ bỏ `validateTeamMembership` và `syncTeamMembersToProject`. Cho phép thành viên Workspace (`MEMBER` trở lên) tạo dự án trực tiếp.
   - `ProjectController.java`: Bỏ param `teamId`.
   - `ProjectMemberServiceImpl.java`: Bỏ `syncTeamMemberToProjects` và `syncTeamMembersToProject`.
   - `Invitation.java`, `InvitationType.java`, `InvitationServiceImpl.java`, `InvitationController.java`, `InvitationMailer.java`: Chỉ còn `type = PROJECT`.
   - `ProjectTemplateServiceImpl.java`, `TemplatePreviewBuilder.java`: Bỏ `teamId` khi nhân bản dự án mẫu.
   - Các DTO Project: Bỏ trường `teamId`, `teamName`.
   - `ProjectSpecification.java`: Bỏ lọc theo `teamId`.
3. **Cập nhật `workspace-service` & `identity-service`**:
   - `workspace-service`: `WorkspaceResponse.java` và `WorkspaceServiceImpl.java` bỏ `teamCount`. `SchemaIntegrityChecker.java` bỏ check `public.teams`.
   - `identity-service`: `UserRepository.java` và `WorkDataCleanupService.java` bỏ xóa `team_members`.
4. **Kiểm thử Backend**:
   - Chạy `mvn clean test-compile -pl work-service,workspace-service,identity-service -am`.
   - Cập nhật các Unit test còn lại (`ProjectServiceTest`, `ProjectMemberServiceTest`, `InvitationServiceTest`, `ProjectDtoValidationTest`) và chạy `mvn test`.

### Giai đoạn 3: Refactor Frontend React 19
1. **Xóa file**:
   - `frontend/src/pages/TeamDetailPage.jsx`
   - `frontend/src/components/modals/CreateTeamModal.jsx`
2. **Chỉnh sửa Routing & Layout**:
   - `frontend/src/App.jsx`: Bỏ route `/teams/:teamId`.
   - `frontend/src/components/layout/Sidebar.jsx`: Bỏ toàn bộ Section Accordion Team; tạo danh sách trực tiếp dự án Workspace.
   - `frontend/src/components/layout/WorkspaceLayout.jsx`: Bỏ `teams`, `leaderTeams`, `fetchTeams`, `CreateTeamModal`; dùng quyền `canCreateProject` trực tiếp từ `useWorkspaceRole()`.
3. **Chỉnh sửa Modals & Pages**:
   - `frontend/src/components/project/CreateProjectModal.jsx`: Bỏ hoàn toàn trường chọn Team, bỏ validate chặn tạo dự án.
   - `frontend/src/pages/ProjectListPage.jsx`: Bỏ lọc theo `teamId`, bỏ cảnh báo chặn tạo dự án.
   - `frontend/src/components/admin/AdminProjectManagement.jsx`: Bỏ lọc theo `teamId` và cột `teamName`.
   - `frontend/src/pages/InvitationsPage.jsx` & `frontend/src/components/common/PendingInvitations.jsx`: Bỏ xử lý lời mời `TEAM`.
   - `frontend/src/api.js`: Xóa `teamApi`, xóa `invitationApi.forTeam`.
   - `frontend/src/hooks/useWorkspaceRole.js` & `frontend/src/utils/projectValidation.js`: Dọn dẹp hàm team.
4. **Kiểm thử Frontend**:
   - Chạy `npm run lint` trong `frontend/`.
   - Chạy `npm run build` trong `frontend/`.

### Giai đoạn 4: Tổng kết & Cập nhật Nhật ký Log
- Ghi nhận chi tiết kết quả từng bước vào `NHAT_KY_THUC_THI_VA_KIEM_THU_REFACTOR.md`.
