package com.datn.notification.cluster;

import com.datn.notification.dto.PushMessage;
import com.datn.notification.service.NotificationPushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Đẩy WebSocket khi chạy nhiều instance ({@code app.cluster.redis-enabled=true}).
 * <p>
 * Broker STOMP là bộ nhớ trong của từng instance, còn Kafka giao mỗi sự kiện cho đúng một instance: người dùng nối
 * WebSocket vào instance khác sẽ không nhận được. Instance xử lý sự kiện publish {@code {r: recipientId, m: message}}
 * lên kênh Redis; mọi instance (kể cả chính nó) nhận và gửi tại chỗ: instance không giữ phiên của người đó thì lệnh
 * gửi không làm gì. Vì vậy mỗi phiên nhận đúng một lần.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.cluster.redis-enabled", havingValue = "true")
public class RedisPushRelay implements MessageListener {

    public static final String CHANNEL = "datn:notifications:push";

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisPushRelay(StringRedisTemplate redis, SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.redis = redis;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /** Ném lỗi nếu Redis không nhận: bên gọi tự gửi tại chỗ thay thế. */
    public void publish(UUID recipientId, PushMessage message) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("r", recipientId.toString());
        envelope.put("m", message);
        redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode root = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            String recipient = root.path("r").asText(null);
            JsonNode payload = root.get("m");
            if (recipient == null || payload == null) return;
            messagingTemplate.convertAndSendToUser(recipient, NotificationPushService.USER_QUEUE, payload);
        } catch (Exception e) {
            log.warn("Bỏ qua thông điệp đẩy hỏng từ Redis: {}", e.getMessage());
        }
    }
}
