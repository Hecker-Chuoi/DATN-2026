package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.dto.request.CreateWorkItemTypeRequest;
import com.datn.workservice.dto.request.UpdateWorkItemTypeRequest;
import com.datn.workservice.dto.response.WorkItemTypeResponse;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.WorkItemType;
import com.datn.workservice.entity.Workflow;
import com.datn.workservice.entity.WorkflowScheme;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.impl.WorkItemTypeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkItemTypeServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkItemTypeRepository workItemTypeRepository;

    @Mock
    private WorkItemRepository workItemRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private WorkflowSchemeRepository workflowSchemeRepository;

    @Mock
    private WorkflowSchemeMappingRepository workflowSchemeMappingRepository;

    @InjectMocks
    private WorkItemTypeServiceImpl workItemTypeService;

    private UUID projectId;
    private UUID currentUserId;
    private Project mockProject;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        mockProject = Project.builder()
                .id(projectId)
                .name("Test Project")
                .key("TEST")
                .build();
    }

    @Test
    @DisplayName("createWorkItemType: Tạo mới thành công và tự động gán vào WorkflowScheme")
    void testCreateWorkItemType_Success() {
        CreateWorkItemTypeRequest request = CreateWorkItemTypeRequest.builder()
                .name("Story")
                .code("STORY")
                .description("User story")
                .hierarchyLevel(0)
                .iconName("Bookmark")
                .colorHex("#10B981")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(mockProject));
        when(workItemTypeRepository.findByProjectIdAndName(projectId, "Story")).thenReturn(Optional.empty());
        when(workItemTypeRepository.findByProjectIdAndCode(projectId, "STORY")).thenReturn(Optional.empty());

        WorkItemType savedType = WorkItemType.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name("Story")
                .code("STORY")
                .description("User story")
                .hierarchyLevel(0)
                .isSubtask(false)
                .build();

        when(workItemTypeRepository.save(any(WorkItemType.class))).thenReturn(savedType);

        WorkflowScheme scheme = WorkflowScheme.builder().id(UUID.randomUUID()).projectId(projectId).build();
        Workflow workflow = Workflow.builder().id(UUID.randomUUID()).projectId(projectId).isDefault(true).build();
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(List.of(scheme));
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.of(workflow));

        WorkItemTypeResponse response = workItemTypeService.createWorkItemType(projectId, request, currentUserId);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("STORY");
        assertThat(response.getHierarchyLevel()).isEqualTo(0);
        verify(workflowSchemeMappingRepository).save(any());
    }

    @Test
    @DisplayName("deleteWorkItemType: Ném lỗi nếu xóa làm số lượng một Level giảm xuống 0 (Bảo toàn tối thiểu 1 per Level)")
    void testDeleteWorkItemType_MinimumLevelGuard_ThrowsBadRequest() {
        UUID typeId = UUID.randomUUID();
        WorkItemType epicType = WorkItemType.builder()
                .id(typeId)
                .projectId(projectId)
                .name("Epic")
                .code("EPIC")
                .hierarchyLevel(1)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(mockProject));
        when(workItemTypeRepository.findById(typeId)).thenReturn(Optional.of(epicType));
        // Dự án chỉ có đúng 1 loại level 1
        when(workItemTypeRepository.findByProjectId(projectId)).thenReturn(List.of(epicType));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> workItemTypeService.deleteWorkItemType(projectId, typeId, currentUserId));

        assertThat(ex.getMessage()).contains("Dự án luôn phải duy trì tối thiểu 1 loại công việc cho Level 1");
    }

    @Test
    @DisplayName("deleteWorkItemType: Ném lỗi nếu đang có WorkItems sử dụng loại công việc này")
    void testDeleteWorkItemType_InUse_ThrowsBadRequest() {
        UUID typeId = UUID.randomUUID();
        WorkItemType taskType = WorkItemType.builder()
                .id(typeId)
                .projectId(projectId)
                .name("Task 1")
                .code("TASK1")
                .hierarchyLevel(0)
                .build();

        WorkItemType otherTask = WorkItemType.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name("Task 2")
                .code("TASK2")
                .hierarchyLevel(0)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(mockProject));
        when(workItemTypeRepository.findById(typeId)).thenReturn(Optional.of(taskType));
        when(workItemTypeRepository.findByProjectId(projectId)).thenReturn(List.of(taskType, otherTask));
        // Có 5 items đang dùng loại này
        when(workItemRepository.countByTypeId(typeId)).thenReturn(5L);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> workItemTypeService.deleteWorkItemType(projectId, typeId, currentUserId));

        assertThat(ex.getMessage()).contains("đang có 5 công việc đang sử dụng");
    }

    @Test
    @DisplayName("deleteWorkItemType: Xóa thành công khi còn loại khác cùng level và không có item nào sử dụng")
    void testDeleteWorkItemType_Success() {
        UUID typeId = UUID.randomUUID();
        WorkItemType taskType = WorkItemType.builder()
                .id(typeId)
                .projectId(projectId)
                .name("Task 1")
                .code("TASK1")
                .hierarchyLevel(0)
                .build();

        WorkItemType otherTask = WorkItemType.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name("Task 2")
                .code("TASK2")
                .hierarchyLevel(0)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(mockProject));
        when(workItemTypeRepository.findById(typeId)).thenReturn(Optional.of(taskType));
        when(workItemTypeRepository.findByProjectId(projectId)).thenReturn(List.of(taskType, otherTask));
        when(workItemRepository.countByTypeId(typeId)).thenReturn(0L);

        workItemTypeService.deleteWorkItemType(projectId, typeId, currentUserId);

        verify(workflowSchemeMappingRepository).deleteByWorkItemTypeId(typeId);
        verify(workItemTypeRepository).delete(taskType);
    }
}
