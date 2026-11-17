package com.datn.notification.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.notification.NotificationType;
import com.datn.common.notification.RecipientReason;
import com.datn.notification.catalog.NotificationCatalog;
import com.datn.notification.catalog.NotificationSpec;
import com.datn.notification.dto.NotificationPreferencesView;
import com.datn.notification.dto.NotificationPreferencesView.CategoryView;
import com.datn.notification.entity.NotificationPreference;
import com.datn.notification.entity.ProjectMute;
import com.datn.notification.enums.NotificationCategory;
import com.datn.notification.repository.NotificationPreferenceRepository;
import com.datn.notification.repository.ProjectMuteRepository;
import com.datn.notification.service.NotificationPreferenceService.CategoryChange;
import com.datn.notification.service.NotificationPreferenceService.Delivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationPreferenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T02:00:00Z");
    private static final UUID BINH = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID CHI = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID PROJECT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock NotificationPreferenceRepository preferences;
    @Mock ProjectMuteRepository mutes;

    private final NotificationCatalog catalog = new NotificationCatalog();
    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(preferences, mutes, catalog, Clock.fixed(NOW, ZoneOffset.UTC));
        when(preferences.findByUserIdInAndCategory(anyCollection(), any())).thenReturn(List.of());
        when(preferences.findByUserId(any())).thenReturn(List.of());
        when(mutes.findByProjectIdAndUserIdIn(any(), anyCollection())).thenReturn(List.of());
        when(mutes.findByUserIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
    }

    private NotificationSpec spec(NotificationType type) {
        return catalog.find(type).orElseThrow();
    }

    private static NotificationPreference pref(UUID user, NotificationCategory c, boolean inApp, boolean email) {
        return NotificationPreference.builder().userId(user).category(c).inApp(inApp).email(email).updatedAt(NOW).build();
    }

    private Map<UUID, Delivery> resolve(NotificationType type, Object... recipients) {
        Map<UUID, RecipientReason> map = new LinkedHashMap<>();
        for (int i = 0; i < recipients.length; i += 2) map.put((UUID) recipients[i], (RecipientReason) recipients[i + 1]);
        return service.resolve(spec(type), PROJECT, map);
    }

    // ─────────────────────────── Tính kênh gửi ───────────────────────────

    @Test
    void noPreference_followsCatalogDefaults() {
        assertThat(resolve(NotificationType.TASK_ASSIGNED, BINH, RecipientReason.NEW_ASSIGNEE).get(BINH))
                .isEqualTo(new Delivery(true, true));    // A1 có email
        assertThat(resolve(NotificationType.TASK_STATUS_CHANGED, BINH, RecipientReason.ASSIGNEE).get(BINH))
                .isEqualTo(new Delivery(true, false));   // A3 không có email
    }

    @Test
    void categoryTurnedOff_hidesOnlyThatUser() {
        when(preferences.findByUserIdInAndCategory(anyCollection(), eq(NotificationCategory.COMMENT)))
                .thenReturn(List.of(pref(BINH, NotificationCategory.COMMENT, false, false)));
        Map<UUID, Delivery> d = resolve(NotificationType.COMMENT_ADDED,
                BINH, RecipientReason.ASSIGNEE, CHI, RecipientReason.REPORTER);
        assertThat(d.get(BINH)).isEqualTo(Delivery.NONE);
        assertThat(d.get(CHI)).isEqualTo(new Delivery(true, false));
    }

    @Test
    void lockedTypes_stayInApp_butEmailCanBeTurnedOff() {
        when(preferences.findByUserIdInAndCategory(anyCollection(), eq(NotificationCategory.COMMENT)))
                .thenReturn(List.of(pref(BINH, NotificationCategory.COMMENT, false, false)));
        when(preferences.findByUserIdInAndCategory(anyCollection(), eq(NotificationCategory.APPROVAL)))
                .thenReturn(List.of(pref(BINH, NotificationCategory.APPROVAL, false, true)));

        // D1 nhắc tên: vẫn vào chuông, email theo cài đặt (đã tắt)
        assertThat(resolve(NotificationType.COMMENT_MENTION, BINH, RecipientReason.MENTIONED).get(BINH))
                .isEqualTo(new Delivery(true, false));
        // C1 "Cần xử lý": vẫn vào chuông dù đã tắt nhóm Xác nhận hoàn thành
        assertThat(resolve(NotificationType.COMPLETION_REQUESTED, BINH, RecipientReason.PROJECT_MANAGER).get(BINH))
                .isEqualTo(new Delivery(true, true));
        // C2 không bị khoá: tắt nhóm là tắt
        assertThat(resolve(NotificationType.COMPLETION_APPROVED, BINH, RecipientReason.REQUESTER).get(BINH))
                .isEqualTo(new Delivery(false, false));
    }

    @Test
    void inAppOff_emailOn_keepsEmailOnly() {
        when(preferences.findByUserIdInAndCategory(anyCollection(), eq(NotificationCategory.REMINDER)))
                .thenReturn(List.of(pref(BINH, NotificationCategory.REMINDER, false, true)));
        assertThat(resolve(NotificationType.TASK_DUE_SOON, BINH, RecipientReason.ASSIGNEE).get(BINH))
                .isEqualTo(new Delivery(false, true));
    }

    @Test
    void mutedProject_dropsFollowUps_butKeepsDirectAndLocked() {
        when(mutes.findByProjectIdAndUserIdIn(eq(PROJECT), anyCollection()))
                .thenReturn(List.of(ProjectMute.builder().userId(BINH).projectId(PROJECT).createdAt(NOW).build()));

        assertThat(resolve(NotificationType.TASK_DUE_SOON, BINH, RecipientReason.ASSIGNEE).get(BINH))
                .isEqualTo(Delivery.NONE);
        assertThat(resolve(NotificationType.COMMENT_ADDED, BINH, RecipientReason.ASSIGNEE).get(BINH))
                .isEqualTo(Delivery.NONE);
        // đích danh: được trả lời bình luận của mình (TARGET_USER), được giao việc
        assertThat(resolve(NotificationType.COMMENT_REPLIED, BINH, RecipientReason.TARGET_USER).get(BINH).inApp()).isTrue();
        assertThat(resolve(NotificationType.TASK_ASSIGNED, BINH, RecipientReason.NEW_ASSIGNEE).get(BINH).inApp()).isTrue();
        // bị khoá: thành viên & quyền
        assertThat(resolve(NotificationType.PROJECT_ROLE_CHANGED, BINH, RecipientReason.TARGET_USER).get(BINH).inApp()).isTrue();
    }

    @Test
    void expiredMute_isIgnored() {
        when(mutes.findByProjectIdAndUserIdIn(eq(PROJECT), anyCollection())).thenReturn(List.of(ProjectMute.builder()
                .userId(BINH).projectId(PROJECT).mutedUntil(NOW.minusSeconds(1)).createdAt(NOW.minusSeconds(3600)).build()));
        assertThat(resolve(NotificationType.TASK_DUE_SOON, BINH, RecipientReason.ASSIGNEE).get(BINH).inApp()).isTrue();
    }

    // ─────────────────────────── Màn cài đặt ───────────────────────────

    @Test
    void view_listsNineCategoriesWithLocksAndEmailAvailability() {
        NotificationPreferencesView v = service.view(BINH);
        Map<String, CategoryView> byName = v.categories().stream()
                .collect(Collectors.toMap(CategoryView::category, Function.identity()));

        assertThat(byName).hasSize(NotificationCategory.values().length);
        assertThat(byName.get("MEMBER").inAppLocked()).isTrue();
        assertThat(byName.get("TASK").inAppLocked()).isFalse();
        assertThat(byName.get("TASK").alwaysInApp()).containsExactly("Được giao việc");
        assertThat(byName.get("COMMENT").alwaysInApp()).containsExactly("Được nhắc tên");
        assertThat(byName.get("APPROVAL").alwaysInApp()).hasSize(2);
        assertThat(byName.get("SPRINT").alwaysInApp()).hasSize(2);
        // Email chỉ có ở nhóm có loại gửi email
        assertThat(byName.values().stream().filter(CategoryView::emailAvailable).map(CategoryView::category))
                .containsExactlyInAnyOrder("TASK", "APPROVAL", "COMMENT", "REMINDER", "MEMBER");
        assertThat(byName.get("DEPENDENCY").email()).isFalse();
        assertThat(byName.get("TASK").inApp()).isTrue();
        assertThat(byName.get("TASK").email()).isTrue();
    }

    @Test
    void update_savesChangedCategories_andCannotTurnOffLockedInApp() {
        when(preferences.findByUserId(BINH)).thenReturn(List.of(pref(BINH, NotificationCategory.TASK, true, true)));

        service.update(BINH, List.of(
                new CategoryChange("comment", false, null),
                new CategoryChange("MEMBER", false, false),
                new CategoryChange("TASK", null, false)));

        ArgumentCaptor<NotificationPreference> saved = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferences, org.mockito.Mockito.times(3)).save(saved.capture());
        Map<NotificationCategory, NotificationPreference> rows = saved.getAllValues().stream()
                .collect(Collectors.toMap(NotificationPreference::getCategory, Function.identity()));
        assertThat(rows.get(NotificationCategory.COMMENT).isInApp()).isFalse();
        assertThat(rows.get(NotificationCategory.COMMENT).isEmail()).isTrue();      // không gửi = giữ mặc định
        assertThat(rows.get(NotificationCategory.MEMBER).isInApp()).isTrue();       // bị khoá
        assertThat(rows.get(NotificationCategory.MEMBER).isEmail()).isFalse();
        assertThat(rows.get(NotificationCategory.TASK).isInApp()).isTrue();         // giữ nguyên
        assertThat(rows.get(NotificationCategory.TASK).isEmail()).isFalse();
    }

    @Test
    void update_rejectsUnknownCategoryOrEmptyBody() {
        assertThatThrownBy(() -> service.update(BINH, List.of(new CategoryChange("KHONG_CO", true, true))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(BINH, List.of())).isInstanceOf(BadRequestException.class);
        verify(preferences, never()).save(any());
    }

    @Test
    void mute_upsertsWithName_andRejectsPastUntil() {
        when(mutes.findById(any())).thenReturn(Optional.empty());
        service.mute(BINH, PROJECT, "  Mộc Coffee ", null);
        ArgumentCaptor<ProjectMute> saved = ArgumentCaptor.forClass(ProjectMute.class);
        verify(mutes).save(saved.capture());
        assertThat(saved.getValue().getProjectName()).isEqualTo("Mộc Coffee");
        assertThat(saved.getValue().getMutedUntil()).isNull();
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW.truncatedTo(ChronoUnit.MICROS));

        assertThatThrownBy(() -> service.mute(BINH, PROJECT, null, NOW.minusSeconds(60)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void view_hidesExpiredMutes() {
        when(mutes.findByUserIdOrderByCreatedAtDesc(BINH)).thenReturn(List.of(
                ProjectMute.builder().userId(BINH).projectId(PROJECT).projectName("Mộc").createdAt(NOW).build(),
                ProjectMute.builder().userId(BINH).projectId(UUID.randomUUID()).mutedUntil(NOW.minusSeconds(5)).createdAt(NOW).build()));
        assertThat(service.view(BINH).mutedProjects()).hasSize(1)
                .first().satisfies(m -> assertThat(m.projectName()).isEqualTo("Mộc"));
    }
}
