-- ============================================================================
-- DATN-2026: WORK SERVICE
-- Flyway Migration: V8__drop_team_and_restructure_projects.sql
-- Description: Loại bỏ hoàn toàn bảng teams & team_members;
--              Đồng bộ dữ liệu thành viên team sang role_assignments;
--              Liên kết dự án trực tiếp vào workspace.
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE 'Bắt đầu quá trình di trú loại bỏ Team...';

    -- 1. ĐỒNG BỘ DỮ LIỆU THÀNH VIÊN TỪ TEAM_MEMBERS SANG ROLE_ASSIGNMENTS (NẾU CHƯA CÓ)
    IF to_regclass('public.team_members') IS NOT NULL 
       AND to_regclass('public.projects') IS NOT NULL 
       AND to_regclass('public.project_roles') IS NOT NULL 
       AND to_regclass('public.role_assignments') IS NOT NULL THEN

        -- 1.1 Đồng bộ LEADER -> ADMINISTRATOR
        INSERT INTO role_assignments (id, project_id, user_id, role_id, assigned_at)
        SELECT 
            gen_random_uuid(),
            p.id,
            tm.user_id,
            pr.id,
            CURRENT_TIMESTAMP
        FROM team_members tm
        JOIN projects p ON p.team_id = tm.team_id
        JOIN project_roles pr ON pr.project_id = p.id AND pr.name = 'ADMINISTRATOR'
        WHERE tm.role = 'LEADER'
          AND NOT EXISTS (
              SELECT 1 FROM role_assignments ra 
              WHERE ra.project_id = p.id AND ra.user_id = tm.user_id
          )
        ON CONFLICT DO NOTHING;

        -- 1.2 Đồng bộ VIEWER -> VIEWER
        INSERT INTO role_assignments (id, project_id, user_id, role_id, assigned_at)
        SELECT 
            gen_random_uuid(),
            p.id,
            tm.user_id,
            pr.id,
            CURRENT_TIMESTAMP
        FROM team_members tm
        JOIN projects p ON p.team_id = tm.team_id
        JOIN project_roles pr ON pr.project_id = p.id AND pr.name = 'VIEWER'
        WHERE tm.role = 'VIEWER'
          AND NOT EXISTS (
              SELECT 1 FROM role_assignments ra 
              WHERE ra.project_id = p.id AND ra.user_id = tm.user_id
          )
        ON CONFLICT DO NOTHING;

        -- 1.3 Đồng bộ MEMBER -> MEMBER
        INSERT INTO role_assignments (id, project_id, user_id, role_id, assigned_at)
        SELECT 
            gen_random_uuid(),
            p.id,
            tm.user_id,
            pr.id,
            CURRENT_TIMESTAMP
        FROM team_members tm
        JOIN projects p ON p.team_id = tm.team_id
        JOIN project_roles pr ON pr.project_id = p.id AND pr.name = 'MEMBER'
        WHERE tm.role NOT IN ('LEADER', 'VIEWER')
          AND NOT EXISTS (
              SELECT 1 FROM role_assignments ra 
              WHERE ra.project_id = p.id AND ra.user_id = tm.user_id
          )
        ON CONFLICT DO NOTHING;

        RAISE NOTICE 'Đã hoàn tất đồng bộ thành viên Team sang Project Role Assignments.';
    END IF;

    -- 2. DỌN DẸP LỜI MỜI CŨ (INVITATIONS) CÓ TYPE = TEAM
    IF to_regclass('public.invitations') IS NOT NULL THEN
        DELETE FROM invitations WHERE type = 'TEAM';
        
        -- Xóa cột team_role
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'invitations' AND column_name = 'team_role'
        ) THEN
            ALTER TABLE invitations DROP COLUMN team_role;
            RAISE NOTICE 'Đã xóa cột team_role khỏi bảng invitations.';
        END IF;

        -- Thêm ràng buộc chỉ cho phép type = PROJECT
        ALTER TABLE invitations DROP CONSTRAINT IF EXISTS chk_invitation_type;
        ALTER TABLE invitations ADD CONSTRAINT chk_invitation_type CHECK (type IN ('PROJECT'));
    END IF;

    -- 3. GỠ KHÓA NGOẠI VÀ CỘT TEAM_ID KHỎI BẢNG PROJECTS
    IF to_regclass('public.projects') IS NOT NULL THEN
        ALTER TABLE projects DROP CONSTRAINT IF EXISTS fk_projects_team;
        ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_team_id_fkey;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'projects' AND column_name = 'team_id'
        ) THEN
            ALTER TABLE projects DROP COLUMN team_id;
            RAISE NOTICE 'Đã xóa cột team_id khỏi bảng projects.';
        END IF;

        -- Đảm bảo index trên workspace_id tồn tại
        CREATE INDEX IF NOT EXISTS idx_projects_workspace ON projects(workspace_id);
    END IF;

    -- 4. DROP CÁC BẢNG TEAMS VÀ TEAM_MEMBERS
    DROP TABLE IF EXISTS team_members CASCADE;
    RAISE NOTICE 'Đã drop bảng team_members.';

    DROP TABLE IF EXISTS teams CASCADE;
    RAISE NOTICE 'Đã drop bảng teams.';

    RAISE NOTICE 'Hoàn thành Flyway Migration V8 thành công!';
END $$;
