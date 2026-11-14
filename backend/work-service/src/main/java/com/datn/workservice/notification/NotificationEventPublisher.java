package com.datn.workservice.notification;

import com.datn.common.notification.NotificationEvent;
import com.datn.common.notification.NotificationType;
import com.datn.common.notification.RecipientReason;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.service.OutboxRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Phát sự kiện thông báo của work-service. Mỗi sự kiện được GHI VÀO OUTBOX trong cùng transaction
 * với nghiệp vụ (Transactional Outbox), OutboxRelay sẽ gửi lên Kafka sau khi commit.
 *
 * <pre>
 * notificationEvents.event(NotificationType.TASK_ASSIGNED, project)
 *         .item(item).actor(userId)
 *         .to(item.getAssigneeId(), RecipientReason.NEW_ASSIGNEE)
 *         .data("priority", item.getPriority())
 *         .publish();
 * </pre>
 *
 * Quy tắc áp dụng khi publish: bỏ dự án mẫu (R7), bỏ người thao tác (R1), chỉ giữ thành viên dự án (R2,
 * trừ khi {@link Draft#allowNonMembers()}). Lỗi trong phần thông báo chỉ ghi log, không làm hỏng nghiệp vụ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    public static final String SOURCE = "work-service";

    private final OutboxRecorder outboxRecorder;
    private final NotificationRecipientResolver recipientResolver;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public Draft event(NotificationType type, Project project) {
        return new Draft(type, project);
    }

    public Draft event(NotificationType type, UUID projectId) {
        Project project = projectId == null ? null : projectRepository.findById(projectId).orElse(null);
        return new Draft(type, project);
    }

    public NotificationRecipientResolver recipients() {
        return recipientResolver;
    }

    /** Bản nháp một sự kiện; gọi {@link #publish()} để ghi vào outbox. */
    public final class Draft {
        private final NotificationType type;
        private final Project project;
        private final Map<UUID, RecipientReason> recipients = new LinkedHashMap<>();
        private final Map<String, Object> data = new LinkedHashMap<>();
        private UUID actorId;
        private String entityType;
        private UUID entityId;
        private String entityCode;
        private String entityTitle;
        private String dedupKey;
        private boolean allowNonMembers;
        private boolean allowDeletedProject;

        private Draft(NotificationType type, Project project) {
            this.type = type;
            this.project = project;
        }

        public Draft entity(String type, UUID id, String code, String title) {
            this.entityType = type;
            this.entityId = id;
            this.entityCode = code;
            this.entityTitle = title;
            return this;
        }

        public Draft item(WorkItem item) {
            return entity("WORK_ITEM", item.getId(), item.getCode(), item.getSummary());
        }

        public Draft sprint(Sprint sprint) {
            data("sprintName", sprint.getName());
            return entity("SPRINT", sprint.getId(), null, sprint.getName());
        }

        public Draft projectEntity() {
            return project == null ? this : entity("PROJECT", project.getId(), project.getKey(), project.getName());
        }

        public Draft actor(UUID actorId) {
            this.actorId = actorId;
            return this;
        }

        /** Thêm người nhận; một người nhiều lý do thì giữ lý do mạnh nhất (R3). */
        public Draft to(UUID userId, RecipientReason reason) {
            if (userId != null) {
                recipients.merge(userId, reason, (a, b) -> a.strength() <= b.strength() ? a : b);
            }
            return this;
        }

        public Draft toAll(Collection<UUID> userIds, RecipientReason reason) {
            if (userIds != null) userIds.forEach(u -> to(u, reason));
            return this;
        }

        /** Giá trị ngày/giờ, UUID, enum được đổi sang chuỗi để JSON gọn và ổn định. */
        public Draft data(String key, Object value) {
            if (value != null) data.put(key, normalize(value));
            return this;
        }

        public Draft dedupKey(String key) {
            this.dedupKey = key;
            return this;
        }

        /** Cho phép người nhận không phải thành viên (người được mời, người vừa bị xoá). */
        public Draft allowNonMembers() {
            this.allowNonMembers = true;
            return this;
        }

        /** Cho phép phát khi dự án đã xoá mềm (thông báo "dự án bị lưu trữ"). */
        public Draft allowDeletedProject() {
            this.allowDeletedProject = true;
            return this;
        }

        public Set<UUID> recipientIds() {
            return recipients.keySet();
        }

        /** @return true nếu đã ghi vào outbox. */
        public boolean publish() {
            try {
                return doPublish();
            } catch (Exception e) {
                log.warn("Không phát được thông báo {}: {}", type, e.getMessage(), e);
                return false;
            }
        }

        private boolean doPublish() {
            if (project == null) return false;
            if (Boolean.TRUE.equals(project.getIsTemplate())) return false;
            if (project.getDeletedAt() != null && !allowDeletedProject) return false;

            if (actorId != null) recipients.remove(actorId);
            if (!allowNonMembers && !recipients.isEmpty()) {
                Set<UUID> keep = recipientResolver.onlyMembers(project, recipients.keySet());
                recipients.keySet().retainAll(keep);
            }
            if (recipients.isEmpty() && !type.isInternal()) return false;

            if (project.getProjectType() != null) data.putIfAbsent("projectType", project.getProjectType().name());

            NotificationEvent event = NotificationEvent.builder()
                    .type(type.name())
                    .source(SOURCE)
                    .occurredAt(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).toString())
                    .actorId(str(actorId))
                    .workspaceId(str(project.getWorkspaceId()))
                    .projectId(str(project.getId()))
                    .projectKey(project.getKey())
                    .projectName(project.getName())
                    .entityType(entityType)
                    .entityId(str(entityId))
                    .entityCode(entityCode)
                    .entityTitle(entityTitle)
                    .recipients(recipients.entrySet().stream()
                            .map(e -> new NotificationEvent.Recipient(e.getKey().toString(), e.getValue().name()))
                            .toList())
                    .data(new LinkedHashMap<>(data))
                    .dedupKey(dedupKey)
                    .build();

            Map<String, Object> payload = objectMapper.convertValue(event, new TypeReference<>() {});
            UUID aggregateId = entityId != null ? entityId : project.getId();
            outboxRecorder.record(NotificationEvent.AGGREGATE_TYPE, aggregateId, type.name(), payload);
            log.debug("Ghi outbox thông báo {} cho {} người nhận ({})", type, recipients.size(), entityCode);
            return true;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    static Object normalize(Object value) {
        if (value instanceof TemporalAccessor || value instanceof UUID || value instanceof Enum<?>) {
            return value.toString();
        }
        if (value instanceof java.math.BigDecimal bd) {
            // Không dùng toán tử ?: ở đây: double và long sẽ bị nâng kiểu chung thành double (3 → 3.0)
            if (bd.stripTrailingZeros().scale() <= 0) {
                return bd.longValue();
            }
            return bd.doubleValue();
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> { if (k != null && v != null) out.put(k.toString(), normalize(v)); });
            return out;
        }
        if (value instanceof Collection<?> c) {
            return c.stream().filter(Objects::nonNull).map(NotificationEventPublisher::normalize).toList();
        }
        return value;
    }

    static List<String> codes(Collection<WorkItem> items) {
        return items.stream().map(WorkItem::getCode).filter(Objects::nonNull).toList();
    }
}
