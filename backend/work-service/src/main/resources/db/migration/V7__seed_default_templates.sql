-- Flyway Migration: V7__seed_default_templates.sql
-- Description: Khởi tạo 3 Dự án mẫu chuẩn nghiệp vụ cho DATN-2026:
--   1. Xây dựng Cửa hàng F&B (Nhà hàng - Quán Café) - [BUSINESS]
--   2. Xây dựng Website Bán hàng E-Commerce - [SOFTWARE]
--   3. Kế hoạch Vận hành Mùa vụ Cây ăn quả (Nông nghiệp) - [OPERATIONS]

-- 0. TẠO CÁC POSTGRESQL SEQUENCES CHO 3 MẪU
CREATE SEQUENCE IF NOT EXISTS work_item_seq_tplfnb START WITH 1 INCREMENT BY 1 NO MAXVALUE NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS work_item_seq_tplweb START WITH 1 INCREMENT BY 1 NO MAXVALUE NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS work_item_seq_tplagri START WITH 1 INCREMENT BY 1 NO MAXVALUE NO CYCLE;

DO $$
DECLARE
    v_ws_id UUID := 'a0000000-0000-0000-0000-000000000001';
    v_creator_id UUID;
    v_start DATE := CURRENT_DATE;
BEGIN
    -- Lấy creator_id thực tế của Default Workspace
    SELECT COALESCE(created_by, 'd457bd54-287f-40a0-ab65-9a4f04dd3606'::uuid)
    INTO v_creator_id
    FROM workspaces WHERE id = v_ws_id LIMIT 1;
    IF v_creator_id IS NULL THEN
        v_creator_id := 'd457bd54-287f-40a0-ab65-9a4f04dd3606'::uuid;
    END IF;

    -- =========================================================================
    -- 1. TEMPLATE 1: XÂY DỰNG CỬA HÀNG F&B (TPLFNB)
    -- =========================================================================
    IF NOT EXISTS (SELECT 1 FROM projects WHERE key = 'TPLFNB') THEN
        -- 1.1 Project
        INSERT INTO projects (id, workspace_id, name, key, description, lead_user_id, created_by, default_assignee_type, project_type, is_template, template_category, start_date, end_date)
        VALUES ('b1000000-0000-0000-0000-000000000001', v_ws_id, 'Xây dựng Cửa hàng F&B (Nhà hàng - Quán Café)', 'TPLFNB',
                'Mẫu dự án chuẩn hóa quy trình mở mới một điểm kinh doanh F&B: từ khảo sát thuê mặt bằng, thiết kế thi công nội thất, mua sắm máy móc quầy bar, tuyển dụng đào tạo nhân sự, hoàn thiện giấy phép ATTP đến chạy thử (Soft Opening) và khai trương.',
                v_creator_id, v_creator_id, 'PROJECT_LEADER', 'SOFTWARE_SCRUM', TRUE, 'BUSINESS', v_start, v_start + 90);

        -- 1.2 Roles & Permissions
        INSERT INTO project_roles (id, project_id, name, description) VALUES
        ('b1000000-0000-0000-0005-000000000001', 'b1000000-0000-0000-0000-000000000001', 'ADMINISTRATOR', 'Toàn quyền quản trị'),
        ('b1000000-0000-0000-0005-000000000002', 'b1000000-0000-0000-0000-000000000001', 'MEMBER', 'Thành viên triển khai'),
        ('b1000000-0000-0000-0005-000000000003', 'b1000000-0000-0000-0000-000000000001', 'VIEWER', 'Theo dõi xem');

        INSERT INTO permission_scheme_entries (project_id, role_id, permission_key) VALUES
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'PROJECT_VIEW'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'PROJECT_ADMIN'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'ISSUE_CREATE'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'ISSUE_EDIT'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'ISSUE_TRANSITION'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'ISSUE_ASSIGN'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'COMMENT_CREATE'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'COMMENT_EDIT_OWN'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'COMMENT_DELETE_OWN'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000001', 'ATTACHMENT_DELETE_OWN'),
        -- MEMBER
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000002', 'PROJECT_VIEW'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000002', 'ISSUE_CREATE'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000002', 'ISSUE_EDIT'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000002', 'ISSUE_TRANSITION'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000002', 'ISSUE_ASSIGN'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000002', 'COMMENT_CREATE'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000002', 'COMMENT_EDIT_OWN'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000002', 'COMMENT_DELETE_OWN'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000002', 'ATTACHMENT_DELETE_OWN'),
        -- VIEWER
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000003', 'PROJECT_VIEW'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000003', 'COMMENT_CREATE'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000003', 'COMMENT_EDIT_OWN'),
        ('b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0005-000000000003', 'COMMENT_DELETE_OWN');

        -- 1.3 Work Item Types
        INSERT INTO work_item_types (id, project_id, name, code, description, icon_name, color_hex, hierarchy_level, is_subtask, is_system_default) VALUES
        ('b1000000-0000-0000-0002-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Epic', 'EPIC', 'Hạng mục lớn bao quát nhiều công việc', 'Zap', '#9333EA', 1, FALSE, TRUE),
        ('b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Task', 'TASK', 'Công việc tiêu chuẩn', 'CheckSquare', '#3B82F6', 0, FALSE, TRUE),
        ('b1000000-0000-0000-0002-000000000003', 'b1000000-0000-0000-0000-000000000001', 'Subtask', 'SUBTASK', 'Công việc con phụ thuộc của Task', 'GitCommit', '#06B6D4', -1, TRUE, TRUE);

        -- 1.4 Statuses
        INSERT INTO statuses (id, project_id, name, category, color_hex, description) VALUES
        ('b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Cần làm', 'TODO', '#64748B', 'Hạng mục công việc chuẩn bị thực hiện'),
        ('b1000000-0000-0000-0001-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Đang thực hiện', 'IN_PROGRESS', '#3B82F6', 'Đang triển khai thi công, mua sắm hoặc tuyển dụng'),
        ('b1000000-0000-0000-0001-000000000003', 'b1000000-0000-0000-0000-000000000001', 'Nghiệm thu / Đánh giá', 'IN_PROGRESS', '#F59E0B', 'Kiểm tra chất lượng món, thẩm định PCCC, nghiệm thu công trình'),
        ('b1000000-0000-0000-0001-000000000004', 'b1000000-0000-0000-0000-000000000001', 'Hoàn thành', 'DONE', '#10B981', 'Đã hoàn tất và nghiệm thu đạt chuẩn');

        -- 1.5 Workflow & Transitions
        INSERT INTO workflows (id, project_id, name, description, is_default)
        VALUES ('b1000000-0000-0000-0003-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Quy trình Mở mới Cửa hàng F&B', 'Quy trình chuẩn kiểm soát từng giai đoạn từ khảo sát đến khai trương', TRUE);

        INSERT INTO workflow_transitions (id, workflow_id, from_status_id, to_status_id, name) VALUES
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000001', NULL, 'b1000000-0000-0000-0001-000000000001', 'Create'),
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000001', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0001-000000000002', 'Bắt đầu triển khai'),
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000001', 'b1000000-0000-0000-0001-000000000002', 'b1000000-0000-0000-0001-000000000003', 'Gửi nghiệm thu'),
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000001', 'b1000000-0000-0000-0001-000000000003', 'b1000000-0000-0000-0001-000000000004', 'Nghiệm thu đạt chuẩn'),
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000001', 'b1000000-0000-0000-0001-000000000003', 'b1000000-0000-0000-0001-000000000002', 'Yêu cầu chỉnh sửa'),
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000001', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0001-000000000004', 'Hoàn tất trực tiếp'),
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000001', 'b1000000-0000-0000-0001-000000000004', 'b1000000-0000-0000-0001-000000000002', 'Mở lại công việc');

        INSERT INTO workflow_schemes (id, project_id, name, description)
        VALUES ('b1000000-0000-0000-0003-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Quy trình Mở mới Cửa hàng F&B Scheme', 'Gắn các loại công việc vào quy trình chuẩn');

        INSERT INTO workflow_scheme_mappings (id, scheme_id, work_item_type_id, workflow_id) VALUES
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000002', 'b1000000-0000-0000-0002-000000000001', 'b1000000-0000-0000-0003-000000000001'),
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000002', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0003-000000000001'),
        (gen_random_uuid(), 'b1000000-0000-0000-0003-000000000002', 'b1000000-0000-0000-0002-000000000003', 'b1000000-0000-0000-0003-000000000001');

        -- 1.6 Board & Columns
        INSERT INTO boards (id, project_id, name, type)
        VALUES ('b1000000-0000-0000-0004-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Bảng Tiến độ Setup Cửa hàng F&B', 'SCRUM');

        INSERT INTO board_columns (id, board_id, name, column_order) VALUES
        ('b1000000-0000-0000-0004-000000000010', 'b1000000-0000-0000-0004-000000000001', 'CHUẨN BỊ', 0),
        ('b1000000-0000-0000-0004-000000000011', 'b1000000-0000-0000-0004-000000000001', 'ĐANG TRIỂN KHAI', 1),
        ('b1000000-0000-0000-0004-000000000012', 'b1000000-0000-0000-0004-000000000001', 'NGHIỆM THU', 2),
        ('b1000000-0000-0000-0004-000000000013', 'b1000000-0000-0000-0004-000000000001', 'HOÀN THÀNH', 3);

        INSERT INTO board_column_statuses (column_id, status_id) VALUES
        ('b1000000-0000-0000-0004-000000000010', 'b1000000-0000-0000-0001-000000000001'),
        ('b1000000-0000-0000-0004-000000000011', 'b1000000-0000-0000-0001-000000000002'),
        ('b1000000-0000-0000-0004-000000000012', 'b1000000-0000-0000-0001-000000000003'),
        ('b1000000-0000-0000-0004-000000000013', 'b1000000-0000-0000-0001-000000000004');

        -- 1.7 Components
        INSERT INTO components (id, project_id, name, description) VALUES
        ('b1000000-0000-0000-0006-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Mặt bằng & Pháp lý', 'Khảo sát, hợp đồng thuê và giấy phép kinh doanh, PCCC, ATTP'),
        ('b1000000-0000-0000-0006-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Thiết kế & Thi công', 'Bản vẽ 2D/3D, cải tạo điện nước, nội thất quầy bar, biển bảng'),
        ('b1000000-0000-0000-0006-000000000003', 'b1000000-0000-0000-0000-000000000001', 'Thiết bị & Dụng cụ', 'Máy pha cà phê, máy xay, tủ lạnh công nghiệp, hệ thống POS'),
        ('b1000000-0000-0000-0006-000000000004', 'b1000000-0000-0000-0000-000000000001', 'Thực đơn & Nguyên liệu', 'Công thức đồ uống/món ăn, tính giá vốn costing, nhà cung cấp'),
        ('b1000000-0000-0000-0006-000000000005', 'b1000000-0000-0000-0000-000000000001', 'Nhân sự & Đào tạo', 'Tuyển dụng quản lý, pha chế, thu ngân, đào tạo quy trình SOP'),
        ('b1000000-0000-0000-0006-000000000006', 'b1000000-0000-0000-0000-000000000001', 'Marketing & Khai trương', 'Truyền thông quảng bá, tuần lễ Soft Opening và Grand Opening');

        -- 1.8 Labels
        INSERT INTO labels (id, project_id, name, color_hex) VALUES
        ('b1000000-0000-0000-0007-000000000001', 'b1000000-0000-0000-0000-000000000001', 'mat-bang', '#3B82F6'),
        ('b1000000-0000-0000-0007-000000000002', 'b1000000-0000-0000-0000-000000000001', 'noi-that', '#8B5CF6'),
        ('b1000000-0000-0000-0007-000000000003', 'b1000000-0000-0000-0000-000000000001', 'tuyen-dung', '#EC4899'),
        ('b1000000-0000-0000-0007-000000000004', 'b1000000-0000-0000-0000-000000000001', 'pha-che', '#F59E0B'),
        ('b1000000-0000-0000-0007-000000000005', 'b1000000-0000-0000-0000-000000000001', 'phap-ly', '#EF4444'),
        ('b1000000-0000-0000-0007-000000000006', 'b1000000-0000-0000-0000-000000000001', 'khai-truong', '#10B981');

        -- 1.9 Sprints (Tất cả trạng thái FUTURE)
        INSERT INTO sprints (id, project_id, name, goal, state, start_date, end_date, version) VALUES
        ('b1000000-0000-0000-0008-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Giai đoạn 1: Pháp lý, Mặt bằng & Thiết kế Concept', 'Chốt mặt bằng kinh doanh, ký hợp đồng thuê và hoàn thiện bản vẽ 3D', 'FUTURE', (v_start || ' 00:00:00')::timestamp, ((v_start + 20) || ' 23:59:59')::timestamp, 0),
        ('b1000000-0000-0000-0008-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Giai đoạn 2: Thi công nội thất, Máy móc & Menu', 'Hoàn tất thi công thô/hoàn thiện, lắp đặt quầy bar và mua sắm thiết bị', 'FUTURE', ((v_start + 20) || ' 00:00:00')::timestamp, ((v_start + 42) || ' 23:59:59')::timestamp, 0),
        ('b1000000-0000-0000-0008-000000000003', 'b1000000-0000-0000-0000-000000000001', 'Giai đoạn 3: Tuyển dụng, Đào tạo & Khai trương', 'Đào tạo nhân sự thực chiến, tuần lễ chạy thử Soft Opening và Grand Opening', 'FUTURE', ((v_start + 42) || ' 00:00:00')::timestamp, ((v_start + 60) || ' 23:59:59')::timestamp, 0);

        -- 1.10 Work Items (Epics, Tasks, Subtasks)
        -- Epic 1
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b1000000-0000-0000-0009-000000000001', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000001', 'b1000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLFNB-1',
                'Khảo sát vị trí, Thuê mặt bằng & Pháp lý kinh doanh', 'Nghiên cứu thị trường khu vực, đàm phán hợp đồng thuê mặt bằng và hoàn thiện các thủ tục pháp lý thành lập hộ kinh doanh / ATTP.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00001:', 'b1000000-0000-0000-0006-000000000001', v_start + 1, v_start + 20, 0);

        -- Task 1.1
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b1000000-0000-0000-0009-000000000002', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000001', 'b1000000-0000-0000-0008-000000000001', 'TPLFNB-2',
                'Khảo sát lưu lượng khách (foot traffic) và lựa chọn mặt bằng', 'Đánh giá tiềm năng khách hàng mục tiêu quanh bán kính 1km và kiểm tra hạ tầng giao thông vỉa hè.',
                'HIGH', v_creator_id, 5.0, 28800, 28800, '0|i00002:', 'b1000000-0000-0000-0006-000000000001', v_start + 1, v_start + 6, 0);

        -- Subtask 1.1.1 & 1.1.2
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b1000000-0000-0000-0009-000000000003', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000003', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000002', 'b1000000-0000-0000-0008-000000000001', 'TPLFNB-3',
         'Đo đếm lượt khách qua lại theo các khung giờ sáng, trưa, tối trong 3 ngày', 'Ghi nhận số lượng người đi bộ, phương tiện xe cộ qua lại cửa hàng.', 'MEDIUM', v_creator_id, NULL, 14400, 14400, '0|i00003:', 'b1000000-0000-0000-0006-000000000001', v_start + 1, v_start + 3, 0),
        ('b1000000-0000-0000-0009-000000000004', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000003', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000002', 'b1000000-0000-0000-0008-000000000001', 'TPLFNB-4',
         'Khảo sát chỗ để xe máy, bãi đỗ ô tô và lối thoát hiểm PCCC', 'Đảm bảo sức chứa tối thiểu 20 xe máy cho khách và an toàn thoát hiểm.', 'MEDIUM', v_creator_id, NULL, 14400, 14400, '0|i00004:', 'b1000000-0000-0000-0006-000000000001', v_start + 3, v_start + 6, 0);

        -- Task 1.2
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b1000000-0000-0000-0009-000000000005', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000001', 'b1000000-0000-0000-0008-000000000001', 'TPLFNB-5',
                'Thương lượng hợp đồng thuê và thủ tục đặt cọc mặt bằng', 'Đàm phán giá thuê, chu kỳ tăng giá, thời gian sửa chữa miễn phí và điều khoản bồi thường.',
                'HIGH', v_creator_id, 5.0, 21600, 21600, '0|i00005:', 'b1000000-0000-0000-0006-000000000001', v_start + 7, v_start + 12, 0);

        -- Subtask 1.2.1 & 1.2.2
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b1000000-0000-0000-0009-000000000006', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000003', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000005', 'b1000000-0000-0000-0008-000000000001', 'TPLFNB-6',
         'Đàm phán thời gian miễn phí tiền thuê để sửa chữa cải tạo (Fit-out)', 'Đề xuất chủ nhà miễn phí 20 - 30 ngày trong thời gian sửa chữa thô.', 'MEDIUM', v_creator_id, NULL, 10800, 10800, '0|i00006:', 'b1000000-0000-0000-0006-000000000001', v_start + 7, v_start + 9, 0),
        ('b1000000-0000-0000-0009-000000000007', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000003', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000005', 'b1000000-0000-0000-0008-000000000001', 'TPLFNB-7',
         'Rà soát điều khoản bồi thường, gia hạn và tiến hành công chứng hợp đồng', 'Rà soát rủi ro pháp lý trước khi ký chính thức.', 'HIGH', v_creator_id, NULL, 10800, 10800, '0|i00007:', 'b1000000-0000-0000-0006-000000000001', v_start + 10, v_start + 12, 0);

        -- Task 1.3
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b1000000-0000-0000-0009-000000000008', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000001', 'b1000000-0000-0000-0008-000000000001', 'TPLFNB-8',
                'Nộp hồ sơ ĐKKD và Giấy chứng nhận Đủ điều kiện An toàn thực phẩm', 'Hoàn thành các thủ tục xin phép cơ quan chức năng địa phương.',
                'HIGH', v_creator_id, 5.0, 36000, 36000, '0|i00008:', 'b1000000-0000-0000-0006-000000000001', v_start + 13, v_start + 19, 0);

        -- Epic 2
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b1000000-0000-0000-0009-000000000009', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000001', 'b1000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLFNB-9',
                'Thiết kế kiến trúc, Thi công nội thất & Biển bảng nhận diện', 'Thiết kế phối cảnh 3D không gian, cải tạo phần thô cấp thoát nước, thi công quầy bar và lắp đặt biển hiệu quảng cáo.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00009:', 'b1000000-0000-0000-0006-000000000002', v_start + 10, v_start + 42, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b1000000-0000-0000-0009-000000000010', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000009', 'b1000000-0000-0000-0008-000000000001', 'TPLFNB-10',
         'Thiết kế bản vẽ mặt bằng 2D và phối cảnh 3D nội ngoại thất', 'Thống nhất phong cách decor, bố trí ánh sáng, luồng di chuyển của nhân viên và khách.', 'HIGH', v_creator_id, 8.0, 57600, 57600, '0|i00010:', 'b1000000-0000-0000-0006-000000000002', v_start + 10, v_start + 18, 0),
        ('b1000000-0000-0000-0009-000000000011', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000009', 'b1000000-0000-0000-0008-000000000002', 'TPLFNB-11',
         'Thi công cải tạo phần thô: cấp thoát nước quầy bar, điện 3 pha và hệ thống thông gió', 'Đường nước thải có bẫy mỡ, đường điện chịu tải cho máy pha cà phê công suất cao.', 'HIGH', v_creator_id, 8.0, 86400, 86400, '0|i00011:', 'b1000000-0000-0000-0006-000000000002', v_start + 21, v_start + 29, 0),
        ('b1000000-0000-0000-0009-000000000012', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000009', 'b1000000-0000-0000-0008-000000000002', 'TPLFNB-12',
         'Sản xuất và lắp đặt quầy bar chuyên dụng, bàn ghế và đèn chiếu sáng', 'Lắp đặt mặt đá quầy bar, bồn rửa âm, tủ kệ đựng syrup và bàn ghế.', 'MEDIUM', v_creator_id, 5.0, 57600, 57600, '0|i00012:', 'b1000000-0000-0000-0006-000000000002', v_start + 30, v_start + 37, 0),
        ('b1000000-0000-0000-0009-000000000013', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000009', 'b1000000-0000-0000-0008-000000000002', 'TPLFNB-13',
         'Thi công lắp đặt biển hiệu quảng cáo mặt tiền, biển vẫy LED và menu board', 'Gia công chữ nổi phát sáng mặt tiền, biển vẫy hai mặt và bảng thực đơn.', 'MEDIUM', v_creator_id, 3.0, 28800, 28800, '0|i00013:', 'b1000000-0000-0000-0006-000000000002', v_start + 38, v_start + 41, 0);

        -- Epic 3
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b1000000-0000-0000-0009-000000000014', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000001', 'b1000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLFNB-14',
                'Xây dựng Thực đơn (Menu), Định lượng & Nhập trang thiết bị', 'Chuẩn hóa công thức đồ uống/món ăn, tính giá vốn costing, mua sắm máy móc quầy bar và tìm kiếm nhà cung cấp nguyên vật liệu.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00014:', 'b1000000-0000-0000-0006-000000000004', v_start + 22, v_start + 42, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b1000000-0000-0000-0009-000000000015', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000014', 'b1000000-0000-0000-0008-000000000002', 'TPLFNB-15',
         'Chốt danh mục đồ uống chủ lực, công thức chuẩn hóa và tính giá vốn (Costing)', 'Xác định món signature, xây dựng định lượng chi tiết để tối ưu Food Cost dưới 30%.', 'HIGH', v_creator_id, 5.0, 43200, 43200, '0|i00015:', 'b1000000-0000-0000-0006-000000000004', v_start + 22, v_start + 27, 0),
        ('b1000000-0000-0000-0009-000000000016', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000014', 'b1000000-0000-0000-0008-000000000002', 'TPLFNB-16',
         'Mua sắm máy pha cà phê công nghiệp, máy xay, máy làm đá và tủ mát trưng bày', 'Chọn nhà phân phối chính hãng bảo hành tận nơi, lắp đặt và hiệu chỉnh áp suất chiết xuất.', 'HIGH', v_creator_id, 5.0, 28800, 28800, '0|i00016:', 'b1000000-0000-0000-0006-000000000003', v_start + 28, v_start + 34, 0),
        ('b1000000-0000-0000-0009-000000000017', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000014', 'b1000000-0000-0000-0008-000000000002', 'TPLFNB-17',
         'Tìm kiếm và ký hợp đồng cung cấp nguyên vật liệu tươi, cà phê hạt, trà và bao bì in thương hiệu', 'So sánh báo giá nhà cung ứng uy tín, đặt in ly cốc và túi mang đi có logo quán.', 'MEDIUM', v_creator_id, 3.0, 28800, 28800, '0|i00017:', 'b1000000-0000-0000-0006-000000000004', v_start + 35, v_start + 40, 0),
        ('b1000000-0000-0000-0009-000000000018', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000014', 'b1000000-0000-0000-0008-000000000002', 'TPLFNB-18',
         'Cài đặt hệ thống phần mềm quản lý bán hàng POS, máy in hóa đơn và mã QR thanh toán', 'Nhập thực đơn lên hệ thống POS, cấu hình phân quyền nhân viên thu ngân.', 'MEDIUM', v_creator_id, 3.0, 21600, 21600, '0|i00018:', 'b1000000-0000-0000-0006-000000000003', v_start + 39, v_start + 41, 0);

        -- Epic 4
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b1000000-0000-0000-0009-000000000019', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000001', 'b1000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLFNB-19',
                'Tuyển dụng, Đào tạo nhân sự & Chuẩn hóa quy trình phục vụ (SOP)', 'Đăng tuyển nhân sự các vị trí quầy bar, phục vụ, thu ngân; tổ chức đào tạo nội bộ kỹ năng pha chế và quy trình ứng xử với khách.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00019:', 'b1000000-0000-0000-0006-000000000005', v_start + 42, v_start + 55, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b1000000-0000-0000-0009-000000000020', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000019', 'b1000000-0000-0000-0008-000000000003', 'TPLFNB-20',
         'Đăng tin và tổ chức phỏng vấn tuyển dụng Quản lý cửa hàng, Barista, Thu ngân và Phục vụ', 'Lên tiêu chuẩn nhân sự, tổ chức phỏng vấn trực tiếp và chọn lựa các ứng viên có thái độ phù hợp.', 'HIGH', v_creator_id, 5.0, 50400, 50400, '0|i00020:', 'b1000000-0000-0000-0006-000000000005', v_start + 42, v_start + 47, 0),
        ('b1000000-0000-0000-0009-000000000021', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000019', 'b1000000-0000-0000-0008-000000000003', 'TPLFNB-21',
         'Tổ chức đào tạo nội bộ: kỹ năng pha chế theo công thức chuẩn, quy trình order và văn hóa phục vụ', 'Đào tạo kỹ năng chào đón khách, xử lý khiếu nại, kỹ thuật đánh sữa latte art và tốc độ ra đồ.', 'HIGH', v_creator_id, 8.0, 72000, 72000, '0|i00021:', 'b1000000-0000-0000-0006-000000000005', v_start + 48, v_start + 52, 0),
        ('b1000000-0000-0000-0009-000000000022', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000019', 'b1000000-0000-0000-0008-000000000003', 'TPLFNB-22',
         'Kiểm tra tay nghề thực tế (Dry-run), đánh giá sát hạch và phát đồng phục làm việc', 'Tổ chức buổi thi tay nghề blind test đồ uống trước toàn thể ban quản lý và cấp phát đồng phục.', 'MEDIUM', v_creator_id, 3.0, 21600, 21600, '0|i00022:', 'b1000000-0000-0000-0006-000000000005', v_start + 53, v_start + 55, 0);

        -- Epic 5
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b1000000-0000-0000-0009-000000000023', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000001', 'b1000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLFNB-23',
                'Truyền thông quảng bá, Vận hành thử nghiệm (Soft Opening) & Khai trương', 'Triển khai chiến dịch truyền thông đa kênh, tổ chức tuần lễ Soft Opening đón khách mời thử nghiệm và ngày hội Grand Opening chính thức.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00023:', 'b1000000-0000-0000-0006-000000000006', v_start + 44, v_start + 60, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b1000000-0000-0000-0009-000000000024', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000023', 'b1000000-0000-0000-0008-000000000003', 'TPLFNB-24',
         'Triển khai chiến dịch truyền thông: chụp ảnh menu, chạy quảng cáo Fanpage và phát voucher', 'Tạo dựng hình ảnh bắt mắt, liên kết với các Food Reviewer địa phương và chuẩn bị quà tặng mở màn.', 'HIGH', v_creator_id, 5.0, 43200, 43200, '0|i00024:', 'b1000000-0000-0000-0006-000000000006', v_start + 44, v_start + 53, 0),
        ('b1000000-0000-0000-0009-000000000025', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000023', 'b1000000-0000-0000-0008-000000000003', 'TPLFNB-25',
         'Tổ chức tuần lễ chạy thử (Soft Opening) đón khách thân thiết để kiểm tra tốc độ ra đồ', 'Phục vụ 50% công suất thực tế, ghi nhận ý kiến phản hồi về chất lượng đồ uống và dịch vụ.', 'HIGH', v_creator_id, 8.0, 57600, 57600, '0|i00025:', 'b1000000-0000-0000-0006-000000000006', v_start + 54, v_start + 57, 0),
        ('b1000000-0000-0000-0009-000000000026', 'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0002-000000000002', 'b1000000-0000-0000-0001-000000000001', 'b1000000-0000-0000-0009-000000000023', 'b1000000-0000-0000-0008-000000000003', 'TPLFNB-26',
         'Tổ chức ngày khai trương chính thức (Grand Opening): bố trí hoa tươi, chương trình giảm giá', 'Chương trình khuyến mãi Mua 1 Tặng 1, âm nhạc sôi động, đón tiếp khách hàng.', 'HIGH', v_creator_id, 5.0, 43200, 43200, '0|i00026:', 'b1000000-0000-0000-0006-000000000006', v_start + 58, v_start + 60, 0);

        PERFORM setval('work_item_seq_tplfnb', 26, true);
    END IF;

    -- =========================================================================
    -- 2. TEMPLATE 2: XÂY DỰNG WEBSITE BÁN HÀNG E-COMMERCE (TPLWEB)
    -- =========================================================================
    IF NOT EXISTS (SELECT 1 FROM projects WHERE key = 'TPLWEB') THEN
        -- 2.1 Project
        INSERT INTO projects (id, workspace_id, name, key, description, lead_user_id, created_by, default_assignee_type, project_type, is_template, template_category, start_date, end_date)
        VALUES ('b2000000-0000-0000-0000-000000000001', v_ws_id, 'Xây dựng Website Bán hàng E-Commerce', 'TPLWEB',
                'Bản mẫu quy trình phát triển website thương mại điện tử chuyên nghiệp theo phương pháp Agile/Scrum: từ phân tích đặc tả, thiết kế UI/UX Figma, phát triển Catalog, Giỏ hàng, Cổng thanh toán trực tuyến đến kiểm thử QA và Golive.',
                v_creator_id, v_creator_id, 'PROJECT_LEADER', 'SOFTWARE_SCRUM', TRUE, 'SOFTWARE', v_start, v_start + 90);

        -- 2.2 Roles & Permissions
        INSERT INTO project_roles (id, project_id, name, description) VALUES
        ('b2000000-0000-0000-0005-000000000001', 'b2000000-0000-0000-0000-000000000001', 'ADMINISTRATOR', 'Toàn quyền quản trị'),
        ('b2000000-0000-0000-0005-000000000002', 'b2000000-0000-0000-0000-000000000001', 'MEMBER', 'Thành viên phát triển'),
        ('b2000000-0000-0000-0005-000000000003', 'b2000000-0000-0000-0000-000000000001', 'VIEWER', 'Khách xem tiến độ');

        INSERT INTO permission_scheme_entries (project_id, role_id, permission_key) VALUES
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'PROJECT_VIEW'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'PROJECT_ADMIN'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'ISSUE_CREATE'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'ISSUE_EDIT'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'ISSUE_TRANSITION'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'ISSUE_ASSIGN'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'COMMENT_CREATE'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'COMMENT_EDIT_OWN'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'COMMENT_DELETE_OWN'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000001', 'ATTACHMENT_DELETE_OWN'),
        -- MEMBER
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000002', 'PROJECT_VIEW'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000002', 'ISSUE_CREATE'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000002', 'ISSUE_EDIT'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000002', 'ISSUE_TRANSITION'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000002', 'ISSUE_ASSIGN'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000002', 'COMMENT_CREATE'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000002', 'COMMENT_EDIT_OWN'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000002', 'COMMENT_DELETE_OWN'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000002', 'ATTACHMENT_DELETE_OWN'),
        -- VIEWER
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000003', 'PROJECT_VIEW'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000003', 'COMMENT_CREATE'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000003', 'COMMENT_EDIT_OWN'),
        ('b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0005-000000000003', 'COMMENT_DELETE_OWN');

        -- 2.3 Work Item Types
        INSERT INTO work_item_types (id, project_id, name, code, description, icon_name, color_hex, hierarchy_level, is_subtask, is_system_default) VALUES
        ('b2000000-0000-0000-0002-000000000001', 'b2000000-0000-0000-0000-000000000001', 'Epic', 'EPIC', 'Hạng mục lớn bao quát nhiều công việc', 'Zap', '#9333EA', 1, FALSE, TRUE),
        ('b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0000-000000000001', 'Task', 'TASK', 'Công việc tiêu chuẩn', 'CheckSquare', '#3B82F6', 0, FALSE, TRUE),
        ('b2000000-0000-0000-0002-000000000003', 'b2000000-0000-0000-0000-000000000001', 'Subtask', 'SUBTASK', 'Công việc con phụ thuộc của Task', 'GitCommit', '#06B6D4', -1, TRUE, TRUE);

        -- 2.4 Statuses (5 trạng thái chuẩn Software)
        INSERT INTO statuses (id, project_id, name, category, color_hex, description) VALUES
        ('b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0000-000000000001', 'To Do', 'TODO', '#64748B', 'Hạng mục công việc đang chờ thực hiện'),
        ('b2000000-0000-0000-0001-000000000002', 'b2000000-0000-0000-0000-000000000001', 'In Progress', 'IN_PROGRESS', '#3B82F6', 'Đang trong quá trình phát triển mã nguồn'),
        ('b2000000-0000-0000-0001-000000000003', 'b2000000-0000-0000-0000-000000000001', 'Code Review', 'IN_PROGRESS', '#8B5CF6', 'Đang rà soát mã nguồn Pull Request và kiểm tra tiêu chuẩn clean code'),
        ('b2000000-0000-0000-0001-000000000004', 'b2000000-0000-0000-0000-000000000001', 'Testing / QA', 'IN_PROGRESS', '#F59E0B', 'Đang kiểm thử chức năng, tích hợp và kiểm tra hiệu năng trên Staging'),
        ('b2000000-0000-0000-0001-000000000005', 'b2000000-0000-0000-0000-000000000001', 'Done', 'DONE', '#10B981', 'Đã hoàn thành kiểm thử và triển khai thành công');

        -- 2.5 Workflow & Transitions
        INSERT INTO workflows (id, project_id, name, description, is_default)
        VALUES ('b2000000-0000-0000-0003-000000000001', 'b2000000-0000-0000-0000-000000000001', 'Quy trình Scrum Phát triển Phần mềm E-Commerce', 'Luồng chuẩn kiểm soát chất lượng qua Code Review và QA trước khi Done', TRUE);

        INSERT INTO workflow_transitions (id, workflow_id, from_status_id, to_status_id, name) VALUES
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000001', NULL, 'b2000000-0000-0000-0001-000000000001', 'Create'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000001', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0001-000000000002', 'Start Progress'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000001', 'b2000000-0000-0000-0001-000000000002', 'b2000000-0000-0000-0001-000000000003', 'Submit PR / Review'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000001', 'b2000000-0000-0000-0001-000000000003', 'b2000000-0000-0000-0001-000000000004', 'Pass Review -> Testing'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000001', 'b2000000-0000-0000-0001-000000000003', 'b2000000-0000-0000-0001-000000000002', 'Request Changes'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000001', 'b2000000-0000-0000-0001-000000000004', 'b2000000-0000-0000-0001-000000000005', 'QA Approved -> Done'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000001', 'b2000000-0000-0000-0001-000000000004', 'b2000000-0000-0000-0001-000000000002', 'Bug Found / Re-open Code'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000001', 'b2000000-0000-0000-0001-000000000005', 'b2000000-0000-0000-0001-000000000002', 'Reopen Item'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000001', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0001-000000000005', 'Quick Done');

        INSERT INTO workflow_schemes (id, project_id, name, description)
        VALUES ('b2000000-0000-0000-0003-000000000002', 'b2000000-0000-0000-0000-000000000001', 'Quy trình Scrum E-Commerce Scheme', 'Gắn các loại công việc vào quy trình chuẩn');

        INSERT INTO workflow_scheme_mappings (id, scheme_id, work_item_type_id, workflow_id) VALUES
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000002', 'b2000000-0000-0000-0002-000000000001', 'b2000000-0000-0000-0003-000000000001'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000002', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0003-000000000001'),
        (gen_random_uuid(), 'b2000000-0000-0000-0003-000000000002', 'b2000000-0000-0000-0002-000000000003', 'b2000000-0000-0000-0003-000000000001');

        -- 2.6 Board & Columns (5 Cột)
        INSERT INTO boards (id, project_id, name, type)
        VALUES ('b2000000-0000-0000-0004-000000000001', 'b2000000-0000-0000-0000-000000000001', 'Bảng Scrum E-Commerce', 'SCRUM');

        INSERT INTO board_columns (id, board_id, name, column_order) VALUES
        ('b2000000-0000-0000-0004-000000000010', 'b2000000-0000-0000-0004-000000000001', 'BACKLOG & TO DO', 0),
        ('b2000000-0000-0000-0004-000000000011', 'b2000000-0000-0000-0004-000000000001', 'IN PROGRESS', 1),
        ('b2000000-0000-0000-0004-000000000012', 'b2000000-0000-0000-0004-000000000001', 'CODE REVIEW', 2),
        ('b2000000-0000-0000-0004-000000000013', 'b2000000-0000-0000-0004-000000000001', 'TESTING / QA', 3),
        ('b2000000-0000-0000-0004-000000000014', 'b2000000-0000-0000-0004-000000000001', 'DONE', 4);

        INSERT INTO board_column_statuses (column_id, status_id) VALUES
        ('b2000000-0000-0000-0004-000000000010', 'b2000000-0000-0000-0001-000000000001'),
        ('b2000000-0000-0000-0004-000000000011', 'b2000000-0000-0000-0001-000000000002'),
        ('b2000000-0000-0000-0004-000000000012', 'b2000000-0000-0000-0001-000000000003'),
        ('b2000000-0000-0000-0004-000000000013', 'b2000000-0000-0000-0001-000000000004'),
        ('b2000000-0000-0000-0004-000000000014', 'b2000000-0000-0000-0001-000000000005');

        -- 2.7 Components
        INSERT INTO components (id, project_id, name, description) VALUES
        ('b2000000-0000-0000-0006-000000000001', 'b2000000-0000-0000-0000-000000000001', 'Backend API', 'Dịch vụ lõi Spring Boot, xử lý dữ liệu và logic thanh toán'),
        ('b2000000-0000-0000-0006-000000000002', 'b2000000-0000-0000-0000-000000000001', 'Frontend Storefront', 'Giao diện khách hàng mua sắm React / Next.js tối ưu trải nghiệm'),
        ('b2000000-0000-0000-0006-000000000003', 'b2000000-0000-0000-0000-000000000001', 'Admin Portal', 'Hệ thống quản trị danh mục sản phẩm, đơn hàng và kho bãi'),
        ('b2000000-0000-0000-0006-000000000004', 'b2000000-0000-0000-0000-000000000001', 'Payment & Integration', 'Tích hợp cổng thanh toán VNPay, Momo, ZaloPay và đối tác vận chuyển'),
        ('b2000000-0000-0000-0006-000000000005', 'b2000000-0000-0000-0000-000000000001', 'DevOps & Security', 'Hạ tầng Docker, CI/CD, chứng chỉ SSL và bảo mật dữ liệu'),
        ('b2000000-0000-0000-0006-000000000006', 'b2000000-0000-0000-0000-000000000001', 'UI/UX Design', 'Hệ thống Design System, wireframe và prototype Figma');

        -- 2.8 Labels
        INSERT INTO labels (id, project_id, name, color_hex) VALUES
        ('b2000000-0000-0000-0007-000000000001', 'b2000000-0000-0000-0000-000000000001', 'frontend', '#3B82F6'),
        ('b2000000-0000-0000-0007-000000000002', 'b2000000-0000-0000-0000-000000000001', 'backend', '#10B981'),
        ('b2000000-0000-0000-0007-000000000003', 'b2000000-0000-0000-0000-000000000001', 'security', '#EF4444'),
        ('b2000000-0000-0000-0007-000000000004', 'b2000000-0000-0000-0000-000000000001', 'payment', '#F59E0B'),
        ('b2000000-0000-0000-0007-000000000005', 'b2000000-0000-0000-0000-000000000001', 'ui-ux', '#8B5CF6'),
        ('b2000000-0000-0000-0007-000000000006', 'b2000000-0000-0000-0000-000000000001', 'api', '#06B6D4');

        -- 2.9 Sprints (4 Sprints x 14 ngày)
        INSERT INTO sprints (id, project_id, name, goal, state, start_date, end_date, version) VALUES
        ('b2000000-0000-0000-0008-000000000001', 'b2000000-0000-0000-0000-000000000001', 'Sprint 1: Phân tích yêu cầu, Kiến trúc CSDL & UI/UX Design', 'Đặc tả yêu cầu chức năng SRS, thiết kế UI Figma và dựng khung mã nguồn hệ thống', 'FUTURE', (v_start || ' 00:00:00')::timestamp, ((v_start + 14) || ' 23:59:59')::timestamp, 0),
        ('b2000000-0000-0000-0008-000000000002', 'b2000000-0000-0000-0000-000000000001', 'Sprint 2: Quản lý Sản phẩm, Danh mục & Xác thực Người dùng', 'Phát triển Catalog sản phẩm đa biến thể, bộ lọc tìm kiếm và xác thực bảo mật tài khoản', 'FUTURE', ((v_start + 14) || ' 00:00:00')::timestamp, ((v_start + 28) || ' 23:59:59')::timestamp, 0),
        ('b2000000-0000-0000-0008-000000000003', 'b2000000-0000-0000-0000-000000000001', 'Sprint 3: Giỏ hàng, Đặt hàng & Cổng thanh toán trực tuyến', 'Hoàn thiện flow mua sắm từ Giỏ hàng, Áp dụng Voucher đến Thanh toán VNPay/Momo', 'FUTURE', ((v_start + 28) || ' 00:00:00')::timestamp, ((v_start + 42) || ' 23:59:59')::timestamp, 0),
        ('b2000000-0000-0000-0008-000000000004', 'b2000000-0000-0000-0000-000000000001', 'Sprint 4: Trang Quản trị Admin, Kiểm thử bảo mật & Go-Live', 'Dashboard quản trị doanh thu, kiểm thử tải hiệu năng và triển khai Production', 'FUTURE', ((v_start + 42) || ' 00:00:00')::timestamp, ((v_start + 56) || ' 23:59:59')::timestamp, 0);

        -- 2.10 Work Items
        -- Epic 1
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b2000000-0000-0000-0009-000000000001', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000001', 'b2000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLWEB-1',
                'Phân tích yêu cầu, Thiết kế kiến trúc & UI/UX Design Hệ thống', 'Khảo sát yêu cầu nghiệp vụ, hoàn thiện tài liệu SRS, thiết kế Prototype Figma và thiết lập kiến trúc cơ sở dữ liệu PostgreSQL.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00001:', 'b2000000-0000-0000-0006-000000000006', v_start + 1, v_start + 14, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b2000000-0000-0000-0009-000000000002', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000001', 'b2000000-0000-0000-0008-000000000001', 'TPLWEB-2',
         'Khảo sát yêu cầu nghiệp vụ và viết tài liệu đặc tả chức năng (SRS)', 'Làm rõ các luồng đặt hàng, xử lý tồn kho, chính sách hoàn trả và ma trận phân quyền người dùng.', 'HIGH', v_creator_id, 5.0, 57600, 57600, '0|i00002:', 'b2000000-0000-0000-0006-000000000001', v_start + 1, v_start + 5, 0),
        ('b2000000-0000-0000-0009-000000000003', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000001', 'b2000000-0000-0000-0008-000000000001', 'TPLWEB-3',
         'Thiết kế Wireframe và Prototype giao diện người dùng trên Figma', 'Xây dựng Design System đồng bộ, thiết kế phiên bản Desktop và Mobile-first đáp ứng tính thẩm mỹ cao.', 'HIGH', v_creator_id, 8.0, 86400, 86400, '0|i00003:', 'b2000000-0000-0000-0006-000000000006', v_start + 4, v_start + 12, 0),
        ('b2000000-0000-0000-0009-000000000004', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000003', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000003', 'b2000000-0000-0000-0008-000000000001', 'TPLWEB-4',
         'Thiết kế luồng trải nghiệm Home, Danh mục và Trang chi tiết sản phẩm', 'Thiết kế bố cục sản phẩm, gallery ảnh zoom, bảng chọn size/màu và khối đánh giá khách hàng.', 'MEDIUM', v_creator_id, NULL, 43200, 43200, '0|i00004:', 'b2000000-0000-0000-0006-000000000006', v_start + 4, v_start + 8, 0),
        ('b2000000-0000-0000-0009-000000000005', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000003', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000003', 'b2000000-0000-0000-0008-000000000001', 'TPLWEB-5',
         'Thiết kế luồng thanh toán Checkout 1 bước tối ưu chuyển đổi trên Mobile', 'Tối giản các bước điền thông tin giao hàng, chọn phương thức thanh toán và xem lại đơn hàng.', 'HIGH', v_creator_id, NULL, 43200, 43200, '0|i00005:', 'b2000000-0000-0000-0006-000000000006', v_start + 8, v_start + 12, 0),
        ('b2000000-0000-0000-0009-000000000006', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000001', 'b2000000-0000-0000-0008-000000000001', 'TPLWEB-6',
         'Thiết kế mô hình CSDL quan hệ PostgreSQL và dựng khung mã nguồn Boilerplate', 'Thiết kế ERD chuẩn hóa 3NF, index tối ưu truy vấn sản phẩm và khởi tạo repository backend/frontend.', 'HIGH', v_creator_id, 5.0, 57600, 57600, '0|i00006:', 'b2000000-0000-0000-0006-000000000001', v_start + 6, v_start + 13, 0);

        -- Epic 2
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b2000000-0000-0000-0009-000000000007', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000001', 'b2000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLWEB-7',
                'Phát triển Danh mục sản phẩm (Product Catalog) & Module Thành viên', 'Xây dựng API quản lý sản phẩm biến thể, bộ lọc tìm kiếm full-text search và module xác thực bảo mật tài khoản người dùng.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00007:', 'b2000000-0000-0000-0006-000000000001', v_start + 14, v_start + 28, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b2000000-0000-0000-0009-000000000008', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000007', 'b2000000-0000-0000-0008-000000000002', 'TPLWEB-8',
         'Phát triển RESTful API Quản lý danh mục đa cấp và sản phẩm có biến thể (Màu, Size)', 'Hỗ trợ phân cấp danh mục cha - con vô hạn cấp, quản lý SKU, giá bán, giá khuyến mãi và số lượng tồn kho.', 'HIGH', v_creator_id, 8.0, 86400, 86400, '0|i00008:', 'b2000000-0000-0000-0006-000000000001', v_start + 14, v_start + 21, 0),
        ('b2000000-0000-0000-0009-000000000009', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000007', 'b2000000-0000-0000-0008-000000000002', 'TPLWEB-9',
         'Xây dựng chức năng Tìm kiếm full-text search và Bộ lọc động sản phẩm', 'Lọc sản phẩm theo khoảng giá, danh mục, thương hiệu, đánh giá sao kết hợp sắp xếp đa tiêu chí.', 'MEDIUM', v_creator_id, 5.0, 57600, 57600, '0|i00009:', 'b2000000-0000-0000-0006-000000000002', v_start + 20, v_start + 26, 0),
        ('b2000000-0000-0000-0009-000000000010', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000007', 'b2000000-0000-0000-0008-000000000002', 'TPLWEB-10',
         'Module Xác thực tài khoản: Đăng ký, Đăng nhập OTP/Google OAuth2 và Quản lý Profile', 'Bảo mật mật khẩu Bcrypt, phát hành JWT token, làm mới refreshToken và quản lý sổ địa chỉ giao hàng.', 'HIGH', v_creator_id, 5.0, 64800, 64800, '0|i00010:', 'b2000000-0000-0000-0006-000000000001', v_start + 15, v_start + 23, 0);

        -- Epic 3
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b2000000-0000-0000-0009-000000000011', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000001', 'b2000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLWEB-11',
                'Giỏ hàng, Đặt hàng & Tích hợp Cổng thanh toán trực tuyến', 'Phát triển logic giỏ hàng lưu trữ Redis, mã giảm giá Coupon, checkout và tích hợp cổng thanh toán VNPay, Momo, thẻ ngân hàng.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00011:', 'b2000000-0000-0000-0006-000000000004', v_start + 28, v_start + 42, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b2000000-0000-0000-0009-000000000012', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000011', 'b2000000-0000-0000-0008-000000000003', 'TPLWEB-12',
         'Phát triển Giỏ hàng lưu trữ session/Redis và tự động đồng bộ khi khách đăng nhập', 'Cho phép khách vãng lai thêm hàng vào giỏ, tự động merge giỏ hàng khi người dùng đăng nhập tài khoản.', 'MEDIUM', v_creator_id, 5.0, 57600, 57600, '0|i00012:', 'b2000000-0000-0000-0006-000000000002', v_start + 28, v_start + 33, 0),
        ('b2000000-0000-0000-0009-000000000013', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000011', 'b2000000-0000-0000-0008-000000000003', 'TPLWEB-13',
         'Xây dựng module Mã giảm giá (Coupon), Tính phí vận chuyển và Khuyến mãi flash sale', 'Kiểm tra điều kiện áp dụng mã, giới hạn số lần sử dụng và tính toán phí ship tự động theo địa chỉ.', 'HIGH', v_creator_id, 8.0, 72000, 72000, '0|i00013:', 'b2000000-0000-0000-0006-000000000001', v_start + 32, v_start + 38, 0),
        ('b2000000-0000-0000-0009-000000000014', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000011', 'b2000000-0000-0000-0008-000000000003', 'TPLWEB-14',
         'Tích hợp cổng thanh toán trực tuyến: VNPay, Momo, ZaloPay và thẻ tín dụng Visa/Mastercard', 'Kết nối sandbox cổng thanh toán, xử lý callback redirect và kiểm tra tính toàn vẹn giao dịch.', 'HIGH', v_creator_id, 8.0, 86400, 86400, '0|i00014:', 'b2000000-0000-0000-0006-000000000004', v_start + 34, v_start + 41, 0),
        ('b2000000-0000-0000-0009-000000000015', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000003', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000014', 'b2000000-0000-0000-0008-000000000003', 'TPLWEB-15',
         'Tích hợp API tạo mã QR thanh toán động và redirect trang thanh toán VNPay', 'Tạo URL thanh toán có chữ ký HMAC-SHA512 kèm mã đơn hàng duy nhất.', 'HIGH', v_creator_id, NULL, 43200, 43200, '0|i00015:', 'b2000000-0000-0000-0006-000000000004', v_start + 34, v_start + 37, 0),
        ('b2000000-0000-0000-0009-000000000016', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000003', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000014', 'b2000000-0000-0000-0008-000000000003', 'TPLWEB-16',
         'Cài đặt Webhook (IPN) xử lý kết quả thanh toán bất đồng bộ và kiểm tra chữ ký số', 'Cập nhật trạng thái đơn hàng sang ĐÃ THANH TOÁN ngay khi nhận được tín hiệu IPN hợp lệ.', 'HIGH', v_creator_id, NULL, 43200, 43200, '0|i00016:', 'b2000000-0000-0000-0006-000000000004', v_start + 38, v_start + 41, 0),
        ('b2000000-0000-0000-0009-000000000017', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000011', 'b2000000-0000-0000-0008-000000000003', 'TPLWEB-17',
         'Hệ thống thông báo tự động gửi email/SMS xác nhận đơn hàng thành công kèm hóa đơn điện tử', 'Thiết kế template email HTML chuyên nghiệp chứa chi tiết món hàng, giá tiền và link theo dõi đơn.', 'LOW', v_creator_id, 3.0, 28800, 28800, '0|i00017:', 'b2000000-0000-0000-0006-000000000001', v_start + 37, v_start + 41, 0);

        -- Epic 4
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b2000000-0000-0000-0009-000000000018', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000001', 'b2000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLWEB-18',
                'Hệ thống Quản trị CMS (Admin Portal) & Quản lý Đơn hàng', 'Xây dựng trang Dashboard quản trị cho nhân viên bán hàng: theo dõi đơn mới, đóng gói, in vận đơn và biểu đồ báo cáo tài chính.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00018:', 'b2000000-0000-0000-0006-000000000003', v_start + 42, v_start + 51, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b2000000-0000-0000-0009-000000000019', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000018', 'b2000000-0000-0000-0008-000000000004', 'TPLWEB-19',
         'Xây dựng Dashboard biểu đồ thống kê doanh thu, tỷ lệ chuyển đổi đơn hàng và tồn kho sản phẩm', 'Biểu đồ trực quan hóa dữ liệu theo ngày, tháng, quý; cảnh báo sản phẩm sắp hết hàng trong kho.', 'HIGH', v_creator_id, 8.0, 72000, 72000, '0|i00019:', 'b2000000-0000-0000-0006-000000000003', v_start + 42, v_start + 48, 0),
        ('b2000000-0000-0000-0009-000000000020', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000018', 'b2000000-0000-0000-0008-000000000004', 'TPLWEB-20',
         'Chức năng Quản lý đơn hàng: Tiếp nhận, Đóng gói, In vận đơn và Bàn giao đơn vị vận chuyển', 'Quy trình xử lý đơn hàng chuẩn 5 bước giúp kho xuất hàng nhanh chóng, không bị sót đơn.', 'MEDIUM', v_creator_id, 5.0, 57600, 57600, '0|i00020:', 'b2000000-0000-0000-0006-000000000003', v_start + 45, v_start + 51, 0);

        -- Epic 5
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b2000000-0000-0000-0009-000000000021', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000001', 'b2000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLWEB-21',
                'Kiểm thử chất lượng (QA), Tối ưu hiệu năng & Triển khai Production', 'Kiểm thử bảo mật chống SQLi/XSS, stress test mô phỏng tải cao, tối ưu điểm Google Core Web Vitals và thiết lập CI/CD triển khai hệ thống.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00021:', 'b2000000-0000-0000-0006-000000000005', v_start + 48, v_start + 56, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b2000000-0000-0000-0009-000000000022', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000021', 'b2000000-0000-0000-0008-000000000004', 'TPLWEB-22',
         'Kiểm thử tải (Load Testing) mô phỏng 10,000 người dùng đồng thời trong sự kiện Sale bằng JMeter', 'Xác định điểm nghẽn cổ chai database pool, thời gian phản hồi API và tối ưu cấu hình Redis cache.', 'HIGH', v_creator_id, 5.0, 50400, 50400, '0|i00022:', 'b2000000-0000-0000-0006-000000000005', v_start + 48, v_start + 53, 0),
        ('b2000000-0000-0000-0009-000000000023', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000021', 'b2000000-0000-0000-0008-000000000004', 'TPLWEB-23',
         'Tối ưu Core Web Vitals (LCP < 2.5s, CLS < 0.1), SEO Onpage và nén ảnh định dạng WebP', 'Đạt điểm PageSpeed Insights trên 90 điểm cho cả Mobile và Desktop, tối ưu thẻ meta OpenGraph mạng xã hội.', 'MEDIUM', v_creator_id, 5.0, 43200, 43200, '0|i00023:', 'b2000000-0000-0000-0006-000000000002', v_start + 50, v_start + 54, 0),
        ('b2000000-0000-0000-0009-000000000024', 'b2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0002-000000000002', 'b2000000-0000-0000-0001-000000000001', 'b2000000-0000-0000-0009-000000000021', 'b2000000-0000-0000-0008-000000000004', 'TPLWEB-24',
         'Thiết lập pipeline CI/CD tự động build, test và triển khai lên môi trường Production Docker', 'Tự động chạy bộ test suite khi merge Pull Request vào nhánh main và rolling deploy không downtime.', 'HIGH', v_creator_id, 5.0, 50400, 50400, '0|i00024:', 'b2000000-0000-0000-0006-000000000005', v_start + 52, v_start + 56, 0);

        PERFORM setval('work_item_seq_tplweb', 24, true);
    END IF;

    -- =========================================================================
    -- 3. TEMPLATE 3: KẾ HOẠCH VẬN HÀNH MÙA VỤ CÂY ĂN QUẢ (TPLAGRI)
    -- =========================================================================
    IF NOT EXISTS (SELECT 1 FROM projects WHERE key = 'TPLAGRI') THEN
        -- 3.1 Project
        INSERT INTO projects (id, workspace_id, name, key, description, lead_user_id, created_by, default_assignee_type, project_type, is_template, template_category, start_date, end_date)
        VALUES ('b3000000-0000-0000-0000-000000000001', v_ws_id, 'Kế hoạch Vận hành Mùa vụ Cây ăn quả (Nông nghiệp)', 'TPLAGRI',
                'Mẫu kế hoạch quản lý toàn bộ chu kỳ 1 mùa vụ thu hoạch cây ăn quả (Sầu riêng, Xoài, Bưởi, Cam...) theo tiêu chuẩn VietGAP: từ phục hồi cây sau vụ trước, tỉa cành tạo tán, xử lý xiết nước kích hoa, nuôi quả, bao trái ngừa dịch hại đến thu hái, phân loại và xuất bán.',
                v_creator_id, v_creator_id, 'PROJECT_LEADER', 'SOFTWARE_SCRUM', TRUE, 'OPERATIONS', v_start, v_start + 90);

        -- 3.2 Roles & Permissions
        INSERT INTO project_roles (id, project_id, name, description) VALUES
        ('b3000000-0000-0000-0005-000000000001', 'b3000000-0000-0000-0000-000000000001', 'ADMINISTRATOR', 'Toàn quyền quản trị'),
        ('b3000000-0000-0000-0005-000000000002', 'b3000000-0000-0000-0000-000000000001', 'MEMBER', 'Thành viên nông vụ'),
        ('b3000000-0000-0000-0005-000000000003', 'b3000000-0000-0000-0000-000000000001', 'VIEWER', 'Khách tham quan');

        INSERT INTO permission_scheme_entries (project_id, role_id, permission_key) VALUES
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'PROJECT_VIEW'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'PROJECT_ADMIN'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'ISSUE_CREATE'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'ISSUE_EDIT'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'ISSUE_TRANSITION'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'ISSUE_ASSIGN'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'COMMENT_CREATE'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'COMMENT_EDIT_OWN'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'COMMENT_DELETE_OWN'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000001', 'ATTACHMENT_DELETE_OWN'),
        -- MEMBER
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000002', 'PROJECT_VIEW'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000002', 'ISSUE_CREATE'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000002', 'ISSUE_EDIT'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000002', 'ISSUE_TRANSITION'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000002', 'ISSUE_ASSIGN'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000002', 'COMMENT_CREATE'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000002', 'COMMENT_EDIT_OWN'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000002', 'COMMENT_DELETE_OWN'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000002', 'ATTACHMENT_DELETE_OWN'),
        -- VIEWER
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000003', 'PROJECT_VIEW'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000003', 'COMMENT_CREATE'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000003', 'COMMENT_EDIT_OWN'),
        ('b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0005-000000000003', 'COMMENT_DELETE_OWN');

        -- 3.3 Work Item Types
        INSERT INTO work_item_types (id, project_id, name, code, description, icon_name, color_hex, hierarchy_level, is_subtask, is_system_default) VALUES
        ('b3000000-0000-0000-0002-000000000001', 'b3000000-0000-0000-0000-000000000001', 'Epic', 'EPIC', 'Hạng mục lớn bao quát nhiều công việc', 'Zap', '#9333EA', 1, FALSE, TRUE),
        ('b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0000-000000000001', 'Task', 'TASK', 'Công việc tiêu chuẩn', 'CheckSquare', '#3B82F6', 0, FALSE, TRUE),
        ('b3000000-0000-0000-0002-000000000003', 'b3000000-0000-0000-0000-000000000001', 'Subtask', 'SUBTASK', 'Công việc con phụ thuộc của Task', 'GitCommit', '#06B6D4', -1, TRUE, TRUE);

        -- 3.4 Statuses
        INSERT INTO statuses (id, project_id, name, category, color_hex, description) VALUES
        ('b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0000-000000000001', 'Lập kế hoạch', 'TODO', '#64748B', 'Lên lịch kỹ thuật canh tác và dự trù vật tư nông nghiệp'),
        ('b3000000-0000-0000-0001-000000000002', 'b3000000-0000-0000-0000-000000000001', 'Đang canh tác', 'IN_PROGRESS', '#3B82F6', 'Đang triển khai bón phân, tưới nước, tỉa cành, kích hoa hoặc bao trái'),
        ('b3000000-0000-0000-0001-000000000003', 'b3000000-0000-0000-0000-000000000001', 'Kiểm tra kỹ thuật', 'IN_PROGRESS', '#F59E0B', 'Đo chỉ số pH đất, kiểm tra sâu bệnh hại, đo độ ngọt Brix và test dư lượng'),
        ('b3000000-0000-0000-0001-000000000004', 'b3000000-0000-0000-0000-000000000001', 'Đạt chuẩn / Đã thu hoạch', 'DONE', '#10B981', 'Nghiệm thu đạt chuẩn kỹ thuật VietGAP và xuất bán hoàn tất');

        -- 3.5 Workflow & Transitions
        INSERT INTO workflows (id, project_id, name, description, is_default)
        VALUES ('b3000000-0000-0000-0003-000000000001', 'b3000000-0000-0000-0000-000000000001', 'Quy trình Quản lý Mùa vụ Nông nghiệp VietGAP', 'Quy trình kiểm soát nghiêm ngặt từng giai đoạn sinh trưởng và an toàn thực phẩm', TRUE);

        INSERT INTO workflow_transitions (id, workflow_id, from_status_id, to_status_id, name) VALUES
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000001', NULL, 'b3000000-0000-0000-0001-000000000001', 'Create'),
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000001', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0001-000000000002', 'Bắt đầu canh tác'),
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000001', 'b3000000-0000-0000-0001-000000000002', 'b3000000-0000-0000-0001-000000000003', 'Yêu cầu kiểm tra kỹ thuật'),
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000001', 'b3000000-0000-0000-0001-000000000003', 'b3000000-0000-0000-0001-000000000004', 'Nghiệm thu đạt chuẩn'),
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000001', 'b3000000-0000-0000-0001-000000000003', 'b3000000-0000-0000-0001-000000000002', 'Xử lý bổ sung / Chăm sóc lại'),
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000001', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0001-000000000004', 'Nghiệm thu trực tiếp'),
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000001', 'b3000000-0000-0000-0001-000000000004', 'b3000000-0000-0000-0001-000000000002', 'Mở lại kiểm tra');

        INSERT INTO workflow_schemes (id, project_id, name, description)
        VALUES ('b3000000-0000-0000-0003-000000000002', 'b3000000-0000-0000-0000-000000000001', 'Quy trình Mùa vụ Nông nghiệp Scheme', 'Gắn các loại công việc vào quy trình chuẩn');

        INSERT INTO workflow_scheme_mappings (id, scheme_id, work_item_type_id, workflow_id) VALUES
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000002', 'b3000000-0000-0000-0002-000000000001', 'b3000000-0000-0000-0003-000000000001'),
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000002', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0003-000000000001'),
        (gen_random_uuid(), 'b3000000-0000-0000-0003-000000000002', 'b3000000-0000-0000-0002-000000000003', 'b3000000-0000-0000-0003-000000000001');

        -- 3.6 Board & Columns
        INSERT INTO boards (id, project_id, name, type)
        VALUES ('b3000000-0000-0000-0004-000000000001', 'b3000000-0000-0000-0000-000000000001', 'Bảng Quản lý Mùa vụ Nông nghiệp', 'SCRUM');

        INSERT INTO board_columns (id, board_id, name, column_order) VALUES
        ('b3000000-0000-0000-0004-000000000010', 'b3000000-0000-0000-0004-000000000001', 'KẾ HOẠCH NÔNG VỤ', 0),
        ('b3000000-0000-0000-0004-000000000011', 'b3000000-0000-0000-0004-000000000001', 'ĐANG THỰC HIỆN', 1),
        ('b3000000-0000-0000-0004-000000000012', 'b3000000-0000-0000-0004-000000000001', 'KIỂM ĐỊNH KỸ THUẬT', 2),
        ('b3000000-0000-0000-0004-000000000013', 'b3000000-0000-0000-0004-000000000001', 'HOÀN THÀNH', 3);

        INSERT INTO board_column_statuses (column_id, status_id) VALUES
        ('b3000000-0000-0000-0004-000000000010', 'b3000000-0000-0000-0001-000000000001'),
        ('b3000000-0000-0000-0004-000000000011', 'b3000000-0000-0000-0001-000000000002'),
        ('b3000000-0000-0000-0004-000000000012', 'b3000000-0000-0000-0001-000000000003'),
        ('b3000000-0000-0000-0004-000000000013', 'b3000000-0000-0000-0001-000000000004');

        -- 3.7 Components
        INSERT INTO components (id, project_id, name, description) VALUES
        ('b3000000-0000-0000-0006-000000000001', 'b3000000-0000-0000-0000-000000000001', 'Đất & Nước tưới', 'Xử lý độ pH đất, hệ thống tưới nhỏ giọt tự động, nguồn nước sạch'),
        ('b3000000-0000-0000-0006-000000000002', 'b3000000-0000-0000-0000-000000000001', 'Phân bón & Dinh dưỡng', 'Phân hữu cơ vi sinh, NPK định kỳ và phân bón lá nuôi quả'),
        ('b3000000-0000-0000-0006-000000000003', 'b3000000-0000-0000-0000-000000000001', 'Bảo vệ thực vật & Sâu bệnh', 'Chế phẩm sinh học phòng trừ nấm khuẩn, sâu đục thân, rệp sáp'),
        ('b3000000-0000-0000-0006-000000000004', 'b3000000-0000-0000-0000-000000000001', 'Tỉa cành & Kích hoa', 'Cắt tỉa cành tạo tán, xiết nước và kỹ thuật phân hóa mầm hoa'),
        ('b3000000-0000-0000-0006-000000000005', 'b3000000-0000-0000-0000-000000000001', 'Nuôi quả & Bao trái', 'Tỉa quả non, bao trái chống ruồi vàng, kiểm tra độ ngọt Brix'),
        ('b3000000-0000-0000-0006-000000000006', 'b3000000-0000-0000-0000-000000000001', 'Thu hoạch & Tiêu thụ', 'Kiểm tra dư lượng thuốc BVTV, hái quả, đóng gói dán tem QR');

        -- 3.8 Labels
        INSERT INTO labels (id, project_id, name, color_hex) VALUES
        ('b3000000-0000-0000-0007-000000000001', 'b3000000-0000-0000-0000-000000000001', 'phan-bon', '#10B981'),
        ('b3000000-0000-0000-0007-000000000002', 'b3000000-0000-0000-0000-000000000001', 'kich-hoa', '#F59E0B'),
        ('b3000000-0000-0000-0007-000000000003', 'b3000000-0000-0000-0000-000000000001', 'bao-trai', '#8B5CF6'),
        ('b3000000-0000-0000-0007-000000000004', 'b3000000-0000-0000-0000-000000000001', 'vietgap', '#3B82F6'),
        ('b3000000-0000-0000-0007-000000000005', 'b3000000-0000-0000-0000-000000000001', 'sau-benh', '#EF4444'),
        ('b3000000-0000-0000-0007-000000000006', 'b3000000-0000-0000-0000-000000000001', 'thu-hoach', '#EC4899');

        -- 3.9 Sprints (3 Giai đoạn)
        INSERT INTO sprints (id, project_id, name, goal, state, start_date, end_date, version) VALUES
        ('b3000000-0000-0000-0008-000000000001', 'b3000000-0000-0000-0000-000000000001', 'Giai đoạn 1: Cải tạo đất, Phục hồi cây & Xử lý phân hóa mầm hoa', 'Phân tích mẫu đất, bón lót phân vi sinh, cắt tỉa cành và kích thích ra hoa đồng loạt', 'FUTURE', (v_start || ' 00:00:00')::timestamp, ((v_start + 18) || ' 23:59:59')::timestamp, 0),
        ('b3000000-0000-0000-0008-000000000002', 'b3000000-0000-0000-0000-000000000001', 'Giai đoạn 2: Nuôi hoa, Thụ phấn & Chăm sóc giai đoạn đậu quả non', 'Bổ sung Canxi-Bo hỗ trợ hạt phấn sống, phun phòng bọ trĩ và tỉa định hình quả non', 'FUTURE', ((v_start + 18) || ' 00:00:00')::timestamp, ((v_start + 36) || ' 23:59:59')::timestamp, 0),
        ('b3000000-0000-0000-0008-000000000003', 'b3000000-0000-0000-0000-000000000001', 'Giai đoạn 3: Nuôi lớn quả, Bao trái chống ruồi đục & Thu hoạch xuất bán', 'Bao bọc túi chuyên dụng toàn vườn, bón kali tăng ngọt và thu hoạch xuất bán siêu thị', 'FUTURE', ((v_start + 36) || ' 00:00:00')::timestamp, ((v_start + 56) || ' 23:59:59')::timestamp, 0);

        -- 3.10 Work Items
        -- Epic 1
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b3000000-0000-0000-0009-000000000001', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000001', 'b3000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLAGRI-1',
                'Cải tạo đất vườn, Hệ thống tưới & Chuẩn bị vật tư nông nghiệp', 'Đo độ pH đất, khử chua bằng vôi bột, bảo dưỡng hệ thống béc tưới nhỏ giọt tự động và dự trù phân bón hữu cơ sinh học.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00001:', 'b3000000-0000-0000-0006-000000000001', v_start + 1, v_start + 18, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b3000000-0000-0000-0009-000000000002', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000001', 'b3000000-0000-0000-0008-000000000001', 'TPLAGRI-2',
         'Đo nồng độ pH đất, kiểm tra độ ẩm và rải vôi bột khử trùng toàn vườn', 'Đo pH tại các điểm rễ cây, xử lý đất chua đạt ngưỡng lý tưởng 5.5 - 6.5 để rễ hấp thu dưỡng chất.', 'HIGH', v_creator_id, 3.0, 28800, 28800, '0|i00002:', 'b3000000-0000-0000-0006-000000000001', v_start + 1, v_start + 6, 0),
        ('b3000000-0000-0000-0009-000000000003', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000003', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000002', 'b3000000-0000-0000-0008-000000000001', 'TPLAGRI-3',
         'Lấy mẫu đất tại 5 điểm đối xứng trong vườn để đo chỉ số pH bằng máy điện tử', 'Lấy mẫu ở tầng đất 10 - 20cm, ghi nhận vào sổ nhật ký canh tác điện tử.', 'MEDIUM', v_creator_id, NULL, 14400, 14400, '0|i00003:', 'b3000000-0000-0000-0006-000000000001', v_start + 1, v_start + 3, 0),
        ('b3000000-0000-0000-0009-000000000004', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000003', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000002', 'b3000000-0000-0000-0008-000000000001', 'TPLAGRI-4',
         'Rải vôi bột khử chua, cân bằng pH và diệt trừ mầm nấm rễ Phytophthora', 'Rải đều quanh tán cây theo định lượng 2kg vôi/gốc, tưới đẫm nước sau khi rải.', 'HIGH', v_creator_id, NULL, 14400, 14400, '0|i00004:', 'b3000000-0000-0000-0006-000000000001', v_start + 3, v_start + 6, 0),
        ('b3000000-0000-0000-0009-000000000005', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000001', 'b3000000-0000-0000-0008-000000000001', 'TPLAGRI-5',
         'Bảo dưỡng, vệ sinh hệ thống đường ống tưới nhỏ giọt và béc phun tự động', 'Sục rửa cặn lắng đường ống dẫn nước chính, kiểm tra áp lực bơm và thay béc hỏng.', 'MEDIUM', v_creator_id, 3.0, 28800, 28800, '0|i00005:', 'b3000000-0000-0000-0006-000000000001', v_start + 5, v_start + 10, 0),
        ('b3000000-0000-0000-0009-000000000006', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000001', 'b3000000-0000-0000-0008-000000000001', 'TPLAGRI-6',
         'Nhập kho phân chuồng hoai mục ủ nấm Trichoderma, phân hữu cơ vi sinh và chế phẩm sinh học', 'Tập kết vật tư nông nghiệp đạt chuẩn kiểm định chất lượng, bảo quản trong kho khô ráo.', 'HIGH', v_creator_id, 5.0, 43200, 43200, '0|i00006:', 'b3000000-0000-0000-0006-000000000002', v_start + 8, v_start + 15, 0);

        -- Epic 2
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b3000000-0000-0000-0009-000000000007', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000001', 'b3000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLAGRI-7',
                'Cắt tỉa cành, Kích thích ra hoa đồng loạt & Quản lý thụ phấn', 'Cắt tỉa cành tăm, kỹ thuật xiết nước kích mầm hoa, phun vi lượng Canxi - Bo tăng tỷ lệ đậu quả và phòng bọ trĩ phá hoa.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00007:', 'b3000000-0000-0000-0006-000000000004', v_start + 12, v_start + 36, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b3000000-0000-0000-0009-000000000008', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000007', 'b3000000-0000-0000-0008-000000000001', 'TPLAGRI-8',
         'Cắt tỉa cành tăm, cành sâu bệnh, cành vượt trong tán để vườn thông thoáng đón ánh sáng', 'Tạo độ thông thoáng cho cây quang hợp, triệt tiêu nơi trú ngụ của rệp sáp và nấm hồng.', 'HIGH', v_creator_id, 5.0, 57600, 57600, '0|i00008:', 'b3000000-0000-0000-0006-000000000004', v_start + 12, v_start + 17, 0),
        ('b3000000-0000-0000-0009-000000000009', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000007', 'b3000000-0000-0000-0008-000000000002', 'TPLAGRI-9',
         'Áp dụng quy trình kỹ thuật xiết nước (cắt nước) và phun phân bón lá lân cao để kích hoa', 'Cắt nước trong 10 - 15 ngày kích cây chuyển hóa sinh sản tạo mầm hoa (mắt cua).', 'HIGH', v_creator_id, 8.0, 57600, 57600, '0|i00009:', 'b3000000-0000-0000-0006-000000000004', v_start + 18, v_start + 24, 0),
        ('b3000000-0000-0000-0009-000000000010', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000007', 'b3000000-0000-0000-0008-000000000002', 'TPLAGRI-10',
         'Phun bổ sung vi lượng Canxi - Bo để tăng sức sống hạt phấn và hỗ trợ thụ phấn đậu quả', 'Phun định kỳ 2 lần trước khi hoa nở rộ 7 ngày giúp cuống hoa dẻo dai, chống rụng sinh lý.', 'HIGH', v_creator_id, 5.0, 36000, 36000, '0|i00010:', 'b3000000-0000-0000-0006-000000000002', v_start + 25, v_start + 30, 0),
        ('b3000000-0000-0000-0009-000000000011', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000007', 'b3000000-0000-0000-0008-000000000002', 'TPLAGRI-11',
         'Kiểm tra và phun phòng ngừa bọ trĩ, rệp sáp tấn công cuống hoa trong giai đoạn nhú mắt cua', 'Sử dụng dầu khoáng nông nghiệp hoặc hoạt chất sinh học an toàn cho ong thụ phấn.', 'MEDIUM', v_creator_id, 5.0, 43200, 43200, '0|i00011:', 'b3000000-0000-0000-0006-000000000003', v_start + 28, v_start + 35, 0);

        -- Epic 3
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b3000000-0000-0000-0009-000000000012', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000001', 'b3000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLAGRI-12',
                'Chăm sóc quả non, Tỉa định quả & Bao bọc quả ngừa sâu bệnh', 'Tuyển chọn những chùm quả khỏe nhất, tỉa bỏ quả méo mó dị tật và tiến hành bọc túi vải chuyên dụng cách ly ruồi vàng đục quả.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00012:', 'b3000000-0000-0000-0006-000000000005', v_start + 36, v_start + 48, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b3000000-0000-0000-0009-000000000013', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000012', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-13',
         'Tỉa bỏ quả non dị tật, quả méo mó, chỉ giữ lại số lượng chùm quả cân đối với sức cây', 'Mỗi chùm chỉ để từ 1 - 2 quả tròn đều, giúp cây tập trung dinh dưỡng nuôi quả đạt Size Loại 1.', 'HIGH', v_creator_id, 5.0, 50400, 50400, '0|i00013:', 'b3000000-0000-0000-0006-000000000005', v_start + 36, v_start + 41, 0),
        ('b3000000-0000-0000-0009-000000000014', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000012', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-14',
         'Phun chế phẩm sinh học gốc thảo mộc làm sạch bề mặt quả trước khi tiến hành bao bọc trái', 'Rửa sạch trứng sâu và bào tử nấm trên bề mặt vỏ quả trước khi đóng kín túi bao.', 'MEDIUM', v_creator_id, 3.0, 21600, 21600, '0|i00014:', 'b3000000-0000-0000-0006-000000000003', v_start + 40, v_start + 43, 0),
        ('b3000000-0000-0000-0009-000000000015', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000012', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-15',
         'Tiến hành bọc túi chuyên dụng (túi vải không dệt 2 lớp) phòng ngừa ruồi vàng đục quả', 'Bọc kín từng quả hoặc chùm quả, cố định miệng túi bằng dây rút nhôm chuyên dụng.', 'HIGH', v_creator_id, 8.0, 86400, 86400, '0|i00015:', 'b3000000-0000-0000-0006-000000000005', v_start + 42, v_start + 48, 0),
        ('b3000000-0000-0000-0009-000000000016', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000003', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000015', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-16',
         'Chuẩn bị túi bao trái đạt chuẩn VietGAP và dụng cụ thang nhôm chuyên dụng', 'Kiểm tra số lượng 5,000 túi bọc và kiểm tra độ an toàn của thang nhôm trước khi trèo.', 'MEDIUM', v_creator_id, NULL, 28800, 28800, '0|i00016:', 'b3000000-0000-0000-0006-000000000005', v_start + 42, v_start + 44, 0),
        ('b3000000-0000-0000-0009-000000000017', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000003', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000015', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-17',
         'Thực hiện bao bọc toàn bộ các cây ăn trái trong vườn theo đúng kỹ thuật', 'Chia đội nhân công phụ trách từng lô cây, đảm bảo không bỏ sót quả trên cành cao.', 'HIGH', v_creator_id, NULL, 57600, 57600, '0|i00017:', 'b3000000-0000-0000-0006-000000000005', v_start + 44, v_start + 48, 0);

        -- Epic 4
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b3000000-0000-0000-0009-000000000018', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000001', 'b3000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLAGRI-18',
                'Quản lý dinh dưỡng giai đoạn nuôi quả lớn & Tăng độ ngọt (Brix)', 'Bón phân cân đối hàm lượng Kali cao giúp trái đậm vị ngọt thơm, điều tiết nước tưới tránh nứt quả và kiểm tra chỉ số đường Brix định kỳ.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00018:', 'b3000000-0000-0000-0006-000000000002', v_start + 45, v_start + 54, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b3000000-0000-0000-0009-000000000019', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000018', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-19',
         'Bón phân NPK cân đối kết hợp phân hữu cơ Kali cao để nuôi cơm trái và tích lũy đường', 'Bón theo hình chiếu tán cây, kết hợp xới nhẹ và tưới nước giữ ẩm để cây hấp thu chậm.', 'HIGH', v_creator_id, 5.0, 36000, 36000, '0|i00019:', 'b3000000-0000-0000-0006-000000000002', v_start + 45, v_start + 50, 0),
        ('b3000000-0000-0000-0009-000000000020', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000018', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-20',
         'Điều tiết độ ẩm và lượng nước tưới hợp lý, tránh ngập úng gây hiện tượng nứt vỏ', 'Duy trì tưới nhẹ vào sáng sớm, giảm 50% lượng nước trước ngày thu hoạch 10 ngày để trái đậm vị.', 'MEDIUM', v_creator_id, 3.0, 28800, 28800, '0|i00020:', 'b3000000-0000-0000-0006-000000000001', v_start + 48, v_start + 53, 0),
        ('b3000000-0000-0000-0009-000000000021', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000018', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-21',
         'Lấy mẫu quả đo chỉ số độ ngọt Brix bằng khúc xạ kế định kỳ hàng tuần để chốt ngày thu hoạch', 'Test độ ngọt đạt chuẩn tối thiểu > 14 độ Brix đối với quả chín cây trước khi phê duyệt thu hoạch.', 'HIGH', v_creator_id, 3.0, 21600, 21600, '0|i00021:', 'b3000000-0000-0000-0006-000000000005', v_start + 51, v_start + 54, 0);

        -- Epic 5
        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version)
        VALUES ('b3000000-0000-0000-0009-000000000022', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000001', 'b3000000-0000-0000-0001-000000000001', NULL, NULL, 'TPLAGRI-22',
                'Thu hoạch đúng độ chín, Sơ chế phân loại & Tiêu thụ xuất bán', 'Cách ly thuốc BVTV đảm bảo dư lượng bằng 0, tổ chức thu hái quả bằng kéo chuyên dụng, đóng thùng dán tem QR truy xuất nguồn gốc và giao siêu thị.',
                'HIGH', v_creator_id, NULL, 0, 0, '0|i00022:', 'b3000000-0000-0000-0006-000000000006', v_start + 52, v_start + 56, 0);

        INSERT INTO work_items (id, project_id, type_id, status_id, parent_id, sprint_id, code, summary, description, priority, reporter_id, story_points, original_estimate_seconds, remaining_estimate_seconds, rank, component_id, start_date, due_date, version) VALUES
        ('b3000000-0000-0000-0009-000000000023', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000022', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-23',
         'Kiểm tra thời gian cách ly phân bón và thuốc BVTV, đảm bảo dư lượng bằng 0 đạt chuẩn VietGAP', 'Gửi mẫu trái đến trung tâm kiểm định độc lập kiểm tra dư lượng kim loại nặng và thuốc BVTV.', 'HIGH', v_creator_id, 5.0, 28800, 28800, '0|i00023:', 'b3000000-0000-0000-0006-000000000006', v_start + 52, v_start + 54, 0),
        ('b3000000-0000-0000-0009-000000000024', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000022', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-24',
         'Tổ chức thu hái quả bằng kéo chuyên dụng vào sáng sớm, xếp vào sọt lót xốp', 'Cắt chừa cuống 2 - 3cm, đặt nhẹ nhàng tránh dập vỏ gây thối hỏng trong quá trình vận chuyển.', 'HIGH', v_creator_id, 8.0, 57600, 57600, '0|i00024:', 'b3000000-0000-0000-0006-000000000006', v_start + 54, v_start + 55, 0),
        ('b3000000-0000-0000-0009-000000000025', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000022', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-25',
         'Sơ chế làm sạch, phân loại Size quả Loại 1 và Loại 2, đóng thùng carton và dán tem QR', 'Lau sạch bụi phấn, dán tem QR truy xuất nguồn gốc lô nông trại và đóng hộp chuẩn quy cách.', 'MEDIUM', v_creator_id, 5.0, 43200, 43200, '0|i00025:', 'b3000000-0000-0000-0006-000000000006', v_start + 55, v_start + 56, 0),
        ('b3000000-0000-0000-0009-000000000026', 'b3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0002-000000000002', 'b3000000-0000-0000-0001-000000000001', 'b3000000-0000-0000-0009-000000000022', 'b3000000-0000-0000-0008-000000000003', 'TPLAGRI-26',
         'Bàn giao lô hàng cho chuỗi cửa hàng hoa quả sạch / siêu thị đối tác và quyết toán mùa vụ', 'Ký biên bản nghiệm thu giao nhận số lượng, trọng lượng và tổng kết hạch toán mùa vụ.', 'HIGH', v_creator_id, 3.0, 21600, 21600, '0|i00026:', 'b3000000-0000-0000-0006-000000000006', v_start + 56, v_start + 56, 0);

        PERFORM setval('work_item_seq_tplagri', 26, true);
    END IF;

    -- Gán liên kết component maps & label maps
    INSERT INTO work_item_component_maps (work_item_id, component_id)
    SELECT id, component_id FROM work_items
    WHERE component_id IS NOT NULL AND project_id IN (
        'b1000000-0000-0000-0000-000000000001',
        'b2000000-0000-0000-0000-000000000001',
        'b3000000-0000-0000-0000-000000000001'
    )
    ON CONFLICT DO NOTHING;

    -- Gán role assignment cho creator
    INSERT INTO role_assignments (project_id, user_id, role_id) VALUES
    ('b1000000-0000-0000-0000-000000000001', v_creator_id, 'b1000000-0000-0000-0005-000000000001'),
    ('b2000000-0000-0000-0000-000000000001', v_creator_id, 'b2000000-0000-0000-0005-000000000001'),
    ('b3000000-0000-0000-0000-000000000001', v_creator_id, 'b3000000-0000-0000-0005-000000000001')
    ON CONFLICT DO NOTHING;

END $$;
