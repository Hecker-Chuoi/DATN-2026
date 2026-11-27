package com.datn.aiservice.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class ClaudeConfig {

    /**
     * SDK tự thử lại 408/409/429/5xx (kể cả 529 quá tải) và lỗi kết nối, tối đa {@code maxRetries} lần.
     * Timeout giới hạn mỗi lần gọi để lượt chat không treo vô hạn.
     */
    @Bean
    public AnthropicClient anthropicClient(AgentProperties props) {
        if (!props.hasApiKey()) {
            // Không fail lúc boot để các API không cần model (lịch sử, bộ nhớ) vẫn chạy; lỗi rõ ràng khi gọi model.
            log.warn("ANTHROPIC_API_KEY chưa được cấu hình – mọi lượt chat sẽ báo lỗi cho tới khi đặt key");
        }
        return AnthropicOkHttpClient.builder()
                .apiKey(props.hasApiKey() ? props.anthropicApiKey() : "MISSING_ANTHROPIC_API_KEY")
                .timeout(Duration.ofMillis(props.requestTimeoutMs()))
                .maxRetries(2)
                .build();
    }

    /** Vòng lặp agent chạy trên virtual thread, controller trả SseEmitter ngay. */
    @Bean(destroyMethod = "close")
    public ExecutorService agentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
