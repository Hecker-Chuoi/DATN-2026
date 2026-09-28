package com.datn.workspaceservice.dto.response;

import com.datn.workspaceservice.enums.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String avatarUrl;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /** Vai trò của người đang gọi API trong workspace này. */
    private WorkspaceRole myRole;

    private long memberCount;

    /**
     * Số lượng OWNER hiện tại. Frontend dùng để disable nút Rời workspace khi
     * người gọi là OWNER duy nhất, thay vì để backend trả 400 rồi mới biết.
     */
    private long ownerCount;

    /**
     * Cố ý để null ở v1: workspace-service không đếm được team mà không đọc bảng
     * của work-service, và một Feign client chiều ngược lại sẽ tạo rủi ro phụ
     * thuộc vòng lúc boot. Frontend tự gọi GET /work/api/v1/work/teams?workspaceId=...
     */
    private Long teamCount;
}
