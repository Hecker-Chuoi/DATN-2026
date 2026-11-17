package com.datn.notification.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.notification.dto.NotificationPage;
import com.datn.notification.dto.NotificationResponse;
import com.datn.notification.dto.PushMessage;
import com.datn.notification.entity.Notification;
import com.datn.notification.enums.NotificationCategory;
import com.datn.notification.repository.NotificationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Thao tác của người dùng trên thông báo của CHÍNH MÌNH. Mọi truy vấn đều lọc theo
 * {@code recipient_id = jwt.sub}; id thuộc người khác trả 404 (không lộ việc id đó tồn tại).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final int MAX_PAGE_SIZE = 50;
    private static final Duration RESTORE_WINDOW = Duration.ofDays(30);

    private final NotificationRepository repository;
    private final NotificationPushService pushService;

    /** Bộ lọc danh sách: all | unread | action. */
    public enum Filter {
        ALL, UNREAD, ACTION;

        public static Filter parse(String s) {
            if (s == null || s.isBlank()) return ALL;
            try {
                return Filter.valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Bộ lọc không hợp lệ: " + s + " (all | unread | action)");
            }
        }
    }

    @Transactional(readOnly = true)
    public NotificationPage list(UUID me, Filter filter, String category, UUID projectId, String cursor, int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Cursor after = Cursor.decode(cursor);
        Specification<Notification> spec = baseSpec(me, filter, parseCategory(category), projectId)
                .and((root, q, cb) -> {
                    if (after == null) return cb.conjunction();
                    return cb.or(
                            cb.lessThan(root.get("updatedAt"), after.updatedAt()),
                            cb.and(cb.equal(root.get("updatedAt"), after.updatedAt()),
                                    cb.lessThan(root.get("id"), after.id())));
                });
        List<Notification> rows = repository.findAll(spec,
                PageRequest.of(0, pageSize + 1, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")))).getContent();

        boolean hasMore = rows.size() > pageSize;
        List<Notification> page = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore ? Cursor.encode(page.get(page.size() - 1)) : null;
        return new NotificationPage(
                page.stream().map(NotificationResponse::from).toList(),
                next,
                repository.countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(me),
                repository.countPendingActions(me));
    }

    @Transactional(readOnly = true)
    public Counts counts(UUID me) {
        return new Counts(repository.countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(me), repository.countPendingActions(me));
    }

    @Transactional
    public NotificationResponse setRead(UUID me, UUID id, boolean read) {
        Notification n = requireOwn(me, id);
        boolean changed = read ? n.getReadAt() == null : n.getReadAt() != null;
        if (changed) {
            n.setReadAt(read ? Instant.now() : null);
            repository.save(n);
            pushService.pushAfterCommit(me, PushMessage.ofIds(read ? "READ" : "UNREAD", List.of(n.getId())));
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public int readAll(UUID me, Filter filter, String category, UUID projectId) {
        Filter f = filter == Filter.ACTION ? Filter.ACTION : Filter.UNREAD;
        Specification<Notification> spec = baseSpec(me, f, parseCategory(category), projectId)
                .and((root, q, cb) -> cb.isNull(root.get("readAt")));
        List<UUID> ids = repository.findAll(spec).stream().map(Notification::getId).toList();
        if (ids.isEmpty()) return 0;
        int updated = 0;
        for (List<UUID> chunk : chunks(ids, 500)) {
            updated += repository.markReadByIds(me, chunk, Instant.now());
        }
        pushService.pushAfterCommit(me, PushMessage.ofIds("READ", ids));
        return updated;
    }

    @Transactional
    public void delete(UUID me, UUID id) {
        Notification n = requireOwn(me, id);
        n.setDeletedAt(Instant.now());
        repository.save(n);
        pushService.pushAfterCommit(me, PushMessage.ofIds("DELETED", List.of(n.getId())));
    }

    @Transactional
    public NotificationResponse restore(UUID me, UUID id) {
        Notification n = repository.findByIdAndRecipientId(id, me)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo"));
        if (n.getDeletedAt() == null) {
            return NotificationResponse.from(n);
        }
        if (n.getDeletedAt().isBefore(Instant.now().minus(RESTORE_WINDOW))) {
            throw new BadRequestException("Thông báo đã bị xoá quá 30 ngày, không thể khôi phục");
        }
        n.setDeletedAt(null);
        repository.save(n);
        NotificationResponse res = NotificationResponse.from(n);
        pushService.pushAfterCommit(me, PushMessage.of("RESTORED", res));
        return res;
    }

    /** Xoá mềm mọi thông báo đã đọc. */
    @Transactional
    public int deleteAllRead(UUID me) {
        List<UUID> ids = repository.findReadIds(me);
        if (ids.isEmpty()) return 0;
        int n = repository.softDeleteAllRead(me, Instant.now());
        pushService.pushAfterCommit(me, PushMessage.ofIds("DELETED", ids));
        return n;
    }

    private Notification requireOwn(UUID me, UUID id) {
        return repository.findByIdAndRecipientIdAndDeletedAtIsNull(id, me)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo"));
    }

    private static Specification<Notification> baseSpec(UUID me, Filter filter, NotificationCategory category, UUID projectId) {
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("recipientId"), me));
            ps.add(cb.isNull(root.get("deletedAt")));
            if (filter == Filter.UNREAD) ps.add(cb.isNull(root.get("readAt")));
            if (filter == Filter.ACTION) {
                ps.add(cb.isTrue(root.get("actionRequired")));
                ps.add(cb.isNull(root.get("actionResolvedAt")));
            }
            if (category != null) ps.add(cb.equal(root.get("category"), category));
            if (projectId != null) ps.add(cb.equal(root.get("projectId"), projectId));
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static NotificationCategory parseCategory(String category) {
        if (category == null || category.isBlank()) return null;
        try {
            return NotificationCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Nhóm thông báo không hợp lệ: " + category);
        }
    }

    private static <T> List<List<T>> chunks(List<T> list, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) out.add(list.subList(i, Math.min(list.size(), i + size)));
        return out;
    }

    public record Counts(long total, long action) {}

    /** Con trỏ phân trang: base64("epochMicros|uuid"). */
    record Cursor(Instant updatedAt, UUID id) {

        static String encode(Notification n) {
            Instant t = n.getUpdatedAt();
            long micros = t.getEpochSecond() * 1_000_000L + t.getNano() / 1_000;
            String raw = micros + "|" + n.getId();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) return null;
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", 2);
                long micros = Long.parseLong(parts[0]);
                Instant t = Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
                return new Cursor(t, UUID.fromString(parts[1]));
            } catch (Exception e) {
                throw new BadRequestException("Con trỏ phân trang không hợp lệ");
            }
        }
    }
}
