package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.model.WorkflowConditionConfig;
import com.datn.workservice.dto.model.WorkflowValidatorConfig;
import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemResolution;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.impl.WorkflowEngineServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceTest {

    @Mock
    private WorkItemRepository workItemRepository;
    @Mock
    private WorkItemLinkRepository workItemLinkRepository;
    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowTransitionRepository workflowTransitionRepository;
    @Mock
    private WorkflowSchemeRepository workflowSchemeRepository;
    @Mock
    private WorkflowSchemeMappingRepository workflowSchemeMappingRepository;
    @Mock
    private StatusRepository statusRepository;
    @Mock
    private WorkItemChangelogRepository workItemChangelogRepository;
    @Mock
    private WorkItemCommentRepository workItemCommentRepository;
    @Mock
    private ProjectMemberService projectMemberService;
    @Mock
    private ContributionReportService contributionReportService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WorkflowEngineServiceImpl workflowEngineService;

    private UUID projectId;
    private UUID typeId;
    private UUID workflowId;
    private UUID workItemId;
    private UUID todoStatusId;
    private UUID inProgressStatusId;
    private UUID doneStatusId;
    private UUID currentUserId;
    private UUID otherUserId;

    private Status todoStatus;
    private Status inProgressStatus;
    private Status doneStatus;
    private Workflow workflow;
    private WorkItem workItem;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        typeId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        workItemId = UUID.randomUUID();
        todoStatusId = UUID.randomUUID();
        inProgressStatusId = UUID.randomUUID();
        doneStatusId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        todoStatus = Status.builder()
                .id(todoStatusId)
                .name("To Do")
                .category(StatusCategory.TODO)
                .colorHex("#94A3B8")
                .build();

        inProgressStatus = Status.builder()
                .id(inProgressStatusId)
                .name("In Progress")
                .category(StatusCategory.IN_PROGRESS)
                .colorHex("#3B82F6")
                .build();

        doneStatus = Status.builder()
                .id(doneStatusId)
                .name("Done")
                .category(StatusCategory.DONE)
                .colorHex("#10B981")
                .build();

        workflow = Workflow.builder()
                .id(workflowId)
                .projectId(projectId)
                .name("Standard Scrum Workflow")
                .isDefault(true)
                .build();

        workItem = WorkItem.builder()
                .id(workItemId)
                .projectId(projectId)
                .typeId(typeId)
                .statusId(todoStatusId)
                .code("PRJ-101")
                .summary("Design Workflow Engine")
                .assigneeId(currentUserId)
                .reporterId(currentUserId)
                .build();
    }

    @Test
    @DisplayName("Resolve Workflow: Resolves through WorkflowScheme mapping successfully")
    void testResolveWorkflow_viaScheme() {
        UUID schemeId = UUID.randomUUID();
        WorkflowScheme scheme = WorkflowScheme.builder().id(schemeId).projectId(projectId).name("Scheme").build();
        WorkflowSchemeMapping mapping = WorkflowSchemeMapping.builder().schemeId(schemeId).workItemTypeId(typeId).workflowId(workflowId).build();

        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of(scheme));
        when(workflowSchemeMappingRepository.findBySchemeIdAndWorkItemTypeId(schemeId, typeId)).thenReturn(Optional.of(mapping));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        Workflow result = workflowEngineService.resolveWorkflow(projectId, typeId);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(workflowId);
    }

    @Test
    @DisplayName("Resolve Workflow: Fallback to Project Default Workflow when no scheme mapping")
    void testResolveWorkflow_fallbackDefault() {
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        Workflow result = workflowEngineService.resolveWorkflow(projectId, typeId);
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Standard Scrum Workflow");
    }

    @Test
    @DisplayName("Get Available Transitions: Filters transitions by current status and conditions")
    void testGetAvailableTransitions_FiltersCorrectly() {
        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        WorkflowTransition t1 = WorkflowTransition.builder()
                .id(UUID.randomUUID())
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress")
                .build();

        WorkflowTransition t2 = WorkflowTransition.builder()
                .id(UUID.randomUUID())
                .workflowId(workflowId)
                .fromStatusId(inProgressStatusId) // Not matching current TODO status
                .toStatusId(doneStatusId)
                .name("Complete")
                .build();

        when(workflowTransitionRepository.findByWorkflowId(workflowId)).thenReturn(List.of(t1, t2));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));

        List<AvailableTransitionResponse> available = workflowEngineService.getAvailableTransitions(workItemId, currentUserId);
        assertThat(available).hasSize(1);
        assertThat(available.get(0).getName()).isEqualTo("Start Progress");
        assertThat(available.get(0).getToStatusName()).isEqualTo("In Progress");
    }

    @Test
    @DisplayName("Get Available Transitions: Hides transition if ASSIGNEE_ONLY condition fails")
    void testGetAvailableTransitions_HidesOnFailedCondition() {
        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        WorkflowConditionConfig condition = WorkflowConditionConfig.builder()
                .type("ASSIGNEE_ONLY")
                .build();

        WorkflowTransition t1 = WorkflowTransition.builder()
                .id(UUID.randomUUID())
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress")
                .conditionsConfig(List.of(Map.of("type", condition.getType())))
                .build();

        when(workflowTransitionRepository.findByWorkflowId(workflowId)).thenReturn(List.of(t1));

        // When queried by otherUserId (not the assignee)
        List<AvailableTransitionResponse> available = workflowEngineService.getAvailableTransitions(workItemId, otherUserId);
        assertThat(available).isEmpty();
    }

    @Test
    @DisplayName("Execute Transition: Success moves state, sets resolvedAt on DONE, saves changelog & comment")
    void testExecuteTransition_ValidDoneTransition() {
        workItem.setStatusId(inProgressStatusId);

        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition transition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(inProgressStatusId)
                .toStatusId(doneStatusId)
                .name("Approve & Done")
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(transition));
        when(statusRepository.findById(doneStatusId)).thenReturn(Optional.of(doneStatus));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder()
                .transitionId(transitionId)
                .resolution("FIXED")
                .comment("Verified on QA environment")
                .build();

        WorkItem updated = workflowEngineService.executeTransition(workItemId, transitionId, request, currentUserId);

        assertThat(updated.getStatusId()).isEqualTo(doneStatusId);
        assertThat(updated.getResolution()).isEqualTo(WorkItemResolution.FIXED);
        assertThat(updated.getResolvedAt()).isNotNull();

        verify(workItemChangelogRepository, times(1)).save(any(WorkItemChangelog.class));
        verify(workItemCommentRepository, times(1)).save(any(WorkItemComment.class));
        verify(workItemRepository, times(1)).save(workItem);
    }

    @Test
    @DisplayName("Execute Transition: Blocks transition when ALL_SUBTASKS_DONE validator finds incomplete subtask")
    void testExecuteTransition_BlockedBySubtasks() {
        workItem.setStatusId(inProgressStatusId);

        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        WorkflowValidatorConfig validatorConfig = WorkflowValidatorConfig.builder()
                .type("ALL_SUBTASKS_DONE")
                .build();

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition transition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(inProgressStatusId)
                .toStatusId(doneStatusId)
                .name("Approve & Done")
                .validatorsConfig(List.of(Map.of("type", validatorConfig.getType())))
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(transition));
        when(statusRepository.findById(doneStatusId)).thenReturn(Optional.of(doneStatus));

        // Subtask that is still in TODO
        WorkItem subtask = WorkItem.builder()
                .id(UUID.randomUUID())
                .parentId(workItemId)
                .statusId(todoStatusId)
                .build();
        when(workItemRepository.findByParentId(workItemId)).thenReturn(List.of(subtask));
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder().transitionId(transitionId).build();

        assertThatThrownBy(() -> workflowEngineService.executeTransition(workItemId, transitionId, request, currentUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nhiệm vụ con (Sub-task) chưa hoàn thành");
    }

    @Test
    @DisplayName("Execute Transition: Moving out of DONE category clears resolution and resolvedAt")
    void testExecuteTransition_ReopenClearsResolution() {
        workItem.setStatusId(doneStatusId);
        workItem.setResolution(WorkItemResolution.DONE);
        workItem.setResolvedAt(LocalDateTime.now().minusDays(1));

        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition transition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(doneStatusId)
                .toStatusId(inProgressStatusId)
                .name("Reopen")
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(transition));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(statusRepository.findById(doneStatusId)).thenReturn(Optional.of(doneStatus));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder().transitionId(transitionId).build();
        WorkItem updated = workflowEngineService.executeTransition(workItemId, transitionId, request, currentUserId);

        assertThat(updated.getStatusId()).isEqualTo(inProgressStatusId);
        assertThat(updated.getResolution()).isNull();
        assertThat(updated.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("Execute Transition: Throws AccessDeniedException when Condition fails")
    void testExecuteTransition_ConditionFails() {
        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        WorkflowConditionConfig condition = WorkflowConditionConfig.builder()
                .type("ASSIGNEE_ONLY")
                .build();

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition transition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress")
                .conditionsConfig(List.of(Map.of("type", condition.getType())))
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(transition));

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder().transitionId(transitionId).build();

        assertThatThrownBy(() -> workflowEngineService.executeTransition(workItemId, transitionId, request, otherUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không đủ điều kiện (Condition)");
    }

    @Test
    @DisplayName("Execute Transition: Throws BadRequestException when WorkItem is BLOCKED by unfinished issue")
    void testExecuteTransition_BlockedByUnfinishedIssue() {
        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition transition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress")
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(transition));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));

        // Setup blocker
        UUID blockerId = UUID.randomUUID();
        WorkItemLink link = WorkItemLink.builder()
                .sourceItemId(blockerId)
                .targetItemId(workItemId)
                .linkType(LinkType.BLOCKS)
                .build();
        WorkItem blockerItem = WorkItem.builder()
                .id(blockerId)
                .code("PRJ-099")
                .summary("Database migration")
                .statusId(todoStatusId)
                .build();

        when(workItemLinkRepository.findByTargetItemIdAndLinkType(workItemId, LinkType.BLOCKS)).thenReturn(List.of(link));
        when(workItemRepository.findById(blockerId)).thenReturn(Optional.of(blockerItem));
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus)); // todoStatus is not DONE

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder().transitionId(transitionId).build();

        assertThatThrownBy(() -> workflowEngineService.executeTransition(workItemId, transitionId, request, currentUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do công việc đang bị chặn bởi");
    }

    @Test
    @DisplayName("Execute Transition: Auto-resolves all subtasks when resolveAllSubtasks is true")
    void testExecuteTransition_ResolveAllSubtasks() {
        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition transition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(inProgressStatusId)
                .toStatusId(doneStatusId)
                .name("Done")
                .build();
        workItem.setStatusId(inProgressStatusId);

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(transition));
        when(statusRepository.findById(doneStatusId)).thenReturn(Optional.of(doneStatus));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID subtaskId = UUID.randomUUID();
        WorkItem subtask = WorkItem.builder()
                .id(subtaskId)
                .parentId(workItemId)
                .code("PRJ-101-1")
                .statusId(todoStatusId)
                .build();
        when(workItemRepository.findByParentId(workItemId)).thenReturn(List.of(subtask));
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder()
                .transitionId(transitionId)
                .resolveAllSubtasks(true)
                .build();

        WorkItem transitioned = workflowEngineService.executeTransition(workItemId, transitionId, request, currentUserId);

        assertThat(transitioned.getStatusId()).isEqualTo(doneStatusId);
        assertThat(subtask.getStatusId()).isEqualTo(doneStatusId);
        assertThat(subtask.getResolution()).isEqualTo(WorkItemResolution.DONE);
        assertThat(subtask.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Execute Transition: Epic auto-resolves all child Stories and grandchild Subtasks when resolveAllSubtasks is true")
    void testExecuteTransition_Epic_AutoResolvesChildStoriesAndGrandchildSubtasks() {
        UUID epicId = UUID.randomUUID();
        WorkItem epic = WorkItem.builder()
                .id(epicId)
                .projectId(projectId)
                .code("PRJ-1")
                .statusId(inProgressStatusId)
                .build();
        when(workItemRepository.findById(epicId)).thenReturn(Optional.of(epic));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition transition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(inProgressStatusId)
                .toStatusId(doneStatusId)
                .name("Done")
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(transition));
        when(statusRepository.findById(doneStatusId)).thenReturn(Optional.of(doneStatus));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID storyId = UUID.randomUUID();
        WorkItem story = WorkItem.builder()
                .id(storyId)
                .parentId(epicId)
                .code("PRJ-2")
                .statusId(todoStatusId)
                .build();

        UUID subtaskId = UUID.randomUUID();
        WorkItem subtask = WorkItem.builder()
                .id(subtaskId)
                .parentId(storyId)
                .code("PRJ-3")
                .statusId(inProgressStatusId)
                .build();

        when(workItemRepository.findByParentId(epicId)).thenReturn(List.of(story));
        when(workItemRepository.findByParentId(storyId)).thenReturn(List.of(subtask));
        when(workItemRepository.findByParentId(subtaskId)).thenReturn(List.of());
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder()
                .transitionId(transitionId)
                .resolveAllSubtasks(true)
                .build();

        WorkItem transitioned = workflowEngineService.executeTransition(epicId, transitionId, request, currentUserId);

        assertThat(transitioned.getStatusId()).isEqualTo(doneStatusId);
        assertThat(story.getStatusId()).isEqualTo(doneStatusId);
        assertThat(story.getResolution()).isEqualTo(WorkItemResolution.DONE);
        assertThat(story.getResolvedAt()).isNotNull();
        assertThat(subtask.getStatusId()).isEqualTo(doneStatusId);
        assertThat(subtask.getResolution()).isEqualTo(WorkItemResolution.DONE);
        assertThat(subtask.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Execute Transition: Auto-transitions parent from TODO to IN_PROGRESS when Subtask starts")
    void testExecuteTransition_AutoTransitionsParentWhenSubtaskStarts() {
        UUID parentId = UUID.randomUUID();
        workItem.setParentId(parentId);
        workItem.setStatusId(todoStatusId);

        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition transition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress")
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(transition));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));
        when(statusRepository.findByCategory(StatusCategory.IN_PROGRESS)).thenReturn(List.of(inProgressStatus));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkItem parentItem = WorkItem.builder()
                .id(parentId)
                .code("PRJ-100")
                .summary("Parent Story")
                .statusId(todoStatusId)
                .build();
        when(workItemRepository.findById(parentId)).thenReturn(Optional.of(parentItem));

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder().transitionId(transitionId).build();
        WorkItem transitioned = workflowEngineService.executeTransition(workItemId, transitionId, request, currentUserId);

        assertThat(transitioned.getStatusId()).isEqualTo(inProgressStatusId);
        assertThat(parentItem.getStatusId()).isEqualTo(inProgressStatusId);
    }

    @Test
    @DisplayName("Execute Transition: Cascades reopen to parent Story and grandparent Epic when Subtask is reopened")
    void testExecuteTransition_CascadesReopenWhenSubtaskReopened() {
        UUID epicId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        // Subtask setup: currently DONE, about to transition to IN_PROGRESS
        workItem.setParentId(storyId);
        workItem.setStatusId(doneStatusId);
        workItem.setResolution(WorkItemResolution.DONE);

        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition reopenTransition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(doneStatusId)
                .toStatusId(inProgressStatusId)
                .name("Reopen Subtask")
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(reopenTransition));
        when(statusRepository.findById(doneStatusId)).thenReturn(Optional.of(doneStatus));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(statusRepository.findByCategory(StatusCategory.IN_PROGRESS)).thenReturn(List.of(inProgressStatus));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // Parent Story: currently DONE
        WorkItem parentStory = WorkItem.builder()
                .id(storyId)
                .code("PRJ-100")
                .summary("Parent Story")
                .statusId(doneStatusId)
                .parentId(epicId)
                .resolution(WorkItemResolution.DONE)
                .resolvedAt(java.time.LocalDateTime.now())
                .build();
        when(workItemRepository.findById(storyId)).thenReturn(Optional.of(parentStory));

        // Grandparent Epic: currently DONE
        WorkItem grandparentEpic = WorkItem.builder()
                .id(epicId)
                .code("PRJ-1")
                .summary("Epic Initiative")
                .statusId(doneStatusId)
                .resolution(WorkItemResolution.DONE)
                .resolvedAt(java.time.LocalDateTime.now())
                .build();
        when(workItemRepository.findById(epicId)).thenReturn(Optional.of(grandparentEpic));

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder().transitionId(transitionId).build();
        WorkItem transitioned = workflowEngineService.executeTransition(workItemId, transitionId, request, currentUserId);

        // Subtask transitioned
        assertThat(transitioned.getStatusId()).isEqualTo(inProgressStatusId);
        assertThat(transitioned.getResolution()).isNull();
        assertThat(transitioned.getResolvedAt()).isNull();

        // Parent Story cascaded reopened
        assertThat(parentStory.getStatusId()).isEqualTo(inProgressStatusId);
        assertThat(parentStory.getResolution()).isNull();
        assertThat(parentStory.getResolvedAt()).isNull();

        // Grandparent Epic cascaded reopened
        assertThat(grandparentEpic.getStatusId()).isEqualTo(inProgressStatusId);
        assertThat(grandparentEpic.getResolution()).isNull();
        assertThat(grandparentEpic.getResolvedAt()).isNull();

        // Verify repository saved all 3 items
        verify(workItemRepository, atLeast(3)).save(any(WorkItem.class));
    }

    @Test
    @DisplayName("Execute Transition: Cascades reopen when Subtask is moved to TODO and parent Story is DONE")
    void testExecuteTransition_CascadesReopenWhenSubtaskMovedToTodo() {
        UUID storyId = UUID.randomUUID();

        workItem.setParentId(storyId);
        workItem.setStatusId(inProgressStatusId);

        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(workItem));
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        UUID transitionId = UUID.randomUUID();
        WorkflowTransition stopTransition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(inProgressStatusId)
                .toStatusId(todoStatusId)
                .name("Move to Todo")
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(stopTransition));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));
        when(statusRepository.findById(doneStatusId)).thenReturn(Optional.of(doneStatus));
        when(statusRepository.findByCategory(StatusCategory.IN_PROGRESS)).thenReturn(List.of(inProgressStatus));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkItem parentStory = WorkItem.builder()
                .id(storyId)
                .code("PRJ-100")
                .summary("Parent Story")
                .statusId(doneStatusId)
                .resolution(WorkItemResolution.DONE)
                .resolvedAt(java.time.LocalDateTime.now())
                .build();
        when(workItemRepository.findById(storyId)).thenReturn(Optional.of(parentStory));

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder().transitionId(transitionId).build();
        WorkItem transitioned = workflowEngineService.executeTransition(workItemId, transitionId, request, currentUserId);

        assertThat(transitioned.getStatusId()).isEqualTo(todoStatusId);

        // Parent Story must be reopened out of DONE to IN_PROGRESS
        assertThat(parentStory.getStatusId()).isEqualTo(inProgressStatusId);
        assertThat(parentStory.getResolution()).isNull();
        assertThat(parentStory.getResolvedAt()).isNull();
    }
}
