package com.datn.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tra tên và email người dùng qua endpoint nội bộ của identity-service
 * ({@code GET /internal/users/{id}}, nhận cả users.id lẫn Keycloak sub).
 *
 * Có cache 10 phút trong bộ nhớ: một sự kiện thường có vài người nhận và một người thao tác,
 * không nên gọi identity-service lặp lại. Lỗi chỉ ghi log và trả về rỗng: thông báo vẫn được tạo
 * với tên mặc định "Một thành viên".
 */
@Slf4j
@Component
public class UserDirectory {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final RestClient restClient;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public UserDirectory(@Value("${app.services.identity.url:http://localhost:8082}") String identityUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.restClient = RestClient.builder().baseUrl(identityUrl).requestFactory(factory).build();
    }

    /** Dùng trong unit test. */
    UserDirectory(RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<UserInfo> find(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        Cached hit = cache.get(userId);
        if (hit != null && hit.expiresAt.isAfter(Instant.now())) {
            return Optional.ofNullable(hit.user);
        }
        UserInfo user = fetch(userId);
        cache.put(userId, new Cached(user, Instant.now().plus(user == null ? Duration.ofMinutes(1) : TTL)));
        return Optional.ofNullable(user);
    }

    /** Tên hiển thị, hoặc "Một thành viên" nếu không tra được. */
    public String displayName(String userId) {
        return find(userId).map(UserInfo::displayName).orElse("Một thành viên");
    }

    private UserInfo fetch(String userId) {
        try {
            Envelope body = restClient.get()
                    .uri("/internal/users/{id}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Envelope>() {});
            return body == null ? null : body.data();
        } catch (Exception e) {
            log.warn("Không tra được người dùng {} từ identity-service: {}", userId, e.getMessage());
            return null;
        }
    }

    private record Cached(UserInfo user, Instant expiresAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(boolean success, UserInfo data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserInfo(String id, String keycloakId, String username, String email,
                           String firstName, String lastName) {
        public String displayName() {
            String full = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
            if (!full.isEmpty()) return full;
            if (username != null && !username.isBlank()) return username;
            return email != null ? email : "Một thành viên";
        }
    }
}
