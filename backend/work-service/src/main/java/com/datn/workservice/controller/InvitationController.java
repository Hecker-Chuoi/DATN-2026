package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.response.InvitationResponse;
import com.datn.workservice.enums.InvitationType;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.security.TeamSecurity;
import com.datn.workservice.service.InvitationService;
import com.datn.workservice.service.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lời mời tham gia nhóm / dự án.
 * Việc tạo lời mời nằm ở POST /teams/{id}/members và POST /projects/{id}/members.
 */
@RestController
@RequestMapping("/api/v1/work/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final TeamSecurity teamSecurity;
    private final ProjectAccessService projectAccessService;

    /** Lời mời đang chờ tôi trả lời */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> listMine() {
        return ResponseEntity.ok(ApiResponse.success(invitationService.listMine(SecurityUtils.getCurrentUserId())));
    }

    /** Lời mời đang chờ của một nhóm (trưởng nhóm) hoặc dự án (PROJECT_ADMIN) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> listForTarget(
            @RequestParam(value = "teamId", required = false) UUID teamId,
            @RequestParam(value = "projectId", required = false) UUID projectId
    ) {
        if (teamId != null) {
            if (!teamSecurity.canManageTeam(teamId)) {
                throw new AccessDeniedException("Chỉ quản trị viên hoặc trưởng nhóm mới xem được lời mời của nhóm");
            }
            return ResponseEntity.ok(ApiResponse.success(invitationService.listForTarget(InvitationType.TEAM, teamId)));
        }
        if (projectId != null) {
            projectAccessService.assertProjectPermission(projectId, PermissionKey.PROJECT_ADMIN);
            return ResponseEntity.ok(ApiResponse.success(invitationService.listForTarget(InvitationType.PROJECT, projectId)));
        }
        throw new AccessDeniedException("Cần chỉ định teamId hoặc projectId");
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<InvitationResponse>> accept(@PathVariable("id") UUID id) {
        InvitationResponse res = invitationService.accept(id, SecurityUtils.getCurrentUserId());
        String name = res.getTargetName() != null ? res.getTargetName() : "";
        return ResponseEntity.ok(ApiResponse.success(res, "Bạn đã tham gia " + name));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<ApiResponse<InvitationResponse>> decline(@PathVariable("id") UUID id) {
        InvitationResponse res = invitationService.decline(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(res, "Đã từ chối lời mời"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<InvitationResponse>> cancel(@PathVariable("id") UUID id) {
        InvitationResponse res = invitationService.cancel(id, SecurityUtils.getCurrentUserId(), SecurityUtils.isGlobalAdmin());
        return ResponseEntity.ok(ApiResponse.success(res, "Đã hủy lời mời"));
    }
}
