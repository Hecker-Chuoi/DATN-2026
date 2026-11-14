package com.datn.notification.service;

import com.datn.common.notification.NotificationEvent;
import com.datn.common.notification.NotificationType;
import com.datn.common.notification.RecipientReason;
import com.datn.notification.catalog.NotificationCatalog;
import com.datn.notification.catalog.NotificationSpec;
import com.datn.notification.catalog.RenderContext;
import com.datn.notification.catalog.Rendered;
import com.datn.notification.client.UserDirectory;
import com.datn.notification.dto.NotificationResponse;
import com.datn.notification.dto.PushMessage;
import com.datn.notification.entity.Notification;
import com.datn.notification.repository.NotificationRepository;
import com.datn.notification.service.NotificationPreferenceService.Delivery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Nhận một sự kiện và biến nó thành thông báo cho từng người nhận.
 *
 * <ol>
 *   <li>Đóng các thông báo "Cần xử lý" mà sự kiện này giải quyết (R9).</li>
 *   <li>Bỏ người thao tác (R1), mỗi người chỉ giữ lý do mạnh nhất (R3).</li>
 *   <li>Gộp vào thông báo chưa đọc cùng nhóm trong cửa sổ gộp (R4), hoặc chèn mới.</li>
 *   <li>Chống trùng bằng {@code dedup_key} UNIQUE: Kafka giao lại hay job chạy lại đều không sinh bản sao (R6).</li>
 *   <li>Áp cài đặt bật / tắt của người nhận ({@link NotificationPreferenceService}): tắt trong ứng dụng thì không
 *       hiện (nếu vẫn bật email thì lưu một bản ẩn để chống gửi email trùng), tắt email thì không gửi mail.</li>
 *   <li>Sau commit: đẩy WebSocket, gửi email nếu loại có cấu hình email.</li>
 * </ol>
 */
@Slf4j
@Service
public class NotificationIngestService {

    private static final int MAX_TRACKED_EVENTS = 30;

    private final NotificationRepository repository;
    private final NotificationCatalog catalog;
    private final UserDirectory users;
    private final NotificationPushService pushService;
    private final NotificationMailer mailer;
    private final NotificationPreferenceService preferenceService;
    private final Duration groupWindow;
    private final Clock clock;

    @Autowired
    public NotificationIngestService(NotificationRepository repository,
                                     NotificationCatalog catalog,
                                     UserDirectory users,
                                     NotificationPushService pushService,
                                     NotificationMailer mailer,
                                     NotificationPreferenceService preferenceService,
                                     @Value("${app.notifications.group-window-minutes:10}") long groupWindowMinutes) {
        this(repository, catalog, users, pushService, mailer, preferenceService,
                Duration.ofMinutes(groupWindowMinutes), Clock.systemUTC());
    }

    NotificationIngestService(NotificationRepository repository, NotificationCatalog catalog, UserDirectory users,
                              NotificationPushService pushService, NotificationMailer mailer,
                              NotificationPreferenceService preferenceService, Duration groupWindow, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.users = users;
        this.pushService = pushService;
        this.mailer = mailer;
        this.preferenceService = preferenceService;
        this.groupWindow = groupWindow;
        this.clock = clock;
    }

    /** @return số thông báo được tạo mới hoặc cập nhật (gộp). */
    @Transactional
    public int ingest(NotificationEvent event) {
        NotificationType type = NotificationType.parse(event.getType());
        NotificationSpec spec = catalog.find(type).orElse(null);
        if (spec == null) {
            log.warn("Bỏ qua sự kiện {}: loại thông báo không xác định '{}'", event.getEventId(), event.getType());
            return 0;
        }

        resolveActions(spec, event);
        if (type.isInternal()) {
            return 0;
        }

        Map<UUID, RecipientReason> recipients = new LinkedHashMap<>();
        strongestReasons(event).forEach((userId, reason) -> {
            UUID id = parseUuid(userId);
            if (id != null) recipients.put(id, reason);
        });
        if (recipients.isEmpty()) {
            return 0;
        }
        Map<UUID, Delivery> deliveries = preferenceService.resolve(spec, parseUuid(event.getProjectId()), recipients);

        String actorName = event.getActorId() == null ? "Hệ thống" : users.displayName(event.getActorId());
        LocalDate today = LocalDate.now(clock.withZone(RenderContext.ZONE));
        int changed = 0;

        // Không bắt lỗi UNIQUE ở đây: flush lỗi làm transaction rollback-only. Hai bản cùng dedup_key
        // hiếm khi chen nhau (mỗi partition một consumer); nếu có, Kafka giao lại và existsByDedupKey bỏ qua.
        for (UUID recipientId : recipients.keySet()) {
            Delivery delivery = deliveries.getOrDefault(recipientId, new Delivery(true, spec.email() != null));
            if (!delivery.any()) continue; // người nhận đã tắt nhóm này / tắt tiếng dự án
            changed += deliver(spec, event, recipientId, actorName, today, delivery) ? 1 : 0;
        }
        return changed;
    }

    /** @return true nếu có thông báo hiện ra (tạo mới hoặc gộp); bản ẩn chỉ để gửi email thì trả false. */
    private boolean deliver(NotificationSpec spec, NotificationEvent event, UUID recipientId, String actorName,
                            LocalDate today, Delivery delivery) {
        String groupKey = spec.groupKey(event);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // Postgres lưu tới micro giây

        if (groupKey != null && delivery.inApp()) {
            Optional<Notification> existing = repository
                    .findFirstByRecipientIdAndGroupKeyAndReadAtIsNullAndDeletedAtIsNullAndUpdatedAtAfterOrderByUpdatedAtDesc(
                            recipientId, groupKey, now.minus(groupWindow));
            if (existing.isPresent()) {
                return merge(existing.get(), spec, event, actorName, today, now);
            }
        }

        String dedupKey = dedupKey(event, recipientId);
        if (repository.existsByDedupKey(dedupKey)) {
            return false;
        }

        RenderContext ctx = new RenderContext(event, recipientId.toString(), actorName, 0, users::displayName, today);
        Rendered rendered = spec.render(ctx);

        Map<String, Object> data = snapshot(event, actorName);
        List<String> actorIds = new ArrayList<>();
        if (event.getActorId() != null) actorIds.add(event.getActorId());
        data.put("actorIds", actorIds);
        data.put("eventIds", new ArrayList<>(List.of(Objects.toString(event.getEventId(), dedupKey))));

        Notification n = Notification.builder()
                .id(UUID.randomUUID())
                .recipientId(recipientId)
                .type(spec.type().name())
                .category(spec.category())
                .severity(rendered.severity() != null ? rendered.severity() : spec.severity())
                .actionRequired(spec.actionRequired())
                .title(truncate(rendered.title(), 500))
                .body(truncate(rendered.body(), 1000))
                .link(truncate(rendered.link(), 500))
                .workspaceId(parseUuid(event.getWorkspaceId()))
                .projectId(parseUuid(event.getProjectId()))
                .entityType(event.getEntityType())
                .entityId(parseUuid(event.getEntityId()))
                .actorId(parseUuid(event.getActorId()))
                .actorCount(Math.max(1, actorIds.size()))
                .data(data)
                .groupKey(groupKey)
                .dedupKey(dedupKey)
                .sourceEventId(event.getEventId())
                .createdAt(now)
                .updatedAt(now)
                // Tắt trong ứng dụng nhưng bật email: lưu bản ẩn (như đã xoá) để dedup_key chặn email trùng
                .deletedAt(delivery.inApp() ? null : now)
                .build();
        repository.saveAndFlush(n);

        if (delivery.inApp()) {
            pushService.pushAfterCommit(recipientId, PushMessage.of("CREATED", NotificationResponse.from(n)));
        }
        if (delivery.email() && spec.email() != null) {
            mailer.sendAfterCommit(spec.email(), ctx, rendered.link());
        }
        return delivery.inApp();
    }

    /** Gộp vào thông báo chưa đọc cùng nhóm: nội dung lấy theo sự kiện mới nhất, đếm số người thao tác khác nhau. */
    @SuppressWarnings("unchecked")
    private boolean merge(Notification n, NotificationSpec spec, NotificationEvent event, String actorName,
                          LocalDate today, Instant now) {
        Map<String, Object> data = n.getData() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(n.getData());
        List<String> eventIds = new ArrayList<>(asStringList(data.get("eventIds")));
        String eventId = event.getEventId();
        if (eventId != null && eventIds.contains(eventId)) {
            return false; // Kafka giao lại đúng sự kiện đã gộp
        }

        List<String> actorIds = new ArrayList<>(asStringList(data.get("actorIds")));
        if (event.getActorId() != null) {
            actorIds.remove(event.getActorId());
            actorIds.add(0, event.getActorId()); // người mới nhất đứng đầu
        }
        int others = Math.max(0, actorIds.size() - 1);

        RenderContext ctx = new RenderContext(event, n.getRecipientId().toString(), actorName, others, users::displayName, today);
        Rendered rendered = spec.render(ctx);

        data.putAll(snapshot(event, actorName));
        data.put("actorIds", actorIds);
        if (eventId != null) eventIds.add(eventId);
        while (eventIds.size() > MAX_TRACKED_EVENTS) eventIds.remove(0);
        data.put("eventIds", eventIds);

        n.setTitle(truncate(rendered.title(), 500));
        n.setBody(truncate(rendered.body(), 1000));
        n.setLink(truncate(rendered.link(), 500));
        n.setSeverity(rendered.severity() != null ? rendered.severity() : spec.severity());
        n.setActorId(parseUuid(event.getActorId()));
        n.setActorCount(Math.max(1, actorIds.size()));
        n.setData(data);
        n.setSourceEventId(eventId);
        n.setUpdatedAt(now);
        repository.save(n);

        pushService.pushAfterCommit(n.getRecipientId(), PushMessage.of("UPDATED", NotificationResponse.from(n)));
        return true;
    }

    /** Đóng các thông báo "Cần xử lý" mà sự kiện này giải quyết, trừ thông báo do chính sự kiện này tạo. */
    private void resolveActions(NotificationSpec spec, NotificationEvent event) {
        if (spec.resolves().isEmpty()) return;
        List<String> types = spec.resolves().stream().map(Enum::name).toList();
        List<Notification> open;
        if (spec.resolveByProject()) {
            UUID projectId = parseUuid(event.getProjectId());
            if (projectId == null) return;
            open = repository.findOpenActionsByProject(projectId, types);
        } else {
            UUID entityId = parseUuid(event.getEntityId());
            if (entityId == null) return;
            open = repository.findOpenActions(entityId, types);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // Postgres lưu tới micro giây
        for (Notification n : open) {
            if (event.getEventId() != null && event.getEventId().equals(n.getSourceEventId())) continue;
            n.setActionResolvedAt(now);
            repository.save(n);
            pushService.pushAfterCommit(n.getRecipientId(), PushMessage.of("UPDATED", NotificationResponse.from(n)));
        }
    }

    /** Bỏ người thao tác; một người nhiều lý do thì giữ lý do mạnh nhất. */
    static Map<String, RecipientReason> strongestReasons(NotificationEvent event) {
        Map<String, RecipientReason> result = new LinkedHashMap<>();
        if (event.getRecipients() == null) return result;
        for (NotificationEvent.Recipient r : event.getRecipients()) {
            if (r == null || r.getUserId() == null || r.getUserId().isBlank()) continue;
            if (r.getUserId().equalsIgnoreCase(Objects.toString(event.getActorId(), ""))) continue;
            RecipientReason reason = RecipientReason.parse(r.getReason());
            result.merge(r.getUserId().toLowerCase(), reason,
                    (a, b) -> a.strength() <= b.strength() ? a : b);
        }
        return result;
    }

    static String dedupKey(NotificationEvent event, UUID recipientId) {
        String base = event.getDedupKey() != null && !event.getDedupKey().isBlank()
                ? event.getDedupKey()
                : (event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString());
        return truncate(base + ":" + recipientId, 255);
    }

    private static Map<String, Object> snapshot(NotificationEvent event, String actorName) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (event.getProjectName() != null) data.put("projectName", event.getProjectName());
        if (event.getProjectKey() != null) data.put("projectKey", event.getProjectKey());
        if (event.getWorkspaceName() != null) data.put("workspaceName", event.getWorkspaceName());
        if (event.getEntityCode() != null) data.put("entityCode", event.getEntityCode());
        if (actorName != null) data.put("actorName", actorName);
        return data;
    }

    private static List<String> asStringList(Object v) {
        if (v instanceof Collection<?> c) return c.stream().map(String::valueOf).toList();
        return List.of();
    }

    static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
