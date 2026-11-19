package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.service.GitHubIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/work/integrations/github")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private final GitHubIntegrationService gitHubIntegrationService;

    /**
     * Tiếp nhận Webhook từ GitHub cho các sự kiện ping, issues, pull_request, push
     */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Map<String, String>>> handleWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestHeader(value = "X-Hub-Signature-256", defaultValue = "") String signature,
            @RequestHeader(value = "X-GitHub-Delivery", defaultValue = "") String deliveryId,
            @RequestBody String rawPayload
    ) {
        log.info("Received GitHub webhook delivery: {} for event: {}", deliveryId, event);
        gitHubIntegrationService.handleWebhook(event, signature, rawPayload);
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok"), "Webhook processed successfully"));
    }
}
