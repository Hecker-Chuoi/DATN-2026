-- ============================================================================
-- DATN-2026: WORKSPACE SERVICE
-- Flyway Migration: V6__unify_user_id_keycloak_sub.sql
-- Mục đích: thống nhất user id toàn hệ thống = Keycloak sub (giống work-service V2, notification-service).
--
-- Trước đây workspace-service lưu users.id của identity-service, trong khi work-service và
-- notification-service lưu Keycloak sub; workspace_members còn lẫn cả hai (lời mời dự án tự thêm người
-- vào workspace bằng sub). Migration này đổi users.id -> users.keycloak_id ở mọi cột người dùng:
--   workspaces.created_by, workspace_members.user_id / invited_by,
--   workspace_invitations.invitee_user_id / invited_by.
-- Giá trị không khớp users.id (đã là sub, hoặc user hệ thống của dữ liệu mẫu) giữ nguyên.
-- Chỉ chạy khi bảng users của identity-service nằm cùng DB (giống work-service V2).
-- ============================================================================

DO $$
DECLARE
    has_users BOOLEAN;
    n BIGINT;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'users'
    ) INTO has_users;

    IF NOT has_users THEN
        RAISE NOTICE 'V6: bảng users không tồn tại trong schema hiện tại, bỏ qua đổi user id';
        RETURN;
    END IF;

    -- Ánh xạ users.id -> Keycloak sub (chỉ các keycloak_id là UUID hợp lệ và khác users.id)
    CREATE TEMP TABLE v6_user_map ON COMMIT DROP AS
    SELECT u.id AS identity_id, u.keycloak_id::uuid AS sub
    FROM users u
    WHERE u.keycloak_id IS NOT NULL
      AND u.keycloak_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      AND u.keycloak_id::uuid <> u.id;

    -- ─── 1. workspace_members ───
    -- Một người có thể có 2 dòng trong cùng workspace (một theo users.id, một theo sub). Đổi xong sẽ trùng
    -- khoá uq_workspace_member, nên giữ dòng vai trò cao hơn (OWNER > ADMIN > MEMBER > GUEST), xoá dòng kia.
    DELETE FROM workspace_members s
    USING workspace_members i, v6_user_map m
    WHERE i.user_id = m.identity_id
      AND s.user_id = m.sub
      AND s.workspace_id = i.workspace_id
      AND (CASE i.role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 WHEN 'MEMBER' THEN 2 ELSE 3 END)
          <= (CASE s.role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 WHEN 'MEMBER' THEN 2 ELSE 3 END);
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n > 0 THEN RAISE NOTICE 'V6: bỏ % dòng thành viên trùng (giữ dòng vai trò cao hơn)', n; END IF;

    DELETE FROM workspace_members i
    USING workspace_members s, v6_user_map m
    WHERE i.user_id = m.identity_id
      AND s.user_id = m.sub
      AND s.workspace_id = i.workspace_id;
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n > 0 THEN RAISE NOTICE 'V6: bỏ % dòng thành viên trùng (dòng theo sub có vai trò cao hơn)', n; END IF;

    UPDATE workspace_members w SET user_id = m.sub FROM v6_user_map m WHERE w.user_id = m.identity_id;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'V6: workspace_members.user_id đổi % dòng', n;

    UPDATE workspace_members w SET invited_by = m.sub FROM v6_user_map m WHERE w.invited_by = m.identity_id;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'V6: workspace_members.invited_by đổi % dòng', n;

    -- ─── 2. workspaces ───
    UPDATE workspaces w SET created_by = m.sub FROM v6_user_map m WHERE w.created_by = m.identity_id;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'V6: workspaces.created_by đổi % dòng', n;

    -- ─── 3. workspace_invitations ───
    -- Hai lời mời đang chờ cho cùng người (một theo users.id, một theo sub) sẽ trùng uq_ws_invitations_pending:
    -- huỷ lời mời cũ hơn.
    UPDATE workspace_invitations old
    SET status = 'CANCELLED', responded_at = now()
    FROM workspace_invitations newer, v6_user_map m
    WHERE old.status = 'PENDING' AND newer.status = 'PENDING'
      AND old.workspace_id = newer.workspace_id
      AND old.id <> newer.id
      AND ((old.invitee_user_id = m.identity_id AND newer.invitee_user_id = m.sub)
        OR (old.invitee_user_id = m.sub AND newer.invitee_user_id = m.identity_id))
      AND old.created_at <= newer.created_at;
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n > 0 THEN RAISE NOTICE 'V6: huỷ % lời mời trùng', n; END IF;

    UPDATE workspace_invitations w SET invitee_user_id = m.sub FROM v6_user_map m WHERE w.invitee_user_id = m.identity_id;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'V6: workspace_invitations.invitee_user_id đổi % dòng', n;

    UPDATE workspace_invitations w SET invited_by = m.sub FROM v6_user_map m WHERE w.invited_by = m.identity_id;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'V6: workspace_invitations.invited_by đổi % dòng', n;
END $$;

-- Ghi chú cột (dữ liệu giờ là Keycloak sub)
COMMENT ON COLUMN workspace_members.user_id IS 'Keycloak sub (quy ước user id toàn hệ thống)';
COMMENT ON COLUMN workspace_invitations.invitee_user_id IS 'Keycloak sub';
COMMENT ON COLUMN workspace_invitations.invited_by IS 'Keycloak sub';
COMMENT ON COLUMN workspaces.created_by IS 'Keycloak sub';
