package com.datn.workservice.service;

import com.datn.workservice.entity.OutboxEvent;
import com.datn.workservice.enums.OutboxStatus;
import com.datn.workservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final OutboxEventRepository repo = mock(OutboxEventRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC);
    private final OutboxRelay relay = new OutboxRelay(repo, kafka, true, clock);

    private static OutboxEvent notification(String projectId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "TASK_ASSIGNED");
        payload.put("projectId", projectId);
        return OutboxEvent.builder().id(UUID.randomUUID()).aggregateType("NOTIFICATION")
                .aggregateId(UUID.randomUUID().toString()).eventType("TASK_ASSIGNED").payload(payload)
                .status(OutboxStatus.PENDING).retryCount(0).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsNotificationWithEventIdAndProjectKey_thenMarksProcessed() {
        OutboxEvent e = notification("proj-1");
        when(repo.lockPendingBatch(anyInt())).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("notification-events"), eq("proj-1"), payload.capture());
        assertThat((Map<String, Object>) payload.getValue()).containsEntry("eventId", e.getId().toString());
        assertThat(e.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(e.getProcessedAt()).isNotNull();
        verify(repo).saveAll(List.of(e));
    }

    @Test
    void brokerDown_keepsPending_doesNotCountRetry_andPauses() {
        OutboxEvent a = notification("p");
        OutboxEvent b = notification("p");
        when(repo.lockPendingBatch(anyInt())).thenReturn(List.of(a, b));
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new org.apache.kafka.common.errors.TimeoutException("no broker")));

        relay.relay();

        assertThat(a.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(a.getRetryCount()).isZero();
        verify(kafka, times(1)).send(anyString(), anyString(), any()); // dừng cả lô sau lỗi đầu tiên

        relay.relay(); // còn trong thời gian tạm dừng: không đọc DB
        verify(repo, times(1)).lockPendingBatch(anyInt());
    }

    @Test
    void permanentError_countsRetries_andFailsAfterTen() {
        OutboxEvent e = notification("p");
        e.setRetryCount(9);
        when(repo.lockPendingBatch(anyInt())).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new org.apache.kafka.common.errors.SerializationException("bad")));

        relay.relay();

        assertThat(e.getRetryCount()).isEqualTo(10);
        assertThat(e.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void disabledRelay_doesNothing() {
        new OutboxRelay(repo, kafka, false, clock).relay();
        verify(repo, never()).lockPendingBatch(anyInt());
    }

    @Test
    void topicMapping() {
        assertThat(OutboxRelay.topicFor("NOTIFICATION")).isEqualTo("notification-events");
        assertThat(OutboxRelay.topicFor("SPRINT")).isEqualTo("project-events-topic");
        assertThat(OutboxRelay.topicFor("WORK_ITEM")).isEqualTo("task-events-topic");
        assertThat(OutboxRelay.topicFor("COMMENT")).isEqualTo("comment-events-topic");
    }
}
