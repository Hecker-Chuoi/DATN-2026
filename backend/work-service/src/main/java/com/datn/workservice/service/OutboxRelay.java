package com.datn.workservice.service;

import com.datn.common.notification.NotificationEvent;
import com.datn.workservice.config.KafkaProducerConfig;
import com.datn.workservice.entity.OutboxEvent;
import com.datn.workservice.enums.OutboxStatus;
import com.datn.workservice.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.KafkaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Đẩy outbox_events (PENDING) lên Kafka mỗi giây.
 *
 * <ul>
 *   <li>NOTIFICATION → topic notification-events, key = projectId (giữ thứ tự trong một dự án),
 *       {@code eventId} = id dòng outbox (notification-service dùng để chống trùng).</li>
 *   <li>Loại khác (SPRINT…) → topic sự kiện nghiệp vụ tương ứng.</li>
 * </ul>
 *
 * Kafka không kết nối được: KHÔNG tính vào số lần thử (sự kiện vẫn chờ, bật Kafka lên là gửi tiếp),
 * chỉ lùi thời gian thử lại tới tối đa 30 giây. Lỗi khác (dữ liệu hỏng): thử tối đa 10 lần rồi FAILED.
 */
@Slf4j
@Component
public class OutboxRelay {

    static final int BATCH_SIZE = 100;
    static final int MAX_RETRIES = 10;
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean enabled;
    private final Clock clock;

    private Instant pausedUntil = Instant.EPOCH;
    private int consecutiveBrokerFailures = 0;

    @org.springframework.beans.factory.annotation.Autowired
    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       @Value("${app.outbox.relay-enabled:true}") boolean enabled) {
        this(repository, kafkaTemplate, enabled, Clock.systemUTC());
    }

    OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, Object> kafkaTemplate, boolean enabled, Clock clock) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.enabled = enabled;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}", initialDelay = 5000)
    @Transactional
    public void relay() {
        if (!enabled || clock.instant().isBefore(pausedUntil)) return;

        List<OutboxEvent> batch = repository.lockPendingBatch(BATCH_SIZE);
        for (OutboxEvent event : batch) {
            try {
                send(event);
                event.setStatus(OutboxStatus.PROCESSED);
                event.setProcessedAt(LocalDateTime.now());
                consecutiveBrokerFailures = 0;
            } catch (BrokerUnavailableException e) {
                // Giữ PENDING, không tăng retryCount; dừng cả lô và lùi thời gian thử lại.
                consecutiveBrokerFailures++;
                long seconds = Math.min(MAX_BACKOFF.getSeconds(), 1L << Math.min(consecutiveBrokerFailures, 5));
                pausedUntil = clock.instant().plusSeconds(seconds);
                log.warn("Kafka chưa sẵn sàng, tạm dừng gửi outbox {} giây: {}", seconds, e.getMessage());
                break;
            } catch (Exception e) {
                int retries = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
                event.setRetryCount(retries);
                if (retries >= MAX_RETRIES) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox {} ({}) thất bại {} lần, chuyển FAILED: {}", event.getId(), event.getEventType(), retries, e.getMessage());
                } else {
                    log.warn("Outbox {} ({}) gửi lỗi lần {}: {}", event.getId(), event.getEventType(), retries, e.getMessage());
                }
            }
        }
        if (!batch.isEmpty()) repository.saveAll(batch);
    }

    private void send(OutboxEvent event) throws Exception {
        String topic = topicFor(event.getAggregateType());
        Map<String, Object> payload = new LinkedHashMap<>(event.getPayload() == null ? Map.of() : event.getPayload());
        String key = event.getAggregateId();
        if (NotificationEvent.AGGREGATE_TYPE.equals(event.getAggregateType())) {
            payload.put("eventId", event.getId().toString());
            Object projectId = payload.get("projectId");
            Object workspaceId = payload.get("workspaceId");
            key = projectId != null ? projectId.toString() : (workspaceId != null ? workspaceId.toString() : key);
        }
        try {
            kafkaTemplate.send(topic, key, payload).get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BrokerUnavailableException("gửi quá 10 giây", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof org.apache.kafka.common.errors.TimeoutException
                    || cause instanceof org.apache.kafka.common.errors.RetriableException) {
                throw new BrokerUnavailableException(cause.getMessage(), cause);
            }
            throw e;
        } catch (KafkaException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.TimeoutException) {
                throw new BrokerUnavailableException(e.getMessage(), e);
            }
            throw e;
        } catch (org.springframework.kafka.KafkaException e) {
            if (e.getMostSpecificCause() instanceof org.apache.kafka.common.errors.TimeoutException) {
                throw new BrokerUnavailableException(e.getMessage(), e);
            }
            throw e;
        }
    }

    static String topicFor(String aggregateType) {
        if (aggregateType == null) return KafkaProducerConfig.PROJECT_EVENTS_TOPIC;
        return switch (aggregateType) {
            case NotificationEvent.AGGREGATE_TYPE -> NotificationEvent.TOPIC;
            case "WORK_ITEM", "TASK" -> KafkaProducerConfig.TASK_EVENTS_TOPIC;
            case "COMMENT" -> KafkaProducerConfig.COMMENT_EVENTS_TOPIC;
            default -> KafkaProducerConfig.PROJECT_EVENTS_TOPIC;
        };
    }

    /** Dọn các dòng đã gửi quá 7 ngày (03:30 mỗi ngày). */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanup() {
        int n = repository.deleteProcessedBefore(LocalDateTime.now().minusDays(7));
        if (n > 0) log.info("Đã dọn {} dòng outbox đã gửi", n);
    }

    /** Kafka không kết nối được (lỗi tạm thời). */
    static class BrokerUnavailableException extends Exception {
        BrokerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
