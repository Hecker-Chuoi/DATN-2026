package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.client.GitHubApiClient;
import com.datn.workservice.dto.github.GitHubIssueDto;
import com.datn.workservice.dto.github.GitHubSyncResultResponse;
import com.datn.workservice.dto.github.GitHubUserResponse;
import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.GitHubLinkType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.*;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.GitHubOAuthStateService;
import com.datn.workservice.security.GitHubWebhookSignatureValidator;
import com.datn.workservice.service.impl.GitHubIntegrationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubIntegrationServiceTest {

    @Mock
    private GitHubApiClient gitHubApiClient;
    @Mock
    private UserGitHubTokenRepository userGitHubTokenRepository;
    @Mock
    private ProjectGitHubRepoRepository projectGitHubRepoRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private WorkItemRepository workItemRepository;
    @Mock
    private WorkItemGitHubLinkRepository workItemGitHubLinkRepository;
    @Mock
    private WorkItemTypeRepository workItemTypeRepository;
    @Mock
    private StatusRepository statusRepository;
    @Mock
    private AtomicKeySequenceGenerator atomicKeySequenceGenerator;
    @Mock
    private LexoRankService lexoRankService;
    @Mock
    private GitHubWebhookSignatureValidator gitHubWebhookSignatureValidator;
    @Mock
    private GitHubOAuthStateService gitHubOAuthStateService;
    @Mock
    private ProjectMemberService projectMemberService;
    @Mock
    private WorkflowEngineService workflowEngineService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GitHubIntegrationServiceImpl gitHubIntegrationService;

    private UUID projectId;
    private UUID currentUserId;
    private Project project;
    private ProjectGitHubRepo projectRepo;
    private UserGitHubToken userToken;
    private Status todoStatus;
    private Status doneStatus;
    private WorkItemType taskType;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));

        projectId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();

        project = Project.builder()
                .id(projectId)
                .name("DATN Core Platform")
                .key("DATN")
                .leadUserId(currentUserId)
                .build();

        projectRepo = ProjectGitHubRepo.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .repoOwner("datn-team")
                .repoName("work-service")
                .repoFullName("datn-team/work-service")
                .repoUrl("https://github.com/datn-team/work-service")
                .webhookSecret("secret-xyz")
                .syncIssuesEnabled(true)
                .autoCloseGithubIssueOnDone(true)
                .build();

        userToken = UserGitHubToken.builder()
                .userId(currentUserId)
                .githubUsername("dev-tester")
                .accessTokenEncrypted("gho_mockToken123")
                .build();

        todoStatus = Status.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name("To Do")
                .category(StatusCategory.TODO)
                .build();

        doneStatus = Status.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name("Done")
                .category(StatusCategory.DONE)
                .build();

        taskType = WorkItemType.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name("Task")
                .code("TASK")
                .hierarchyLevel(0)
                .build();
    }

    @Test
    @DisplayName("syncIssues: Kéo Issues thành công và lưu WorkItems kèm liên kết GitHub")
    void testSyncIssues_Success() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectGitHubRepoRepository.findByProjectId(projectId)).thenReturn(Optional.of(projectRepo));
        when(userGitHubTokenRepository.findByUserId(currentUserId)).thenReturn(Optional.of(userToken));

        GitHubIssueDto issue1 = GitHubIssueDto.builder()
                .id(101L)
                .number(1)
                .title("Issue 1: Fix bug login")
                .body("Login returns 500")
                .state("open")
                .htmlUrl("https://github.com/datn-team/work-service/issues/1")
                .user(GitHubUserResponse.builder().login("octocat").build())
                .build();

        GitHubIssueDto issue2 = GitHubIssueDto.builder()
                .id(102L)
                .number(2)
                .title("Issue 2: Add avatar upload")
                .body("Upload avatar to S3")
                .state("closed")
                .htmlUrl("https://github.com/datn-team/work-service/issues/2")
                .user(GitHubUserResponse.builder().login("octocat").build())
                .build();

        when(gitHubApiClient.getRepositoryIssues(eq("gho_mockToken123"), eq("datn-team"), eq("work-service"), eq("all")))
                .thenReturn(List.of(issue1, issue2));

        when(workItemTypeRepository.findByProjectIdOrProjectIdIsNull(projectId)).thenReturn(List.of(taskType));
        when(statusRepository.findByProjectId(projectId)).thenReturn(List.of(todoStatus, doneStatus));
        when(atomicKeySequenceGenerator.generateNextCode("DATN")).thenReturn("DATN-1", "DATN-2");
        when(lexoRankService.calculateInitialRank()).thenReturn("0|i00000:");

        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(invocation -> {
            WorkItem wi = invocation.getArgument(0);
            wi.setId(UUID.randomUUID());
            return wi;
        });

        GitHubSyncResultResponse result = gitHubIntegrationService.syncIssues(projectId, currentUserId);

        assertThat(result.getTotalFound()).isEqualTo(2);
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getUpdatedCount()).isEqualTo(0);
        assertThat(result.getLastSyncedAt()).isNotNull();

        verify(workItemRepository, times(2)).save(any(WorkItem.class));
        verify(workItemGitHubLinkRepository, times(2)).save(any(WorkItemGitHubLink.class));
        verify(projectGitHubRepoRepository).save(projectRepo);
    }

    @Test
    @DisplayName("handleWebhook: Sự kiện ping trả về 200 mà không lỗi")
    void testHandleWebhook_Ping() {
        String payload = "{\"hook_id\":12345,\"zen\":\"Design for failure.\"}";
        gitHubIntegrationService.handleWebhook("ping", "any-sig", payload);
    }

    @Test
    @DisplayName("handleWebhook: Chữ ký không hợp lệ quăng BadRequestException")
    void testHandleWebhook_InvalidSignature() {
        String payload = "{\"repository\":{\"full_name\":\"datn-team/work-service\"}}";
        when(projectGitHubRepoRepository.findAllByRepoFullNameIgnoreCase("datn-team/work-service")).thenReturn(List.of(projectRepo));
        when(gitHubWebhookSignatureValidator.isValidSignature(anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> gitHubIntegrationService.handleWebhook("issues", "invalid-sig", payload))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chữ ký Webhook GitHub không hợp lệ");
    }

    @Test
    @DisplayName("handleWebhook: Sự kiện issues.opened tạo WorkItem và Link mới")
    void testHandleWebhook_IssuesOpened() {
        String payload = """
            {
                "action": "opened",
                "repository": { "full_name": "datn-team/work-service" },
                "issue": {
                    "id": 555,
                    "number": 15,
                    "title": "OAuth token expires too soon",
                    "body": "Token TTL should be 1 hour",
                    "state": "open",
                    "html_url": "https://github.com/datn-team/work-service/issues/15",
                    "user": { "login": "dev1", "avatar_url": "https://avatar.com/1" }
                }
            }
            """;

        when(projectGitHubRepoRepository.findAllByRepoFullNameIgnoreCase("datn-team/work-service")).thenReturn(List.of(projectRepo));
        when(gitHubWebhookSignatureValidator.isValidSignature(anyString(), anyString(), eq("secret-xyz"))).thenReturn(true);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(workItemGitHubLinkRepository.findByProjectIdAndLinkTypeAndGithubNumber(projectId, GitHubLinkType.ISSUE, 15))
                .thenReturn(Optional.empty());

        when(workItemTypeRepository.findByProjectIdOrProjectIdIsNull(projectId)).thenReturn(List.of(taskType));
        when(statusRepository.findByProjectId(projectId)).thenReturn(List.of(todoStatus));
        when(atomicKeySequenceGenerator.generateNextCode("DATN")).thenReturn("DATN-15");
        when(lexoRankService.calculateInitialRank()).thenReturn("0|i00000:");

        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> {
            WorkItem wi = inv.getArgument(0);
            wi.setId(UUID.randomUUID());
            return wi;
        });

        gitHubIntegrationService.handleWebhook("issues", "valid-sig", payload);

        verify(workItemRepository).save(any(WorkItem.class));
        verify(workItemGitHubLinkRepository).saveAndFlush(argThat(link ->
                link.getGithubNumber() == 15 && "OPEN".equals(link.getStatus()) && projectId.equals(link.getProjectId())
        ));
    }

    private static final String CLOSED_PAYLOAD = """
            {
                "action": "closed",
                "repository": { "full_name": "datn-team/work-service" },
                "sender": { "id": 999, "login": "dev1" },
                "issue": {
                    "id": 555,
                    "number": 15,
                    "state": "closed"
                }
            }
            """;

    private WorkItemGitHubLink stubLinkedIssue15(UUID workItemId) {
        WorkItemGitHubLink existingLink = WorkItemGitHubLink.builder()
                .id(UUID.randomUUID())
                .workItemId(workItemId)
                .projectId(projectId)
                .linkType(GitHubLinkType.ISSUE)
                .githubNumber(15)
                .status("OPEN")
                .build();
        when(projectGitHubRepoRepository.findAllByRepoFullNameIgnoreCase("datn-team/work-service")).thenReturn(List.of(projectRepo));
        when(gitHubWebhookSignatureValidator.isValidSignature(anyString(), anyString(), eq("secret-xyz"))).thenReturn(true);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(workItemGitHubLinkRepository.findByProjectIdAndLinkTypeAndGithubNumber(projectId, GitHubLinkType.ISSUE, 15))
                .thenReturn(Optional.of(existingLink));
        return existingLink;
    }

    @Test
    @DisplayName("handleWebhook: issues.closed chuyển Task qua WorkflowEngine với tư cách người đóng Issue (qua cổng Tạm hoàn thành)")
    void testHandleWebhook_IssuesClosed_GoesThroughWorkflow() {
        UUID workItemId = UUID.randomUUID();
        WorkItem existingItem = WorkItem.builder()
                .id(workItemId)
                .projectId(projectId)
                .code("DATN-15")
                .statusId(todoStatus.getId())
                .build();
        WorkItemGitHubLink existingLink = stubLinkedIssue15(workItemId);

        UUID actorId = UUID.randomUUID();
        UUID doneTransitionId = UUID.randomUUID();
        when(userGitHubTokenRepository.findFirstByGithubUserId(999L))
                .thenReturn(Optional.of(UserGitHubToken.builder().userId(actorId).accessTokenEncrypted("x").build()));
        when(projectMemberService.hasPermission(projectId, actorId, PermissionKey.ISSUE_TRANSITION)).thenReturn(true);
        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(existingItem));
        when(statusRepository.findById(todoStatus.getId())).thenReturn(Optional.of(todoStatus));
        when(workflowEngineService.getAvailableTransitions(workItemId, actorId)).thenReturn(List.of(
                AvailableTransitionResponse.builder().transitionId(UUID.randomUUID()).toStatusCategory(StatusCategory.IN_PROGRESS).build(),
                AvailableTransitionResponse.builder().transitionId(doneTransitionId).toStatusCategory(StatusCategory.DONE).build()
        ));
        when(workflowEngineService.executeTransition(eq(workItemId), eq(doneTransitionId), any(ExecuteTransitionRequest.class), eq(actorId)))
                .thenReturn(existingItem);

        gitHubIntegrationService.handleWebhook("issues", "valid-sig", CLOSED_PAYLOAD);

        assertThat(existingLink.getStatus()).isEqualTo("CLOSED");
        verify(workItemGitHubLinkRepository).save(existingLink);
        verify(workflowEngineService).executeTransition(eq(workItemId), eq(doneTransitionId),
                argThat(r -> "DONE".equals(r.getResolution())), eq(actorId));
        // Không gán thẳng statusId nữa
        verify(workItemRepository, never()).save(any(WorkItem.class));
    }

    @Test
    @DisplayName("handleWebhook: issues.closed bởi người chưa liên kết DATN chỉ cập nhật liên kết, không đổi Task")
    void testHandleWebhook_IssuesClosed_UnknownSender() {
        WorkItemGitHubLink existingLink = stubLinkedIssue15(UUID.randomUUID());
        when(userGitHubTokenRepository.findFirstByGithubUserId(999L)).thenReturn(Optional.empty());

        gitHubIntegrationService.handleWebhook("issues", "valid-sig", CLOSED_PAYLOAD);

        assertThat(existingLink.getStatus()).isEqualTo("CLOSED");
        verifyNoInteractions(workflowEngineService);
    }

    @Test
    @DisplayName("handleWebhook: tắt cờ autoCloseTaskOnIssueClose thì không đổi Task")
    void testHandleWebhook_IssuesClosed_FlagDisabled() {
        projectRepo.setAutoCloseTaskOnIssueClose(false);
        WorkItemGitHubLink existingLink = stubLinkedIssue15(UUID.randomUUID());

        gitHubIntegrationService.handleWebhook("issues", "valid-sig", CLOSED_PAYLOAD);

        assertThat(existingLink.getStatus()).isEqualTo("CLOSED");
        verifyNoInteractions(workflowEngineService);
        verify(userGitHubTokenRepository, never()).findFirstByGithubUserId(any());
    }

    @Test
    @DisplayName("handleWebhook: repo nối vào 2 dự án -> chỉ xử lý dự án có secret khớp chữ ký")
    void testHandleWebhook_MultipleProjects_OnlyVerifiedProcessed() {
        ProjectGitHubRepo otherRepo = ProjectGitHubRepo.builder()
                .projectId(UUID.randomUUID())
                .repoFullName("Datn-Team/Work-Service")
                .webhookSecret("other-secret")
                .syncIssuesEnabled(true)
                .build();
        when(projectGitHubRepoRepository.findAllByRepoFullNameIgnoreCase("datn-team/work-service"))
                .thenReturn(List.of(otherRepo, projectRepo));
        when(gitHubWebhookSignatureValidator.isValidSignature(anyString(), anyString(), eq("other-secret"))).thenReturn(false);
        when(gitHubWebhookSignatureValidator.isValidSignature(anyString(), anyString(), eq("secret-xyz"))).thenReturn(true);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(workItemGitHubLinkRepository.findByProjectIdAndLinkTypeAndGithubNumber(projectId, GitHubLinkType.ISSUE, 15))
                .thenReturn(Optional.empty());

        String payload = """
            { "action": "deleted", "repository": { "full_name": "datn-team/work-service" }, "issue": { "number": 15 } }
            """;
        gitHubIntegrationService.handleWebhook("issues", "sig", payload);

        verify(projectRepository).findById(projectId);
        verify(projectRepository, never()).findById(otherRepo.getProjectId());
    }

    @Test
    @DisplayName("handleWebhook: repo chưa có secret (kết nối trước V13) bị từ chối")
    void testHandleWebhook_NoSecret_Rejected() {
        projectRepo.setWebhookSecret(null);
        when(projectGitHubRepoRepository.findAllByRepoFullNameIgnoreCase("datn-team/work-service")).thenReturn(List.of(projectRepo));

        assertThatThrownBy(() -> gitHubIntegrationService.handleWebhook("issues", "sig", CLOSED_PAYLOAD))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(gitHubWebhookSignatureValidator);
    }

    @Test
    @DisplayName("syncIssues: không dùng token của người ngoài dự án khi người gọi/Lead chưa kết nối GitHub")
    void testSyncIssues_NoProjectToken_Throws() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectGitHubRepoRepository.findByProjectId(projectId)).thenReturn(Optional.of(projectRepo));
        when(userGitHubTokenRepository.findByUserId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gitHubIntegrationService.syncIssues(projectId, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("syncWorkItemStatusToGitHub: Task chuyển sang DONE -> gọi API đóng Issue trên GitHub")
    void testSyncWorkItemStatusToGitHub_DoneClosesIssue() {
        UUID workItemId = UUID.randomUUID();
        WorkItem item = WorkItem.builder()
                .id(workItemId)
                .projectId(projectId)
                .code("DATN-15")
                .build();

        WorkItemGitHubLink link = WorkItemGitHubLink.builder()
                .id(UUID.randomUUID())
                .workItemId(workItemId)
                .linkType(GitHubLinkType.ISSUE)
                .githubNumber(15)
                .status("OPEN")
                .build();

        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(item));
        when(projectGitHubRepoRepository.findByProjectId(projectId)).thenReturn(Optional.of(projectRepo));
        when(workItemGitHubLinkRepository.findByWorkItemIdOrderByCreatedAtDesc(workItemId)).thenReturn(List.of(link));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userGitHubTokenRepository.findByUserId(currentUserId)).thenReturn(Optional.of(userToken));
        when(gitHubApiClient.updateIssueState("gho_mockToken123", "datn-team", "work-service", 15, "closed")).thenReturn(true);

        gitHubIntegrationService.syncWorkItemStatusToGitHub(workItemId, StatusCategory.IN_PROGRESS, StatusCategory.DONE, currentUserId);

        verify(gitHubApiClient).updateIssueState("gho_mockToken123", "datn-team", "work-service", 15, "closed");
        assertThat(link.getStatus()).isEqualTo("CLOSED");
        verify(workItemGitHubLinkRepository).save(link);
    }
}
