package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.*;
import com.datn.workservice.dto.response.*;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.impl.WorkflowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

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
    private WorkItemTypeRepository workItemTypeRepository;

    @InjectMocks
    private WorkflowServiceImpl workflowService;

    private UUID projectId;
    private UUID workflowId;
    private UUID todoStatusId;
    private UUID inProgressStatusId;
    private Workflow workflow;
    private Status todoStatus;
    private Status inProgressStatus;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        todoStatusId = UUID.randomUUID();
        inProgressStatusId = UUID.randomUUID();

        workflow = Workflow.builder()
                .id(workflowId)
                .projectId(projectId)
                .name("Scrum Workflow")
                .isDefault(true)
                .build();

        todoStatus = Status.builder()
                .id(todoStatusId)
                .name("To Do")
                .category(StatusCategory.TODO)
                .build();

        inProgressStatus = Status.builder()
                .id(inProgressStatusId)
                .name("In Progress")
                .category(StatusCategory.IN_PROGRESS)
                .build();
    }

    @Test
    @DisplayName("Get Project Workflow Detail: Returns workflow with all statuses and transitions")
    void testGetProjectWorkflowDetail_Success() {
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        WorkflowTransition transition = WorkflowTransition.builder()
                .id(UUID.randomUUID())
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress")
                .build();

        when(workflowTransitionRepository.findByWorkflowId(workflowId)).thenReturn(List.of(transition));
        when(statusRepository.findAll()).thenReturn(List.of(todoStatus, inProgressStatus));

        WorkflowDetailResponse detail = workflowService.getProjectWorkflowDetail(projectId);

        assertThat(detail).isNotNull();
        assertThat(detail.getName()).isEqualTo("Scrum Workflow");
        assertThat(detail.getTransitions()).hasSize(1);
        assertThat(detail.getTransitions().get(0).getName()).isEqualTo("Start Progress");
        assertThat(detail.getStatuses()).hasSize(2);
    }

    @Test
    @DisplayName("Create Workflow: Successfully saves new workflow")
    void testCreateWorkflow_Success() {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .projectId(projectId)
                .name("Bug Workflow")
                .description("Custom workflow for bugs")
                .isDefault(false)
                .build();

        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> {
            Workflow w = inv.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        WorkflowResponse response = workflowService.createWorkflow(request);
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Bug Workflow");
        verify(workflowRepository, times(1)).save(any(Workflow.class));
    }

    @Test
    @DisplayName("Create Status: Throws BadRequestException when name already exists")
    void testCreateStatus_DuplicateNameFails() {
        CreateStatusRequest request = CreateStatusRequest.builder()
                .name("To Do")
                .category(StatusCategory.TODO)
                .build();

        when(statusRepository.existsByName("To Do")).thenReturn(true);

        assertThatThrownBy(() -> workflowService.createStatus(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("Create Transition: Saves transition when source and target statuses exist")
    void testCreateTransition_Success() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(workflowTransitionRepository.existsByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, todoStatusId, inProgressStatusId)).thenReturn(false);
        when(workflowTransitionRepository.existsByWorkflowIdAndNameIgnoreCase(workflowId, "Start Progress")).thenReturn(false);

        WorkflowTransition transition = WorkflowTransition.builder()
                .id(UUID.randomUUID())
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress")
                .build();
        when(workflowTransitionRepository.save(any(WorkflowTransition.class))).thenReturn(transition);

        CreateWorkflowTransitionRequest request = CreateWorkflowTransitionRequest.builder()
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress")
                .build();

        WorkflowTransitionResponse response = workflowService.createTransition(workflowId, request);
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Start Progress");
    }

    @Test
    @DisplayName("Create Transition: Throws BadRequest when fromStatus equals toStatus")
    void testCreateTransition_SameStatus_ThrowsBadRequest() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        CreateWorkflowTransitionRequest request = CreateWorkflowTransitionRequest.builder()
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(todoStatusId)
                .name("Self Transition")
                .build();

        assertThatThrownBy(() -> workflowService.createTransition(workflowId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không được trùng");
    }

    @Test
    @DisplayName("Create Transition: Throws BadRequest when transition path already exists")
    void testCreateTransition_DuplicatePath_ThrowsBadRequest() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(workflowTransitionRepository.existsByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, todoStatusId, inProgressStatusId)).thenReturn(true);

        CreateWorkflowTransitionRequest request = CreateWorkflowTransitionRequest.builder()
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress Again")
                .build();

        assertThatThrownBy(() -> workflowService.createTransition(workflowId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Đã tồn tại bước chuyển giữa hai trạng thái này");
    }

    @Test
    @DisplayName("Create Transition: Throws BadRequest when duplicate initial transition path exists")
    void testCreateTransition_DuplicateInitialPath_ThrowsBadRequest() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));
        when(workflowTransitionRepository.existsByWorkflowIdAndFromStatusIdIsNullAndToStatusId(workflowId, todoStatusId)).thenReturn(true);

        CreateWorkflowTransitionRequest request = CreateWorkflowTransitionRequest.builder()
                .workflowId(workflowId)
                .fromStatusId(null)
                .toStatusId(todoStatusId)
                .name("Create 2")
                .build();

        assertThatThrownBy(() -> workflowService.createTransition(workflowId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Đã tồn tại bước chuyển giữa hai trạng thái này");
    }

    @Test
    @DisplayName("Create Transition: Throws BadRequest when transition name already exists")
    void testCreateTransition_DuplicateName_ThrowsBadRequest() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(workflowTransitionRepository.existsByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, todoStatusId, inProgressStatusId)).thenReturn(false);
        when(workflowTransitionRepository.existsByWorkflowIdAndNameIgnoreCase(workflowId, "Start Progress")).thenReturn(true);

        CreateWorkflowTransitionRequest request = CreateWorkflowTransitionRequest.builder()
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Start Progress")
                .build();

        assertThatThrownBy(() -> workflowService.createTransition(workflowId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã tồn tại trong quy trình");
    }

    @Test
    @DisplayName("Create Transition: Throws BadRequest when status belongs to a different project")
    void testCreateTransition_StatusDifferentProject_ThrowsBadRequest() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        Status foreignStatus = Status.builder()
                .id(UUID.randomUUID())
                .projectId(UUID.randomUUID()) // Different project!
                .name("Foreign Status")
                .category(StatusCategory.IN_PROGRESS)
                .build();
        when(statusRepository.findById(todoStatusId)).thenReturn(Optional.of(todoStatus));
        when(statusRepository.findById(foreignStatus.getId())).thenReturn(Optional.of(foreignStatus));

        CreateWorkflowTransitionRequest request = CreateWorkflowTransitionRequest.builder()
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(foreignStatus.getId())
                .name("Go to Foreign")
                .build();

        assertThatThrownBy(() -> workflowService.createTransition(workflowId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thuộc phạm vi dự án");
    }

    @Test
    @DisplayName("Update Transition: Successfully updates transition and allows clearing fromStatusId to null")
    void testUpdateTransition_Success() {
        UUID transitionId = UUID.randomUUID();
        WorkflowTransition existing = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Old Name")
                .build();

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(existing));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(workflowTransitionRepository.findByWorkflowIdAndFromStatusIdIsNullAndToStatusId(workflowId, inProgressStatusId)).thenReturn(Optional.empty());
        when(workflowTransitionRepository.findByWorkflowIdAndNameIgnoreCase(workflowId, "New Name")).thenReturn(Optional.empty());
        when(workflowTransitionRepository.save(any(WorkflowTransition.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateWorkflowTransitionRequest updateReq = UpdateWorkflowTransitionRequest.builder()
                .fromStatusId(null) // Clear to null (Initial/Any)
                .toStatusId(inProgressStatusId)
                .name("New Name")
                .build();

        WorkflowTransitionResponse updated = workflowService.updateTransition(workflowId, transitionId, updateReq);
        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getFromStatusId()).isNull();
    }

    @Test
    @DisplayName("Update Transition: Throws BadRequest when updating to duplicate transition path")
    void testUpdateTransition_DuplicatePath_ThrowsBadRequest() {
        UUID transitionId = UUID.randomUUID();
        UUID otherTransitionId = UUID.randomUUID();
        WorkflowTransition existing = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .fromStatusId(todoStatusId)
                .toStatusId(inProgressStatusId)
                .name("Current")
                .build();

        WorkflowTransition another = WorkflowTransition.builder()
                .id(otherTransitionId)
                .workflowId(workflowId)
                .fromStatusId(null)
                .toStatusId(inProgressStatusId)
                .name("Existing Null To InProgress")
                .build();

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(existing));
        when(statusRepository.findById(inProgressStatusId)).thenReturn(Optional.of(inProgressStatus));
        when(workflowTransitionRepository.findByWorkflowIdAndFromStatusIdIsNullAndToStatusId(workflowId, inProgressStatusId)).thenReturn(Optional.of(another));

        UpdateWorkflowTransitionRequest updateReq = UpdateWorkflowTransitionRequest.builder()
                .fromStatusId(null)
                .toStatusId(inProgressStatusId)
                .build();

        assertThatThrownBy(() -> workflowService.updateTransition(workflowId, transitionId, updateReq))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Đã tồn tại bước chuyển giữa hai trạng thái này");
    }

    @Test
    @DisplayName("Delete Transition: Deletes transition from repository")
    void testDeleteTransition_Success() {
        UUID transitionId = UUID.randomUUID();
        WorkflowTransition transition = WorkflowTransition.builder()
                .id(transitionId)
                .workflowId(workflowId)
                .name("Old Transition")
                .build();

        when(workflowTransitionRepository.findById(transitionId)).thenReturn(Optional.of(transition));

        workflowService.deleteTransition(workflowId, transitionId);
        verify(workflowTransitionRepository, times(1)).delete(transition);
    }
}
