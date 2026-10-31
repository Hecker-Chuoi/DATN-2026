package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.client.GitHubApiClient;
import com.datn.workservice.dto.github.GitHubIssueDto;
import com.datn.workservice.dto.github.GitHubSyncResultResponse;
import com.datn.workservice.dto.github.GitHubUserResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.GitHubLinkType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.*;
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
        when(projectGitHubRepoRepository.findByRepoFullName("datn-team/work-service")).thenReturn(Optional.of(projectRepo));
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

        when(projectGitHubRepoRepository.findByRepoFullName("datn-team/work-service")).thenReturn(Optional.of(projectRepo));
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
        verify(workItemGitHubLinkRepository).save(argThat(link ->
                link.getGithubNumber() == 15 && "OPEN".equals(link.getStatus())
        ));
    }

    @Test
    @DisplayName("handleWebhook: Sự kiện issues.closed cập nhật link sang CLOSED và task sang DONE")
    void testHandleWebhook_IssuesClosed() {
        String payload = """
            {
                "action": "closed",
                "repository": { "full_name": "datn-team/work-service" },
                "issue": {
                    "id": 555,
                    "number": 15,
                    "state": "closed"
                }
            }
            """;

        UUID workItemId = UUID.randomUUID();
        WorkItem existingItem = WorkItem.builder()
                .id(workItemId)
                .projectId(projectId)
                .code("DATN-15")
                .statusId(todoStatus.getId())
                .build();

        WorkItemGitHubLink existingLink = WorkItemGitHubLink.builder()
                .id(UUID.randomUUID())
                .workItemId(workItemId)
                .linkType(GitHubLinkType.ISSUE)
                .githubNumber(15)
                .status("OPEN")
                .build();

        when(projectGitHubRepoRepository.findByRepoFullName("datn-team/work-service")).thenReturn(Optional.of(projectRepo));
        when(gitHubWebhookSignatureValidator.isValidSignature(anyString(), anyString(), eq("secret-xyz"))).thenReturn(true);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(workItemGitHubLinkRepository.findByProjectIdAndLinkTypeAndGithubNumber(projectId, GitHubLinkType.ISSUE, 15))
                .thenReturn(Optional.of(existingLink));
        when(workItemRepository.findById(workItemId)).thenReturn(Optional.of(existingItem));
        when(statusRepository.findByProjectId(projectId)).thenReturn(List.of(todoStatus, doneStatus));

        gitHubIntegrationService.handleWebhook("issues", "valid-sig", payload);

        assertThat(existingLink.getStatus()).isEqualTo("CLOSED");
        assertThat(existingItem.getStatusId()).isEqualTo(doneStatus.getId());
        verify(workItemGitHubLinkRepository).save(existingLink);
        verify(workItemRepository).save(existingItem);
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

        gitHubIntegrationService.syncWorkItemStatusToGitHub(workItemId, StatusCategory.IN_PROGRESS, StatusCategory.DONE);

        verify(gitHubApiClient).updateIssueState("gho_mockToken123", "datn-team", "work-service", 15, "closed");
        assertThat(link.getStatus()).isEqualTo("CLOSED");
        verify(workItemGitHubLinkRepository).save(link);
    }
}
