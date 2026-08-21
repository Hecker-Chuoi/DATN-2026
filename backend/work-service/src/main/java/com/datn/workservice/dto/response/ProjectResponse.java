package com.datn.workservice.dto.response;

import com.datn.workservice.entity.Project;
import com.datn.workservice.enums.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private UUID teamId;
    private String teamName;
    private String key;
    private String name;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID createdBy;
    private String createdByName;

    @JsonProperty("isPublic")
    private Boolean isPublic;

    @JsonProperty("isTemplate")
    private Boolean isTemplate;

    private long taskCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .teamId(project.getTeamId())
                .key(project.getKey())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .createdBy(project.getCreatedBy())
                .isPublic(project.getIsPublic())
                .isTemplate(project.getIsTemplate())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
