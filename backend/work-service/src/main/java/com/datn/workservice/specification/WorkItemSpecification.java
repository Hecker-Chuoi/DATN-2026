package com.datn.workservice.specification;

import com.datn.workservice.dto.request.WorkItemFilterRequest;
import com.datn.workservice.entity.WorkItem;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class WorkItemSpecification extends BaseSpecification {

    public static Specification<WorkItem> withFilter(WorkItemFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Exclude soft deleted items
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // Project ID
            if (filter.getProjectId() != null) {
                predicates.add(cb.equal(root.get("projectId"), filter.getProjectId()));
            }

            // Keyword search across code and summary
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String pattern = "%" + filter.getKeyword().trim().toLowerCase() + "%";
                Predicate codeMatch = cb.like(cb.lower(root.get("code")), pattern);
                Predicate summaryMatch = cb.like(cb.lower(root.get("summary")), pattern);
                predicates.add(cb.or(codeMatch, summaryMatch));
            }

            // Type IDs
            if (filter.getTypeIds() != null && !filter.getTypeIds().isEmpty()) {
                predicates.add(root.get("typeId").in(filter.getTypeIds()));
            }

            // Status IDs
            if (filter.getStatusIds() != null && !filter.getStatusIds().isEmpty()) {
                predicates.add(root.get("statusId").in(filter.getStatusIds()));
            }

            // Priorities
            if (filter.getPriorities() != null && !filter.getPriorities().isEmpty()) {
                predicates.add(root.get("priority").in(filter.getPriorities()));
            }

            // Assignees
            if (filter.getAssigneeIds() != null && !filter.getAssigneeIds().isEmpty()) {
                predicates.add(root.get("assigneeId").in(filter.getAssigneeIds()));
            }

            // Reporters
            if (filter.getReporterIds() != null && !filter.getReporterIds().isEmpty()) {
                predicates.add(root.get("reporterId").in(filter.getReporterIds()));
            }

            // Sprint ID
            if (filter.getSprintId() != null) {
                predicates.add(cb.equal(root.get("sprintId"), filter.getSprintId()));
            }

            // Is Backlog (sprintId IS NULL)
            if (Boolean.TRUE.equals(filter.getIsBacklog())) {
                predicates.add(cb.isNull(root.get("sprintId")));
            } else if (Boolean.FALSE.equals(filter.getIsBacklog())) {
                predicates.add(cb.isNotNull(root.get("sprintId")));
            }

            // Parent ID
            if (filter.getParentId() != null) {
                predicates.add(cb.equal(root.get("parentId"), filter.getParentId()));
            }

            // Is Parent Null (root-level tasks/stories/epics)
            if (Boolean.TRUE.equals(filter.getIsParentNull())) {
                predicates.add(cb.isNull(root.get("parentId")));
            }

            // Component IDs
            if (filter.getComponentIds() != null && !filter.getComponentIds().isEmpty()) {
                predicates.add(root.get("componentId").in(filter.getComponentIds()));
            }

            // Fix Version IDs
            if (filter.getFixVersionIds() != null && !filter.getFixVersionIds().isEmpty()) {
                predicates.add(root.get("fixVersionId").in(filter.getFixVersionIds()));
            }

            // Due Date range
            if (filter.getDueDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), filter.getDueDateFrom()));
            }
            if (filter.getDueDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), filter.getDueDateTo()));
            }

            // Created Date range
            if (filter.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedFrom()));
            }
            if (filter.getCreatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getCreatedTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
