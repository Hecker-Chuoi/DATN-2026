-- ============================================================================
-- DATN-2026: WORKSPACE SERVICE
-- Flyway Migration: V2__seed_default_workspace_and_backfill.sql
-- Description: Seed "Default Workspace" + repoint dữ liệu teams/projects đang có.
--              Trước đây TeamController nhét user_id của người tạo vào
--              workspace_id (comment "// Placeholder workspace"), và frontend
--              hardcode workspaceId = a0000000-0000-0000-0000-000000000001.
--              Migration này làm cho UUID hardcode đó trở thành thật.
-- Idempotent: chạy lại nhiều lần vẫn an toàn.
-- ============================================================================
--
-- HAI CẠM BẪY PHẢI SỐNG SÓT:
--
-- 1. Bảng `users` KHÔNG do Flyway tạo. identity-service không có db/migration và
--    không có block spring.flyway — `users` sinh ra từ ddl-auto: update. Nên
--    `users` có thể CHƯA TỒN TẠI khi migration này chạy.
-- 2. `teams` / `projects` đến từ history Flyway ĐỘC LẬP của work-service
--    (flyway_work_schema_history), không có thứ tự bảo đảm so với history này.
--
-- Vì vậy mọi bảng cross-service chỉ được chạm qua EXECUTE (dynamic SQL) bọc
-- trong guard to_regclass — để câu lệnh không bị PARSE khi bảng chưa tồn tại.
-- ============================================================================

DO $$
DECLARE
    v_ws        UUID    := 'a0000000-0000-0000-0000-000000000001';
    v_creator   UUID    := NULL;
    v_has_users BOOLEAN := to_regclass('public.users')    IS NOT NULL;
    v_has_teams BOOLEAN := to_regclass('public.teams')    IS NOT NULL;
    v_has_proj  BOOLEAN := to_regclass('public.projects') IS NOT NULL;
BEGIN
    -- 1. Xác định user "đầu tiên" để làm OWNER (giữ bất biến: >= 1 OWNER)
    IF v_has_users THEN
        EXECUTE 'SELECT id FROM users ORDER BY created_at NULLS LAST, id LIMIT 1' INTO v_creator;
    END IF;

    -- 2. Seed workspace mặc định với UUID CỐ ĐỊNH (frontend đang hardcode chính nó)
    --
    -- EDGE CASE: nếu users rỗng (DB mới), created_by nhận giá trị sentinel là
    -- chính v_ws và workspace có ZERO OWNER. Vô hại: lúc đó chưa ai ở trong đó
    -- và chưa có team nào; user đầu tiên sẽ tự POST /api/v1/workspaces để tạo
    -- workspace riêng. Cố tình KHÔNG thêm CommandLineRunner để chữa.
    INSERT INTO workspaces (id, name, slug, description, created_by)
    VALUES (v_ws, 'Default Workspace', 'default',
            'Workspace mặc định được tạo tự động khi nâng cấp lên kiến trúc Workspace',
            COALESCE(v_creator, v_ws))
    ON CONFLICT (id) DO NOTHING;

    -- 3. Đưa toàn bộ user hiện có vào workspace mặc định với vai trò MEMBER
    IF v_has_users THEN
        EXECUTE format(
            'INSERT INTO workspace_members (workspace_id, user_id, role)
             SELECT %L::uuid, u.id, ''MEMBER'' FROM users u
             ON CONFLICT ON CONSTRAINT uq_workspace_member DO NOTHING', v_ws);
    END IF;

    -- 4. Nâng user đầu tiên lên OWNER (DO UPDATE vì bước 3 vừa chèn user này làm MEMBER)
    IF v_creator IS NOT NULL THEN
        INSERT INTO workspace_members (workspace_id, user_id, role)
        VALUES (v_ws, v_creator, 'OWNER')
        ON CONFLICT ON CONSTRAINT uq_workspace_member
        DO UPDATE SET role = 'OWNER';
    END IF;

    -- 5. Repoint teams.
    --
    -- Vị từ là NOT EXISTS (... FROM workspaces ...) chứ KHÔNG so sánh với một
    -- giá trị placeholder cụ thể. Đó là điều làm nó tự chữa: mọi hàng đang trỏ
    -- tới workspace không tồn tại (placeholder user-id, hoặc workspace bị xóa
    -- trước khi có FK) đều được kéo về default; chạy lần hai là no-op.
    IF v_has_teams THEN
        EXECUTE format(
            'UPDATE teams SET workspace_id = %L::uuid, updated_at = CURRENT_TIMESTAMP
             WHERE workspace_id IS NULL
                OR NOT EXISTS (SELECT 1 FROM workspaces w WHERE w.id = teams.workspace_id)', v_ws);
        RAISE NOTICE 'Đã backfill teams.workspace_id -> %', v_ws;
    ELSE
        RAISE NOTICE 'Bỏ qua backfill teams: bảng chưa tồn tại (work-service chưa migrate).';
    END IF;

    -- 6. Repoint projects (cùng logic tự chữa như teams)
    IF v_has_proj THEN
        EXECUTE format(
            'UPDATE projects SET workspace_id = %L::uuid, updated_at = CURRENT_TIMESTAMP
             WHERE workspace_id IS NULL
                OR NOT EXISTS (SELECT 1 FROM workspaces w WHERE w.id = projects.workspace_id)', v_ws);
        RAISE NOTICE 'Đã backfill projects.workspace_id -> %', v_ws;
    ELSE
        RAISE NOTICE 'Bỏ qua backfill projects: bảng chưa tồn tại.';
    END IF;
END $$;
