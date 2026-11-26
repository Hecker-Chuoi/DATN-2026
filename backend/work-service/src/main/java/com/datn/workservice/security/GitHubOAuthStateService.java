package com.datn.workservice.security;

import com.datn.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Tham số {@code state} của OAuth GitHub, chống OAuth CSRF (kẻ tấn công dụ nạn nhân mở link callback chứa
 * {@code code} của họ để gắn tài khoản GitHub lạ vào tài khoản DATN của nạn nhân).
 * <p>
 * Không lưu trạng thái: state = base64url("userId:projectId:hếtHạn:nonce") + "." + HMAC-SHA256. Callback chỉ
 * chấp nhận state do chính server ký cho đúng người dùng và dự án đang gọi, trong vòng {@link #TTL_SECONDS}.
 */
@Slf4j
@Component
public class GitHubOAuthStateService {

    static final long TTL_SECONDS = 600;
    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SecureRandom random = new SecureRandom();
    private final byte[] key;
    private final Clock clock;

    @Autowired
    public GitHubOAuthStateService(@Value("${github.state-secret:}") String stateSecret,
                                   @Value("${github.client-secret:}") String clientSecret) {
        this(stateSecret, clientSecret, Clock.systemUTC());
    }

    GitHubOAuthStateService(String stateSecret, String clientSecret, Clock clock) {
        this.clock = clock;
        String secret = !isBlank(stateSecret) ? stateSecret : clientSecret;
        if (isBlank(secret)) {
            // Không cấu hình gì: khoá ngẫu nhiên theo vòng đời tiến trình (khởi động lại thì state cũ hết hiệu lực)
            byte[] k = new byte[32];
            random.nextBytes(k);
            this.key = k;
            log.warn("github.state-secret/client-secret chưa cấu hình, dùng khoá ngẫu nhiên tạm thời cho OAuth state");
        } else {
            this.key = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String issue(UUID projectId, UUID userId) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        long expiresAt = clock.instant().getEpochSecond() + TTL_SECONDS;
        String payload = userId + ":" + projectId + ":" + expiresAt + ":" + HexFormat.of().formatHex(nonce);
        String encoded = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + B64.encodeToString(sign(encoded));
    }

    public void verify(String state, UUID projectId, UUID userId) {
        if (isBlank(state) || state.indexOf('.') < 0) {
            throw invalid();
        }
        String encoded = state.substring(0, state.indexOf('.'));
        byte[] givenSig;
        String payload;
        try {
            givenSig = B64D.decode(state.substring(state.indexOf('.') + 1));
            payload = new String(B64D.decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(sign(encoded), givenSig)) {
            throw invalid();
        }
        String[] parts = payload.split(":");
        if (parts.length != 4
                || !parts[0].equals(String.valueOf(userId))
                || !parts[1].equals(String.valueOf(projectId))) {
            throw invalid();
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (clock.instant().getEpochSecond() > expiresAt) {
            throw new BadRequestException("Phiên ủy quyền GitHub đã hết hạn, vui lòng thử kết nối lại");
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Không ký được OAuth state", e);
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException("Tham số state của OAuth GitHub không hợp lệ, vui lòng thử kết nối lại");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
