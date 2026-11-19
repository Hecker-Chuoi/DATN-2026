package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.client.GitHubApiClient;
import com.datn.workservice.dto.github.ConnectRepoRequest;
import com.datn.workservice.dto.github.GitHubBranchDto;
import com.datn.workservice.dto.github.GitHubIssueDto;
import com.datn.workservice.dto.github.GitHubRepoDto;
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

    private WorkItem stubPrTask(String code, Status status) {
        WorkItem item = WorkItem.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .code(code)
                .statusId(status.getId())
                .build();
        when(projectGitHubRepoRepository.findAllByRepoFullNameIgnoreCase("datn-team/work-service")).thenReturn(List.of(projectRepo));
        when(gitHubWebhookSignatureValidator.isValidSignature(anyString(), anyString(), eq("secret-xyz"))).thenReturn(true);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        lenient().when(workItemRepository.findByProjectIdAndCode(projectId, code)).thenReturn(Optional.of(item));
        lenient().when(workItemGitHubLinkRepository.findByWorkItemIdAndLinkTypeAndGithubNumber(any(), eq(GitHubLinkType.PULL_REQUEST), anyInt()))
                .thenReturn(Optional.empty());
        return item;
    }

    private static String prPayload(String action, boolean merged, boolean draft, String branch, String mergedBy) {
        return """
            {
              "action": "%s",
              "repository": { "full_name": "datn-team/work-service" },
              "sender": { "id": 999 },
              "pull_request": {
                "id": 7001, "number": 42, "title": "Thêm đăng nhập OAuth",
                "html_url": "https://github.com/datn-team/work-service/pull/42",
                "merged": %s, "draft": %s,
                "head": { "ref": "%s" },
                "base": { "ref": "main" },
                "user": { "login": "dev1" },
                "merged_by": %s
              }
            }
            """.formatted(action, merged, draft, branch, mergedBy);
    }

    // ─── Khung "Phát triển" ───

    private WorkItem stubDevTask(String code, String summary) {
        WorkItem item = WorkItem.builder().id(UUID.randomUUID()).projectId(projectId).code(code).summary(summary).build();
        when(workItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        return item;
    }

    private void stubDevRepo(String branch) {
        projectRepo.setDefaultBranch(branch);
        when(projectGitHubRepoRepository.findByProjectId(projectId)).thenReturn(Optional.of(projectRepo));
    }

    @Test
    @DisplayName("tên nhánh gợi ý: bỏ dấu tiếng Việt, chữ thường, cắt 40 ký tự, không đuôi '-'")
    void testSuggestBranchName() {
        assertThat(GitHubIntegrationServiceImpl.suggestBranchName("FRONE-3", "Đăng nhập Google!")).isEqualTo("feat/FRONE-3-dang-nhap-google");
        assertThat(GitHubIntegrationServiceImpl.suggestBranchName("frone-3", "  ")).isEqualTo("feat/FRONE-3");
        assertThat(GitHubIntegrationServiceImpl.suggestBranchName("FRONE-3", null)).isEqualTo("feat/FRONE-3");
        String longName = GitHubIntegrationServiceImpl.suggestBranchName("FRONE-3",
                "Cấu hình hệ thống điều hướng cho toàn bộ ứng dụng web quản trị");
        assertThat(longName).startsWith("feat/FRONE-3-cau-hinh-he-thong").doesNotEndWith("-");
        assertThat(longName.length()).isLessThanOrEqualTo("feat/FRONE-3-".length() + 40);
    }

    @Test
    @DisplayName("development: dự án chưa liên kết repo thì linked=false")
    void testDevelopmentInfo_NotLinked() {
        WorkItem item = stubDevTask("DATN-15", "Đăng nhập");
        when(projectGitHubRepoRepository.findByProjectId(projectId)).thenReturn(Optional.empty());

        assertThat(gitHubIntegrationService.getDevelopmentInfo(item.getId()).isLinked()).isFalse();
    }

    @Test
    @DisplayName("development: chưa có nhánh thì gợi ý tên, link PR dùng nhánh đích và từ khoá tự đặt")
    void testDevelopmentInfo_SuggestsBranchAndPrLinks() {
        WorkItem item = stubDevTask("DATN-15", "Đăng nhập Google");
        stubDevRepo("develop");
        projectRepo.setPrCloseKeywords("final, done");
        when(workItemGitHubLinkRepository.findByWorkItemIdOrderByCreatedAtDesc(item.getId())).thenReturn(List.of());

        var info = gitHubIntegrationService.getDevelopmentInfo(item.getId());

        assertThat(info.isLinked()).isTrue();
        assertThat(info.isBranchCreated()).isFalse();
        assertThat(info.getBranchName()).isEqualTo("feat/DATN-15-dang-nhap-google");
        assertThat(info.getPrTitle()).isEqualTo("DATN-15 Đăng nhập Google");
        assertThat(info.getClosePrTitle()).isEqualTo("final DATN-15 Đăng nhập Google");
        assertThat(info.getCompareUrl()).startsWith(
                "https://github.com/datn-team/work-service/compare/develop...feat/DATN-15-dang-nhap-google?expand=1&title=DATN-15%20");
        assertThat(info.getCheckoutCommand()).isEqualTo("git fetch origin && git checkout feat/DATN-15-dang-nhap-google");
    }

    @Test
    @DisplayName("tạo nhánh: tách từ nhánh đích, lưu link BRANCH")
    void testCreateBranch_CreatesFromBaseAndSavesLink() {
        WorkItem item = stubDevTask("DATN-15", "Đăng nhập");
        stubDevRepo("develop");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userGitHubTokenRepository.findByUserId(currentUserId)).thenReturn(Optional.of(userToken));
        when(workItemGitHubLinkRepository.findByWorkItemIdOrderByCreatedAtDesc(item.getId())).thenReturn(List.of());
        when(gitHubApiClient.createBranch("gho_mockToken123", "datn-team", "work-service", "feat/DATN-15-dang-nhap", "develop"))
                .thenReturn(true);

        var info = gitHubIntegrationService.createBranch(item.getId(), currentUserId);

        assertThat(info.isBranchCreated()).isTrue();
        verify(workItemGitHubLinkRepository).save(argThat(l -> l.getLinkType() == GitHubLinkType.BRANCH
                && "feat/DATN-15-dang-nhap".equals(l.getGithubId())
                && "https://github.com/datn-team/work-service/tree/feat/DATN-15-dang-nhap".equals(l.getUrl())));
    }

    @Test
    @DisplayName("tạo nhánh: nhánh đã có trên GitHub vẫn gắn link")
    void testCreateBranch_AlreadyExists_StillLinks() {
        WorkItem item = stubDevTask("DATN-15", "Đăng nhập");
        stubDevRepo("main");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userGitHubTokenRepository.findByUserId(currentUserId)).thenReturn(Optional.of(userToken));
        when(workItemGitHubLinkRepository.findByWorkItemIdOrderByCreatedAtDesc(item.getId())).thenReturn(List.of());
        when(gitHubApiClient.createBranch(any(), any(), any(), any(), any())).thenReturn(false);

        gitHubIntegrationService.createBranch(item.getId(), currentUserId);

        verify(workItemGitHubLinkRepository).save(argThat(l -> l.getLinkType() == GitHubLinkType.BRANCH));
    }

    @Test
    @DisplayName("tạo nhánh: Task đã gắn nhánh thì tạo lại đúng nhánh đó, không lưu link trùng")
    void testCreateBranch_ExistingLink_NoDuplicate() {
        WorkItem item = stubDevTask("DATN-15", "Tên mới khác");
        stubDevRepo("main");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userGitHubTokenRepository.findByUserId(currentUserId)).thenReturn(Optional.of(userToken));
        WorkItemGitHubLink link = WorkItemGitHubLink.builder().workItemId(item.getId())
                .linkType(GitHubLinkType.BRANCH).githubId("feat/DATN-15-cu").build();
        when(workItemGitHubLinkRepository.findByWorkItemIdOrderByCreatedAtDesc(item.getId())).thenReturn(List.of(link));
        when(gitHubApiClient.createBranch(any(), any(), any(), eq("feat/DATN-15-cu"), eq("main"))).thenReturn(true);

        var info = gitHubIntegrationService.createBranch(item.getId(), currentUserId);

        assertThat(info.getBranchName()).isEqualTo("feat/DATN-15-cu");
        verify(workItemGitHubLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("tạo nhánh: không ai trong dự án có token GitHub → báo lỗi, không gọi GitHub")
    void testCreateBranch_NoToken() {
        WorkItem item = stubDevTask("DATN-15", "Đăng nhập");
        stubDevRepo("main");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userGitHubTokenRepository.findByUserId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gitHubIntegrationService.createBranch(item.getId(), currentUserId))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("tạo nhánh: dự án chưa liên kết repo → báo lỗi")
    void testCreateBranch_NotLinked() {
        WorkItem item = stubDevTask("DATN-15", "Đăng nhập");
        when(projectGitHubRepoRepository.findByProjectId(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gitHubIntegrationService.createBranch(item.getId(), currentUserId))
                .isInstanceOf(BadRequestException.class);
    }

    // ─── Tạo Issue từ Task ───

    @Test
    @DisplayName("tạo Issue: gửi tiêu đề + mô tả có dấu ẩn datn-task, lưu liên kết ISSUE")
    void testCreateIssue_CreatesAndLinks() {
        WorkItem item = stubDevTask("DATN-15", "Đăng nhập Google");
        item.setDescription("Dùng OAuth2");
        stubDevRepo("main");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userGitHubTokenRepository.findByUserId(currentUserId)).thenReturn(Optional.of(userToken));
        when(workItemGitHubLinkRepository.findByWorkItemIdOrderByCreatedAtDesc(item.getId())).thenReturn(List.of());
        GitHubIssueDto created = new GitHubIssueDto();
        created.setId(99L);
        created.setNumber(7);
        created.setTitle("Đăng nhập Google");
        created.setHtmlUrl("https://github.com/datn-team/work-service/issues/7");
        when(gitHubApiClient.createIssue(eq("gho_mockToken123"), eq("datn-team"), eq("work-service"), eq("Đăng nhập Google"),
                argThat(b -> b.startsWith("Dùng OAuth2") && b.contains("<!-- datn-task:" + item.getId() + " -->"))))
                .thenReturn(created);
        when(workItemGitHubLinkRepository.findByProjectIdAndLinkTypeAndGithubNumber(projectId, GitHubLinkType.ISSUE, 7))
                .thenReturn(Optional.empty());

        gitHubIntegrationService.createIssue(item.getId(), currentUserId);

        verify(workItemGitHubLinkRepository).saveAndFlush(argThat(l -> l.getLinkType() == GitHubLinkType.ISSUE
                && l.getGithubNumber() == 7 && item.getId().equals(l.getWorkItemId())));
    }

    @Test
    @DisplayName("tạo Issue: Task đã có Issue liên kết thì báo lỗi, không gọi GitHub")
    void testCreateIssue_AlreadyLinked() {
        WorkItem item = stubDevTask("DATN-15", "Đăng nhập");
        stubDevRepo("main");
        when(workItemGitHubLinkRepository.findByWorkItemIdOrderByCreatedAtDesc(item.getId())).thenReturn(List.of(
                WorkItemGitHubLink.builder().linkType(GitHubLinkType.ISSUE).githubNumber(3).build()));

        assertThatThrownBy(() -> gitHubIntegrationService.createIssue(item.getId(), currentUserId))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("webhook issues.opened có dấu datn-task: gắn vào Task gốc, không tạo Task mới")
    void testIssueOpened_WithDatnMarker_LinksExistingTask() {
        UUID originId = UUID.randomUUID();
        WorkItem origin = WorkItem.builder().id(originId).projectId(projectId).code("DATN-15").build();
        when(projectGitHubRepoRepository.findAllByRepoFullNameIgnoreCase("datn-team/work-service")).thenReturn(List.of(projectRepo));
        when(gitHubWebhookSignatureValidator.isValidSignature(anyString(), anyString(), eq("secret-xyz"))).thenReturn(true);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(workItemGitHubLinkRepository.findByProjectIdAndLinkTypeAndGithubNumber(projectId, GitHubLinkType.ISSUE, 7))
                .thenReturn(Optional.empty());
        when(workItemRepository.findById(originId)).thenReturn(Optional.of(origin));

        String payload = "{ \"action\": \"opened\", \"repository\": { \"full_name\": \"datn-team/work-service\" },"
                + " \"issue\": { \"id\": 99, \"number\": 7, \"title\": \"Đăng nhập\", \"state\": \"open\","
                + " \"html_url\": \"https://github.com/datn-team/work-service/issues/7\","
                + " \"body\": \"---\\nTạo từ DATN · DATN-15\\n<!-- datn-task:" + originId + " -->\" } }";
        gitHubIntegrationService.handleWebhook("issues", "sig", payload);

        verify(workItemGitHubLinkRepository).saveAndFlush(argThat(l -> originId.equals(l.getWorkItemId()) && l.getGithubNumber() == 7));
        verify(workItemRepository, never()).save(any());
    }

    private void mapGithubUser(long githubId, UUID userId) {
        when(userGitHubTokenRepository.findFirstByGithubUserId(githubId))
                .thenReturn(Optional.of(UserGitHubToken.builder().userId(userId).accessTokenEncrypted("x").build()));
        when(projectMemberService.hasPermission(projectId, userId, PermissionKey.ISSUE_TRANSITION)).thenReturn(true);
    }

    @Test
    @DisplayName("pull_request.opened: nhánh feat/datn-15-... gắn PR vào DATN-15 và chuyển Task sang trạng thái Review")
    void testPullRequestOpened_MovesToReview() {
        WorkItem item = stubPrTask("DATN-15", todoStatus);
        UUID actorId = UUID.randomUUID();
        mapGithubUser(999L, actorId);
        when(workItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(statusRepository.findById(todoStatus.getId())).thenReturn(Optional.of(todoStatus));
        UUID reviewTransition = UUID.randomUUID();
        when(workflowEngineService.getAvailableTransitions(item.getId(), actorId)).thenReturn(List.of(
                AvailableTransitionResponse.builder().transitionId(UUID.randomUUID()).toStatusName("In Progress").toStatusCategory(StatusCategory.IN_PROGRESS).build(),
                AvailableTransitionResponse.builder().transitionId(reviewTransition).toStatusName("Code Review").toStatusCategory(StatusCategory.IN_PROGRESS).build(),
                AvailableTransitionResponse.builder().transitionId(UUID.randomUUID()).toStatusName("Done").toStatusCategory(StatusCategory.DONE).build()
        ));
        when(workflowEngineService.executeTransition(eq(item.getId()), eq(reviewTransition), any(), eq(actorId))).thenReturn(item);

        gitHubIntegrationService.handleWebhook("pull_request", "sig",
                prPayload("opened", false, false, "feat/datn-15-oauth", "null"));

        verify(workItemGitHubLinkRepository).save(argThat(l -> l.getLinkType() == GitHubLinkType.PULL_REQUEST
                && l.getGithubNumber() == 42 && "OPEN".equals(l.getStatus()) && projectId.equals(l.getProjectId())));
        verify(workflowEngineService).executeTransition(eq(item.getId()), eq(reviewTransition), any(), eq(actorId));
    }

    @Test
    @DisplayName("pull_request.closed + merged: hoàn thành Task với tư cách người merge (không phải sender)")
    void testPullRequestMerged_CompletesAsMerger() {
        WorkItem item = stubPrTask("DATN-15", todoStatus);
        UUID mergerUserId = UUID.randomUUID();
        mapGithubUser(555L, mergerUserId);
        when(workItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(statusRepository.findById(todoStatus.getId())).thenReturn(Optional.of(todoStatus));
        UUID doneTransition = UUID.randomUUID();
        when(workflowEngineService.getAvailableTransitions(item.getId(), mergerUserId)).thenReturn(List.of(
                AvailableTransitionResponse.builder().transitionId(doneTransition).toStatusName("Done").toStatusCategory(StatusCategory.DONE).build()
        ));
        when(workflowEngineService.executeTransition(eq(item.getId()), eq(doneTransition), any(), eq(mergerUserId))).thenReturn(item);

        gitHubIntegrationService.handleWebhook("pull_request", "sig",
                prPayload("closed", true, false, "fix/other", "{ \"id\": 555 }").replace("Thêm đăng nhập OAuth", "Closes DATN-15 sửa lỗi"));

        verify(workItemGitHubLinkRepository).save(argThat(l -> "MERGED".equals(l.getStatus())));
        verify(workflowEngineService).executeTransition(eq(item.getId()), eq(doneTransition),
                argThat(r -> "DONE".equals(r.getResolution())), eq(mergerUserId));
        verify(userGitHubTokenRepository, never()).findFirstByGithubUserId(999L);
    }

    private void stubMergeAsAdmin(WorkItem item) {
        mapGithubUser(555L, currentUserId);
        when(workItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(statusRepository.findById(todoStatus.getId())).thenReturn(Optional.of(todoStatus));
        when(workflowEngineService.getAvailableTransitions(item.getId(), currentUserId)).thenReturn(List.of(
                AvailableTransitionResponse.builder().transitionId(UUID.randomUUID()).toStatusName("Done").toStatusCategory(StatusCategory.DONE).build()
        ));
        when(workflowEngineService.executeTransition(eq(item.getId()), any(), any(), eq(currentUserId))).thenReturn(item);
    }

    private static String mergedPr(String branch, String title) {
        return prPayload("closed", true, false, branch, "{ \"id\": 555 }").replace("Thêm đăng nhập OAuth", title);
    }

    @Test
    @DisplayName("merge PR chỉ nhắc mã trong tên nhánh (không Closes/Fixes): chỉ cập nhật chip MERGED, giữ trạng thái")
    void testMergedPr_MentionOnly_KeepsStatus() {
        stubPrTask("DATN-15", todoStatus);

        gitHubIntegrationService.handleWebhook("pull_request", "sig", mergedPr("feat/DATN-15-ui", "Làm giao diện phần 1"));

        verify(workItemGitHubLinkRepository).save(argThat(l -> "MERGED".equals(l.getStatus())));
        verifyNoInteractions(workflowEngineService);
    }

    @Test
    @DisplayName("nhánh fix/DATN-15 không được tính là từ khoá đóng")
    void testMergedPr_FixBranchPrefix_IsNotCloseKeyword() {
        stubPrTask("DATN-15", todoStatus);

        gitHubIntegrationService.handleWebhook("pull_request", "sig", mergedPr("fix/DATN-15-login", "Sửa lỗi đăng nhập"));

        verifyNoInteractions(workflowEngineService);
    }

    @Test
    @DisplayName("'fixes: datn-15' và 'Hoàn thành DATN-15' đều là từ khoá đóng (không phân biệt hoa thường)")
    void testMergedPr_CloseKeywordVariants() {
        WorkItem item = stubPrTask("DATN-15", todoStatus);
        stubMergeAsAdmin(item);

        gitHubIntegrationService.handleWebhook("pull_request", "sig", mergedPr("feat/x", "fixes: datn-15 phần cuối"));

        verify(workflowEngineService).executeTransition(eq(item.getId()), any(),
                argThat(r -> "DONE".equals(r.getResolution())), eq(currentUserId));
    }

    @Test
    @DisplayName("'Closes DATN-15, refs DATN-16': chỉ DATN-15 hoàn thành, DATN-16 chỉ gắn liên kết")
    void testMergedPr_OnlyKeywordedCodeCompletes() {
        WorkItem item15 = stubPrTask("DATN-15", todoStatus);
        WorkItem item16 = WorkItem.builder().id(UUID.randomUUID()).projectId(projectId).code("DATN-16").statusId(todoStatus.getId()).build();
        when(workItemRepository.findByProjectIdAndCode(projectId, "DATN-16")).thenReturn(Optional.of(item16));
        stubMergeAsAdmin(item15);

        gitHubIntegrationService.handleWebhook("pull_request", "sig", mergedPr("feat/x", "Closes DATN-15, refs DATN-16"));

        verify(workflowEngineService).executeTransition(eq(item15.getId()), any(), any(), eq(currentUserId));
        verify(workflowEngineService, never()).getAvailableTransitions(eq(item16.getId()), any());
        verify(workItemGitHubLinkRepository, times(2)).save(argThat(l -> "MERGED".equals(l.getStatus())));
    }

    @Test
    @DisplayName("từ khoá tự đặt 'done': 'Done FRONE-15' hoàn thành Task")
    void testMergedPr_CustomKeyword_Completes() {
        projectRepo.setPrCloseKeywords("done, final");
        WorkItem item = stubPrTask("DATN-15", todoStatus);
        stubMergeAsAdmin(item);

        gitHubIntegrationService.handleWebhook("pull_request", "sig", mergedPr("feat/x", "Done DATN-15 giao diện"));

        verify(workflowEngineService).executeTransition(eq(item.getId()), any(),
                argThat(r -> "DONE".equals(r.getResolution())), eq(currentUserId));
    }

    @Test
    @DisplayName("đã đặt từ khoá riêng thì 'Closes' mặc định không còn tác dụng")
    void testMergedPr_CustomKeyword_ReplacesDefaults() {
        projectRepo.setPrCloseKeywords("final");
        stubPrTask("DATN-15", todoStatus);

        gitHubIntegrationService.handleWebhook("pull_request", "sig", mergedPr("feat/x", "Closes DATN-15"));

        verifyNoInteractions(workflowEngineService);
    }

    @Test
    @DisplayName("từ khoá tự đặt có ký tự đặc biệt / nhiều chữ được so khớp nguyên văn")
    void testMergedPr_CustomKeyword_LiteralMatch() {
        projectRepo.setPrCloseKeywords("[xong] hết");
        WorkItem item = stubPrTask("DATN-15", todoStatus);
        stubMergeAsAdmin(item);

        gitHubIntegrationService.handleWebhook("pull_request", "sig", mergedPr("feat/x", "[XONG]  hết: DATN-15"));

        verify(workflowEngineService).executeTransition(eq(item.getId()), any(), any(), eq(currentUserId));
    }

    @Test
    @DisplayName("chuẩn hoá từ khoá: bỏ trống, bỏ trùng, rỗng thì về mặc định, quá dài thì báo lỗi")
    void testNormalizeCloseKeywords() {
        assertThat(GitHubIntegrationServiceImpl.normalizeCloseKeywords(" done ,Done,, final  release ")).isEqualTo("done, final release");
        assertThat(GitHubIntegrationServiceImpl.normalizeCloseKeywords("  , ")).isNull();
        assertThatThrownBy(() -> GitHubIntegrationServiceImpl.normalizeCloseKeywords("x".repeat(41)))
                .isInstanceOf(com.datn.common.exception.BadRequestException.class);
        assertThatThrownBy(() -> GitHubIntegrationServiceImpl.normalizeCloseKeywords("!!!"))
                .isInstanceOf(com.datn.common.exception.BadRequestException.class);
    }

    @Test
    @DisplayName("pull_request merge vào nhánh khác nhánh đích: chỉ gắn liên kết, không hoàn thành Task")
    void testPullRequestMergedIntoOtherBranch_LinkOnly() {
        stubPrTask("DATN-15", todoStatus);

        gitHubIntegrationService.handleWebhook("pull_request", "sig",
                prPayload("closed", true, false, "feat/DATN-15", "{ \"id\": 555 }")
                        .replace("\"base\": { \"ref\": \"main\" }", "\"base\": { \"ref\": \"develop\" }"));

        verify(workItemGitHubLinkRepository).save(argThat(l -> "MERGED".equals(l.getStatus())));
        verifyNoInteractions(workflowEngineService);
    }

    private void stubConnect(String githubDefault, String... branches) {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userGitHubTokenRepository.findByUserId(currentUserId)).thenReturn(Optional.of(userToken));
        when(gitHubApiClient.getRepository("gho_mockToken123", "datn-team", "work-service")).thenReturn(GitHubRepoDto.builder()
                .fullName("datn-team/work-service")
                .htmlUrl("https://github.com/datn-team/work-service")
                .defaultBranch(githubDefault)
                .build());
        lenient().when(gitHubApiClient.listBranches("gho_mockToken123", "datn-team", "work-service")).thenReturn(
                Arrays.stream(branches).map(b -> GitHubBranchDto.builder().name(b).build()).toList());
    }

    @Test
    @DisplayName("connectRepository: lưu nhánh đích người dùng chọn khi nhánh tồn tại trên repo")
    void testConnect_UsesChosenBranch() {
        stubConnect("main", "main", "develop");

        gitHubIntegrationService.connectRepository(projectId, ConnectRepoRequest.builder()
                .repoFullName("datn-team/work-service")
                .repoUrl("https://github.com/datn-team/work-service")
                .defaultBranch("develop")
                .build(), currentUserId);

        verify(projectGitHubRepoRepository).save(argThat(r -> "develop".equals(r.getDefaultBranch())));
    }

    @Test
    @DisplayName("connectRepository: nhánh không tồn tại thì báo lỗi, không lưu gì")
    void testConnect_UnknownBranch_Rejected() {
        stubConnect("main", "main");

        assertThatThrownBy(() -> gitHubIntegrationService.connectRepository(projectId, ConnectRepoRequest.builder()
                .repoFullName("datn-team/work-service")
                .repoUrl("https://github.com/datn-team/work-service")
                .defaultBranch("release")
                .build(), currentUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("release");
        verify(projectGitHubRepoRepository, never()).save(any());
    }

    @Test
    @DisplayName("connectRepository: không chọn nhánh thì dùng nhánh mặc định trên GitHub")
    void testConnect_NoBranch_UsesGithubDefault() {
        stubConnect("trunk");

        gitHubIntegrationService.connectRepository(projectId, ConnectRepoRequest.builder()
                .repoFullName("datn-team/work-service")
                .repoUrl("https://github.com/datn-team/work-service")
                .build(), currentUserId);

        verify(projectGitHubRepoRepository).save(argThat(r -> "trunk".equals(r.getDefaultBranch())));
        verify(gitHubApiClient, never()).listBranches(any(), any(), any());
    }

    @Test
    @DisplayName("pull_request draft: chỉ gắn liên kết, không chuyển trạng thái")
    void testPullRequestDraft_LinkOnly() {
        stubPrTask("DATN-15", todoStatus);

        gitHubIntegrationService.handleWebhook("pull_request", "sig",
                prPayload("opened", false, true, "feat/DATN-15", "null"));

        verify(workItemGitHubLinkRepository).save(argThat(l -> l.getLinkType() == GitHubLinkType.PULL_REQUEST));
        verifyNoInteractions(workflowEngineService);
    }

    @Test
    @DisplayName("pull_request không nhắc mã Task: bỏ qua; mã của dự án khác (XDATN-15) không bị nhận nhầm")
    void testPullRequestWithoutTaskCode_Ignored() {
        when(projectGitHubRepoRepository.findAllByRepoFullNameIgnoreCase("datn-team/work-service")).thenReturn(List.of(projectRepo));
        when(gitHubWebhookSignatureValidator.isValidSignature(anyString(), anyString(), eq("secret-xyz"))).thenReturn(true);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        gitHubIntegrationService.handleWebhook("pull_request", "sig",
                prPayload("opened", false, false, "feat/XDATN-15-login", "null"));

        verify(workItemRepository, never()).findByProjectIdAndCode(any(), any());
        verify(workItemGitHubLinkRepository, never()).save(any());
        verifyNoInteractions(workflowEngineService);
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
