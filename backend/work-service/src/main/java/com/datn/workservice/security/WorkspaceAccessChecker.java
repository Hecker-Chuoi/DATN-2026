package com.datn.workservice.security;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.ForbiddenException;
import com.datn.workservice.client.WorkspaceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Kiểm tra quyền truy cập workspace từ phía work-service.
 * <p>
 * <b>FAIL CLOSED.</b> Mọi lỗi Feign hoặc timeout đều thành ForbiddenException.
 * Cố tình KHÔNG copy thái độ catch-rồi-bỏ-qua của TeamController.getUserAliasIds
 * và ProjectMemberServiceImpl.getProjectMembers: bỏ qua lỗi là chấp nhận được khi
 * chỉ hydrate dữ liệu HIỂN THỊ, nhưng chí tử khi đang PHÂN QUYỀN - workspace-service
 * chết sẽ biến thành "ai cũng vào được mọi workspace".
 * <p>
 * Cache 60 giây (bean cacheManager của RedisConfig) để các endpoint listing không
 * fan-out một lời gọi Feign cho mỗi hàng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceAccessChecker {

    /** Thứ tự bậc, khớp WorkspaceRole của workspace-service: OWNER cao nhất. */
    private static final List<String> RANKS = List.of("OWNER", "ADMIN", "MEMBER", "GUEST");

    private final WorkspaceClient workspaceClient;
    private final CanonicalUserResolver canonicalUserResolver;

    /**
     * @return tên vai trò, hoặc null nếu không phải thành viên
     * @throws ForbiddenException khi không gọi được workspace-service (fail closed)
     */
    @Cacheable(value = "wsRole", key = "#workspaceId + ':' + #userId", unless = "#result == null")
    public String getRole(UUID workspaceId, UUID userId) {
        if (workspaceId == null || userId == null) return null;
        try {
            UUID canonicalId = canonicalUserResolver.resolve(userId);
            ApiResponse<String> response = workspaceClient.getMemberRole(workspaceId, canonicalId);
            return response == null ? null : response.getData();
        } catch (Exception e) {
            log.error("Không gọi được workspace-service để kiểm tra quyền trên workspace {}: {}",
                    workspaceId, e.getMessage());
            throw new ForbiddenException("Không xác minh được quyền truy cập workspace");
        }
    }

    /**
     * Người gọi phải là thành viên workspace với bậc tối thiểu minRole.
     * <p>
     * userId truyền vào có thể là id lấy từ JWT (Keycloak sub) hoặc users.id.
     * getRole đã tự động chuẩn hóa sang users.id qua canonicalUserResolver.
     */
    public void requireAtLeast(UUID workspaceId, UUID userId, String minRole) {
        if (workspaceId == null) {
            throw new ForbiddenException("Thiếu workspaceId để kiểm tra quyền");
        }

        String role = getRole(workspaceId, userId);
        if (role == null) {
            throw new ForbiddenException("Bạn không phải thành viên của workspace này");
        }

        int actual = RANKS.indexOf(role);
        int required = RANKS.indexOf(minRole);
        if (actual < 0 || required < 0 || actual > required) {
            throw new ForbiddenException(String.format(
                    "Bạn cần vai trò %s trở lên trong workspace này (vai trò hiện tại: %s)", minRole, role));
        }
    }

    /** Chỉ cần là thành viên, vai trò nào cũng được. */
    public void requireMember(UUID workspaceId, UUID userId) {
        requireAtLeast(workspaceId, userId, "GUEST");
    }

    /**
     * Danh sách workspace id của user - dùng để scope các truy vấn listing khi
     * client không truyền workspaceId. Fail closed: lỗi thì trả về danh sách rỗng
     * để truy vấn ra trang trống, KHÔNG phải trang không filter.
     */
    public List<UUID> getWorkspaceIds(UUID userId) {
        try {
            ApiResponse<List<UUID>> response =
                    workspaceClient.getWorkspaceIds(canonicalUserResolver.resolve(userId));
            if (response == null || response.getData() == null) {
                return List.of();
            }
            return response.getData();
        } catch (Exception e) {
            log.error("Không lấy được danh sách workspace của user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }
}
