package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemCommentResponse {
    private UUID id;
    private UUID workItemId;
    private UUID parentCommentId;
    private UUID authorId;
    private String authorName;
    private String authorAvatar;
    private String authorEmail;
    private String body;
    private boolean isEdited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<WorkItemCommentResponse> replies = new ArrayList<>();
}
