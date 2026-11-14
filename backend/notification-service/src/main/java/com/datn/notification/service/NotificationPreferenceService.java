package com.datn.notification.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.notification.NotificationType;
import com.datn.common.notification.RecipientReason;
import com.datn.notification.catalog.NotificationCatalog;
import com.datn.notification.catalog.NotificationSpec;
import com.datn.notification.dto.NotificationPreferencesView;
import com.datn.notification.dto.NotificationPreferencesView.CategoryView;
import com.datn.notification.dto.NotificationPreferencesView.MutedProjectView;
import com.datn.notification.entity.NotificationPreference;
import com.datn.notification.entity.ProjectMute;
import com.datn.notification.enums.NotificationCategory;
import com.datn.notification.repository.NotificationPreferenceRepository;
import com.datn.notification.repository.ProjectMuteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.datn.common.notification.NotificationType.*;
import static com.datn.common.notification.RecipientReason.*;

/**
 * Cài đặt bật / tắt thông báo (tài liệu 3 §10.1).
 *
 * <ul>
 *   <li>Theo <b>nhóm</b> × <b>kênh</b> (trong ứng dụng, email). Chưa có dòng cài đặt thì theo mặc định của danh mục.</li>
 *   <li><b>Bị khoá trong ứng dụng</b> (không tắt được): 6 loại "Cần xử lý", cả nhóm Thành viên &amp; quyền,
 *       được giao việc (A1), được nhắc tên (D1). Email thì luôn tắt được.</li>
 *   <li><b>Tắt tiếng dự án</b>: bỏ mọi thông báo của dự án đó trừ thông báo gửi đích danh và các loại bị khoá.</li>
 * </ul>
 */
@Service
public class NotificationPreferenceService {

    /** Lý do nhận "đích danh": tắt tiếng dự án vẫn nhận. */
    static final Set<RecipientReason> DIRECT_REASONS = EnumSet.of(MENTIONED, NEW_ASSIGNEE, TARGET_USER, REQUESTER, INVITEE);

    /** Cả nhóm luôn hiện trong chuông: liên quan quyền truy cập của chính người nhận. */
    static final Set<NotificationCategory> IN_APP_LOCKED_CATEGORIES = EnumSet.of(NotificationCategory.MEMBER);

    /** Từng loại luôn hiện trong chuông dù đã tắt nhóm (kèm tên hiển thị ở màn cài đặt). */
    private static final Map<NotificationType, String> ALWAYS_IN_APP_LABELS = new EnumMap<>(Map.of(
            TASK_ASSIGNED, "Được giao việc",
            COMMENT_MENTION, "Được nhắc tên",
            COMPLETION_REQUESTED, "Có yêu cầu xác nhận hoàn thành chờ bạn",
            COMPLETION_PENDING_REMINDER, "Nhắc yêu cầu xác nhận chờ lâu",
            SPRINT_OVERDUE, "Chu kỳ quá hạn",
            NEXT_SPRINT_DUE, "Đến ngày bắt đầu chu kỳ tiếp theo",
            PROJECT_INVITATION_RECEIVED, "Lời mời vào dự án",
            WORKSPACE_INVITATION_RECEIVED, "Lời mời vào không gian làm việc"));

    /** Kênh gửi thực tế cho một người nhận. */
    public record Delivery(boolean inApp, boolean email) {
        public static final Delivery NONE = new Delivery(false, false);

        public boolean any() {
            return inApp || email;
        }
    }

    private record CategoryMeta(boolean inAppLocked, boolean emailAvailable, List<String> alwaysInApp) {}

    private final NotificationPreferenceRepository preferences;
    private final ProjectMuteRepository mutes;
    private final Clock clock;
    private final Map<NotificationCategory, CategoryMeta> meta = new EnumMap<>(NotificationCategory.class);

    @Autowired
    public NotificationPreferenceService(NotificationPreferenceRepository preferences, ProjectMuteRepository mutes,
                                         NotificationCatalog catalog) {
        this(preferences, mutes, catalog, Clock.systemUTC());
    }

    NotificationPreferenceService(NotificationPreferenceRepository preferences, ProjectMuteRepository mutes,
                                  NotificationCatalog catalog, Clock clock) {
        this.preferences = preferences;
        this.mutes = mutes;
        this.clock = clock;
        for (NotificationCategory category : NotificationCategory.values()) {
            boolean emailAvailable = false;
            List<String> always = new ArrayList<>();
            for (NotificationSpec spec : catalog.all().values()) {
                if (spec.category() != category || spec.type().isInternal()) continue;
                if (spec.email() != null) emailAvailable = true;
                if (!IN_APP_LOCKED_CATEGORIES.contains(category) && inAppLocked(spec)) {
                    always.add(ALWAYS_IN_APP_LABELS.getOrDefault(spec.type(), spec.type().name()));
                }
            }
            meta.put(category, new CategoryMeta(IN_APP_LOCKED_CATEGORIES.contains(category), emailAvailable, List.copyOf(always)));
        }
    }

    /** Loại này có luôn hiện trong chuông không (không tắt được bằng cài đặt hay tắt tiếng dự án). */
    public static boolean inAppLocked(NotificationSpec spec) {
        return spec.actionRequired()
                || IN_APP_LOCKED_CATEGORIES.contains(spec.category())
                || ALWAYS_IN_APP_LABELS.containsKey(spec.type());
    }

    /**
     * Kênh gửi thực tế của một sự kiện cho từng người nhận: một truy vấn cài đặt + một truy vấn tắt tiếng cho cả lô.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Delivery> resolve(NotificationSpec spec, UUID projectId, Map<UUID, RecipientReason> recipients) {
        Map<UUID, Delivery> result = new LinkedHashMap<>();
        if (recipients.isEmpty()) return result;

        boolean locked = inAppLocked(spec);
        boolean hasEmail = spec.email() != null;
        Map<UUID, NotificationPreference> prefs = preferences
                .findByUserIdInAndCategory(recipients.keySet(), spec.category()).stream()
                .collect(Collectors.toMap(NotificationPreference::getUserId, Function.identity(), (a, b) -> a));
        Set<UUID> muted = locked || projectId == null ? Set.of() : activeMutes(projectId, recipients.keySet());

        for (Map.Entry<UUID, RecipientReason> e : recipients.entrySet()) {
            UUID userId = e.getKey();
            if (muted.contains(userId) && !DIRECT_REASONS.contains(e.getValue())) {
                result.put(userId, Delivery.NONE);
                continue;
            }
            NotificationPreference p = prefs.get(userId);
            boolean inApp = locked || p == null || p.isInApp();
            boolean email = hasEmail && (p == null || p.isEmail());
            result.put(userId, new Delivery(inApp, email));
        }
        return result;
    }

    private Set<UUID> activeMutes(UUID projectId, Collection<UUID> userIds) {
        Instant now = clock.instant();
        return mutes.findByProjectIdAndUserIdIn(projectId, userIds).stream()
                .filter(m -> m.activeAt(now))
                .map(ProjectMute::getUserId)
                .collect(Collectors.toSet());
    }

    // ─────────────────────────── Màn cài đặt ───────────────────────────

    @Transactional(readOnly = true)
    public NotificationPreferencesView view(UUID userId) {
        Map<NotificationCategory, NotificationPreference> mine = preferences.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getCategory, Function.identity(), (a, b) -> a));
        List<CategoryView> categories = new ArrayList<>();
        for (NotificationCategory c : NotificationCategory.values()) {
            CategoryMeta m = meta.get(c);
            NotificationPreference p = mine.get(c);
            categories.add(new CategoryView(
                    c.name(),
                    m.inAppLocked() || p == null || p.isInApp(),
                    m.emailAvailable() && (p == null || p.isEmail()),
                    m.inAppLocked(),
                    m.emailAvailable(),
                    m.alwaysInApp()));
        }
        Instant now = clock.instant();
        List<MutedProjectView> muted = mutes.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(x -> x.activeAt(now))
                .map(x -> new MutedProjectView(x.getProjectId(), x.getProjectName(), x.getMutedUntil(), x.getCreatedAt()))
                .toList();
        return new NotificationPreferencesView(categories, muted);
    }

    /** Lưu cài đặt của các nhóm được gửi lên (nhóm không gửi thì giữ nguyên). */
    @Transactional
    public NotificationPreferencesView update(UUID userId, List<CategoryChange> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new BadRequestException("Chưa có cài đặt nào để lưu");
        }
        Map<NotificationCategory, NotificationPreference> mine = preferences.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getCategory, Function.identity(), (a, b) -> a));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        for (CategoryChange change : changes) {
            NotificationCategory category = parseCategory(change.category());
            CategoryMeta m = meta.get(category);
            NotificationPreference p = mine.computeIfAbsent(category, c -> NotificationPreference.builder()
                    .userId(userId).category(c).inApp(true).email(true).build());
            if (change.inApp() != null) p.setInApp(m.inAppLocked() || change.inApp());
            if (change.email() != null) p.setEmail(change.email());
            p.setUpdatedAt(now);
            preferences.save(p);
        }
        return view(userId);
    }

    /** Tắt tiếng một dự án; {@code until} null = đến khi bật lại. */
    @Transactional
    public NotificationPreferencesView mute(UUID userId, UUID projectId, String projectName, Instant until) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (until != null && !until.isAfter(now)) {
            throw new BadRequestException("Thời điểm bật lại phải ở tương lai");
        }
        String name = projectName == null || projectName.isBlank() ? null : projectName.trim();
        if (name != null && name.length() > 255) name = name.substring(0, 255);
        ProjectMute m = mutes.findById(new ProjectMute.Key(userId, projectId))
                .orElseGet(() -> ProjectMute.builder().userId(userId).projectId(projectId).createdAt(now).build());
        if (name != null) m.setProjectName(name);
        m.setMutedUntil(until == null ? null : until.truncatedTo(ChronoUnit.MICROS));
        mutes.save(m);
        return view(userId);
    }

    @Transactional
    public NotificationPreferencesView unmute(UUID userId, UUID projectId) {
        mutes.deleteById(new ProjectMute.Key(userId, projectId));
        return view(userId);
    }

    private static NotificationCategory parseCategory(String raw) {
        try {
            return NotificationCategory.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Nhóm thông báo không hợp lệ: " + raw);
        }
    }

    /** Thay đổi của một nhóm; trường null = giữ nguyên. */
    public record CategoryChange(String category, Boolean inApp, Boolean email) {}
}
