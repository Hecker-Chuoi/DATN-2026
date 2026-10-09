package com.datn.workservice.integration.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Mã hóa access token GitHub trước khi lưu DB: AES-256-GCM, key từ GITHUB_TOKEN_ENC_KEY (base64 32 byte).
 * Đổi key = token cũ không giải mã được, người dùng phải kết nối lại.
 */
@Component
public class TokenCrypto {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int IV_LEN = 12;

    private final SecretKey key;

    public TokenCrypto(@Value("${app.github.token-enc-key}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException("app.github.token-enc-key phải là base64 của đúng 32 byte (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV_LEN + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không mã hóa được token", e);
        }
    }

    public String decrypt(String enc) {
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, IV_LEN));
            return new String(c.doFinal(all, IV_LEN, all.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Không giải mã được token (key đã đổi?)", e);
        }
    }
}
