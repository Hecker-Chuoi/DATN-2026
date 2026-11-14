package com.datn.workservice.notification;

import com.datn.common.notification.NotificationType;
import com.datn.common.notification.RecipientReason;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.WorkItemPriority;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.datn.workservice.notification.NotificationTestSupport.data;
import static com.datn.workservice.notification.NotificationTestSupport.recipients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventPublisherTest {

    private final NotificationTestSupport t = new NotificationTestSupport();
    private final UUID actor = UUID.randomUUID();
    private final UUID binh = UUID.randomUUID();
    private final UUID chi = UUID.randomUUID();

    private WorkItem item() {
        return WorkItem.builder().id(UUID.randomUUID()).projectId(t.project.getId()).code("MOC-2")
                .summary("Khảo sát").priority(WorkItemPriority.HIGH).build();
    }

    @Test
    void writesCompleteEnvelopeToOutbox() {
        WorkItem item = item();
        boolean ok = t.publisher.event(NotificationType.TASK_ASSIGNED, t.project).item(item).actor(actor)
                .to(binh, RecipientReason.NEW_ASSIGNEE)
                .data("priority", WorkItemPriority.HIGH)
                .data("dueDate", LocalDate.of(2026, 11, 20))
                .data("at", LocalDateTime.of(2026, 11, 20, 9, 30))
                .data("points", new BigDecimal("3.00"))
                .data("half", new BigDecimal("2.5"))
                .data("counts", Map.of(binh, 2))
                .data("missing", null)
                .publish();

        assertThat(ok).isTrue();
        verify(t.outbox).record(eq("NOTIFICATION"), eq(item.getId()), eq("TASK_ASSIGNED"), any());
        Map<String, Object> p = t.only("TASK_ASSIGNED");
        assertThat(p).containsEntry("source", "work-service")
                .containsEntry("actorId", actor.toString())
                .containsEntry("projectId", t.project.getId().toString())
                .containsEntry("workspaceId", t.project.getWorkspaceId().toString())
                .containsEntry("projectKey", "MOC").containsEntry("projectName", "Mộc Coffee")
                .containsEntry("entityType", "WORK_ITEM").containsEntry("entityCode", "MOC-2")
                .containsEntry("entityTitle", "Khảo sát")
                .doesNotContainKey("eventId"); // relay sẽ đặt
        assertThat(recipients(p)).containsExactly(Map.entry(binh.toString(), "NEW_ASSIGNEE"));
        assertThat(data(p)).containsEntry("priority", "HIGH")
                .containsEntry("dueDate", "2026-11-20")
                .containsEntry("at", "2026-11-20T09:30")
                .containsEntry("points", 3L)
                .containsEntry("half", 2.5)
                .containsEntry("counts", Map.of(binh.toString(), 2))
                .containsEntry("projectType", "SOFTWARE_SCRUM")
                .doesNotContainKey("missing");
    }

    @Test
    void actorNeverReceivesOwnNotification_andNoRecipientsMeansNothingWritten() {
        boolean ok = t.publisher.event(NotificationType.TASK_ASSIGNED, t.project).item(item()).actor(actor)
                .to(actor, RecipientReason.NEW_ASSIGNEE).publish();
        assertThat(ok).isFalse();
        verify(t.outbox, never()).record(anyString(), any(), anyString(), any());
    }

    @Test
    void strongestReasonKeptPerRecipient() {
        t.publisher.event(NotificationType.COMMENT_ADDED, t.project).item(item()).actor(actor)
                .to(binh, RecipientReason.REPORTER).to(binh, RecipientReason.ASSIGNEE).to(chi, RecipientReason.REPORTER)
                .publish();
        assertThat(recipients(t.only("COMMENT_ADDED")))
                .containsEntry(binh.toString(), "ASSIGNEE").containsEntry(chi.toString(), "REPORTER");
    }

    @Test
    void nonMembersAreDropped_unlessAllowed() {
        when(t.resolver.onlyMembers(eq(t.project), any())).thenReturn(Set.of(binh));
        t.publisher.event(NotificationType.TASK_ASSIGNED, t.project).item(item()).actor(actor)
                .to(binh, RecipientReason.ASSIGNEE).to(chi, RecipientReason.ASSIGNEE).publish();
        assertThat(recipients(t.only("TASK_ASSIGNED"))).containsOnlyKeys(binh.toString());

        t.publisher.event(NotificationType.PROJECT_INVITATION_RECEIVED, t.project).projectEntity().actor(actor)
                .to(chi, RecipientReason.INVITEE).allowNonMembers().publish();
        assertThat(recipients(t.only("PROJECT_INVITATION_RECEIVED"))).containsOnlyKeys(chi.toString());
    }

    @Test
    void templateProject_isSkipped() {
        t.project.setIsTemplate(true);
        assertThat(t.publisher.event(NotificationType.TASK_ASSIGNED, t.project).item(item())
                .to(binh, RecipientReason.ASSIGNEE).publish()).isFalse();
        verify(t.outbox, never()).record(anyString(), any(), anyString(), any());
    }

    @Test
    void deletedProject_onlyWhenAllowed() {
        t.project.setDeletedAt(LocalDateTime.now());
        assertThat(t.publisher.event(NotificationType.TASK_ASSIGNED, t.project).item(item())
                .to(binh, RecipientReason.ASSIGNEE).publish()).isFalse();
        assertThat(t.publisher.event(NotificationType.PROJECT_ARCHIVED, t.project).projectEntity().actor(actor)
                .to(binh, RecipientReason.MEMBER).allowDeletedProject().publish()).isTrue();
    }

    @Test
    void internalTypeIsWrittenEvenWithoutRecipients() {
        assertThat(t.publisher.event(NotificationType.COMPLETION_WITHDRAWN, t.project).item(item()).actor(actor).publish())
                .isTrue();
        assertThat(t.types()).containsExactly("COMPLETION_WITHDRAWN");
    }

    @Test
    void failuresNeverBreakTheBusinessOperation() {
        doThrow(new IllegalStateException("db")).when(t.outbox).record(anyString(), any(), anyString(), any());
        assertThat(t.publisher.event(NotificationType.TASK_ASSIGNED, t.project).item(item())
                .to(binh, RecipientReason.ASSIGNEE).publish()).isFalse();
        assertThat(t.publisher.event(NotificationType.TASK_ASSIGNED, (com.datn.workservice.entity.Project) null)
                .to(binh, RecipientReason.ASSIGNEE).publish()).isFalse();
    }
}
