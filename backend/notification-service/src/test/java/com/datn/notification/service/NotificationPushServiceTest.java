package com.datn.notification.service;

import com.datn.notification.cluster.RedisPushRelay;
import com.datn.notification.dto.PushMessage;
import com.datn.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationPushServiceTest {

    private static final UUID BINH = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock SimpMessagingTemplate messaging;
    @Mock NotificationRepository repository;
    @Mock ObjectProvider<RedisPushRelay> relayProvider;
    @Mock RedisPushRelay relay;

    private NotificationPushService service;
    private final PushMessage msg = PushMessage.ofIds("READ", List.of(UUID.randomUUID()));

    @BeforeEach
    void setUp() {
        service = new NotificationPushService(messaging, repository, relayProvider);
        when(repository.countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(BINH)).thenReturn(3L);
        when(repository.countPendingActions(BINH)).thenReturn(1L);
    }

    @Test
    void singleInstance_sendsLocallyWithFreshCounts() {
        when(relayProvider.getIfAvailable()).thenReturn(null);
        service.pushAfterCommit(BINH, msg);
        verify(messaging).convertAndSendToUser(eq(BINH.toString()), eq(NotificationPushService.USER_QUEUE),
                argThat(m -> m instanceof PushMessage p && p.unreadCount() == 3 && p.actionCount() == 1));
    }

    @Test
    void cluster_publishesToRedis_insteadOfLocalSend() throws Exception {
        when(relayProvider.getIfAvailable()).thenReturn(relay);
        service.pushAfterCommit(BINH, msg);
        verify(relay).publish(eq(BINH), argThat(p -> p.unreadCount() == 3));
        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void cluster_redisDown_fallsBackToLocalSend() throws Exception {
        when(relayProvider.getIfAvailable()).thenReturn(relay);
        doThrow(new RuntimeException("redis down")).when(relay).publish(any(), any());
        service.pushAfterCommit(BINH, msg);
        verify(messaging).convertAndSendToUser(eq(BINH.toString()), eq(NotificationPushService.USER_QUEUE), any());
    }
}
