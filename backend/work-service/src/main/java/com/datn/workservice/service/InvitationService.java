package com.datn.workservice.service;

import com.datn.workservice.dto.response.InvitationResponse;
import com.datn.workservice.enums.InvitationType;

import java.util.List;
import java.util.UUID;

public interface InvitationService {
    /** inviteeUserId có thể là id identity hoặc Keycloak sub, service tự quy đổi về Keycloak sub */
    InvitationResponse inviteToProject(UUID projectId, UUID inviteeUserId, UUID roleId, UUID invitedBy);
    /** Lời mời đang chờ gửi tới người dùng hiện tại */
    List<InvitationResponse> listMine(UUID userId);
    /** Lời mời đang chờ của một dự án (cho quản trị dự án) */
    List<InvitationResponse> listForTarget(InvitationType type, UUID targetId);
    InvitationResponse accept(UUID invitationId, UUID userId);
    InvitationResponse decline(UUID invitationId, UUID userId);
    /** Người gửi, Global ADMIN hoặc quản trị dự án được hủy lời mời đang chờ */
    InvitationResponse cancel(UUID invitationId, UUID userId, boolean globalAdmin);
}
