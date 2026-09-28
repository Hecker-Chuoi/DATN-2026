package com.datn.workservice.service;

import com.datn.workservice.entity.OutboxEvent;
import com.datn.workservice.enums.OutboxStatus;
import com.datn.workservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Ghi su kien nghiep vu vao bang outbox_events trong cung transaction voi thay doi du lieu
 * (Transactional Outbox). Worker doc bang nay va day len Kafka thuoc Giai doan 8.4.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Ghi mot su kien. Loi ghi outbox khong duoc lam hong nghiep vu chinh nen duoc nuot lai
     * va chi log canh bao.
     */
    public void record(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId == null ? null : aggregateId.toString())
                    .eventType(eventType)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();
            outboxEventRepository.save(event);
            log.debug("Recorded outbox event {} for {} {}", eventType, aggregateType, aggregateId);
        } catch (Exception ex) {
            log.warn("Failed to record outbox event {} for {} {}: {}",
                    eventType, aggregateType, aggregateId, ex.getMessage());
        }
    }
}
