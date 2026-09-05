package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CreateWorkItemCommentRequest;
import com.datn.workservice.dto.request.UpdateWorkItemCommentRequest;
import com.datn.workservice.dto.response.WorkItemCommentResponse;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.WorkItemCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public ResponseEntity<ApiResponse<List<WorkItemCommentResponse>>> getComments(
            @PathVariable("itemId") UUID workItemId
    ) {
        List<WorkItemCommentResponse> tree = commentService.getCommentsTree(workItemId);
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    @PostMapping({"/items/{itemId}/comments", "/work-items/{itemId}/comments"})
    public ResponseEntity<ApiResponse<WorkItemCommentResponse>> addComment(
            @PathVariable("itemId") UUID workItemId,
            @Valid @RequestBody CreateWorkItemCommentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = resolveUserId(jwt);
        WorkItemCommentResponse response = commentService.addComment(workItemId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Bình luận đã được đăng thành công"));
    }

    @PutMapping({"/items/{itemId}/comments/{commentId}", "/work-items/{itemId}/comments/{commentId}"})
    public ResponseEntity<ApiResponse<WorkItemCommentResponse>> updateComment(
            @PathVariable("itemId") UUID workItemId,
            @PathVariable("commentId") UUID commentId,
            @Valid @RequestBody UpdateWorkItemCommentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = resolveUserId(jwt);
        WorkItemCommentResponse response = commentService.updateComment(workItemId, commentId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã cập nhật bình luận"));
    }

    @DeleteMapping({"/items/{itemId}/comments/{commentId}", "/work-items/{itemId}/comments/{commentId}"})
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable("itemId") UUID workItemId,
            @PathVariable("commentId") UUID commentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = resolveUserId(jwt);
        commentService.deleteComment(workItemId, commentId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa bình luận"));
    }

    private UUID resolveUserId(Jwt jwt) {
        return SecurityUtils.getCurrentUserIdOptional().orElseGet(() -> {
            if (jwt != null) {
                String sub = jwt.getSubject();
                try {
                    return UUID.fromString(sub);
                } catch (Exception ignored) {
                }
            }
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        });
    }
}
