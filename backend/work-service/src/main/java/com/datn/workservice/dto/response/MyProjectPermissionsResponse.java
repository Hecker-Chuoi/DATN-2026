package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Quyền của người dùng hiện tại trong một dự án (frontend dùng để ẩn/hiện UI).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyProjectPermissionsResponse {

    private UUID projectId;

    /** Tên vai trò dự án (ADMINISTRATOR / MEMBER / VIEWER / ...), null nếu chỉ là Global ADMIN. */
    private String roleName;

    private Boolean isLead;

    private Boolean isGlobalAdmin;

    private List<String> permissions;
}
