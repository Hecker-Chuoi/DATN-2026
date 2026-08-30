package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectDetailResponse;
import com.datn.workservice.dto.response.ProjectResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.DefaultAssigneeType;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private WorkItemTypeRepository workItemTypeRepository;
    @Mock
    private StatusRepository statusRepository;
    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowTransitionRepository workflowTransitionRepository;
    @Mock
    private WorkflowSchemeRepository workflowSchemeRepository;
    @Mock
    private WorkflowSchemeMappingRepository workflowSchemeMappingRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private BoardColumnRepository boardColumnRepository;
    @Mock
    private BoardColumnStatusRepository boardColumnStatusRepository;
    @Mock
    private ProjectRoleRepository projectRoleRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private WorkItemRepository workItemRepository;
    @Mock
    private AtomicKeySequenceGenerator atomicKeySequenceGenerator;
    @Mock
    private IdentityClient identityClient;

    @InjectMocks
    private ProjectServiceImpl projectService;

    private UUID workspaceId;
    private UUID currentUserId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
    }

    @Test
    @DisplayName("createProject: Creates project and auto-seeds all ecosystem elements")
    void testCreateProjectSuccess() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .workspaceId(workspaceId)
                .name("Core Banking Engine")
                .key("CBE")
                .description("Banking core system")
                .projectType(ProjectType.SOFTWARE_SCRUM)
                .startDate(LocalDate.now())
                .build();

        Project savedProject = Project.builder()
                .id(projectId)
                .workspaceId(workspaceId)
                .name("Core Banking Engine")
                .key("CBE")
                .description("Banking core system")
                .leadUserId(currentUserId)
                .projectType(ProjectType.SOFTWARE_SCRUM)
                .defaultAssigneeType(DefaultAssigneeType.PROJECT_LEADER)
                .isTemplate(false)
                .createdBy(currentUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(projectRepository.existsByKeyIgnoreCase("CBE")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        // Mock ecosystem dependencies
        when(workItemTypeRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<WorkItemType> types = inv.getArgument(0);
            types.forEach(t -> t.setId(UUID.randomUUID()));
            return types;
        });

        Status statusMock = Status.builder().id(UUID.randomUUID()).name("To Do").category(StatusCategory.TODO).build();
        when(statusRepository.findByName(anyString())).thenReturn(Optional.of(statusMock));

        Workflow workflowMock = Workflow.builder().id(UUID.randomUUID()).projectId(projectId).name("CBE Workflow").build();
        when(workflowRepository.save(any(Workflow.class))).thenReturn(workflowMock);

        WorkflowScheme schemeMock = WorkflowScheme.builder().id(UUID.randomUUID()).projectId(projectId).build();
        when(workflowSchemeRepository.save(any(WorkflowScheme.class))).thenReturn(schemeMock);

        Board boardMock = Board.builder().id(UUID.randomUUID()).projectId(projectId).name("CBE Board").build();
        when(boardRepository.save(any(Board.class))).thenReturn(boardMock);

        BoardColumn colMock = BoardColumn.builder().id(UUID.randomUUID()).boardId(boardMock.getId()).name("TO DO").columnOrder(0).build();
        when(boardColumnRepository.save(any(BoardColumn.class))).thenReturn(colMock);

        ProjectRole roleMock = ProjectRole.builder().id(UUID.randomUUID()).projectId(projectId).name("ADMINISTRATOR").build();
        when(projectRoleRepository.save(any(ProjectRole.class))).thenReturn(roleMock);

        // Execute
        ProjectResponse response = projectService.createProject(request, currentUserId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(projectId);
        assertThat(response.getKey()).isEqualTo("CBE");
        assertThat(response.getName()).isEqualTo("Core Banking Engine");

        // Verify sequence generation
        verify(atomicKeySequenceGenerator).createSequenceForProject("CBE");
        // Verify 5 WorkItemTypes seeded
        verify(workItemTypeRepository).saveAll(argThat(list -> ((List<?>) list).size() == 5));
        // Verify Workflow created
        verify(workflowRepository).save(any(Workflow.class));
        // Verify Board and columns created
        verify(boardRepository).save(any(Board.class));
        verify(boardColumnRepository, times(3)).save(any(BoardColumn.class));
        // Verify Roles created
        verify(projectRoleRepository, times(3)).save(any(ProjectRole.class));
        // Verify Administrator assigned
        verify(roleAssignmentRepository, atLeastOnce()).save(any(RoleAssignment.class));
    }

    @Test
    @DisplayName("createProject: Throws BadRequestException when key already exists")
    void testCreateProjectDuplicateKey() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .workspaceId(workspaceId)
                .name("Duplicate Project")
                .key("DUP")
                .build();

        when(projectRepository.existsByKeyIgnoreCase("DUP")).thenReturn(true);

        assertThatThrownBy(() -> projectService.createProject(request, currentUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DUP");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("getProjectById: Returns enriched ProjectDetailResponse")
    void testGetProjectByIdSuccess() {
        Project project = Project.builder()
                .id(projectId)
                .workspaceId(workspaceId)
                .name("Alpha Project")
                .key("ALPHA")
                .leadUserId(currentUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(workItemRepository.countByProjectId(projectId)).thenReturn(15L);
        when(roleAssignmentRepository.findByProjectId(projectId)).thenReturn(List.of(
                RoleAssignment.builder().projectId(projectId).userId(currentUserId).roleId(UUID.randomUUID()).build()
        ));
        when(workItemTypeRepository.findByProjectId(projectId)).thenReturn(List.of(
                WorkItemType.builder().id(UUID.randomUUID()).name("Story").code("STORY").hierarchyLevel(0).build()
        ));
        when(projectRoleRepository.findByProjectId(projectId)).thenReturn(List.of(
                ProjectRole.builder().id(UUID.randomUUID()).name("ADMINISTRATOR").build()
        ));

        ProjectDetailResponse detail = projectService.getProjectById(projectId);

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(projectId);
        assertThat(detail.getKey()).isEqualTo("ALPHA");
        assertThat(detail.getWorkItemCount()).isEqualTo(15L);
        assertThat(detail.getMemberCount()).isEqualTo(1L);
        assertThat(detail.getWorkItemTypes()).hasSize(1);
        assertThat(detail.getRoles()).hasSize(1);
    }

    @Test
    @DisplayName("getProjectById: Throws ResourceNotFoundException when not found")
    void testGetProjectByIdNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateProject: Successfully updates name and description")
    void testUpdateProjectSuccess() {
        Project project = Project.builder()
                .id(projectId)
                .workspaceId(workspaceId)
                .name("Old Name")
                .key("OLD")
                .build();

        UpdateProjectRequest req = UpdateProjectRequest.builder()
                .name("New Name")
                .description("New Description")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse updated = projectService.updateProject(projectId, req, currentUserId);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDescription()).isEqualTo("New Description");
    }

    @Test
    @DisplayName("deleteProject & restoreProject: Soft-delete sets and clears deletedAt")
    void testSoftDeleteAndRestore() {
        Project project = Project.builder()
                .id(projectId)
                .workspaceId(workspaceId)
                .name("Project To Delete")
                .key("DEL")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        // Delete
        projectService.deleteProject(projectId, currentUserId);
        assertThat(project.getDeletedAt()).isNotNull();

        // Restore
        ProjectResponse restored = projectService.restoreProject(projectId, currentUserId);
        assertThat(restored.getDeletedAt()).isNull();
    }
}
