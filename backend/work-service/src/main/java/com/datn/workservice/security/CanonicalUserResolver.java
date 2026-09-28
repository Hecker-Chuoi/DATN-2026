package com.datn.workservice.security;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Đổi id lấy từ JWT thành identity-service {@code users.id}.
 * <p>
 * Vì sao cần: {@code SecurityUtils.getCurrentUserId()} của work-service trả về
 * Keycloak {@code sub} khi token không có claim {@code user_id} (realm hiện chưa
 * cấu hình protocol mapper cho attribute đó). Nhưng bảng
 * {@code workspace_members} của workspace-service khóa theo {@code users.id},
 * nên truyền thẳng sub vào sẽ luôn tra ra "không phải thành viên".
 * <p>
 * Tách thành bean RIÊNG vì {@code @Cacheable} chỉ có hiệu lực khi lời gọi đi qua
 * proxy Spring; nếu {@link WorkspaceAccessChecker} tự gọi method của chính nó
 * thì self-invocation bỏ qua proxy và cache không bao giờ chạy.
 * <p>
 * Cache trả về {@code String} chứ không phải {@code UUID} là CỐ Ý:
 * {@code RedisConfig} dùng GenericJackson2JsonRedisSerializer với
 * activateDefaultTyping(NON_FINAL), mà UUID là class FINAL nên Jackson không ghi
 * kèm type info - giá trị đọc lại từ Redis sẽ là String và gây
 * ClassCastException ngay lần cache hit đầu tiên.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanonicalUserResolver {

    private final IdentityClient identityClient;

    /**
     * @param rawUserId id lấy từ JWT (có thể là users.id, hoặc Keycloak sub)
     * @return users.id dạng chuỗi; trả về chính rawUserId nếu không resolve được
     */
    @Cacheable(value = "canonicalUserId", key = "#rawUserId.toString()")
    public String resolveAsString(UUID rawUserId) {
        try {
            // Endpoint này resolve được CẢ users.id LẪN keycloak_id
            // (UserService.getUserById làm findById(id).or(findByKeycloakId(id))).
            ApiResponse<UserResponse> response = identityClient.getUserById(rawUserId);
            if (response != null && response.getData() != null && response.getData().getId() != null) {
                return response.getData().getId().toString();
            }
        } catch (Exception e) {
            log.debug("Không resolve được id chuẩn cho {}: {}", rawUserId, e.getMessage());
        }
        // Không resolve được thì giữ nguyên id gốc: phía gọi vẫn sẽ fail closed khi
        // tra membership không thấy, nên đây không phải lỗ hổng quyền.
        return rawUserId.toString();
    }

    public UUID resolve(UUID rawUserId) {
        if (rawUserId == null) {
            return null;
        }
        return UUID.fromString(resolveAsString(rawUserId));
    }
}
