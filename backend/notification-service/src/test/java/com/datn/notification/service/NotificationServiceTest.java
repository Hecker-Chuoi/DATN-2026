package com.datn.notification.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.notification.dto.PushMessage;
import com.datn.notification.entity.Notification;
import com.datn.notification.enums.NotificationCategory;
import com.datn.notification.enums.NotificationSeverity;
import com.datn.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID ME = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock NotificationRepository repository;
    @Mock NotificationPushService push;
    @InjectMocks NotificationService service;

    private static Notification notification(Instant readAt, Instant deletedAt) {
        return Notification.builder().id(UUID.randomUUID()).recipientId(ME).type("TASK_ASSIGNED")
                .category(NotificationCategory.TASK).severity(NotificationSeverity.INFO).title("t").dedupKey("k")
                .readAt(readAt).deletedAt(deletedAt)
                .createdAt(Instant.parse("2026-11-14T01:00:00Z")).updatedAt(Instant.parse("2026-11-14T01:00:00.123456Z"))
                .build();
    }

    @Test
    void cursor_roundTripsWithMicrosecondPrecision() {
        Notification n = notification(null, null);
        String cursor = NotificationService.Cursor.encode(n);
        NotificationService.Cursor decoded = NotificationService.Cursor.decode(cursor);
        assertThat(decoded.updatedAt()).isEqualTo(n.getUpdatedAt());
        assertThat(decoded.id()).isEqualTo(n.getId());
    }

    @Test
    void cursor_garbageIsBadRequest() {
        assertThatThrownBy(() -> NotificationService.Cursor.decode("@@@")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void filter_parse() {
        assertThat(NotificationService.Filter.parse(null)).isEqualTo(NotificationService.Filter.ALL);
        assertThat(NotificationService.Filter.parse("unread")).isEqualTo(NotificationService.Filter.UNREAD);
        assertThat(NotificationService.Filter.parse("ACTION")).isEqualTo(NotificationService.Filter.ACTION);
        assertThatThrownBy(() -> NotificationService.Filter.parse("abc")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void someoneElsesNotification_is404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndRecipientIdAndDeletedAtIsNull(id, ME)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setRead(ME, id, true)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(ME, id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markRead_thenUnread_pushesToOtherTabs() {
        Notification n = notification(null, null);
        when(repository.findByIdAndRecipientIdAndDeletedAtIsNull(n.getId(), ME)).thenReturn(Optional.of(n));

        assertThat(service.setRead(ME, n.getId(), true).read()).isTrue();
        assertThat(n.getReadAt()).isNotNull();
        ArgumentCaptor<PushMessage> msg = ArgumentCaptor.forClass(PushMessage.class);
        verify(push).pushAfterCommit(eq(ME), msg.capture());
        assertThat(msg.getValue().kind()).isEqualTo("READ");
        assertThat(msg.getValue().ids()).containsExactly(n.getId());

        assertThat(service.setRead(ME, n.getId(), false).read()).isFalse();
        assertThat(n.getReadAt()).isNull();
    }

    @Test
    void markRead_whenAlreadyRead_isNoOp() {
        Notification n = notification(Instant.now(), null);
        when(repository.findByIdAndRecipientIdAndDeletedAtIsNull(n.getId(), ME)).thenReturn(Optional.of(n));
        service.setRead(ME, n.getId(), true);
        verify(repository, never()).save(any());
        verify(push, never()).pushAfterCommit(any(), any());
    }

    @Test
    void delete_isSoft_andRestoreBringsItBack() {
        Notification n = notification(null, null);
        when(repository.findByIdAndRecipientIdAndDeletedAtIsNull(n.getId(), ME)).thenReturn(Optional.of(n));
        service.delete(ME, n.getId());
        assertThat(n.getDeletedAt()).isNotNull();

        when(repository.findByIdAndRecipientId(n.getId(), ME)).thenReturn(Optional.of(n));
        assertThat(service.restore(ME, n.getId()).id()).isEqualTo(n.getId());
        assertThat(n.getDeletedAt()).isNull();
    }

    @Test
    void restore_afterThirtyDays_isRejected() {
        Notification n = notification(null, Instant.now().minus(Duration.ofDays(31)));
        when(repository.findByIdAndRecipientId(n.getId(), ME)).thenReturn(Optional.of(n));
        assertThatThrownBy(() -> service.restore(ME, n.getId())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteAllRead_pushesDeletedIds() {
        UUID a = UUID.randomUUID();
        when(repository.findReadIds(ME)).thenReturn(List.of(a));
        when(repository.softDeleteAllRead(eq(ME), any())).thenReturn(1);
        assertThat(service.deleteAllRead(ME)).isEqualTo(1);
        ArgumentCaptor<PushMessage> msg = ArgumentCaptor.forClass(PushMessage.class);
        verify(push).pushAfterCommit(eq(ME), msg.capture());
        assertThat(msg.getValue().kind()).isEqualTo("DELETED");
        assertThat(msg.getValue().ids()).containsExactly(a);
    }
}
