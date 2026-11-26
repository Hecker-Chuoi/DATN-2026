package com.datn.workservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Mã hoá token GitHub (access / refresh) trước khi lưu DB bằng AES-256-GCM.
 * <p>
 * Định dạng lưu: {@code enc:v1:<base64(iv 12 byte + ciphertext + tag)>}. Giá trị không có tiền tố là dữ liệu cũ
 * (plaintext, trước khi có mã hoá): vẫn đọc được và sẽ được mã hoá ở lần lưu kế tiếp.
 * <p>
 * Khoá: {@code github.token-encryption-key} (chuỗi bất kỳ, được băm SHA-256 thành khoá 256 bit). Không cấu hình thì
 * dẫn xuất từ {@code github.client-secret}; khi đó đổi client secret sẽ làm token cũ không giải mã được và người dùng
 * phải kết nối lại GitHub.
 */
@Slf4j
@Component
public class GitHubTokenCipher {

    static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public GitHubTokenCipher(@Value("${github.token-encryption-key:}") String encryptionKey,
                             @Value("${github.client-secret:}") String clientSecret) {
        String material = !isBlank(encryptionKey) ? encryptionKey : clientSecret;
        if (isBlank(material)) {
            this.key = null;
            log.warn("Chưa cấu hình github.token-encryption-key / client-secret: token GitHub sẽ được lưu KHÔNG mã hoá");
            return;
        }
        if (isBlank(encryptionKey)) {
            log.warn("Chưa cấu hình github.token-encryption-key, dẫn xuất khoá mã hoá token từ client-secret");
        }
        this.key = new SecretKeySpec(sha256(material), "AES");
    }

    public String encrypt(String plain) {
        if (plain == null || key == null || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Không mã hoá được token GitHub", e);
        }
    }

    /**
     * @return token gốc; {@code null} nếu không giải mã được (đổi khoá / dữ liệu hỏng) — nơi dùng sẽ yêu cầu kết nối lại.
     */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored; // dữ liệu cũ chưa mã hoá
        }
        if (key == null) {
            log.error("Token GitHub đã được mã hoá nhưng không có khoá để giải mã");
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Không giải mã được token GitHub (khoá đã đổi?): {}", e.getMessage());
            return null;
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
