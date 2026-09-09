package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.response.WorkItemAttachmentResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireTaskPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.WorkItemAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class WorkItemAttachmentController {

    private final WorkItemAttachmentService attachmentService;

    @GetMapping({"/items/{itemId}/attachments", "/work-items/{itemId}/attachments"})
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<WorkItemAttachmentResponse>>> getAttachments(
            @PathVariable("itemId") UUID workItemId
    ) {
        List<WorkItemAttachmentResponse> attachments = attachmentService.getAttachments(workItemId);
        return ResponseEntity.ok(ApiResponse.success(attachments));
    }

    @PostMapping(value = {"/items/{itemId}/attachments", "/work-items/{itemId}/attachments"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireTaskPermission(PermissionKey.ISSUE_EDIT)
    public ResponseEntity<ApiResponse<WorkItemAttachmentResponse>> uploadAttachment(
            @PathVariable("itemId") UUID workItemId,
            @RequestParam("file") MultipartFile file
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        WorkItemAttachmentResponse response = attachmentService.uploadAttachment(workItemId, file, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã tải lên tệp đính kèm thành công"));
    }

    /**
     * Xóa tệp: thành viên dự án; service kiểm tra ATTACHMENT_DELETE_OWN (tệp của mình) / ATTACHMENT_DELETE_ALL.
     */
    @DeleteMapping({"/items/{itemId}/attachments/{attachmentId}", "/work-items/{itemId}/attachments/{attachmentId}"})
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @PathVariable("itemId") UUID workItemId,
            @PathVariable("attachmentId") UUID attachmentId
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        attachmentService.deleteAttachment(workItemId, attachmentId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa tệp đính kèm"));
    }

    /**
     * Tải tệp: quyền PROJECT_VIEW trên dự án chứa tệp được kiểm tra trong service.
     */
    @GetMapping({"/attachments/{attachmentId}/download", "/items/{itemId}/attachments/{attachmentId}/download"})
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("attachmentId") UUID attachmentId
    ) {
        WorkItemAttachmentResponse metadata = attachmentService.getAttachmentById(attachmentId);
        Resource resource = attachmentService.loadAttachmentResource(attachmentId);

        String contentType = metadata.getContentType() != null ? metadata.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String encodedFileName = URLEncoder.encode(metadata.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }
}
