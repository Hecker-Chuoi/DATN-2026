package com.datn.notification.dto;

import com.datn.notification.entity.Notification;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Một thông báo trả về cho trình duyệt (REST và WebSocket dùng chung). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        UUID id,
        String type,
        String category,
        String severity,
        boolean actionRequired,
        boolean actionResolved,
        String title,
        String body,
        String link,
        UUID workspaceId,
        UUID projectId,
        String projectKey,
        String projectName,
        String workspaceName,
        String entityType,
        UUID entityId,
        Actor actor,
        int actorCount,
        boolean read,
        Instant createdAt,
        Instant updatedAt
) {

    public record Actor(UUID id, String name) {}

    public static NotificationResponse from(Notification n) {
        Map<String, Object> d = n.getData() == null ? Map.of() : n.getData();
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getCategory().name(),
                n.getSeverity().name(),
                n.isActionRequired(),
                n.getActionResolvedAt() != null,
                n.getTitle(),
                n.getBody(),
                n.getLink(),
                n.getWorkspaceId(),
                n.getProjectId(),
                str(d.get("projectKey")),
                str(d.get("projectName")),
                str(d.get("workspaceName")),
                n.getEntityType(),
                n.getEntityId(),
                n.getActorId() == null ? null : new Actor(n.getActorId(), str(d.get("actorName"))),
                n.getActorCount(),
                n.getReadAt() != null,
                n.getCreatedAt(),
                n.getUpdatedAt());
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
