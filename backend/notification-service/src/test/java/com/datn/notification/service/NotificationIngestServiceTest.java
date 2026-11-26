package com.datn.notification.service;

import com.datn.common.notification.NotificationEvent;
import com.datn.common.notification.NotificationType;
import com.datn.common.notification.RecipientReason;
import com.datn.notification.catalog.NotificationCatalog;
import com.datn.notification.client.UserDirectory;
import com.datn.notification.dto.PushMessage;
import com.datn.notification.entity.Notification;
import com.datn.notification.enums.NotificationCategory;
import com.datn.notification.enums.NotificationSeverity;
import com.datn.notification.entity.NotificationPreference;
import com.datn.notification.entity.ProjectMute;
import com.datn.notification.repository.NotificationPreferenceRepository;
import com.datn.notification.repository.NotificationRepository;
import com.datn.notification.repository.ProjectMuteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationIngestServiceTest {

    private static final String ACTOR = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String BINH = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String CHI = "cccccccc-0000-0000-0000-000000000003";
    private static final String ITEM = "dddddddd-0000-0000-0000-000000000004";
    private static final Instant NOW = Instant.parse("2026-11-14T02:00:00Z");

    @Mock NotificationRepository repository;
    @Mock UserDirectory users;
    @Mock NotificationPushService push;
    @Mock NotificationMailer mailer;
    @Mock NotificationPreferenceRepository preferenceRepository;
    @Mock ProjectMuteRepository muteRepository;

    private NotificationIngestService service;

    @BeforeEach
    void setUp() {
        NotificationCatalog catalog = new NotificationCatalog();
        NotificationPreferenceService preferences = new NotificationPreferenceService(
                preferenceRepository, muteRepository, catalog, Clock.fixed(NOW, ZoneOffset.UTC));
        service = new NotificationIngestService(repository, catalog, users, push, mailer, preferences,
                Duration.ofMinutes(10), Clock.fixed(NOW, ZoneOffset.UTC));
        when(preferenceRepository.findByUserIdInAndCategory(anyCollection(), any())).thenReturn(List.of());
        when(muteRepository.findByProjectIdAndUserIdIn(any(), anyCollection())).thenReturn(List.of());
        when(users.displayName(ACTOR)).thenReturn("An");
        when(users.displayName(CHI)).thenReturn("Chi");
        when(repository.existsByDedupKey(anyString())).thenReturn(false);
        when(repository.findFirstByRecipientIdAndGroupKeyAndReadAtIsNullAndDeletedAtIsNullAndUpdatedAtAfterOrderByUpdatedAtDesc(
                any(), anyString(), any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static NotificationEvent event(NotificationType type, String eventId, String actor, Object... recipients) {
        List<NotificationEvent.Recipient> rs = new ArrayList<>();
        for (int i = 0; i < recipients.length; i += 2) {
            rs.add(new NotificationEvent.Recipient((String) recipients[i], ((RecipientReason) recipients[i + 1]).name()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("priority", "MEDIUM");
        data.put("snippet", "Nhớ chụp ảnh lối thoát hiểm");
        data.put("commentId", "eeeeeeee-0000-0000-0000-000000000005");
        return NotificationEvent.builder()
                .eventId(eventId).type(type.name()).actorId(actor)
                .workspaceId("11111111-1111-1111-1111-111111111111")
                .projectId("22222222-2222-2222-2222-222222222222")
                .projectName("Mộc Coffee").projectKey("MOC")
                .entityType("WORK_ITEM").entityId(ITEM).entityCode("MOC-2").entityTitle("Khảo sát")
                .recipients(rs).data(data).build();
    }

    @Test
    void unknownType_isIgnored() {
        NotificationEvent e = event(NotificationType.TASK_ASSIGNED, "e1", ACTOR, BINH, RecipientReason.NEW_ASSIGNEE);
        e.setType("KHONG_CO");
        assertThat(service.ingest(e)).isZero();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createsOneNotificationPerRecipient_skipsActor_andPushesAndEmails() {
        NotificationEvent e = event(NotificationType.TASK_ASSIGNED, "e1", ACTOR,
                BINH, RecipientReason.NEW_ASSIGNEE, ACTOR, RecipientReason.REPORTER);

        assertThat(service.ingest(e)).isEqualTo(1);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(saved.capture());
        Notification n = saved.getValue();
        assertThat(n.getRecipientId()).isEqualTo(UUID.fromString(BINH));
        assertThat(n.getType()).isEqualTo("TASK_ASSIGNED");
        assertThat(n.getCategory()).isEqualTo(NotificationCategory.TASK);
        assertThat(n.getSeverity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(n.getTitle()).isEqualTo("<b>An</b> đã giao cho bạn <b>MOC-2</b> · Khảo sát");
        assertThat(n.getLink()).isEqualTo("/browse/MOC-2");
        assertThat(n.getDedupKey()).isEqualTo("e1:" + BINH);
        assertThat(n.getActorId()).isEqualTo(UUID.fromString(ACTOR));
        assertThat(n.getData()).containsEntry("actorName", "An").containsEntry("projectName", "Mộc Coffee");
        assertThat(n.getCreatedAt()).isEqualTo(NOW);

        ArgumentCaptor<PushMessage> msg = ArgumentCaptor.forClass(PushMessage.class);
        verify(push).pushAfterCommit(eq(UUID.fromString(BINH)), msg.capture());
        assertThat(msg.getValue().kind()).isEqualTo("CREATED");
        verify(mailer).sendAfterCommit(any(), any(), eq("/browse/MOC-2"));
    }

    @Test
    void strongestReasonWins_andDuplicateRecipientsCollapse() {
        NotificationEvent e = event(NotificationType.COMMENT_ADDED, "e1", ACTOR,
                BINH, RecipientReason.REPORTER, BINH, RecipientReason.ASSIGNEE, BINH.toUpperCase(), RecipientReason.MENTIONED);
        Map<String, RecipientReason> r = NotificationIngestService.strongestReasons(e);
        assertThat(r).hasSize(1).containsEntry(BINH, RecipientReason.MENTIONED);
    }

    @Test
    void alreadyDelivered_isNotDuplicated() {
        when(repository.existsByDedupKey("e1:" + BINH)).thenReturn(true);
        NotificationEvent e = event(NotificationType.TASK_ASSIGNED, "e1", ACTOR, BINH, RecipientReason.NEW_ASSIGNEE);
        assertThat(service.ingest(e)).isZero();
        verify(repository, never()).saveAndFlush(any());
        verify(push, never()).pushAfterCommit(any(), any());
    }

    @Test
    void scheduledJob_usesItsOwnDedupKey() {
        NotificationEvent e = event(NotificationType.TASK_DUE_SOON, "random-event-id", null, BINH, RecipientReason.ASSIGNEE);
        e.setDedupKey("TASK_DUE_SOON:" + ITEM + ":2026-11-15");
        e.getData().put("dueDate", "2026-11-15");
        e.getData().put("daysLeft", 1);
        service.ingest(e);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDedupKey()).isEqualTo("TASK_DUE_SOON:" + ITEM + ":2026-11-15:" + BINH);
        assertThat(saved.getValue().getActorId()).isNull();
        assertThat(saved.getValue().getTitle()).isEqualTo("<b>MOC-2</b> đến hạn vào ngày mai (15/11)");
    }

    @Test
    void groupableType_mergesIntoUnreadNotification_andCountsDistinctActors() {
        Notification existing = Notification.builder()
                .id(UUID.randomUUID()).recipientId(UUID.fromString(BINH)).type("COMMENT_ADDED")
                .category(NotificationCategory.COMMENT).severity(NotificationSeverity.INFO)
                .title("<b>Chi</b> đã bình luận trong <b>MOC-2</b>").dedupKey("old:" + BINH).groupKey("D2:" + ITEM)
                .data(new LinkedHashMap<>(Map.of("actorIds", List.of(CHI), "eventIds", List.of("old"))))
                .createdAt(NOW.minusSeconds(120)).updatedAt(NOW.minusSeconds(120)).build();
        when(repository.findFirstByRecipientIdAndGroupKeyAndReadAtIsNullAndDeletedAtIsNullAndUpdatedAtAfterOrderByUpdatedAtDesc(
                eq(UUID.fromString(BINH)), eq("D2:" + ITEM), eq(NOW.minus(Duration.ofMinutes(10)))))
                .thenReturn(Optional.of(existing));

        NotificationEvent e = event(NotificationType.COMMENT_ADDED, "e2", ACTOR, BINH, RecipientReason.ASSIGNEE);
        assertThat(service.ingest(e)).isEqualTo(1);

        verify(repository, never()).saveAndFlush(any());
        verify(repository).save(existing);
        assertThat(existing.getTitle()).isEqualTo("<b>An</b> và 1 người khác đã bình luận trong <b>MOC-2</b>");
        assertThat(existing.getActorCount()).isEqualTo(2);
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
        assertThat(existing.getActorId()).isEqualTo(UUID.fromString(ACTOR));

        ArgumentCaptor<PushMessage> msg = ArgumentCaptor.forClass(PushMessage.class);
        verify(push).pushAfterCommit(eq(UUID.fromString(BINH)), msg.capture());
        assertThat(msg.getValue().kind()).isEqualTo("UPDATED");

        // Kafka giao lại đúng sự kiện e2: không đổi gì
        assertThat(service.ingest(e)).isZero();
        verify(repository, times(1)).save(existing);
    }

    @Test
    void sameActorCommentingTwice_staysOneActor() {
        Notification existing = Notification.builder()
                .id(UUID.randomUUID()).recipientId(UUID.fromString(BINH)).type("COMMENT_ADDED")
                .category(NotificationCategory.COMMENT).severity(NotificationSeverity.INFO)
                .title("x").dedupKey("old:" + BINH).groupKey("D2:" + ITEM)
                .data(new LinkedHashMap<>(Map.of("actorIds", List.of(ACTOR), "eventIds", List.of("old"))))
                .createdAt(NOW).updatedAt(NOW).build();
        when(repository.findFirstByRecipientIdAndGroupKeyAndReadAtIsNullAndDeletedAtIsNullAndUpdatedAtAfterOrderByUpdatedAtDesc(
                any(), anyString(), any())).thenReturn(Optional.of(existing));
        service.ingest(event(NotificationType.COMMENT_ADDED, "e3", ACTOR, BINH, RecipientReason.ASSIGNEE));
        assertThat(existing.getActorCount()).isEqualTo(1);
        assertThat(existing.getTitle()).isEqualTo("<b>An</b> đã bình luận trong <b>MOC-2</b>");
    }

    @Test
    void approval_resolvesOpenCompletionRequestsOfEveryManager_butNotTheOneItCreated() {
        Notification otherManager = Notification.builder().id(UUID.randomUUID()).recipientId(UUID.fromString(CHI))
                .type("COMPLETION_REQUESTED").category(NotificationCategory.APPROVAL).severity(NotificationSeverity.INFO)
                .actionRequired(true).title("t").dedupKey("req:" + CHI).sourceEventId("req").data(new LinkedHashMap<>())
                .createdAt(NOW).updatedAt(NOW).build();
        Notification fromThisEvent = Notification.builder().id(UUID.randomUUID()).recipientId(UUID.fromString(BINH))
                .type("COMPLETION_REQUESTED").category(NotificationCategory.APPROVAL).severity(NotificationSeverity.INFO)
                .actionRequired(true).title("t").dedupKey("e9:" + BINH).sourceEventId("e9").data(new LinkedHashMap<>())
                .createdAt(NOW).updatedAt(NOW).build();
        when(repository.findOpenActions(eq(UUID.fromString(ITEM)), anyCollection()))
                .thenReturn(List.of(otherManager, fromThisEvent));

        service.ingest(event(NotificationType.COMPLETION_APPROVED, "e9", ACTOR, BINH, RecipientReason.REQUESTER));

        assertThat(otherManager.getActionResolvedAt()).isEqualTo(NOW);
        assertThat(fromThisEvent.getActionResolvedAt()).isNull();
        verify(push).pushAfterCommit(eq(UUID.fromString(CHI)), any());
    }

    @Test
    void internalType_onlyResolves_neverCreates() {
        NotificationEvent e = event(NotificationType.PROJECT_INVITATION_CANCELLED, "e5", ACTOR, BINH, RecipientReason.INVITEE);
        e.setEntityType("INVITATION");
        assertThat(service.ingest(e)).isZero();
        verify(repository).findOpenActions(eq(UUID.fromString(ITEM)), eq(List.of("PROJECT_INVITATION_RECEIVED")));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void invalidRecipientIds_areSkipped() {
        NotificationEvent e = event(NotificationType.TASK_ASSIGNED, "e1", ACTOR, "not-a-uuid", RecipientReason.NEW_ASSIGNEE);
        assertThat(service.ingest(e)).isZero();
        verify(repository, never()).saveAndFlush(any());
    }

    // ─────────────────────────── Cài đặt bật / tắt (tài liệu 3 §10.1) ───────────────────────────

    private void prefer(String user, NotificationCategory category, boolean inApp, boolean email) {
        when(preferenceRepository.findByUserIdInAndCategory(anyCollection(), eq(category))).thenReturn(List.of(
                NotificationPreference.builder().userId(UUID.fromString(user)).category(category)
                        .inApp(inApp).email(email).updatedAt(NOW).build()));
    }

    @Test
    void categoryOff_noNotificationNoPushNoMail() {
        prefer(BINH, NotificationCategory.COMMENT, false, false);
        assertThat(service.ingest(event(NotificationType.COMMENT_ADDED, "p1", ACTOR, BINH, RecipientReason.ASSIGNEE))).isZero();
        verify(repository, never()).saveAndFlush(any());
        verify(push, never()).pushAfterCommit(any(), any());
        verify(mailer, never()).sendAfterCommit(any(), any(), any());
    }

    @Test
    void mentionStillDelivered_whenCommentsOff_butEmailFollowsSetting() {
        prefer(BINH, NotificationCategory.COMMENT, false, false);
        assertThat(service.ingest(event(NotificationType.COMMENT_MENTION, "p2", ACTOR, BINH, RecipientReason.MENTIONED))).isEqualTo(1);
        verify(repository).saveAndFlush(any());
        verify(push).pushAfterCommit(eq(UUID.fromString(BINH)), any());
        verify(mailer, never()).sendAfterCommit(any(), any(), any());
    }

    @Test
    void emailOff_keepsInApp() {
        prefer(BINH, NotificationCategory.TASK, true, false);
        assertThat(service.ingest(event(NotificationType.TASK_ASSIGNED, "p3", ACTOR, BINH, RecipientReason.NEW_ASSIGNEE))).isEqualTo(1);
        verify(push).pushAfterCommit(eq(UUID.fromString(BINH)), any());
        verify(mailer, never()).sendAfterCommit(any(), any(), any());
    }

    @Test
    void inAppOff_emailOn_storesHiddenCopyForDedup_andSendsMailOnly() {
        prefer(BINH, NotificationCategory.REMINDER, false, true);
        NotificationEvent e = event(NotificationType.TASK_DUE_SOON, "p4", null, BINH, RecipientReason.ASSIGNEE);
        e.getData().put("dueDate", "2026-11-15");
        e.getData().put("daysLeft", 1);

        assertThat(service.ingest(e)).isZero(); // không có gì hiện ra trong chuông

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDeletedAt()).isEqualTo(NOW);
        verify(push, never()).pushAfterCommit(any(), any());
        verify(mailer).sendAfterCommit(any(), any(), any());
    }

    @Test
    void mutedProject_skipsFollowUp_keepsDirect() {
        when(muteRepository.findByProjectIdAndUserIdIn(any(), anyCollection())).thenReturn(List.of(ProjectMute.builder()
                .userId(UUID.fromString(BINH)).projectId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .createdAt(NOW).build()));

        assertThat(service.ingest(event(NotificationType.TASK_STATUS_CHANGED, "p5", ACTOR, BINH, RecipientReason.ASSIGNEE))).isZero();
        assertThat(service.ingest(event(NotificationType.TASK_ASSIGNED, "p6", ACTOR, BINH, RecipientReason.NEW_ASSIGNEE))).isEqualTo(1);
    }

    @Test
    void actionRequiredStaysVisible_evenWhenCategoryOff() {
        prefer(BINH, NotificationCategory.APPROVAL, false, false);
        assertThat(service.ingest(event(NotificationType.COMPLETION_REQUESTED, "p7", ACTOR, BINH, RecipientReason.PROJECT_MANAGER)))
                .isEqualTo(1);
    }
}
