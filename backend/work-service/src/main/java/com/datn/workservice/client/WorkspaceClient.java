package com.datn.workservice.client;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.client.dto.EnsureWorkspaceMemberRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/**
 * Feign Client tới các endpoint nội bộ của workspace-service (/internal/workspaces/**).
 * <p>
 * Vì sao gọi qua Feign mà không đọc thẳng bảng workspace_members (cả hai bảng
 * đều nằm trong datn_main_db nên đọc trực tiếp là khả thi và nhanh hơn):
 * work-service chạy spring.jpa.hibernate.ddl-auto = update, nên bất kỳ entity
 * được map ở đây là một bảng Hibernate được PHÉP ALTER - tức là service này sẽ
 * âm thầm sửa schema của service khác. Chi phí Feign được chặn lại bằng cache
 * 60 giây trong WorkspaceAccessChecker.
 * <p>
 * Nếu về sau profiling cho thấy điểm này nóng, cửa thoát là JPA view Immutable
 * hoặc projection local nuôi bằng Kafka - không phải map entity sở hữu.
 */
@FeignClient(
        name = "workspace-service",
        url = "${app.services.workspace.url:http://localhost:8087}"
)
public interface WorkspaceClient {

    /**
     * Vai trò của user trong workspace.
     *
     * @return data = null khi user không phải thành viên
     */
    @GetMapping("/internal/workspaces/{workspaceId}/members/{userId}/role")
    ApiResponse<String> getMemberRole(@PathVariable("workspaceId") UUID workspaceId,
                                      @PathVariable("userId") UUID userId);

    /** Danh sách workspace id của user, đã loại workspace bị soft-delete. */
    @GetMapping("/internal/workspaces/users/{userId}/ids")
    ApiResponse<List<UUID>> getWorkspaceIds(@PathVariable("userId") UUID userId);

    @GetMapping("/internal/workspaces/{workspaceId}/exists")
    ApiResponse<Boolean> exists(@PathVariable("workspaceId") UUID workspaceId);

    /** Tự động đảm bảo user là thành viên của workspace (Smart Auto-Cascade) */
    @PostMapping("/internal/workspaces/{workspaceId}/members/ensure")
    ApiResponse<Void> ensureMember(@PathVariable("workspaceId") UUID workspaceId,
                                   @RequestBody EnsureWorkspaceMemberRequest request);
}
