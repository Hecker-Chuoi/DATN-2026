package com.datn.workservice.service.impl;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.CreateWorkItemCommentRequest;
import com.datn.workservice.dto.request.UpdateWorkItemCommentRequest;
import com.datn.workservice.dto.response.WorkItemCommentResponse;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemComment;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.repository.WorkItemCommentRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.service.ProjectAccessService;
import com.datn.workservice.service.WorkItemCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemCommentServiceImpl implements WorkItemCommentService {

    private final WorkItemCommentRepository commentRepository;
    private final WorkItemRepository workItemRepository;
    private final IdentityClient identityClient;
    private final ProjectAccessService projectAccessService;

    @Override
    @Transactional(readOnly = true)
    public List<WorkItemCommentResponse> getCommentsTree(UUID workItemId) {
        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + workItemId));

        List<WorkItemComment> allComments = commentRepository.findByWorkItemIdAndDeletedAtIsNullOrderByCreatedAtAsc(workItemId);
        if (allComments.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect author IDs
        Set<UUID> authorIds = allComments.stream()
                .map(WorkItemComment::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserResponse> userMap = fetchUsersBatch(authorIds);

        // Convert all to DTOs
        Map<UUID, WorkItemCommentResponse> dtoMap = new LinkedHashMap<>();
        for (WorkItemComment c : allComments) {
            UserResponse author = userMap.get(c.getAuthorId());
            String authorName = author != null
                    ? (author.getFullName() != null && !author.getFullName().isBlank() ? author.getFullName() : author.getUsername())
                    : "Người dùng (" + c.getAuthorId().toString().substring(0, 8) + ")";

            boolean isEdited = c.getUpdatedAt() != null && c.getCreatedAt() != null && c.getUpdatedAt().isAfter(c.getCreatedAt().plusSeconds(2));

            WorkItemCommentResponse dto = WorkItemCommentResponse.builder()
                    .id(c.getId())
                    .workItemId(c.getWorkItemId())
                    .parentCommentId(c.getParentCommentId())
                    .authorId(c.getAuthorId())
                    .authorName(authorName)
                    .authorAvatar(null)
                    .authorEmail(author != null ? author.getEmail() : null)
                    .body(c.getBody())
                    .isEdited(isEdited)
                    .createdAt(c.getCreatedAt())
                    .updatedAt(c.getUpdatedAt())
                    .replies(new ArrayList<>())
                    .build();

            dtoMap.put(c.getId(), dto);
        }

        // Assemble into threaded tree
        List<WorkItemCommentResponse> rootComments = new ArrayList<>();
        for (WorkItemCommentResponse dto : dtoMap.values()) {
            if (dto.getParentCommentId() != null && dtoMap.containsKey(dto.getParentCommentId())) {
                dtoMap.get(dto.getParentCommentId()).getReplies().add(dto);
            } else {
                rootComments.add(dto);
            }
        }

        return rootComments;
    }

    @Override
    @Transactional
    public WorkItemCommentResponse addComment(UUID workItemId, CreateWorkItemCommentRequest request, UUID userId) {
        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + workItemId));

        if (item.getDeletedAt() != null) {
            throw new BadRequestException("Không thể bình luận trên công việc đã bị xóa: " + item.getCode());
        }

        if (request.getParentCommentId() != null) {
            WorkItemComment parent = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found: " + request.getParentCommentId()));
            if (!parent.getWorkItemId().equals(workItemId)) {
                throw new BadRequestException("Bình luận cha không thuộc về công việc này");
            }
        }

        WorkItemComment comment = WorkItemComment.builder()
                .workItemId(workItemId)
                .parentCommentId(request.getParentCommentId())
                .authorId(userId)
                .body(request.getBody().trim())
                .build();

        WorkItemComment saved = commentRepository.save(comment);
        log.info("User {} added comment {} on WorkItem {}", userId, saved.getId(), item.getCode());

        UserResponse author = fetchSingleUser(userId);
        String authorName = author != null
                ? (author.getFullName() != null && !author.getFullName().isBlank() ? author.getFullName() : author.getUsername())
                : "Người dùng (" + userId.toString().substring(0, 8) + ")";

        return WorkItemCommentResponse.builder()
                .id(saved.getId())
                .workItemId(saved.getWorkItemId())
                .parentCommentId(saved.getParentCommentId())
                .authorId(saved.getAuthorId())
                .authorName(authorName)
                .authorAvatar(null)
                .authorEmail(author != null ? author.getEmail() : null)
                .body(saved.getBody())
                .isEdited(false)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .replies(new ArrayList<>())
                .build();
    }

    @Override
    @Transactional
    public WorkItemCommentResponse updateComment(UUID workItemId, UUID commentId, UpdateWorkItemCommentRequest request, UUID userId) {
        WorkItemComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with ID: " + commentId));

        if (!comment.getWorkItemId().equals(workItemId)) {
            throw new BadRequestException("Bình luận không thuộc về công việc này");
        }

        if (comment.getDeletedAt() != null) {
            throw new BadRequestException("Không thể cập nhật bình luận đã xóa");
        }

        // Chỉ tác giả có quyền COMMENT_EDIT_OWN mới được sửa
        UUID projectId = resolveProjectId(workItemId);
        boolean isOwner = comment.getAuthorId().equals(userId);
        if (!isOwner || !projectAccessService.hasProjectPermission(projectId, PermissionKey.COMMENT_EDIT_OWN)) {
            throw new AccessDeniedException("Bạn chỉ có quyền sửa bình luận của chính mình");
        }

        comment.setBody(request.getBody().trim());
        comment.setUpdatedAt(LocalDateTime.now());
        WorkItemComment updated = commentRepository.save(comment);

        UserResponse author = fetchSingleUser(userId);
        String authorName = author != null
                ? (author.getFullName() != null && !author.getFullName().isBlank() ? author.getFullName() : author.getUsername())
                : "Người dùng (" + userId.toString().substring(0, 8) + ")";

        return WorkItemCommentResponse.builder()
                .id(updated.getId())
                .workItemId(updated.getWorkItemId())
                .parentCommentId(updated.getParentCommentId())
                .authorId(updated.getAuthorId())
                .authorName(authorName)
                .authorAvatar(null)
                .authorEmail(author != null ? author.getEmail() : null)
                .body(updated.getBody())
                .isEdited(true)
                .createdAt(updated.getCreatedAt())
                .updatedAt(updated.getUpdatedAt())
                .replies(new ArrayList<>())
                .build();
    }

    @Override
    @Transactional
    public void deleteComment(UUID workItemId, UUID commentId, UUID userId) {
        WorkItemComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with ID: " + commentId));

        if (!comment.getWorkItemId().equals(workItemId)) {
            throw new BadRequestException("Bình luận không thuộc về công việc này");
        }

        // Tác giả cần COMMENT_DELETE_OWN; người khác (Administrator) cần COMMENT_DELETE_ALL
        UUID projectId = resolveProjectId(workItemId);
        boolean isOwner = comment.getAuthorId().equals(userId);
        boolean allowed = (isOwner && projectAccessService.hasProjectPermission(projectId, PermissionKey.COMMENT_DELETE_OWN))
                || projectAccessService.hasProjectPermission(projectId, PermissionKey.COMMENT_DELETE_ALL);
        if (!allowed) {
            throw new AccessDeniedException(isOwner
                    ? "Bạn không có quyền xóa bình luận trong dự án này"
                    : "Bạn chỉ có quyền xóa bình luận của chính mình");
        }

        // Soft delete
        comment.setDeletedAt(LocalDateTime.now());
        commentRepository.save(comment);
        log.info("User {} deleted comment {} on WorkItem {}", userId, commentId, workItemId);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UUID resolveProjectId(UUID workItemId) {
        return workItemRepository.findById(workItemId)
                .map(WorkItem::getProjectId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + workItemId));
    }

    private UserResponse fetchSingleUser(UUID userId) {
        if (userId == null) return null;
        try {
            ApiResponse<UserResponse> res = identityClient.getUserById(userId);
            if (res != null && res.getData() != null) {
                return res.getData();
            }
        } catch (Exception e) {
            log.debug("Failed to fetch user info for {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private Map<UUID, UserResponse> fetchUsersBatch(Set<UUID> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        try {
            ApiResponse<List<UserResponse>> res = identityClient.getUsersByIds(new ArrayList<>(userIds));
            if (res != null && res.getData() != null) {
                return res.getData().stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserResponse::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user batch for comments: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
