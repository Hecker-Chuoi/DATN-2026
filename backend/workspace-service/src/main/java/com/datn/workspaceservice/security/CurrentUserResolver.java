package com.datn.workspaceservice.security;

import com.datn.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Nguồn duy nhất cung cấp user id của người gọi trong workspace-service.
 * <p>
 * <b>Bất biến: luôn trả về identity-service {@code users.id}, không bao giờ trả
 * về Keycloak {@code sub}.</b>
 * <p>
 * Lý do chọn users.id làm id chuẩn: {@code UserService.getCurrentUser} bên
 * identity-service GHI LẠI {@code user.keycloakId} khi email khớp một Keycloak
 * account khác, nên {@code keycloak_id} là MUTABLE. Một bảng membership khóa
 * theo id mutable sẽ âm thầm mất quyền khi id đó đổi.
 * <p>
 * Nhờ bất biến này, workspace-service KHÔNG cần cơ chế "alias id" như
 * {@code TeamController.getUserAliasIds} của work-service (vốn bắn một lời gọi
 * Feign cho MỖI lần kiểm tra quyền). Lookup membership ở đây luôn chỉ là
 * {@code findByWorkspaceIdAndUserId} - một lần hit index, zero Feign.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final IdentityUserLookup identityUserLookup;

    /**
     * @return users.id của người gọi
     * @throws UnauthorizedException khi chưa xác thực, hoặc không resolve được
     */
    public UUID currentUserId() {
        // Đường nhanh: realm đã có protocol mapper cho attribute user_id.
        return SecurityUtils.getUserIdClaim()
                .orElseGet(() -> {
                    // Đường chậm: token chưa mang claim user_id (mapper chưa cấu hình,
                    // hoặc token được phát trước khi cấu hình). Đổi sub -> users.id,
                    // có cache Redis nên chỉ tốn một lời gọi Feign cho mỗi phiên.
                    String subject = SecurityUtils.getRawSubject()
                            .orElseThrow(() -> new UnauthorizedException("Yêu cầu xác thực để thực hiện thao tác này"));
                    return identityUserLookup.resolveBySubject(subject);
                });
    }
}
