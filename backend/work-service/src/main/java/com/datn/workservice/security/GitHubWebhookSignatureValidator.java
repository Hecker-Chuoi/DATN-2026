package com.datn.workservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@Component
public class GitHubWebhookSignatureValidator {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    /**
     * Xác thực chữ ký HMAC SHA-256 trong header X-Hub-Signature-256 từ GitHub Webhook.
     * So sánh an toàn chống tấn công Timing Attack bằng MessageDigest.isEqual.
     *
     * @param rawPayload      Nội dung thô của Webhook Request Body
     * @param signatureHeader Giá trị của header X-Hub-Signature-256 (có dạng "sha256=abcdef...")
     * @param secret          Secret key của Webhook
     * @return true nếu chữ ký hợp lệ, false nếu không hợp lệ
     */
    public boolean isValidSignature(String rawPayload, String signatureHeader, String secret) {
        if (rawPayload == null || signatureHeader == null || secret == null) {
            log.warn("Webhook signature validation failed: missing payload, signature, or secret");
            return false;
        }

        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Webhook signature header does not start with '{}'", SIGNATURE_PREFIX);
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] computedHash = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = SIGNATURE_PREFIX + HexFormat.of().formatHex(computedHash);

            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error verifying GitHub webhook signature: {}", e.getMessage(), e);
            return false;
        }
    }
}
