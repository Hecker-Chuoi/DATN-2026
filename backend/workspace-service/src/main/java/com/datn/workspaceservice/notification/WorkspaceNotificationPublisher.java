package com.datn.workspaceservice.notification;

import com.datn.common.notification.NotificationEvent;
import com.datn.common.notification.NotificationType;
import com.datn.common.notification.RecipientReason;
import com.datn.workspaceservice.client.IdentityClient;
import com.datn.workspaceservice.client.dto.UserResponse;
import com.datn.workspaceservice.entity.Workspace;
import com.datn.workspaceservice.entity.WorkspaceInvitation;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.repository.WorkspaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.datn.common.notification.NotificationType.*;

/**
 * Thông báo nhóm G8–G11 (không gian làm việc). Ghi sự kiện vào bảng outbox_events (dùng chung DB với
 * work-service) trong CÙNG transaction với nghiệp vụ; OutboxRelay của work-service gửi lên Kafka.
 *
 * workspace-service lưu user theo users.id của identity-service, còn thông báo dùng Keycloak sub,
 * nên mọi id được đổi sang sub qua {@code GET /internal/users/{id}} trước khi ghi.
 * Lỗi trong phần thông báo chỉ ghi log, không làm hỏng nghiệp vụ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceNotificationPublisher {

    public static final String SOURCE = "workspace-service";

    private final JdbcTemplate jdbcTemplate;
    private final IdentityClient identityClient;
    private final WorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper;

    /** G8: người được mời (chưa là thành viên). */
    public void invitationReceived(WorkspaceInvitation inv, UUID actorUserId) {
        publish(WORKSPACE_INVITATION_RECEIVED, inv.getWorkspaceId(), "INVITATION", inv.getId(), actorUserId,
                Map.of(inv.getInviteeUserId(), RecipientReason.INVITEE),
                Map.of("role", String.valueOf(inv.getRole())));
    }

    /** G11 cho người mời; đồng thời đóng G8 của người được mời (R9). */
    public void invitationAnswered(WorkspaceInvitation inv, boolean accepted, UUID actorUserId) {
        Map<UUID, RecipientReason> to = new LinkedHashMap<>();
        if (inv.getInvitedBy() != null) to.put(inv.getInvitedBy(), RecipientReason.INVITER);
        publish(WORKSPACE_INVITATION_ANSWERED, inv.getWorkspaceId(), "INVITATION", inv.getId(), actorUserId, to,
                Map.of("accepted", accepted, "role", String.valueOf(inv.getRole())));
    }

    /** Lời mời bị huỷ: chỉ đóng G8 (sự kiện nội bộ). */
    public void invitationCancelled(WorkspaceInvitation inv, UUID actorUserId) {
        publish(WORKSPACE_INVITATION_CANCELLED, inv.getWorkspaceId(), "INVITATION", inv.getId(), actorUserId, Map.of(), Map.of());
    }

    /** G10. */
    public void roleChanged(UUID workspaceId, UUID targetUserId, WorkspaceRole oldRole, WorkspaceRole newRole, UUID actorUserId) {
        if (Objects.equals(oldRole, newRole)) return;
        publish(WORKSPACE_ROLE_CHANGED, workspaceId, "WORKSPACE", workspaceId, actorUserId,
                Map.of(targetUserId, RecipientReason.TARGET_USER),
                Map.of("oldRole", String.valueOf(oldRole), "newRole", String.valueOf(newRole)));
    }

    /** G9. */
    public void memberRemoved(UUID workspaceId, UUID targetUserId, UUID actorUserId) {
        publish(WORKSPACE_MEMBER_REMOVED, workspaceId, "WORKSPACE", workspaceId, actorUserId,
                Map.of(targetUserId, RecipientReason.TARGET_USER), Map.of());
    }

    // ─────────────────────────────────────────────────────────────

    private void publish(NotificationType type, UUID workspaceId, String entityType, UUID entityId, UUID actorUserId,
                         Map<UUID, RecipientReason> recipients, Map<String, Object> data) {
        try {
            Workspace ws = workspaceId == null ? null : workspaceRepository.findById(workspaceId).orElse(null);
            if (ws == null) return;
            String actorSub = toSub(actorUserId);

            List<NotificationEvent.Recipient> rs = new ArrayList<>();
            recipients.forEach((userId, reason) -> {
                String sub = toSub(userId);
                if (sub != null && !sub.equalsIgnoreCase(Objects.toString(actorSub, ""))) {
                    rs.add(new NotificationEvent.Recipient(sub, reason.name()));
                }
            });
            if (rs.isEmpty() && !type.isInternal()) return;

            NotificationEvent event = NotificationEvent.builder()
                    .type(type.name())
                    .source(SOURCE)
                    .occurredAt(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).toString())
                    .actorId(actorSub)
                    .workspaceId(workspaceId.toString())
                    .workspaceName(ws.getName())
                    .entityType(entityType)
                    .entityId(entityId == null ? null : entityId.toString())
                    .entityTitle(ws.getName())
                    .recipients(rs)
                    .data(new LinkedHashMap<>(data))
                    .build();
            String json = objectMapper.writeValueAsString(event);
            jdbcTemplate.update("""
                    INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, created_at)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb), 'PENDING', 0, now())
                    """, UUID.randomUUID(), NotificationEvent.AGGREGATE_TYPE,
                    (entityId != null ? entityId : workspaceId).toString(), type.name(), json);
            log.debug("Ghi outbox thông báo {} ({} người nhận)", type, rs.size());
        } catch (Exception e) {
            log.warn("Không phát được thông báo {}: {}", type, e.getMessage());
        }
    }

    /** users.id (hoặc sub) → Keycloak sub; null nếu không tra được. */
    String toSub(UUID userId) {
        if (userId == null) return null;
        try {
            var res = identityClient.getUserById(userId);
            UserResponse u = res == null ? null : res.getData();
            if (u == null) return null;
            return u.getKeycloakId() != null && !u.getKeycloakId().isBlank() ? u.getKeycloakId() : null;
        } catch (Exception e) {
            log.warn("Không đổi được user {} sang Keycloak sub: {}", userId, e.getMessage());
            return null;
        }
    }
}
