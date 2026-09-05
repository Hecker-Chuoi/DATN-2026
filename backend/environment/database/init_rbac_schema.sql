-- PostgreSQL Script khởi tạo Bảng & Dữ liệu Quyền cho RBAC + Resource Scope (DATN-2026)

-- 1. Tạo bảng permissions (Danh sách quyền nguyên tử trong hệ thống)
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    module VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Tạo bảng role_permissions (Ánh xạ Vai trò Dự án & Hệ thống -> Mã quyền)
CREATE TABLE IF NOT EXISTS role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name VARCHAR(50) NOT NULL, -- 'ADMIN', 'OWNER', 'MEMBER', 'OBSERVER'
    permission_code VARCHAR(50) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_permission UNIQUE (role_name, permission_code)
);

-- 3. INSERT DANH SÁCH PERMISSIONS
INSERT INTO permissions (code, name, description, module) VALUES
-- Module: PROJECT
('PROJECT_CREATE', 'Tạo dự án', 'Cho phép tạo dự án mới', 'PROJECT'),
('PROJECT_VIEW', 'Xem dự án', 'Cho phép xem thông tin và danh sách dự án', 'PROJECT'),
('PROJECT_EDIT', 'Sửa dự án', 'Cho phép chỉnh sửa thông tin dự án', 'PROJECT'),
('PROJECT_DELETE', 'Xóa dự án', 'Cho phép xóa dự án', 'PROJECT'),

-- Module: TEAM
('MEMBER_INVITE', 'Mời thành viên', 'Cho phép mời thành viên mới vào dự án', 'TEAM'),
('MEMBER_REMOVE', 'Xóa thành viên', 'Cho phép xóa thành viên khỏi dự án', 'TEAM'),
('MEMBER_ROLE_UPDATE', 'Cập nhật vai trò', 'Cho phép thay đổi vai trò của thành viên trong dự án', 'TEAM'),

-- Module: TASK
('TASK_VIEW', 'Xem công việc', 'Cho phép xem danh sách và chi tiết công việc', 'TASK'),
('TASK_CREATE', 'Tạo công việc', 'Cho phép tạo mới công việc trong dự án', 'TASK'),
('TASK_EDIT', 'Chỉnh sửa công việc', 'Cho phép sửa thông tin công việc', 'TASK'),
('TASK_STATUS_UPDATE', 'Cập nhật trạng thái công việc', 'Cho phép chuyển trạng thái (Todo, In Progress, Done)', 'TASK'),
('TASK_DELETE', 'Xóa công việc', 'Cho phép xóa công việc', 'TASK'),

-- Module: COMMENT
('COMMENT_CREATE', 'Tạo bình luận', 'Cho phép đăng bình luận trong công việc', 'COMMENT'),
('COMMENT_DELETE_OWN', 'Xóa bình luận của mình', 'Cho phép xóa bình luận do chính mình tạo', 'COMMENT'),
('COMMENT_DELETE_ALL', 'Xóa mọi bình luận', 'Cho phép người quản trị xóa bất kỳ bình luận nào trong dự án', 'COMMENT')
ON CONFLICT (code) DO NOTHING;

-- 4. INSERT MA TRẬN PHÂN QUYỀN VÀO BẢNG role_permissions

-- ==================== VAI TRÒ: ADMIN (Toàn quyền hệ thống) ====================
INSERT INTO role_permissions (role_name, permission_code) VALUES
('ADMIN', 'PROJECT_CREATE'),
('ADMIN', 'PROJECT_VIEW'),
('ADMIN', 'PROJECT_EDIT'),
('ADMIN', 'PROJECT_DELETE'),
('ADMIN', 'MEMBER_INVITE'),
('ADMIN', 'MEMBER_REMOVE'),
('ADMIN', 'MEMBER_ROLE_UPDATE'),
('ADMIN', 'TASK_VIEW'),
('ADMIN', 'TASK_CREATE'),
('ADMIN', 'TASK_EDIT'),
('ADMIN', 'TASK_STATUS_UPDATE'),
('ADMIN', 'TASK_DELETE'),
('ADMIN', 'COMMENT_CREATE'),
('ADMIN', 'COMMENT_DELETE_OWN'),
('ADMIN', 'COMMENT_DELETE_ALL')
ON CONFLICT (role_name, permission_code) DO NOTHING;

-- ==================== VAI TRÒ: OWNER (Có đầy đủ 15 quyền trong dự án) ====================
INSERT INTO role_permissions (role_name, permission_code) VALUES
('OWNER', 'PROJECT_CREATE'),
('OWNER', 'PROJECT_VIEW'),
('OWNER', 'PROJECT_EDIT'),
('OWNER', 'PROJECT_DELETE'),
('OWNER', 'MEMBER_INVITE'),
('OWNER', 'MEMBER_REMOVE'),
('OWNER', 'MEMBER_ROLE_UPDATE'),
('OWNER', 'TASK_VIEW'),
('OWNER', 'TASK_CREATE'),
('OWNER', 'TASK_EDIT'),
('OWNER', 'TASK_STATUS_UPDATE'),
('OWNER', 'TASK_DELETE'),
('OWNER', 'COMMENT_CREATE'),
('OWNER', 'COMMENT_DELETE_OWN'),
('OWNER', 'COMMENT_DELETE_ALL')
ON CONFLICT (role_name, permission_code) DO NOTHING;

-- ==================== VAI TRÒ: MEMBER (Thành viên phát triển) ====================
INSERT INTO role_permissions (role_name, permission_code) VALUES
('MEMBER', 'PROJECT_VIEW'),
('MEMBER', 'TASK_VIEW'),
('MEMBER', 'TASK_CREATE'),
('MEMBER', 'TASK_EDIT'),
('MEMBER', 'TASK_STATUS_UPDATE'),
('MEMBER', 'COMMENT_CREATE'),
('MEMBER', 'COMMENT_DELETE_OWN')
ON CONFLICT (role_name, permission_code) DO NOTHING;

-- ==================== VAI TRÒ: OBSERVER (Khách / Giám sát) ====================
INSERT INTO role_permissions (role_name, permission_code) VALUES
('OBSERVER', 'PROJECT_VIEW'),
('OBSERVER', 'TASK_VIEW'),
('OBSERVER', 'COMMENT_CREATE'),
('OBSERVER', 'COMMENT_DELETE_OWN')
ON CONFLICT (role_name, permission_code) DO NOTHING;
