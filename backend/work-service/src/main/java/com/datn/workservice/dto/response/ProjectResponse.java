package com.datn.workservice.dto.response;

import com.datn.workservice.enums.DefaultAssigneeType;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.TemplateCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID teamId;
    private String name;
    private String key;
    private String description;
    private String iconUrl;
    private UUID leadUserId;
    private DefaultAssigneeType defaultAssigneeType;
    private ProjectType projectType;
    private Boolean isTemplate;
    private TemplateCategory templateCategory;
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /** Vai trò của người dùng hiện tại trong dự án (ADMINISTRATOR / MEMBER / VIEWER), null nếu không xác định. */
    private String myRole;
}
