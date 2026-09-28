package com.datn.workspaceservice.security;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.ForbiddenException;
import com.datn.workspaceservice.client.IdentityClient;
import com.datn.workspaceservice.client.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Đổi Keycloak {@code sub} thành identity-service {@code users.id}, có cache.
 * <p>
 * Tách thành bean RIÊNG (không gộp vào {@link CurrentUserResolver}) là CỐ Ý:
 * {@code @Cacheable} chỉ hoạt động khi lời gọi đi qua proxy Spring. Nếu
 * CurrentUserResolver tự gọi method của chính nó thì self-invocation sẽ bỏ qua
 * proxy và cache không bao giờ chạy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityUserLookup {

    private final IdentityClient identityClient;

    /**
     * @param subject Keycloak sub
     * @return users.id tương ứng
     * @throws ForbiddenException khi identity-service không phản hồi hoặc không
     *         tìm thấy user. FAIL CLOSED - tuyệt đối không fallback về subject,
     *         vì như vậy sẽ ghi một hàng membership khóa theo id sai.
     *         <p>
     *         Dùng 403 chứ KHÔNG dùng 401 là điểm quan trọng: khi method này chạy
     *         thì JWT đã được resource server xác thực xong, nên mọi thất bại ở
     *         đây KHÔNG BAO GIỜ chữa được bằng refresh token. Trả 401 sẽ khiến
     *         response interceptor của frontend tưởng token hết hạn, gọi
     *         /auth/refresh (thành công, vì token vốn hợp lệ), retry, rồi lại
     *         401 - nhiều request đồng thời dồn vào cùng hàng đợi refresh và
     *         sinh ra một loạt promise bị reject.
     */
    @Cacheable(value = "wsUserId", key = "#subject")
    public UUID resolveBySubject(String subject) {
        UUID subjectUuid;
        try {
            subjectUuid = UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Keycloak subject không phải UUID hợp lệ: " + subject);
        }

        try {
            ApiResponse<UserResponse> response = identityClient.getUserById(subjectUuid);
            if (response == null || response.getData() == null || response.getData().getId() == null) {
                throw notProvisioned(subject);
            }
            UUID canonicalId = response.getData().getId();
            log.debug("Đã resolve Keycloak sub {} -> users.id {}", subject, canonicalId);
            return canonicalId;
        } catch (ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            // Feign trả 404 = tài khoản có trên Keycloak nhưng chưa có hàng trong
            // bảng users của identity-service. Phân biệt với lỗi mạng để thông
            // điệp nói đúng việc người dùng cần làm.
            if (msg.contains("404") || msg.contains("Not Found")) {
                log.warn("Tài khoản chưa được khởi tạo trong identity-service: sub={}", subject);
                throw notProvisioned(subject);
            }
            log.error("Không gọi được identity-service để resolve sub {}: {}", subject, msg);
            throw new ForbiddenException(
                    "Không xác minh được danh tính người dùng do không kết nối được identity-service.");
        }
    }

    private ForbiddenException notProvisioned(String subject) {
        return new ForbiddenException(
                "Tài khoản của bạn tồn tại trên Keycloak nhưng chưa được khởi tạo trong hệ thống. "
                        + "Hãy đăng ký qua trang Đăng ký của ứng dụng, hoặc liên hệ quản trị viên. "
                        + "(subject: " + subject + ")");
    }
}
