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
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDetailResponse {

    private UUID id;
    private UUID workspaceId;
    private String name;
    private String key;
    private String description;
    private String iconUrl;
    private UUID leadUserId;
    private String leadUserName;
    private String leadUserEmail;
    private String leadUserAvatarUrl;
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

    private Long workItemCount;
    private Long memberCount;
    private List<WorkItemTypeResponse> workItemTypes;
    private List<ProjectRoleResponse> roles;
}
