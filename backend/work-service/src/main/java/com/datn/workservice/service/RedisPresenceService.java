package com.datn.workservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service quản lý sự hiện diện thời gian thực (Live Presence & Ephemeral State) bằng Redis.
 * Sử dụng cơ chế TTL ngắn (5-10s) không ghi đè vào PostgreSQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPresenceService {

    private static final String PRESENCE_TASK_KEY_PREFIX = "presence:task:";
    private static final String TYPING_TASK_KEY_PREFIX = "typing:task:";
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(10);
    private static final Duration TYPING_TTL = Duration.ofSeconds(3);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Client gửi nhịp tim (heartbeat) định kỳ 5s để duy trì avatar "Đang xem Task"
     */
    public void recordUserPresence(UUID taskId, UUID userId) {
        String key = PRESENCE_TASK_KEY_PREFIX + taskId;
        redisTemplate.opsForSet().add(key, userId.toString());
        redisTemplate.expire(key, PRESENCE_TTL);
        log.debug("User {} presence recorded for task {}", userId, taskId);
    }

    /**
     * Lấy danh sách User IDs đang cùng mở xem Task này
     */
    public Set<UUID> getViewers(UUID taskId) {
        String key = PRESENCE_TASK_KEY_PREFIX + taskId;
        Set<Object> members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream()
                .map(m -> UUID.fromString(m.toString()))
                .collect(Collectors.toSet());
    }

    /**
     * Ghi nhận trạng thái đang gõ bình luận (Live Typing indicator)
     */
    public void recordTyping(UUID taskId, UUID userId) {
        String key = TYPING_TASK_KEY_PREFIX + taskId + ":" + userId;
        redisTemplate.opsForValue().set(key, "TYPING", TYPING_TTL);
    }
}
