# Rà soát phân quyền workspace-service (2026-10-09)

Phạm vi: `backend/workspace-service` và phần work-service gọi sang nó. Chỉ báo cáo, không sửa code trong đợt này (trừ mục 1 đã được thay bằng cơ chế lời mời).

## Cơ chế hiện tại

- Thang vai trò `WorkspaceRole`: OWNER > ADMIN > MEMBER > GUEST, so sánh bằng `atLeast()` theo ordinal.
- Nguồn sự thật duy nhất là hàng `workspace_members` (không có `owner_id` trên `workspaces`).
- Enforce bằng annotation `@RequireWorkspaceRole` + aspect `security/WorkspaceRoleGuard`: resolve `workspaceId` chỉ theo tên `@PathVariable`, không resolve được thì từ chối, người ngoài nhận 404. Service kiểm tra lại qua `requireAtLeast()`.
- ~~`userId` luôn là `users.id`~~ → **Đã đổi (V6, 15/11/2026):** user id thống nhất toàn hệ thống là Keycloak `sub`, `CurrentUserResolver` lấy thẳng `sub` từ JWT (xem `docs/AUTHORIZATION.md` §5).
- work-service hỏi role qua `client/WorkspaceClient` (3 endpoint `/internal/workspaces/**`), cache Redis `wsRole` 10 phút.

## Đã làm đúng, đừng phá

| Điểm | Vị trí |
|---|---|
| Aspect fail-closed, không heuristic resolve id | `security/WorkspaceRoleGuard.java` |
| 404 thay 403 cho người ngoài tenant | `WorkspaceMemberServiceImpl.requireMembership` |
| `lockOwners()` SELECT FOR UPDATE chống race mất OWNER cuối | `WorkspaceMemberRepository`, `assertNotLastOwner` |
| User id = Keycloak `sub` lấy thẳng từ JWT (V6 bỏ `IdentityUserLookup` và cache `wsUserId`) | `CurrentUserResolver` |
| Hydrate tên/email bằng 1 batch call | `WorkspaceMemberServiceImpl.hydrateUsers` |

## Lỗ hổng

| # | Mức | Lỗ hổng | Vị trí | Hướng sửa |
|---|---|---|---|---|
| 1 | Cao | `/internal/**` và `/actuator/**` permitAll, service bind cổng công khai (8087/9997); ai tới được cổng đọc được role mọi `(workspaceId, userId)` và danh sách workspace của mọi user | `config/SecurityConfig.java`, `controller/InternalWorkspaceController.java` | Bind `127.0.0.1` khi chạy Docker; thêm shared-secret header cho `/internal/**` ở cả identity/workspace/notification |
| 2 | Cao | Cache `wsRole` 10 phút ở work-service không evict: bị xóa khỏi workspace vẫn vào được project tới 10 phút | `work-service/security/WorkspaceAccessChecker.java` (`@Cacheable("wsRole")`), `work-service/config/RedisConfig.java` | Giảm TTL xuống 60s, hoặc workspace-service xóa key khi đổi/xóa member |
| 3 | Cao | Thêm thành viên không cần đồng thuận, không thông báo | `WorkspaceMemberServiceImpl.addMember` (cũ) | **Đã thay bằng lời mời** (`workspace_invitations` V4, email) |
| 4 | TB | ADMIN xóa/hạ cấp được ADMIN khác và tự tạo thêm ADMIN; hai ADMIN có thể loại nhau | `updateMemberRole`, `removeMember` (chỉ bảo vệ OWNER) | Chỉ OWNER được đụng tới hàng có role ADMIN hoặc cấp role ADMIN |
| 5 | ~~TB~~ Đã hết | Fast path tin claim `user_id` trong JWT, không cross-check với `sub`; mapper chưa có trong realm nên hiện luôn đi slow path, nhưng khi ai đó thêm mapper từ user attribute editable thì user tự đặt `user_id` người khác | `security/SecurityUtils.getUserIdClaim`, `CurrentUserResolver` | Bỏ fast path, hoặc kiểm tra `users.keycloak_id == sub` khi dùng claim  — V6 không còn đọc claim `user_id` |
| 6 | TB | GUEST đọc được toàn bộ danh sách thành viên kèm email | `getMembers` chỉ `requireMembership` | GUEST chỉ thấy tên, ẩn email, hoặc yêu cầu MEMBER+ |
| 7 | TB | Migration V2 nhét mọi `users` vào Default Workspace làm MEMBER (= quyền tạo project) | `db/migration/V2__seed_default_workspace_and_backfill.sql` | Chấp nhận cho dữ liệu cũ; user mới không bị ảnh hưởng vì identity không tự thêm vào workspace |
| 8 | TB | Không có global ADMIN bypass: realm ADMIN không xem/khôi phục được workspace mình không là member, trong khi work-service cho ADMIN thấy mọi project | không có `isGlobalAdmin` nào trong workspace-service | Thêm bypass trong `WorkspaceRoleGuard` + `requireAtLeast` giống `SecurityUtils.isGlobalAdmin()` của work-service |
| 9 | Thấp | `PUT /{workspaceId}` chỉ dựa vào aspect, service không re-check role | `WorkspaceServiceImpl.updateWorkspace` | Thêm `requireAtLeast(ADMIN)` trong service |
| 10 | Thấp | Tạo workspace không giới hạn, mỗi lần tự thành OWNER | `WorkspaceController.create` | Giới hạn số workspace/user nếu cần |
| 11 | Thấp | OWNER không tự hạ cấp được (chặn mọi self-change), chỉ còn đường `/leave` | `updateMemberRole` | Cho phép OWNER tự hạ cấp khi còn OWNER khác |
| 12 | Thấp | `countOwners()` không controller nào dùng | `WorkspaceMemberService` | Xóa |

Ghi chú frontend: `utils/workspaceRole.js` xếp `ROLE_RANK` ngược chiều backend (GUEST 0 … OWNER 3) nhưng ngữ nghĩa đúng; khi đồng bộ hai bên cần để ý.
