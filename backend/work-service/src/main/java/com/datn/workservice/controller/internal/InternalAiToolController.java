package com.datn.workservice.controller.internal;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.ai.AiToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint nội bộ cho ai-service (mỗi tool một POST, body = input của tool + projectId/workItemId).
 * Gateway không route /internal/**; {@code AiToolApiKeyFilter} kiểm X-API-Key và đặt user hiện tại = X-User-Id.
 * Quyền của người dùng trong dự án do {@link AiToolService} kiểm tra lại cho từng tool.
 */
@RestController
@RequestMapping("/internal/ai-tools")
@RequiredArgsConstructor
public class InternalAiToolController {

    private final AiToolService tools;

    // ─── Đọc ───
    @PostMapping("/search-work-items")
    public ApiResponse<Object> searchWorkItems(@RequestBody Map<String, Object> in) { return ok(tools.searchWorkItems(uid(), in)); }

    @PostMapping("/get-work-item")
    public ApiResponse<Object> getWorkItem(@RequestBody Map<String, Object> in) { return ok(tools.getWorkItem(uid(), in)); }

    @PostMapping("/get-my-issues")
    public ApiResponse<Object> getMyIssues(@RequestBody Map<String, Object> in) { return ok(tools.getMyIssues(uid(), in)); }

    @PostMapping("/get-project-metadata")
    public ApiResponse<Object> getProjectMetadata(@RequestBody Map<String, Object> in) { return ok(tools.getProjectMetadata(uid(), in)); }

    @PostMapping("/list-project-members")
    public ApiResponse<Object> listProjectMembers(@RequestBody Map<String, Object> in) { return ok(tools.listProjectMembers(uid(), in)); }

    @PostMapping("/list-sprints")
    public ApiResponse<Object> listSprints(@RequestBody Map<String, Object> in) { return ok(tools.listSprints(uid(), in)); }

    @PostMapping("/get-sprint-progress")
    public ApiResponse<Object> getSprintProgress(@RequestBody Map<String, Object> in) { return ok(tools.getSprintProgress(uid(), in)); }

    @PostMapping("/get-agile-report")
    public ApiResponse<Object> getAgileReport(@RequestBody Map<String, Object> in) { return ok(tools.getAgileReport(uid(), in)); }

    @PostMapping("/get-contribution-report")
    public ApiResponse<Object> getContributionReport(@RequestBody Map<String, Object> in) { return ok(tools.getContributionReport(uid(), in)); }

    @PostMapping("/list-attachments")
    public ApiResponse<Object> listAttachments(@RequestBody Map<String, Object> in) { return ok(tools.listAttachments(uid(), in)); }

    @PostMapping("/read-attachment")
    public ApiResponse<Object> readAttachment(@RequestBody Map<String, Object> in) { return ok(tools.readAttachment(uid(), in)); }

    // ─── Ghi (ai-service chỉ gọi sau khi người dùng duyệt) ───
    @PostMapping("/create-work-item")
    public ApiResponse<Object> createWorkItem(@RequestBody Map<String, Object> in) { return ok(tools.createWorkItem(uid(), in)); }

    @PostMapping("/create-subtasks")
    public ApiResponse<Object> createSubtasks(@RequestBody Map<String, Object> in) { return ok(tools.createSubtasks(uid(), in)); }

    @PostMapping("/update-work-item")
    public ApiResponse<Object> updateWorkItem(@RequestBody Map<String, Object> in) { return ok(tools.updateWorkItem(uid(), in)); }

    @PostMapping("/transition-work-item")
    public ApiResponse<Object> transitionWorkItem(@RequestBody Map<String, Object> in) { return ok(tools.transitionWorkItem(uid(), in)); }

    @PostMapping("/assign-work-item")
    public ApiResponse<Object> assignWorkItem(@RequestBody Map<String, Object> in) { return ok(tools.assignWorkItem(uid(), in)); }

    @PostMapping("/move-to-sprint")
    public ApiResponse<Object> moveToSprint(@RequestBody Map<String, Object> in) { return ok(tools.moveToSprint(uid(), in)); }

    @PostMapping("/add-comment")
    public ApiResponse<Object> addComment(@RequestBody Map<String, Object> in) { return ok(tools.addComment(uid(), in)); }

    @PostMapping("/link-work-items")
    public ApiResponse<Object> linkWorkItems(@RequestBody Map<String, Object> in) { return ok(tools.linkWorkItems(uid(), in)); }

    private static UUID uid() {
        return SecurityUtils.getCurrentUserId();
    }

    private static ApiResponse<Object> ok(Object data) {
        return ApiResponse.success(data);
    }
}
