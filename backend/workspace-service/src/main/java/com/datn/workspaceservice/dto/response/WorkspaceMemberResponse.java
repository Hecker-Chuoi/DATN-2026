package com.datn.workspaceservice.dto.response;

import com.datn.workspaceservice.enums.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một thành viên workspace, đã hydrate thông tin hiển thị từ identity-service.
 * <p>
 * Các trường username/email/fullName có thể null khi identity-service không phản
 * hồi: danh sách vẫn trả về (degrade) thay vì để cả request 500 - cùng thái độ
 * với ProjectMemberServiceImpl.getProjectMembers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMemberResponse {

    private UUID id;
    private UUID workspaceId;

    /** identity-service users.id. */
    private UUID userId;

    private WorkspaceRole role;
    private UUID invitedBy;
    private LocalDateTime joinedAt;

    private String username;
    private String email;
    private String fullName;
}
