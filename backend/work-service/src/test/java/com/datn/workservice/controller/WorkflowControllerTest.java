package com.datn.workservice.controller;

import com.datn.workservice.dto.request.CreateStatusRequest;
import com.datn.workservice.dto.request.CreateWorkflowTransitionRequest;
import com.datn.workservice.dto.response.*;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private WorkflowController workflowController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID projectId;
    private UUID workflowId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(workflowController).build();
        objectMapper = new ObjectMapper();
        projectId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
    }

    @Test
    @DisplayName("GET /api/v1/work/projects/{projectId}/workflow: Returns 200 OK with workflow detail")
    void testGetProjectWorkflowDetail_Success() throws Exception {
        WorkflowDetailResponse detail = WorkflowDetailResponse.builder()
                .id(workflowId)
                .projectId(projectId)
                .name("Scrum Workflow")
                .transitions(List.of())
                .statuses(List.of())
                .build();

        when(workflowService.getProjectWorkflowDetail(projectId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/work/projects/{projectId}/workflow", projectId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Scrum Workflow"));

        verify(workflowService, times(1)).getProjectWorkflowDetail(projectId);
    }

    @Test
    @DisplayName("GET /api/v1/work/projects/{projectId}/statuses: Returns list of project statuses")
    void testGetProjectStatuses_Success() throws Exception {
        StatusResponse status = StatusResponse.builder()
                .id(UUID.randomUUID())
                .name("To Do")
                .category(StatusCategory.TODO)
                .colorHex("#94A3B8")
                .build();

        when(workflowService.getProjectStatuses(projectId)).thenReturn(List.of(status));

        mockMvc.perform(get("/api/v1/work/projects/{projectId}/statuses", projectId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("To Do"));

        verify(workflowService, times(1)).getProjectStatuses(projectId);
    }

    @Test
    @DisplayName("POST /api/v1/work/statuses: Creates new status and returns 201 Created")
    void testCreateStatus_Success() throws Exception {
        CreateStatusRequest request = CreateStatusRequest.builder()
                .name("In Review")
                .category(StatusCategory.IN_PROGRESS)
                .colorHex("#3B82F6")
                .build();

        StatusResponse response = StatusResponse.builder()
                .id(UUID.randomUUID())
                .name("In Review")
                .category(StatusCategory.IN_PROGRESS)
                .colorHex("#3B82F6")
                .build();

        when(workflowService.createStatus(any(CreateStatusRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/work/statuses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("In Review"));

        verify(workflowService, times(1)).createStatus(any(CreateStatusRequest.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/work/workflows/{workflowId}/transitions/{transitionId}: Deletes transition")
    void testDeleteTransition_Success() throws Exception {
        UUID transitionId = UUID.randomUUID();
        doNothing().when(workflowService).deleteTransition(workflowId, transitionId);

        mockMvc.perform(delete("/api/v1/work/workflows/{workflowId}/transitions/{transitionId}", workflowId, transitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(workflowService, times(1)).deleteTransition(workflowId, transitionId);
    }
}
