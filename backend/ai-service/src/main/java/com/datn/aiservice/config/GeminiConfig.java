package com.datn.aiservice.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class GeminiConfig {

    @Bean
    public Client geminiClient(AgentProperties props) {
        if (!props.hasGeminiKey()) {
            // Không fail lúc boot để các API không cần Gemini (lịch sử, bộ nhớ) vẫn chạy; lỗi rõ ràng khi gọi model.
            log.warn("GEMINI_API_KEY chưa được cấu hình – mọi lượt chat sẽ báo lỗi cho tới khi đặt biến môi trường này");
            return Client.builder().apiKey("MISSING_GEMINI_API_KEY").build();
        }
        // Không có timeout thì khi Gemini quá tải (không trả header) lượt chat treo vô hạn.
        // Tắt retry nội bộ của SDK: GeminiLlmClient tự thử lại và chuyển sang light-model.
        HttpOptions http = HttpOptions.builder()
                .timeout(props.requestTimeoutMs())
                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                .build();
        return Client.builder().apiKey(props.geminiApiKey()).httpOptions(http).build();
    }

    /** Vòng lặp agent chạy trên virtual thread, controller trả SseEmitter ngay. */
    @Bean(destroyMethod = "close")
    public ExecutorService agentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
