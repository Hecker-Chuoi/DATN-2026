package com.datn.workspaceservice.dto.request;

import com.datn.workspaceservice.enums.WorkspaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Thêm thành viên vào workspace.
 * <p>
 * Dùng DTO có kiểu + Valid, KHÔNG dùng Map như TeamController (ở đó Map làm cho
 * Valid không bao giờ chạy và 4 DTO đã viết sẵn thành dead code).
 */
@Data
public class AddWorkspaceMemberRequest {

    /** Email hoặc username của người được thêm. */
    @NotBlank(message = "Cần cung cấp email hoặc username của người dùng")
    private String identifier;

    @NotNull(message = "Vai trò không được để trống")
    private WorkspaceRole role;
}
