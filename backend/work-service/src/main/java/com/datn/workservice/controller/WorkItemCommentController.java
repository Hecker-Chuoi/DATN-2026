package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CreateWorkItemCommentRequest;
import com.datn.workservice.dto.request.UpdateWorkItemCommentRequest;
import com.datn.workservice.dto.response.WorkItemCommentResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireTaskPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.WorkItemCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class WorkItemCommentController {

    private final WorkItemCommentService commentService;

    @GetMapping({"/items/{itemId}/comments", "/work-items/{itemId}/comments"})
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<WorkItemCommentResponse>>> getComments(
            @PathVariable("itemId") UUID workItemId
    ) {
        List<WorkItemCommentResponse> tree = commentService.getCommentsTree(workItemId);
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    @PostMapping({"/items/{itemId}/comments", "/work-items/{itemId}/comments"})
    @RequireTaskPermission(PermissionKey.COMMENT_CREATE)
    public ResponseEntity<ApiResponse<WorkItemCommentResponse>> addComment(
            @PathVariable("itemId") UUID workItemId,
            @Valid @RequestBody CreateWorkItemCommentRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        WorkItemCommentResponse response = commentService.addComment(workItemId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Bình luận đã được đăng thành công"));
    }

    /**
     * Sửa bình luận: chỉ tác giả với quyền COMMENT_EDIT_OWN (kiểm tra trong service).
     */
    @PutMapping({"/items/{itemId}/comments/{commentId}", "/work-items/{itemId}/comments/{commentId}"})
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<WorkItemCommentResponse>> updateComment(
            @PathVariable("itemId") UUID workItemId,
            @PathVariable("commentId") UUID commentId,
            @Valid @RequestBody UpdateWorkItemCommentRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        WorkItemCommentResponse response = commentService.updateComment(workItemId, commentId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã cập nhật bình luận"));
    }

    /**
     * Xóa bình luận: tác giả với COMMENT_DELETE_OWN hoặc bất kỳ ai có COMMENT_DELETE_ALL (kiểm tra trong service).
     */
    @DeleteMapping({"/items/{itemId}/comments/{commentId}", "/work-items/{itemId}/comments/{commentId}"})
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable("itemId") UUID workItemId,
            @PathVariable("commentId") UUID commentId
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        commentService.deleteComment(workItemId, commentId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa bình luận"));
    }
}
