-- ============================================================================
-- DATN-2026: WORK SERVICE
-- Flyway Migration: V2__authz_hardening.sql
-- Mục đích:
--   1. Chuẩn hóa định danh user về Keycloak ID (JWT sub) ở mọi bảng của work-service.
--      (Trước đây một số luồng lưu DB id của identity-service, gây lệch khi kiểm tra quyền.)
--   2. Bổ sung các quyền hạt mịn mới (COMMENT_*_OWN/ALL, ATTACHMENT_*_OWN/ALL) cho dự án đã tồn tại.
--   3. Đảm bảo thành viên team và Project Lead có role_assignments trong dự án.
--   4. Gỡ mô hình phân quyền cũ (role_permissions / permissions) không còn dùng.
-- ============================================================================

-- ─── 1. Chuẩn hóa user_id: DB id -> Keycloak id (chỉ khi bảng users của identity-service cùng DB) ───
DO $$
DECLARE
    has_users BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'users'
    ) INTO has_users;

    IF NOT has_users THEN
        RAISE NOTICE 'V2: bảng users không tồn tại trong schema hiện tại, bỏ qua bước chuẩn hóa user_id';
        RETURN;
    END IF;

    -- Bảng tạm: ánh xạ db_id -> keycloak_id (chỉ các keycloak_id là UUID hợp lệ)
    CREATE TEMP TABLE tmp_user_id_map AS
    SELECT u.id AS db_id, u.keycloak_id::uuid AS kc_id
    FROM users u
    WHERE u.keycloak_id IS NOT NULL
      AND u.keycloak_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      AND u.keycloak_id::uuid <> u.id;

    -- role_assignments (UNIQUE project_id, user_id, role_id)
    UPDATE role_assignments ra
    SET user_id = m.kc_id
    FROM tmp_user_id_map m
    WHERE ra.user_id = m.db_id
      AND NOT EXISTS (
          SELECT 1 FROM role_assignments x
          WHERE x.project_id = ra.project_id AND x.user_id = m.kc_id AND x.role_id = ra.role_id
      );
    DELETE FROM role_assignments ra USING tmp_user_id_map m WHERE ra.user_id = m.db_id;

    -- team_members (UNIQUE team_id, user_id)
    UPDATE team_members tm
    SET user_id = m.kc_id
    FROM tmp_user_id_map m
    WHERE tm.user_id = m.db_id
      AND NOT EXISTS (
          SELECT 1 FROM team_members x WHERE x.team_id = tm.team_id AND x.user_id = m.kc_id
      );
    DELETE FROM team_members tm USING tmp_user_id_map m WHERE tm.user_id = m.db_id;

    -- Các cột tham chiếu user không có ràng buộc unique
    UPDATE teams t SET owner_id = m.kc_id FROM tmp_user_id_map m WHERE t.owner_id = m.db_id;
    UPDATE projects p SET lead_user_id = m.kc_id FROM tmp_user_id_map m WHERE p.lead_user_id = m.db_id;
    UPDATE projects p SET created_by = m.kc_id FROM tmp_user_id_map m WHERE p.created_by = m.db_id;
    UPDATE work_items w SET assignee_id = m.kc_id FROM tmp_user_id_map m WHERE w.assignee_id = m.db_id;
    UPDATE work_items w SET reporter_id = m.kc_id FROM tmp_user_id_map m WHERE w.reporter_id = m.db_id;
    UPDATE work_item_comments c SET author_id = m.kc_id FROM tmp_user_id_map m WHERE c.author_id = m.db_id;
    UPDATE work_item_attachments a SET uploaded_by = m.kc_id FROM tmp_user_id_map m WHERE a.uploaded_by = m.db_id;

    DROP TABLE tmp_user_id_map;
END $$;

-- ─── 2. Bổ sung quyền mới cho các vai trò mặc định của dự án đã tồn tại ───
INSERT INTO permission_scheme_entries (id, project_id, role_id, permission_key)
SELECT gen_random_uuid(), pr.project_id, pr.id, k.perm_key
FROM project_roles pr
JOIN (
    VALUES
        ('ADMINISTRATOR', 'COMMENT_EDIT_OWN'),
        ('ADMINISTRATOR', 'COMMENT_DELETE_OWN'),
        ('ADMINISTRATOR', 'COMMENT_DELETE_ALL'),
        ('ADMINISTRATOR', 'ATTACHMENT_DELETE_OWN'),
        ('ADMINISTRATOR', 'ATTACHMENT_DELETE_ALL'),
        ('MEMBER', 'COMMENT_EDIT_OWN'),
        ('MEMBER', 'COMMENT_DELETE_OWN'),
        ('MEMBER', 'ATTACHMENT_DELETE_OWN'),
        ('VIEWER', 'COMMENT_CREATE'),
        ('VIEWER', 'COMMENT_EDIT_OWN'),
        ('VIEWER', 'COMMENT_DELETE_OWN')
) AS k(role_name, perm_key) ON k.role_name = pr.name
WHERE pr.project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM permission_scheme_entries e
      WHERE e.project_id = pr.project_id AND e.role_id = pr.id AND e.permission_key = k.perm_key
  );

-- ─── 3a. Thành viên team chưa có vai trò trong dự án của team -> gán theo TeamRole ───
INSERT INTO role_assignments (id, project_id, user_id, role_id, assigned_at)
SELECT gen_random_uuid(), p.id, tm.user_id, pr.id, CURRENT_TIMESTAMP
FROM projects p
JOIN team_members tm ON tm.team_id = p.team_id
JOIN project_roles pr ON pr.project_id = p.id
    AND pr.name = CASE tm.role WHEN 'LEADER' THEN 'ADMINISTRATOR' WHEN 'VIEWER' THEN 'VIEWER' ELSE 'MEMBER' END
WHERE p.team_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_assignments ra WHERE ra.project_id = p.id AND ra.user_id = tm.user_id
  );

-- ─── 3b. Project Lead / người tạo luôn là ADMINISTRATOR của dự án ───
INSERT INTO role_assignments (id, project_id, user_id, role_id, assigned_at)
SELECT gen_random_uuid(), p.id, p.lead_user_id, pr.id, CURRENT_TIMESTAMP
FROM projects p
JOIN project_roles pr ON pr.project_id = p.id AND pr.name = 'ADMINISTRATOR'
WHERE p.lead_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_assignments ra WHERE ra.project_id = p.id AND ra.user_id = p.lead_user_id
  );

INSERT INTO role_assignments (id, project_id, user_id, role_id, assigned_at)
SELECT gen_random_uuid(), p.id, p.created_by, pr.id, CURRENT_TIMESTAMP
FROM projects p
JOIN project_roles pr ON pr.project_id = p.id AND pr.name = 'ADMINISTRATOR'
WHERE p.created_by IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_assignments ra WHERE ra.project_id = p.id AND ra.user_id = p.created_by
  );

-- ─── 4. Gỡ mô hình phân quyền cũ (bảng do Hibernate tạo từ entity đã xóa) ───
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS permissions;
