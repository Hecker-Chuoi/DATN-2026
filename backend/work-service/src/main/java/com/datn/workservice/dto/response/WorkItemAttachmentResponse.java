package com.datn.workservice.dto.response;

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
public class WorkItemAttachmentResponse {
    private UUID id;
    private UUID workItemId;
    private String fileName;
    private String fileUrl;
    private String downloadUrl;
    private Long fileSize;
    private String formattedFileSize;
    private String contentType;
    private UUID uploadedBy;
    private String uploaderName;
    private String uploaderAvatar;
    private LocalDateTime uploadedAt;
}
