package com.datn.workservice.service;

import com.datn.workservice.client.NotificationClient;
import com.datn.workservice.dto.response.InvitationResponse;
import com.datn.workservice.enums.InvitationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Gửi email thông báo lời mời tham gia nhóm / dự án (template invite-member.html ở notification-service).
 * Tách khỏi InvitationServiceImpl để @Async có hiệu lực (self-invoke không qua proxy):
 * request mời trả về ngay, SMTP chậm hay chết chỉ ảnh hưởng thread nền. Fail-safe: lỗi chỉ log.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvitationMailer {

    private final NotificationClient notificationClient;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Async
    public void send(InvitationResponse inv) {
        if (inv.getInviteeEmail() == null || inv.getInviteeEmail().isBlank()) {
            log.warn("Bỏ qua email lời mời {}: không có email người được mời", inv.getId());
            return;
        }
        try {
            String kind = inv.getType() == InvitationType.TEAM ? "nhóm" : "dự án";
            String name = inv.getTargetName() == null ? "" : inv.getTargetName();
            notificationClient.sendTemplate(new NotificationClient.TemplateEmailRequest(
                    inv.getInviteeEmail(),
                    "[DATN-2026] Lời mời tham gia " + kind + " " + name,
                    "invite-member",
                    Map.of(
                            "recipient_name", inv.getInviteeName() == null ? "bạn" : inv.getInviteeName(),
                            "inviter_name", inv.getInviterName() == null ? "Một thành viên" : inv.getInviterName(),
                            "target_kind", kind,
                            "target_name", name,
                            "role", inv.getRoleName() == null ? "MEMBER" : inv.getRoleName(),
                            "link", frontendUrl + "/invitations")));
            log.info("Đã gửi email lời mời {} tới {}", inv.getId(), inv.getInviteeEmail());
        } catch (Exception e) {
            log.warn("Không gửi được email lời mời {} tới {}: {}", inv.getId(), inv.getInviteeEmail(), e.getMessage());
        }
    }
}
