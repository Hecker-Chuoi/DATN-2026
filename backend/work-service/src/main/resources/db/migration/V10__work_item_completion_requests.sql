-- ============================================================================
-- DATN-2026: WORK SERVICE
-- Flyway Migration: V10__work_item_completion_requests.sql
-- Description: Yêu cầu xác nhận hoàn thành ("Tạm hoàn thành") — BR-W01..BR-W08
--
-- Thành viên không phải Admin dự án chuyển công việc sang trạng thái nhóm DONE
-- thì KHÔNG đổi trạng thái ngay mà tạo một yêu cầu PENDING ở bảng này.
-- Chỉ Admin dự án mới xác nhận (APPROVED) hoặc từ chối (REJECTED).
-- Bảng giữ lại toàn bộ lịch sử yêu cầu, không xóa bản ghi cũ.
-- ============================================================================

CREATE TABLE IF NOT EXISTS work_item_completion_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    transition_id UUID,                          -- Bước chuyển người yêu cầu đã chọn (NULL: hệ thống tự tạo, vd đánh dấu trùng lặp)
    target_status_id UUID NOT NULL,              -- Trạng thái DONE đích
    resolution VARCHAR(100),                     -- Kết quả xử lý đề xuất (DONE, DUPLICATE...)
    resolve_all_subtasks BOOLEAN NOT NULL DEFAULT FALSE,
    request_note TEXT,                           -- Ghi chú của người yêu cầu
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, CANCELLED
    requested_by UUID NOT NULL,                  -- Keycloak sub
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by UUID,                             -- Admin xác nhận / từ chối, hoặc người hủy
    decided_at TIMESTAMP,
    decision_note TEXT
);

-- Mỗi công việc chỉ có tối đa MỘT yêu cầu đang chờ
CREATE UNIQUE INDEX IF NOT EXISTS uq_completion_request_pending
    ON work_item_completion_requests(work_item_id) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_completion_request_project_status
    ON work_item_completion_requests(project_id, status);
CREATE INDEX IF NOT EXISTS idx_completion_request_item
    ON work_item_completion_requests(work_item_id, requested_at DESC);
