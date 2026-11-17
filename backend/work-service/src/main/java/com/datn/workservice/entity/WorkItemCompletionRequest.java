package com.datn.workservice.entity;

import com.datn.workservice.enums.CompletionRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Yêu cầu xác nhận hoàn thành ("Tạm hoàn thành") của một công việc.
 *
 * Thành viên không phải Admin dự án chuyển công việc sang nhóm trạng thái DONE thì hệ thống
 * KHÔNG đổi trạng thái ngay mà ghi một bản ghi PENDING; chỉ Admin dự án mới xác nhận được.
 * Bảng lưu lại lịch sử, không xóa bản ghi cũ.
 */
@Entity
@Table(name = "work_item_completion_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemCompletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    /** Bước chuyển người yêu cầu đã chọn; null khi hệ thống tự tạo (vd đánh dấu trùng lặp). */
    @Column(name = "transition_id")
    private UUID transitionId;

    @Column(name = "target_status_id", nullable = false)
    private UUID targetStatusId;

    @Column(length = 100)
    private String resolution;

    @Column(name = "resolve_all_subtasks", nullable = false)
    @Builder.Default
    private Boolean resolveAllSubtasks = false;

    @Column(name = "request_note", columnDefinition = "TEXT")
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CompletionRequestStatus status = CompletionRequestStatus.PENDING;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;
}
