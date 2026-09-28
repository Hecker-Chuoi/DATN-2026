-- ============================================================================
-- DATN-2026: WORKSPACE SERVICE
-- Flyway Migration: V3__link_teams_projects_workspace_fk.sql
-- Description: Thêm FK thật teams/projects -> workspaces.
-- ============================================================================
--
-- BÀI TOÁN: FK ràng buộc bảng `teams` (thuộc history của work-service) nhưng
-- tham chiếu bảng `workspaces` (thuộc history của workspace-service). Hai
-- Flyway history độc lập, không chờ nhau được.
--
-- CÁCH GIẢI: đặt FK ở workspace-service, KHÔNG phải work-service. V1 của chính
-- service này đã bảo đảm `workspaces` tồn tại — nửa phụ thuộc đó là TRONG CÙNG
-- history nên có thứ tự. Nửa còn lại (teams/projects đã tồn tại chưa) bọc
-- to_regclass. Phương án thay thế (nhân bản CREATE TABLE IF NOT EXISTS
-- workspaces sang work-service) bị loại vì rủi ro DDL drift giữa hai chủ sở hữu.
--
-- ON DELETE RESTRICT vì workspaces dùng soft-delete, không bao giờ xóa cứng.
--
-- RỦI RO TỒN ĐỌNG: nếu trên DB mới workspace-service migrate TRƯỚC work-service
-- thì V2/V3 no-op nhưng Flyway vẫn đánh dấu applied => FK vĩnh viễn không có.
-- Giải bằng PHÁT HIỆN, không phải phòng ngừa: SchemaIntegrityChecker log WARN
-- lúc boot khi teams tồn tại mà thiếu fk_teams_workspace. Không dùng migration
-- repeatable (R__) vì nó chỉ chạy lại khi checksum đổi nên cũng không tự chữa.
-- Thứ tự boot đúng trên DB mới: identity-service -> work-service -> workspace-service.
-- ============================================================================

DO $$
BEGIN
    IF to_regclass('public.teams') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_teams_workspace') THEN
        EXECUTE 'ALTER TABLE teams
                 ADD CONSTRAINT fk_teams_workspace
                 FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT';
        RAISE NOTICE 'Đã thêm FK fk_teams_workspace.';
    ELSE
        RAISE NOTICE 'Bỏ qua FK teams: bảng chưa tồn tại hoặc FK đã có.';
    END IF;

    IF to_regclass('public.projects') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_projects_workspace') THEN
        EXECUTE 'ALTER TABLE projects
                 ADD CONSTRAINT fk_projects_workspace
                 FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT';
        RAISE NOTICE 'Đã thêm FK fk_projects_workspace.';
    ELSE
        RAISE NOTICE 'Bỏ qua FK projects: bảng chưa tồn tại hoặc FK đã có.';
    END IF;
END $$;
