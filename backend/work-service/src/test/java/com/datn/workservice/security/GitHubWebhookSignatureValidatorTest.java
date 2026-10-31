package com.datn.workservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubWebhookSignatureValidatorTest {

    private GitHubWebhookSignatureValidator validator;
    private final String secret = "test-secret-key-12345";
    private final String payload = "{\"action\":\"opened\",\"issue\":{\"number\":1}}";

    @BeforeEach
    void setUp() {
        validator = new GitHubWebhookSignatureValidator();
    }

    private String computeSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hash);
    }

    @Test
    @DisplayName("isValidSignature: Chữ ký hợp lệ trả về true")
    void testValidSignature() throws Exception {
        String validSig = computeSignature(payload, secret);
        boolean result = validator.isValidSignature(payload, validSig, secret);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValidSignature: Chữ ký không khớp trả về false")
    void testInvalidSignature() {
        boolean result = validator.isValidSignature(payload, "sha256=wrongsignature1234567890abcdef", secret);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValidSignature: Header không có tiền tố sha256= trả về false")
    void testMissingPrefix() {
        boolean result = validator.isValidSignature(payload, "abcdef1234567890", secret);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValidSignature: Payload bị thay đổi (tampered) trả về false")
    void testTamperedPayload() throws Exception {
        String validSig = computeSignature(payload, secret);
        String tampered = payload + " ";
        boolean result = validator.isValidSignature(tampered, validSig, secret);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValidSignature: Tham số null trả về false")
    void testNullParameters() {
        assertThat(validator.isValidSignature(null, "sig", "secret")).isFalse();
        assertThat(validator.isValidSignature("payload", null, "secret")).isFalse();
        assertThat(validator.isValidSignature("payload", "sig", null)).isFalse();
    }
}
