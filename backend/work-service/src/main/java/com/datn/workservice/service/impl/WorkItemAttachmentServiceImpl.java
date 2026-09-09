package com.datn.workservice.service.impl;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.response.WorkItemAttachmentResponse;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemAttachment;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.repository.WorkItemAttachmentRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.service.ProjectAccessService;
import com.datn.workservice.service.WorkItemAttachmentService;
import com.datn.workservice.service.WorkItemAuditService;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemAttachmentServiceImpl implements WorkItemAttachmentService {

    private final WorkItemAttachmentRepository attachmentRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemAuditService auditService;
    private final IdentityClient identityClient;
    private final MinioClient minioClient;
    private final ProjectAccessService projectAccessService;

    @Value("${minio.bucket-name:work-attachments}")
    private String bucketName = "work-attachments";

    @Override
    @Transactional(readOnly = true)
    public List<WorkItemAttachmentResponse> getAttachments(UUID workItemId) {
        List<WorkItemAttachment> attachments = attachmentRepository.findByWorkItemIdOrderByUploadedAtDesc(workItemId);
        if (attachments.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> userIds = attachments.stream()
                .map(WorkItemAttachment::getUploadedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserResponse> userMap = fetchUsersBatch(userIds);

        return attachments.stream()
                .map(att -> mapToResponse(att, userMap.get(att.getUploadedBy())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkItemAttachmentResponse uploadAttachment(UUID workItemId, MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tải lên không được để trống");
        }

        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + workItemId));

        if (item.getDeletedAt() != null) {
            throw new BadRequestException("Không thể tải lên tệp cho công việc đã bị xóa: " + item.getCode());
        }

        String rawFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        if (rawFileName.contains("..")) {
            throw new BadRequestException("Tên tệp không hợp lệ: " + rawFileName);
        }

        // Object Key in MinIO: attachments/{workItemId}/{UUID}_{rawFileName}
        String objectKey = "attachments/" + workItemId + "/" + UUID.randomUUID() + "_" + rawFileName;

        try {
            ensureBucketExists();
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectKey)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                                .build()
                );
            }
            log.info("Successfully uploaded object '{}' to MinIO bucket '{}'", objectKey, bucketName);
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể lưu trữ tệp tin lên MinIO: " + e.getMessage());
        }

        WorkItemAttachment attachment = WorkItemAttachment.builder()
                .workItemId(workItemId)
                .fileName(rawFileName)
                .fileUrl(objectKey)
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .uploadedBy(userId)
                .build();

        WorkItemAttachment saved = attachmentRepository.save(attachment);
        log.info("Saved attachment ID {} for WorkItem {}", saved.getId(), item.getCode());

        // Audit Trail
        auditService.recordCustomEvent(
                workItemId,
                "ATTACHMENT",
                null,
                "Đã tải lên tệp: " + rawFileName + " (" + formatFileSize(file.getSize()) + ") lên MinIO",
                userId
        );

        UserResponse uploader = fetchSingleUser(userId);
        return mapToResponse(saved, uploader);
    }

    @Override
    @Transactional
    public void deleteAttachment(UUID workItemId, UUID attachmentId, UUID userId) {
        WorkItemAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with ID: " + attachmentId));

        if (!attachment.getWorkItemId().equals(workItemId)) {
            throw new BadRequestException("Tệp đính kèm không thuộc về công việc này");
        }

        // Người tải lên cần ATTACHMENT_DELETE_OWN; người khác (Administrator) cần ATTACHMENT_DELETE_ALL
        UUID projectId = workItemRepository.findById(workItemId)
                .map(WorkItem::getProjectId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + workItemId));
        boolean isOwner = userId != null && userId.equals(attachment.getUploadedBy());
        boolean allowed = (isOwner && projectAccessService.hasProjectPermission(projectId, PermissionKey.ATTACHMENT_DELETE_OWN))
                || projectAccessService.hasProjectPermission(projectId, PermissionKey.ATTACHMENT_DELETE_ALL);
        if (!allowed) {
            throw new AccessDeniedException(isOwner
                    ? "Bạn không có quyền xóa tệp đính kèm trong dự án này"
                    : "Bạn chỉ có quyền xóa tệp đính kèm do chính mình tải lên");
        }

        // Delete from MinIO
        String objectKey = attachment.getFileUrl();
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            log.info("Successfully removed object '{}' from MinIO bucket '{}'", objectKey, bucketName);
        } catch (Exception e) {
            log.warn("Could not delete object from MinIO for attachment {}: {}", attachmentId, e.getMessage());
        }

        attachmentRepository.delete(attachment);
        log.info("Deleted attachment ID {} from WorkItem {}", attachmentId, workItemId);

        // Audit Trail
        auditService.recordCustomEvent(
                workItemId,
                "ATTACHMENT",
                "Tệp: " + attachment.getFileName(),
                "Đã xóa tệp khỏi MinIO",
                userId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadAttachmentResource(UUID attachmentId) {
        WorkItemAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with ID: " + attachmentId));
        projectAccessService.assertTaskPermission(attachment.getWorkItemId(), PermissionKey.PROJECT_VIEW);

        String objectKey = attachment.getFileUrl();

        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            byte[] bytes = stream.readAllBytes();
            stream.close();
            return new ByteArrayResource(bytes);
        } catch (Exception e) {
            log.error("Failed to load attachment from MinIO: {}", e.getMessage(), e);
            throw new ResourceNotFoundException("Không tìm thấy tệp tin trên MinIO Object Storage: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WorkItemAttachmentResponse getAttachmentById(UUID attachmentId) {
        WorkItemAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with ID: " + attachmentId));
        projectAccessService.assertTaskPermission(attachment.getWorkItemId(), PermissionKey.PROJECT_VIEW);
        UserResponse uploader = fetchSingleUser(attachment.getUploadedBy());
        return mapToResponse(attachment, uploader);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket existence check encountered an issue: {}", e.getMessage());
        }
    }

    private WorkItemAttachmentResponse mapToResponse(WorkItemAttachment att, UserResponse uploader) {
        String uploaderName = uploader != null
                ? (uploader.getFullName() != null && !uploader.getFullName().isBlank() ? uploader.getFullName() : uploader.getUsername())
                : "Người dùng (" + att.getUploadedBy().toString().substring(0, 8) + ")";

        String downloadUrl = "/api/v1/work/attachments/" + att.getId() + "/download";

        return WorkItemAttachmentResponse.builder()
                .id(att.getId())
                .workItemId(att.getWorkItemId())
                .fileName(att.getFileName())
                .fileUrl(downloadUrl) // Direct download API url
                .downloadUrl(downloadUrl)
                .fileSize(att.getFileSize())
                .formattedFileSize(formatFileSize(att.getFileSize()))
                .contentType(att.getContentType())
                .uploadedBy(att.getUploadedBy())
                .uploaderName(uploaderName)
                .uploaderAvatar(null)
                .uploadedAt(att.getUploadedAt())
                .build();
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private UserResponse fetchSingleUser(UUID userId) {
        if (userId == null) return null;
        try {
            ApiResponse<UserResponse> res = identityClient.getUserById(userId);
            if (res != null && res.getData() != null) {
                return res.getData();
            }
        } catch (Exception e) {
            log.debug("Failed to fetch user {}: {}", userId, e.getMessage());
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
            log.warn("Failed to fetch user batch for attachments: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
