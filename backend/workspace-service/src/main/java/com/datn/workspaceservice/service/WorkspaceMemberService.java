package com.datn.workspaceservice.service;

import com.datn.common.dto.PageResponse;
import com.datn.workspaceservice.dto.request.AddWorkspaceMemberRequest;
import com.datn.workspaceservice.dto.response.WorkspaceInvitationResponse;
import com.datn.workspaceservice.dto.response.WorkspaceMemberResponse;
import com.datn.workspaceservice.enums.WorkspaceRole;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quản trị thành viên workspace.
 * <p>
 * Theo quy ước của ProjectMemberService: mọi method nhận currentUserId qua THAM
 * SỐ, service không bao giờ tự đọc SecurityContext. Nhờ vậy unit test không cần
 * dựng SecurityContext, và controller/aspect là nơi duy nhất biết về HTTP.
 */
public interface WorkspaceMemberService {

    /**
     * Vai trò của user trong workspace, hoặc empty nếu không phải thành viên.
     * Đây là method mà WorkspaceRoleGuard gọi trên mọi request được bảo vệ, nên
     * nó phải là một lần hit index duy nhất - không Feign, không alias id.
     */
    Optional<WorkspaceRole> findRole(UUID workspaceId, UUID userId);

    /** Số OWNER hiện tại. Dùng cho bất biến "workspace luôn có ít nhất một OWNER". */
    long countOwners(UUID workspaceId);

    PageResponse<WorkspaceMemberResponse> getMembers(UUID workspaceId,
                                                     UUID currentUserId,
                                                     String search,
                                                     WorkspaceRole roleFilter,
                                                     Pageable pageable);

    WorkspaceMemberResponse getMyMembership(UUID workspaceId, UUID currentUserId);

    /**
     * Tạo lời mời (không thêm thẳng). Người được mời chấp nhận qua acceptInvitation
     * mới có hàng workspace_members. Gửi email thông báo, fail-safe.
     */
    WorkspaceInvitationResponse invite(UUID workspaceId,
                                       AddWorkspaceMemberRequest request,
                                       UUID currentUserId);

    List<WorkspaceInvitationResponse> listMyInvitations(UUID userId);

    List<WorkspaceInvitationResponse> listInvitations(UUID workspaceId);

    WorkspaceInvitationResponse acceptInvitation(UUID invitationId, UUID userId);

    WorkspaceInvitationResponse declineInvitation(UUID invitationId, UUID userId);

    /** Hủy bởi người mời hoặc ADMIN+ của workspace. */
    WorkspaceInvitationResponse cancelInvitation(UUID invitationId, UUID userId);

    WorkspaceMemberResponse updateMemberRole(UUID workspaceId,
                                             UUID targetUserId,
                                             WorkspaceRole newRole,
                                             UUID currentUserId);

    void removeMember(UUID workspaceId, UUID targetUserId, UUID currentUserId);

    void leaveWorkspace(UUID workspaceId, UUID currentUserId);

    /**
     * Tự động đảm bảo user có mặt trong workspace với vai trò tối thiểu (Smart Auto-Cascade).
     * Idempotent: nếu user đã là thành viên thì không làm gì.
     */
    void ensureMember(UUID workspaceId, UUID userId, WorkspaceRole role, UUID invitedBy);
}
