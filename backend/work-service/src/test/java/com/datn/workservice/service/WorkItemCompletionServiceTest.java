package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.dto.response.CompletionRequestResponse;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemComment;
import com.datn.workservice.entity.WorkItemCompletionRequest;
import com.datn.workservice.enums.CompletionRequestStatus;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemCommentRepository;
import com.datn.workservice.repository.WorkItemCompletionRequestRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.service.impl.WorkItemCompletionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * "Tạm hoàn thành" → Admin dự án xác nhận / từ chối (BR-W03..BR-W08).
 */
@ExtendWith(MockitoExtension.class)
class WorkItemCompletionServiceTest {

    @Mock private WorkItemCompletionRequestRepository requestRepository;
    @Mock private WorkItemCompletionGate completionGate;
    @Mock private WorkflowEngineService workflowEngineService;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private WorkItemCommentRepository commentRepository;
    @Mock private IdentityClient identityClient;
    @Mock private com.datn.workservice.notification.WorkItemNotifier workItemNotifier;

    @InjectMocks private WorkItemCompletionServiceImpl completionService;

    private UUID projectId;
    private UUID itemId;
    private UUID memberId;
    private UUID adminId;
    private WorkItem item;
    private WorkItemCompletionRequest pending;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        item = WorkItem.builder()
                .id(itemId)
                .projectId(projectId)
                .code("MOC-7")
                .summary("Pha chế menu mùa đông")
                .statusId(UUID.randomUUID())
                .assigneeId(memberId)
                .build();

        pending = WorkItemCompletionRequest.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .workItemId(itemId)
                .targetStatusId(UUID.randomUUID())
                .status(CompletionRequestStatus.PENDING)
                .requestedBy(memberId)
                .requestedAt(LocalDateTime.now())
                .build();

        lenient().when(workItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        lenient().when(workItemRepository.findAllById(any())).thenReturn(List.of(item));
        lenient().when(completionGate.findPending(itemId)).thenReturn(Optional.of(pending));
    }

    @Test
    @DisplayName("Xác nhận: chỉ Admin dự án mới được xác nhận hoàn thành")
    void approve_rejectsNonAdmin() {
        when(completionGate.canConfirmCompletion(projectId, memberId)).thenReturn(false);

        assertThatThrownBy(() -> completionService.approve(itemId, memberId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ Admin dự án");
        verifyNoInteractions(workflowEngineService);
    }

    @Test
    @DisplayName("Xác nhận: Admin chạy lại bước chuyển đã lưu qua WorkflowEngine")
    void approve_byAdmin_executesStoredTransition() {
        when(completionGate.canConfirmCompletion(projectId, adminId)).thenReturn(true);
        WorkItem done = WorkItem.builder().id(itemId).statusId(pending.getTargetStatusId()).build();
        when(workflowEngineService.executeApprovedCompletion(itemId, pending, adminId)).thenReturn(done);

        WorkItem result = completionService.approve(itemId, adminId);

        assertThat(result.getStatusId()).isEqualTo(pending.getTargetStatusId());
        verify(workflowEngineService).executeApprovedCompletion(itemId, pending, adminId);
    }

    @Test
    @DisplayName("Xác nhận: báo lỗi khi công việc không có yêu cầu đang chờ")
    void approve_withoutPendingRequest_fails() {
        when(completionGate.canConfirmCompletion(projectId, adminId)).thenReturn(true);
        when(completionGate.findPending(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> completionService.approve(itemId, adminId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không có yêu cầu xác nhận hoàn thành");
    }

    @Test
    @DisplayName("Từ chối: lưu lý do, ghi lịch sử và bình luận để người làm thấy")
    void reject_byAdmin_recordsReasonHistoryAndComment() {
        when(completionGate.canConfirmCompletion(projectId, adminId)).thenReturn(true);
        doAnswer(inv -> {
            WorkItemCompletionRequest r = inv.getArgument(0);
            r.setStatus(inv.getArgument(1));
            r.setDecidedBy(inv.getArgument(2));
            r.setDecisionNote(inv.getArgument(3));
            return null;
        }).when(completionGate).close(any(), any(), any(), any());

        CompletionRequestResponse res = completionService.reject(itemId, "  Thiếu ảnh chụp menu  ", adminId);

        assertThat(res.getStatus()).isEqualTo(CompletionRequestStatus.REJECTED);
        assertThat(res.getDecisionNote()).isEqualTo("Thiếu ảnh chụp menu");
        verify(requestRepository).save(pending);
        verify(completionGate).recordChangelog(eq(itemId), anyString(), eq("Bị từ chối: Thiếu ảnh chụp menu"), eq(adminId));
        verify(commentRepository).save(any(WorkItemComment.class));
        verifyNoInteractions(workflowEngineService);
    }

    @Test
    @DisplayName("Hủy: người khác (không phải người gửi, không phải Admin) không được hủy yêu cầu")
    void cancel_byStranger_isDenied() {
        UUID stranger = UUID.randomUUID();
        when(completionGate.canConfirmCompletion(projectId, stranger)).thenReturn(false);

        assertThatThrownBy(() -> completionService.cancel(itemId, stranger))
                .isInstanceOf(AccessDeniedException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hủy: người gửi được rút lại yêu cầu của mình")
    void cancel_byRequester_closesRequest() {
        completionService.cancel(itemId, memberId);

        verify(completionGate).close(eq(pending), eq(CompletionRequestStatus.CANCELLED), eq(memberId), anyString());
        verify(requestRepository).save(pending);
        verify(completionGate).recordChangelog(eq(itemId), anyString(), anyString(), eq(memberId));
    }
}
