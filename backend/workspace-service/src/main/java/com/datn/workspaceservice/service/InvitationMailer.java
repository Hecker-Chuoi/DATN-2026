package com.datn.workspaceservice.service;

import com.datn.workspaceservice.client.NotificationClient;
import com.datn.workspaceservice.dto.response.WorkspaceInvitationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Gửi email thông báo lời mời tham gia workspace (template invite-member.html ở notification-service).
 * Tách class để @Async có hiệu lực (self-invoke không qua proxy). Fail-safe: lỗi chỉ log.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvitationMailer {

    private final NotificationClient notificationClient;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Async
    public void send(WorkspaceInvitationResponse inv) {
        if (inv.getInviteeEmail() == null || inv.getInviteeEmail().isBlank()) {
            log.warn("Bỏ qua email lời mời {}: không có email người được mời", inv.getId());
            return;
        }
        try {
            String name = inv.getWorkspaceName() == null ? "" : inv.getWorkspaceName();
            notificationClient.sendTemplate(new NotificationClient.TemplateEmailRequest(
                    inv.getInviteeEmail(),
                    "[DATN-2026] Lời mời tham gia workspace " + name,
                    "invite-member",
                    Map.of(
                            "recipient_name", inv.getInviteeName() == null ? "bạn" : inv.getInviteeName(),
                            "inviter_name", inv.getInviterName() == null ? "Một thành viên" : inv.getInviterName(),
                            "target_kind", "workspace",
                            "target_name", name,
                            "role", inv.getRole() == null ? "MEMBER" : inv.getRole().name(),
                            "link", frontendUrl + "/invitations")));
            log.info("Đã gửi email lời mời {} tới {}", inv.getId(), inv.getInviteeEmail());
        } catch (Exception e) {
            log.warn("Không gửi được email lời mời {} tới {}: {}", inv.getId(), inv.getInviteeEmail(), e.getMessage());
        }
    }
}
