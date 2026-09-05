package com.datn.workservice.service;

import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemType;
import com.datn.workservice.exception.InvalidHierarchyException;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.repository.WorkItemTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrictHierarchyValidatorTest {

    @Mock
    private WorkItemRepository workItemRepository;

    @Mock
    private WorkItemTypeRepository workItemTypeRepository;

    private StrictHierarchyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StrictHierarchyValidator(workItemRepository, workItemTypeRepository);
    }

    @Test
    @DisplayName("isHierarchyAllowed - Matrix of valid and invalid combinations")
    void testIsHierarchyAllowed() {
        // Epic (1)
        assertTrue(validator.isHierarchyAllowed(null, 1));
        assertFalse(validator.isHierarchyAllowed(1, 1));
        assertFalse(validator.isHierarchyAllowed(0, 1));
        assertFalse(validator.isHierarchyAllowed(-1, 1));

        // Standard (0)
        assertTrue(validator.isHierarchyAllowed(null, 0));
        assertTrue(validator.isHierarchyAllowed(1, 0));
        assertFalse(validator.isHierarchyAllowed(0, 0));
        assertFalse(validator.isHierarchyAllowed(-1, 0));

        // Subtask (-1)
        assertFalse(validator.isHierarchyAllowed(null, -1));
        assertFalse(validator.isHierarchyAllowed(1, -1));
        assertTrue(validator.isHierarchyAllowed(0, -1));
        assertFalse(validator.isHierarchyAllowed(-1, -1));
    }

    @Test
    @DisplayName("validateHierarchyLevels - Epic cannot have a parent")
    void testEpicCannotHaveParent() {
        assertDoesNotThrow(() -> validator.validateHierarchyLevels(null, 1));

        InvalidHierarchyException ex = assertThrows(InvalidHierarchyException.class,
                () -> validator.validateHierarchyLevels(1, 1));
        assertTrue(ex.getMessage().contains("Epic (Hierarchy Level 1) cannot have a parent"));
    }

    @Test
    @DisplayName("validateHierarchyLevels - Standard item can only have Epic parent or null")
    void testStandardItemParentRules() {
        assertDoesNotThrow(() -> validator.validateHierarchyLevels(null, 0));
        assertDoesNotThrow(() -> validator.validateHierarchyLevels(1, 0));

        InvalidHierarchyException ex0 = assertThrows(InvalidHierarchyException.class,
                () -> validator.validateHierarchyLevels(0, 0));
        assertTrue(ex0.getMessage().contains("Standard item (Story/Task/Bug) can only have an Epic"));

        InvalidHierarchyException exSub = assertThrows(InvalidHierarchyException.class,
                () -> validator.validateHierarchyLevels(-1, 0));
        assertTrue(exSub.getMessage().contains("Standard item (Story/Task/Bug) can only have an Epic"));
    }

    @Test
    @DisplayName("validateHierarchyLevels - Subtask must have standard parent")
    void testSubtaskParentRules() {
        assertDoesNotThrow(() -> validator.validateHierarchyLevels(0, -1));

        // Subtask with null parent
        InvalidHierarchyException exNull = assertThrows(InvalidHierarchyException.class,
                () -> validator.validateHierarchyLevels(null, -1));
        assertTrue(exNull.getMessage().contains("cannot stand alone"));

        // Subtask with Epic parent
        InvalidHierarchyException exEpic = assertThrows(InvalidHierarchyException.class,
                () -> validator.validateHierarchyLevels(1, -1));
        assertTrue(exEpic.getMessage().contains("cannot be directly attached to an Epic"));

        // Subtask with Subtask parent (multi-level nesting)
        InvalidHierarchyException exSub = assertThrows(InvalidHierarchyException.class,
                () -> validator.validateHierarchyLevels(-1, -1));
        assertTrue(exSub.getMessage().contains("Multi-level subtask nesting is not allowed"));
    }

    @Test
    @DisplayName("validateHierarchy - Cannot be own parent")
    void testCannotBeOwnParent() {
        UUID itemId = UUID.randomUUID();
        UUID childTypeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        WorkItemType childType = WorkItemType.builder()
                .id(childTypeId)
                .hierarchyLevel(0)
                .build();

        when(workItemTypeRepository.findById(childTypeId)).thenReturn(Optional.of(childType));

        InvalidHierarchyException ex = assertThrows(InvalidHierarchyException.class,
                () -> validator.validateHierarchy(itemId, childTypeId, itemId, projectId));
        assertTrue(ex.getMessage().contains("An item cannot be its own parent"));
    }

    @Test
    @DisplayName("validateHierarchy - Cannot assign parent from different project")
    void testDifferentProjectParent() {
        UUID itemId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childTypeId = UUID.randomUUID();
        UUID projectId1 = UUID.randomUUID();
        UUID projectId2 = UUID.randomUUID();

        WorkItemType childType = WorkItemType.builder().id(childTypeId).hierarchyLevel(-1).build();
        WorkItem parentItem = WorkItem.builder().id(parentId).projectId(projectId2).code("PRJ2-1").build();

        when(workItemTypeRepository.findById(childTypeId)).thenReturn(Optional.of(childType));
        when(workItemRepository.findById(parentId)).thenReturn(Optional.of(parentItem));

        InvalidHierarchyException ex = assertThrows(InvalidHierarchyException.class,
                () -> validator.validateHierarchy(itemId, childTypeId, parentId, projectId1));
        assertTrue(ex.getMessage().contains("belongs to a different project"));
    }

    @Test
    @DisplayName("validateHierarchy - Cannot assign deleted parent")
    void testDeletedParent() {
        UUID itemId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childTypeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        WorkItemType childType = WorkItemType.builder().id(childTypeId).hierarchyLevel(-1).build();
        WorkItem parentItem = WorkItem.builder()
                .id(parentId)
                .projectId(projectId)
                .code("PRJ-1")
                .deletedAt(LocalDateTime.now())
                .build();

        when(workItemTypeRepository.findById(childTypeId)).thenReturn(Optional.of(childType));
        when(workItemRepository.findById(parentId)).thenReturn(Optional.of(parentItem));

        InvalidHierarchyException ex = assertThrows(InvalidHierarchyException.class,
                () -> validator.validateHierarchy(itemId, childTypeId, parentId, projectId));
        assertTrue(ex.getMessage().contains("Cannot link to a deleted parent item"));
    }

    @Test
    @DisplayName("validateHierarchy - Success for valid Story under Epic and Subtask under Story")
    void testSuccessfulHierarchyValidation() {
        UUID epicId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID subtaskId = UUID.randomUUID();
        UUID epicTypeId = UUID.randomUUID();
        UUID storyTypeId = UUID.randomUUID();
        UUID subtaskTypeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        WorkItemType epicType = WorkItemType.builder().id(epicTypeId).hierarchyLevel(1).build();
        WorkItemType storyType = WorkItemType.builder().id(storyTypeId).hierarchyLevel(0).build();
        WorkItemType subtaskType = WorkItemType.builder().id(subtaskTypeId).hierarchyLevel(-1).build();

        WorkItem epicItem = WorkItem.builder().id(epicId).typeId(epicTypeId).projectId(projectId).code("PRJ-1").build();
        WorkItem storyItem = WorkItem.builder().id(storyId).typeId(storyTypeId).projectId(projectId).code("PRJ-2").build();

        // 1. Validate Epic stand alone
        when(workItemTypeRepository.findById(epicTypeId)).thenReturn(Optional.of(epicType));
        assertDoesNotThrow(() -> validator.validateHierarchy(epicId, epicTypeId, null, projectId));

        // 2. Validate Story under Epic
        when(workItemTypeRepository.findById(storyTypeId)).thenReturn(Optional.of(storyType));
        when(workItemRepository.findById(epicId)).thenReturn(Optional.of(epicItem));
        assertDoesNotThrow(() -> validator.validateHierarchy(storyId, storyTypeId, epicId, projectId));

        // 3. Validate Subtask under Story
        when(workItemTypeRepository.findById(subtaskTypeId)).thenReturn(Optional.of(subtaskType));
        when(workItemRepository.findById(storyId)).thenReturn(Optional.of(storyItem));
        assertDoesNotThrow(() -> validator.validateHierarchy(subtaskId, subtaskTypeId, storyId, projectId));
    }
}
