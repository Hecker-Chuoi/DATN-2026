package com.datn.workservice.controller;

import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.service.WorkflowEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WorkItemTransitionControllerTest {

    @Mock
    private WorkflowEngineService workflowEngineService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WorkItemTransitionController workItemTransitionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID workItemId;
    private UUID transitionId;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(workItemTransitionController).build();
        objectMapper = new ObjectMapper();

        workItemId = UUID.randomUUID();
        transitionId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();

        // Setup Spring Security context mock
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", currentUserId.toString())
                .claim("user_id", currentUserId.toString())
                .build();

        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getPrincipal()).thenReturn(jwt);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/work/work-items/{id}/transitions: Returns 200 OK with list of available transitions")
    void testGetAvailableTransitions_Success() throws Exception {
        AvailableTransitionResponse transition = AvailableTransitionResponse.builder()
                .transitionId(transitionId)
                .name("Start Progress")
                .toStatusId(UUID.randomUUID())
                .toStatusName("In Progress")
                .toStatusCategory(StatusCategory.IN_PROGRESS)
                .toStatusColorHex("#3B82F6")
                .hasScreen(false)
                .requiredFields(List.of())
                .build();

        when(workflowEngineService.getAvailableTransitions(eq(workItemId), any(UUID.class)))
                .thenReturn(List.of(transition));

        mockMvc.perform(get("/api/v1/work/work-items/{id}/transitions", workItemId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Start Progress"))
                .andExpect(jsonPath("$.data[0].toStatusName").value("In Progress"));

        verify(workflowEngineService, times(1)).getAvailableTransitions(eq(workItemId), any(UUID.class));
    }

    @Test
    @DisplayName("POST /api/v1/work/work-items/{id}/transitions/{transitionId}: Executes transition by path variable")
    void testExecuteTransitionById_Success() throws Exception {
        WorkItem workItem = WorkItem.builder()
                .id(workItemId)
                .code("PRJ-1")
                .summary("Transitioning item")
                .build();

        when(workflowEngineService.executeTransition(eq(workItemId), eq(transitionId), any(ExecuteTransitionRequest.class), eq(currentUserId)))
                .thenReturn(workItem);

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder()
                .resolution("DONE")
                .comment("Moving to completed")
                .build();

        mockMvc.perform(post("/api/v1/work/work-items/{id}/transitions/{transitionId}", workItemId, transitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Chuyển trạng thái công việc thành công"))
                .andExpect(jsonPath("$.data.code").value("PRJ-1"));

        verify(workflowEngineService, times(1)).executeTransition(eq(workItemId), eq(transitionId), any(ExecuteTransitionRequest.class), eq(currentUserId));
    }

    @Test
    @DisplayName("POST /api/v1/work/work-items/{id}/transitions: Executes transition with transitionId in body")
    void testExecuteTransitionByBody_Success() throws Exception {
        WorkItem workItem = WorkItem.builder()
                .id(workItemId)
                .code("PRJ-2")
                .summary("Transitioning item via body")
                .build();

        when(workflowEngineService.executeTransition(eq(workItemId), eq(transitionId), any(ExecuteTransitionRequest.class), eq(currentUserId)))
                .thenReturn(workItem);

        ExecuteTransitionRequest request = ExecuteTransitionRequest.builder()
                .transitionId(transitionId)
                .resolution("DONE")
                .build();

        mockMvc.perform(post("/api/v1/work/work-items/{id}/transitions", workItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("PRJ-2"));

        verify(workflowEngineService, times(1)).executeTransition(eq(workItemId), eq(transitionId), any(ExecuteTransitionRequest.class), eq(currentUserId));
    }
}
