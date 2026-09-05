package com.datn.workservice.service;

import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemType;
import com.datn.workservice.exception.InvalidHierarchyException;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.repository.WorkItemTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * StrictHierarchyValidator - Enforces strict 3-level Jira hierarchy constraints:
 * Level 1: Epic -> Level 0: Standard (Story/Task/Bug) -> Level -1: Subtask.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrictHierarchyValidator {

    public static final int LEVEL_EPIC = 1;
    public static final int LEVEL_STANDARD = 0;
    public static final int LEVEL_SUBTASK = -1;

    private final WorkItemRepository workItemRepository;
    private final WorkItemTypeRepository workItemTypeRepository;

    /**
     * Checks if parent level and child level form a valid 3-level Jira hierarchy.
     *
     * @param parentLevel hierarchy level of the parent (or null if no parent)
     * @param childLevel  hierarchy level of the child item
     * @return true if permitted, false otherwise
     */
    public boolean isHierarchyAllowed(Integer parentLevel, Integer childLevel) {
        if (childLevel == null) return false;

        // Level 1: Epic must NOT have any parent
        if (childLevel == LEVEL_EPIC) {
            return parentLevel == null;
        }

        // Level 0: Standard (Story, Task, Bug) can stand alone or have an Epic parent
        if (childLevel == LEVEL_STANDARD) {
            return parentLevel == null || parentLevel == LEVEL_EPIC;
        }

        // Level -1: Subtask MUST have a Standard (Level 0) parent
        if (childLevel == LEVEL_SUBTASK) {
            return parentLevel != null && parentLevel == LEVEL_STANDARD;
        }

        return false;
    }

    /**
     * Validates hierarchy levels directly, throwing InvalidHierarchyException with detailed explanation.
     */
    public void validateHierarchyLevels(Integer parentLevel, Integer childLevel) {
        if (childLevel == null) {
            throw new InvalidHierarchyException("Child work item hierarchy level must not be null.");
        }

        if (childLevel == LEVEL_EPIC && parentLevel != null) {
            throw new InvalidHierarchyException("Epic (Hierarchy Level 1) cannot have a parent item.");
        }

        if (childLevel == LEVEL_STANDARD) {
            if (parentLevel != null && parentLevel != LEVEL_EPIC) {
                throw new InvalidHierarchyException(
                        "Standard item (Story/Task/Bug) can only have an Epic (Level 1) as its parent. Found parent level: " + parentLevel
                );
            }
        }

        if (childLevel == LEVEL_SUBTASK) {
            if (parentLevel == null) {
                throw new InvalidHierarchyException("Sub-task (-1) must have a parent (Story/Task/Bug). It cannot stand alone without a parent.");
            }
            if (parentLevel == LEVEL_EPIC) {
                throw new InvalidHierarchyException("Sub-task (-1) cannot be directly attached to an Epic (1). It must belong to a Story, Task, or Bug (0).");
            }
            if (parentLevel == LEVEL_SUBTASK) {
                throw new InvalidHierarchyException("Sub-task (-1) cannot have another Sub-task as parent (Multi-level subtask nesting is not allowed).");
            }
            if (parentLevel != LEVEL_STANDARD) {
                throw new InvalidHierarchyException("Sub-task (-1) must belong to a Level 0 item (Story/Task/Bug). Found parent level: " + parentLevel);
            }
        }
    }

    /**
     * Full validation of work item creation or parent update in context of database entities.
     *
     * @param currentItemId ID of current item (null if creating new)
     * @param childTypeId   WorkItemType ID of child item
     * @param parentId      Parent WorkItem ID (optional)
     * @param projectId     Current project ID
     */
    public void validateHierarchy(UUID currentItemId, UUID childTypeId, UUID parentId, UUID projectId) {
        WorkItemType childType = workItemTypeRepository.findById(childTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItemType not found with ID: " + childTypeId));

        Integer childLevel = childType.getHierarchyLevel();

        if (parentId == null) {
            validateHierarchyLevels(null, childLevel);
            return;
        }

        // Self-parent check
        if (currentItemId != null && currentItemId.equals(parentId)) {
            throw new InvalidHierarchyException("An item cannot be its own parent.");
        }

        WorkItem parentItem = workItemRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent WorkItem not found with ID: " + parentId));

        // Same project check
        if (!parentItem.getProjectId().equals(projectId)) {
            throw new InvalidHierarchyException("Parent item (" + parentItem.getCode() + ") belongs to a different project.");
        }

        // Parent deleted check
        if (parentItem.getDeletedAt() != null) {
            throw new InvalidHierarchyException("Cannot link to a deleted parent item: " + parentItem.getCode());
        }

        WorkItemType parentType = workItemTypeRepository.findById(parentItem.getTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent WorkItemType not found with ID: " + parentItem.getTypeId()));

        validateHierarchyLevels(parentType.getHierarchyLevel(), childLevel);
    }
}
