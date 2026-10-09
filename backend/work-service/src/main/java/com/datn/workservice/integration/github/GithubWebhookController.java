package com.datn.workservice.integration.github;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint công khai GitHub gọi vào (permitAll ở SecurityConfig work-service và gateway).
 * Xác thực bằng chữ ký HMAC X-Hub-Signature-256; nhận byte[] để tính HMAC trên raw body.
 */
@RestController
@RequiredArgsConstructor
public class GithubWebhookController {

    private final GithubService githubService;

    @PostMapping("/api/v1/work/github/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value = "X-GitHub-Event", defaultValue = "unknown") String event,
                                        @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                        @RequestBody byte[] body) {
        githubService.handleWebhook(event, signature, body);
        return ResponseEntity.ok().build();
    }
}
