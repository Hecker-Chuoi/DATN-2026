package com.datn.workservice.service;

import com.datn.workservice.entity.OutboxEvent;
import com.datn.workservice.enums.OutboxStatus;
import com.datn.workservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ghi sự kiện nghiệp vụ vào bảng outbox_events trong CÙNG transaction với thay đổi dữ liệu
 * (Transactional Outbox). {@link OutboxRelay} đọc bảng này và gửi lên Kafka.
 *
 * Không nuốt lỗi: dòng outbox chỉ được INSERT lúc flush/commit, nên try/catch quanh save() không bắt được
 * lỗi thật (rủi ro R-9 trong tài liệu realtime). Nguyên tắc của Outbox là nghiệp vụ và sự kiện
 * CÙNG thành công hoặc CÙNG thất bại; đầu vào được kiểm tra trước để lỗi chỉ có thể đến từ DB.
 * Lỗi trong việc chuẩn bị nội dung thông báo được NotificationEventPublisher bắt riêng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private final OutboxEventRepository outboxEventRepository;

    public void record(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId.toString())
                .eventType(eventType)
                .payload(payload == null ? Map.of() : payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        outboxEventRepository.save(event);
        log.debug("Recorded outbox event {} for {} {}", eventType, aggregateType, aggregateId);
    }
}
