package com.datn.workspaceservice.security;

import com.datn.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Nguồn duy nhất cung cấp user id của người gọi trong workspace-service.
 * <p>
 * <b>Quy ước toàn hệ thống: user id là Keycloak {@code sub}</b> (giống work-service và notification-service).
 * Mọi cột người dùng của workspace-service ({@code workspace_members.user_id}, {@code workspaces.created_by},
 * {@code workspace_invitations.*}) lưu {@code sub}; chỉ identity-service dùng {@code users.id} riêng
 * (xem {@code docs/AUTHORIZATION.md} §5 và migration {@code V6__unify_user_id_keycloak_sub.sql}).
 * <p>
 * {@code sub} nằm sẵn trong JWT đã được resource server xác thực, nên không cần gọi identity-service.
 */
@Component
public class CurrentUserResolver {

    /**
     * @return Keycloak {@code sub} của người gọi
     * @throws UnauthorizedException khi chưa xác thực hoặc {@code sub} không phải UUID
     */
    public UUID currentUserId() {
        String subject = SecurityUtils.getRawSubject()
                .orElseThrow(() -> new UnauthorizedException("Yêu cầu xác thực để thực hiện thao tác này"));
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Token không hợp lệ");
        }
    }
}
