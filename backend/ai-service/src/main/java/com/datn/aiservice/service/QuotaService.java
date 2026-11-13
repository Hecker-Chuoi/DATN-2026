package com.datn.aiservice.service;

import com.datn.aiservice.agent.llm.LlmTurn;
import com.datn.aiservice.config.AgentProperties;
import com.datn.aiservice.entity.AiUsageLog;
import com.datn.aiservice.repository.AiUsageLogRepository;
import com.datn.aiservice.repository.AiUserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Giới hạn lượt gọi Gemini theo ngày (Redis) và ghi ai_usage_logs. Cả hai đều fail-safe. */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;
    private final AiUsageLogRepository usageRepo;
    private final AiUserSettingsRepository settingsRepo;
    private final AgentProperties props;

    /** @return false khi đã vượt hạn mức ngày của user. Redis lỗi thì cho qua. */
    public boolean tryConsume(String userId) {
        int limit = settingsRepo.findById(userId)
                .map(s -> s.getDailyRequestLimit() == null ? props.dailyRequestLimitPerUser() : s.getDailyRequestLimit())
                .orElse(props.dailyRequestLimitPerUser());
        try {
            String key = "ai:requests:" + userId + ":" + LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DAY);
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1) {
                redis.expire(key, Duration.ofDays(2));
            }
            return n == null || n <= limit;
        } catch (Exception e) {
            log.warn("Không đếm được lượt gọi trên Redis ({}), cho qua", e.getMessage());
            return true;
        }
    }

    public void record(String userId, UUID conversationId, String model, LlmTurn.Usage u) {
        try {
            usageRepo.save(AiUsageLog.builder()
                    .userId(userId).conversationId(conversationId).model(model)
                    .inputTokens(u.inputTokens()).outputTokens(u.outputTokens())
                    .thinkingTokens(u.thinkingTokens()).cachedTokens(u.cachedTokens())
                    .build());
        } catch (Exception e) {
            log.warn("Không ghi được ai_usage_logs: {}", e.getMessage());
        }
    }
}
