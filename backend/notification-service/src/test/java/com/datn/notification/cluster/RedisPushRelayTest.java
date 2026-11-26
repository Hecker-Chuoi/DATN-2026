package com.datn.notification.cluster;

import com.datn.notification.dto.PushMessage;
import com.datn.notification.service.NotificationPushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisPushRelayTest {

    private static final UUID BINH = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock StringRedisTemplate redis;
    @Mock SimpMessagingTemplate messaging;

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private RedisPushRelay relay() {
        return new RedisPushRelay(redis, messaging, mapper);
    }

    private static DefaultMessage message(String body) {
        return new DefaultMessage(RedisPushRelay.CHANNEL.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void publish_thenEveryInstanceDeliversLocally_sameJsonShape() throws Exception {
        PushMessage msg = PushMessage.ofIds("READ", List.of(UUID.randomUUID())).withCounts(3, 1);
        relay().publish(BINH, msg);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(RedisPushRelay.CHANNEL), sent.capture());

        // instance nhận (có thể là chính nó) gửi xuống phiên WebSocket tại chỗ
        relay().onMessage(message(sent.getValue()), null);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(eq(BINH.toString()), eq(NotificationPushService.USER_QUEUE), payload.capture());
        JsonNode node = (JsonNode) payload.getValue();
        assertThat(node.get("kind").asText()).isEqualTo("READ");
        assertThat(node.get("unreadCount").asLong()).isEqualTo(3);
        assertThat(node.get("actionCount").asLong()).isEqualTo(1);
        assertThat(mapper.readTree(mapper.writeValueAsString(msg))).isEqualTo(node);
    }

    @Test
    void brokenMessage_isIgnored() {
        relay().onMessage(message("không phải json"), null);
        relay().onMessage(message("{\"m\":{\"kind\":\"READ\"}}"), null); // thiếu người nhận
        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }
}
