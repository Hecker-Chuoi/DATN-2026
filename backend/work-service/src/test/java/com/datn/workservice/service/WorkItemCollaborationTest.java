package com.datn.workservice.service;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.CreateWorkItemCommentRequest;
import com.datn.workservice.dto.request.CreateWorkItemLinkRequest;
import com.datn.workservice.dto.request.UpdateWorkItemCommentRequest;
import com.datn.workservice.dto.response.WorkItemAttachmentResponse;
import com.datn.workservice.dto.response.WorkItemChangelogResponse;
import com.datn.workservice.dto.response.WorkItemCommentResponse;
import com.datn.workservice.dto.response.WorkItemLinkResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.exception.CyclicDependencyException;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkItemCollaborationTest {

    @Mock
    private WorkItemChangelogRepository changelogRepository;
    @Mock
    private WorkItemCommentRepository commentRepository;
    @Mock
    private WorkItemAttachmentRepository attachmentRepository;
    @Mock
    private WorkItemLinkRepository linkRepository;
    @Mock
    private WorkItemRepository workItemRepository;
    @Mock
    private WorkItemTypeRepository workItemTypeRepository;
    @Mock
    private StatusRepository statusRepository;
    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private ComponentRepository componentRepository;
    @Mock
    private VersionRepository versionRepository;
    @Mock
    private IdentityClient identityClient;
    @Mock
    private io.minio.MinioClient minioClient;

    private WorkItemAuditServiceImpl auditService;
    private WorkItemCommentServiceImpl commentService;
    private WorkItemAttachmentServiceImpl attachmentService;
    private WorkItemLinkServiceImpl linkService;
    private DependencyCycleDetector cycleDetector;

    private UUID projectId;
    private UUID userId;
    private UUID workItemId1;
    private UUID workItemId2;
    private WorkItem item1;
    private WorkItem item2;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        workItemId1 = UUID.randomUUID();
        workItemId2 = UUID.randomUUID();

        item1 = WorkItem.builder()
                .id(workItemId1)
                .projectId(projectId)
                .code("DATN-101")
                .summary("Backend API implementation")
                .priority(WorkItemPriority.MEDIUM)
                .statusId(UUID.randomUUID())
                .typeId(UUID.randomUUID())
                .reporterId(userId)
                .version(1)
                .build();

        item2 = WorkItem.builder()
                .id(workItemId2)
                .projectId(projectId)
                .code("DATN-102")
                .summary("Frontend React components")
                .priority(WorkItemPriority.HIGH)
                .statusId(UUID.randomUUID())
                .typeId(UUID.randomUUID())
                .reporterId(userId)
                .version(1)
                .build();

        auditService = new WorkItemAuditServiceImpl(
                changelogRepository,
                workItemTypeRepository,
                statusRepository,
                sprintRepository,
                componentRepository,
                versionRepository,
                workItemRepository,
                identityClient
        );

        commentService = new WorkItemCommentServiceImpl(
                commentRepository,
                workItemRepository,
                identityClient
        );

        cycleDetector = new DependencyCycleDetector(linkRepository, workItemRepository);

        attachmentService = new WorkItemAttachmentServiceImpl(
                attachmentRepository,
                workItemRepository,
                auditService,
                identityClient,
                minioClient
        );

        linkService = new WorkItemLinkServiceImpl(
                linkRepository,
                workItemRepository,
                workItemTypeRepository,
                statusRepository,
                cycleDetector,
                auditService,
                identityClient
        );
    }

    // ─── Step 5.3: Audit Trail Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Audit Trail: Should record diff when summary and priority change")
    void testRecordDiff() {
        WorkItem oldItem = WorkItem.builder()
                .id(workItemId1)
                .summary("Old Summary")
                .priority(WorkItemPriority.LOW)
                .storyPoints(new BigDecimal("3.00"))
                .build();

        WorkItem newItem = WorkItem.builder()
                .id(workItemId1)
                .summary("New Summary")
                .priority(WorkItemPriority.HIGH)
                .storyPoints(new BigDecimal("5.00"))
                .build();

        auditService.recordDiff(oldItem, newItem, userId);

        verify(changelogRepository).saveAll(argThat((List<WorkItemChangelog> logs) -> {
            assertThat(logs).hasSize(3);
            assertThat(logs).anyMatch(l -> "SUMMARY".equals(l.getFieldName()) && "Old Summary".equals(l.getOldValue()) && "New Summary".equals(l.getNewValue()));
            assertThat(logs).anyMatch(l -> "PRIORITY".equals(l.getFieldName()) && "LOW".equals(l.getOldValue()) && "HIGH".equals(l.getNewValue()));
            assertThat(logs).anyMatch(l -> "STORY_POINTS".equals(l.getFieldName()) && "3.00".equals(l.getOldValue()) && "5.00".equals(l.getNewValue()));
            return true;
        }));
    }

    @Test
    @DisplayName("Audit Trail: Should fetch changelogs and enrich author details")
    void testGetChangelogs() {
        WorkItemChangelog logEntry = WorkItemChangelog.builder()
                .id(UUID.randomUUID())
                .workItemId(workItemId1)
                .fieldName("STATUS")
                .oldValue("To Do")
                .newValue("In Progress")
                .changedBy(userId)
                .changedAt(LocalDateTime.now())
                .build();

        when(changelogRepository.findByWorkItemIdOrderByChangedAtDesc(workItemId1)).thenReturn(List.of(logEntry));
        when(identityClient.getUsersByIds(any())).thenReturn(ApiResponse.success(List.of(
                UserResponse.builder().id(userId).username("admin").firstName("Admin").lastName("User").build()
        )));

        List<WorkItemChangelogResponse> responses = auditService.getChangelogs(workItemId1);

        assertThat(responses).hasSize(1);
        WorkItemChangelogResponse res = responses.get(0);
        assertThat(res.getFieldName()).isEqualTo("STATUS");
        assertThat(res.getFieldDisplayName()).isEqualTo("Trạng thái (Status)");
        assertThat(res.getAuthorName()).isEqualTo("Admin User");
    }

    // ─── Step 5.4: Comments Threaded Tests ─────────────────────────────────────

    @Test
    @DisplayName("Comments: Should create root comment and nested reply")
    void testThreadedComments() {
        when(workItemRepository.findById(workItemId1)).thenReturn(Optional.of(item1));

        UUID rootCommentId = UUID.randomUUID();
        WorkItemComment rootComment = WorkItemComment.builder()
                .id(rootCommentId)
                .workItemId(workItemId1)
                .authorId(userId)
                .body("Root comment text")
                .createdAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        UUID replyCommentId = UUID.randomUUID();
        WorkItemComment replyComment = WorkItemComment.builder()
                .id(replyCommentId)
                .workItemId(workItemId1)
                .parentCommentId(rootCommentId)
                .authorId(userId)
                .body("Nested reply text")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(commentRepository.findByWorkItemIdAndDeletedAtIsNullOrderByCreatedAtAsc(workItemId1))
                .thenReturn(List.of(rootComment, replyComment));
        when(identityClient.getUsersByIds(any())).thenReturn(ApiResponse.success(List.of(
                UserResponse.builder().id(userId).username("dev1").firstName("Developer").lastName("One").build()
        )));

        List<WorkItemCommentResponse> tree = commentService.getCommentsTree(workItemId1);

        assertThat(tree).hasSize(1);
        WorkItemCommentResponse rootDto = tree.get(0);
        assertThat(rootDto.getId()).isEqualTo(rootCommentId);
        assertThat(rootDto.getBody()).isEqualTo("Root comment text");
        assertThat(rootDto.getReplies()).hasSize(1);
        assertThat(rootDto.getReplies().get(0).getId()).isEqualTo(replyCommentId);
        assertThat(rootDto.getReplies().get(0).getBody()).isEqualTo("Nested reply text");
    }

    @Test
    @DisplayName("Comments: Should add comment and validate parent comment")
    void testAddComment() {
        when(workItemRepository.findById(workItemId1)).thenReturn(Optional.of(item1));
        when(commentRepository.save(any(WorkItemComment.class))).thenAnswer(invocation -> {
            WorkItemComment c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            return c;
        });

        CreateWorkItemCommentRequest req = CreateWorkItemCommentRequest.builder()
                .body("New discussion comment")
                .build();

        WorkItemCommentResponse response = commentService.addComment(workItemId1, req, userId);

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isEqualTo("New discussion comment");
        verify(commentRepository).save(any(WorkItemComment.class));
    }

    // ─── Step 5.4: Attachments Tests ──────────────────────────────────────────

    @Test
    @DisplayName("Attachments: Should upload file to MinIO and save metadata")
    void testUploadAttachment() throws Exception {
        when(workItemRepository.findById(workItemId1)).thenReturn(Optional.of(item1));
        when(minioClient.bucketExists(any())).thenReturn(true);
        when(attachmentRepository.save(any(WorkItemAttachment.class))).thenAnswer(invocation -> {
            WorkItemAttachment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setUploadedAt(LocalDateTime.now());
            return a;
        });

        MockMultipartFile file = new MockMultipartFile("file", "design-mockup.png", "image/png", "sample content".getBytes());

        WorkItemAttachmentResponse response = attachmentService.uploadAttachment(workItemId1, file, userId);

        assertThat(response).isNotNull();
        assertThat(response.getFileName()).isEqualTo("design-mockup.png");
        assertThat(response.getContentType()).isEqualTo("image/png");
        verify(minioClient).putObject(any(io.minio.PutObjectArgs.class));
        verify(attachmentRepository).save(any(WorkItemAttachment.class));
        verify(changelogRepository).save(any(WorkItemChangelog.class));
    }

    // ─── Step 5.4: Linked Issues Tests ────────────────────────────────────────

    @Test
    @DisplayName("Links: Should create link and prevent cyclic dependencies")
    void testCreateLink() {
        when(workItemRepository.findById(workItemId1)).thenReturn(Optional.of(item1));
        when(workItemRepository.findById(workItemId2)).thenReturn(Optional.of(item2));
        when(linkRepository.existsBySourceItemIdAndTargetItemIdAndLinkType(workItemId1, workItemId2, LinkType.BLOCKS)).thenReturn(false);
        when(linkRepository.findByLinkType(LinkType.BLOCKS)).thenReturn(Collections.emptyList());

        when(linkRepository.save(any(WorkItemLink.class))).thenAnswer(invocation -> {
            WorkItemLink l = invocation.getArgument(0);
            l.setId(UUID.randomUUID());
            l.setCreatedAt(LocalDateTime.now());
            return l;
        });

        CreateWorkItemLinkRequest req = CreateWorkItemLinkRequest.builder()
                .targetItemId(workItemId2)
                .linkType(LinkType.BLOCKS)
                .build();

        WorkItemLinkResponse response = linkService.createLink(workItemId1, req, userId);

        assertThat(response).isNotNull();
        assertThat(response.getLinkType()).isEqualTo(LinkType.BLOCKS);
        assertThat(response.getRelationshipDescription()).contains("Chặn");
        verify(linkRepository).save(any(WorkItemLink.class));
    }

    @Test
    @DisplayName("Links: Should reject self-linking")
    void testSelfLinkRejection() {
        CreateWorkItemLinkRequest req = CreateWorkItemLinkRequest.builder()
                .targetItemId(workItemId1)
                .linkType(LinkType.RELATES_TO)
                .build();

        assertThatThrownBy(() -> linkService.createLink(workItemId1, req, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể tự liên kết");
    }

    @Test
    @DisplayName("Links: Should reject cyclic dependency loop (A blocks B, B blocks A)")
    void testCyclicDependencyRejection() {
        when(workItemRepository.findById(workItemId1)).thenReturn(Optional.of(item1));
        when(workItemRepository.findById(workItemId2)).thenReturn(Optional.of(item2));

        // Existing link: item2 blocks item1 (targetItemId -> sourceItemId path already exists)
        WorkItemLink existing = WorkItemLink.builder()
                .sourceItemId(workItemId2)
                .targetItemId(workItemId1)
                .linkType(LinkType.BLOCKS)
                .build();

        when(linkRepository.findByLinkType(LinkType.BLOCKS)).thenReturn(List.of(existing));
        when(workItemRepository.findAllById(any())).thenReturn(List.of(item1, item2));

        CreateWorkItemLinkRequest req = CreateWorkItemLinkRequest.builder()
                .targetItemId(workItemId2)
                .linkType(LinkType.BLOCKS)
                .build();

        // Adding item1 blocks item2 creates loop: item1 -> item2 -> item1
        assertThatThrownBy(() -> linkService.createLink(workItemId1, req, userId))
                .isInstanceOf(CyclicDependencyException.class)
                .hasMessageContaining("chu trình phụ thuộc vòng lặp");
    }
}
