package com.datn.workservice.dto.request;

import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filter payload for dynamic multi-criteria search and querying of Work Items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemFilterRequest {

    private UUID projectId;

    /**
     * Search keyword matching across code, summary, or description.
     */
    private String keyword;

    private List<UUID> typeIds;

    private List<UUID> statusIds;

    private List<StatusCategory> statusCategories;

    private List<WorkItemPriority> priorities;

    private List<UUID> assigneeIds;

    private List<UUID> reporterIds;

    /**
     * Specific sprint ID.
     */
    private UUID sprintId;

    /**
     * Filter by backlog: true = sprintId IS NULL, false = sprintId IS NOT NULL.
     */
    private Boolean isBacklog;

    /**
     * Parent item ID (to query all subtasks of a task, or stories of an epic).
     */
    private UUID parentId;

    /**
     * Filter root level items (parentId IS NULL).
     */
    private Boolean isParentNull;

    private List<UUID> componentIds;

    private List<UUID> fixVersionIds;

    private LocalDate dueDateFrom;

    private LocalDate dueDateTo;

    private LocalDateTime createdFrom;

    private LocalDateTime createdTo;

    private List<UUID> labelIds;
}
