package com.datn.workservice.controller;

import com.datn.workservice.service.GitHubIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GitHubWebhookControllerTest {

    @Mock
    private GitHubIntegrationService gitHubIntegrationService;

    @InjectMocks
    private GitHubWebhookController gitHubWebhookController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(gitHubWebhookController).build();
    }

    @Test
    @DisplayName("POST /webhook: Tiếp nhận Webhook thành công và gọi handleWebhook")
    void testHandleWebhook_Success() throws Exception {
        String payload = "{\"action\":\"opened\"}";

        mockMvc.perform(post("/api/v1/work/integrations/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "issues")
                        .header("X-Hub-Signature-256", "sha256=test")
                        .header("X-GitHub-Delivery", "delivery-123")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ok"));

        verify(gitHubIntegrationService).handleWebhook("issues", "sha256=test", payload);
    }
}
