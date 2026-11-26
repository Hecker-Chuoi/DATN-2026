package com.datn.workspaceservice.client;

import com.datn.workspaceservice.client.dto.UserResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kết quả {@code POST /internal/users/batch} của identity-service.
 * <p>
 * identity nhận được cả hai loại id, nhưng mỗi phần tử trả về mang {@code id} là id riêng của bảng
 * {@code users} (identity), còn mọi bảng nghiệp vụ (work-service, workspace-service) lưu Keycloak {@code sub} ({@code keycloakId}).
 * Hai giá trị này khác nhau với người dùng thật, nên map theo {@code getId()} rồi tra bằng {@code sub}
 * sẽ không thấy ai (giao diện hiện "Người dùng (xxxxxxxx)"). Hàm này đánh chỉ mục theo CẢ HAI id.
 */
public final class IdentityUsers {

    private IdentityUsers() {
    }

    public static Map<UUID, UserResponse> indexByAnyId(List<UserResponse> users) {
        Map<UUID, UserResponse> map = new HashMap<>();
        if (users == null) return map;
        for (UserResponse u : users) {
            if (u == null) continue;
            if (u.getKeycloakId() != null) {
                try {
                    map.putIfAbsent(UUID.fromString(u.getKeycloakId()), u);
                } catch (IllegalArgumentException ignored) {
                    // keycloakId hỏng: vẫn còn khoá theo id của identity
                }
            }
            if (u.getId() != null) map.putIfAbsent(u.getId(), u);
        }
        return map;
    }
}
