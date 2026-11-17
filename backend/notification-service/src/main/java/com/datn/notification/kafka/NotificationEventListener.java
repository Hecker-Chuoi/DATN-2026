package com.datn.notification.kafka;

import com.datn.common.notification.NotificationEvent;
import com.datn.notification.service.NotificationIngestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Đọc topic notification-events (do OutboxRelay của work-service / workspace-service gửi lên).
 * Giao ít nhất một lần: notification-service chống trùng bằng dedup_key, nên nhận lại vẫn an toàn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationIngestService ingestService;

    @KafkaListener(
            topics = NotificationEvent.TOPIC,
            groupId = "notification-service",
            autoStartup = "${app.notifications.kafka-enabled:true}")
    public void onMessage(ConsumerRecord<String, String> record) throws JsonProcessingException {
        NotificationEvent event = objectMapper.readValue(record.value(), NotificationEvent.class);
        int changed = ingestService.ingest(event);
        log.debug("Đã xử lý sự kiện {} ({}), {} thông báo được tạo/cập nhật", event.getEventId(), event.getType(), changed);
    }
}
