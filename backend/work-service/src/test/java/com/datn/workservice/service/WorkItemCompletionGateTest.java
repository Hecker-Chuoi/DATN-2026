package com.datn.workservice.service;

import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemChangelog;
import com.datn.workservice.entity.WorkItemCompletionRequest;
import com.datn.workservice.enums.CompletionRequestStatus;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.WorkItemChangelogRepository;
import com.datn.workservice.repository.WorkItemCompletionRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkItemCompletionGateTest {

    @Mock private WorkItemCompletionRequestRepository requestRepository;
    @Mock private WorkItemChangelogRepository changelogRepository;
    @Mock private ProjectMemberService projectMemberService;

    @InjectMocks private WorkItemCompletionGate gate;

    private UUID projectId;
    private UUID memberId;
    private WorkItem item;
    private Status inProgress;
    private Status done;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        item = WorkItem.builder().id(UUID.randomUUID()).projectId(projectId).code("MOC-1").build();
        inProgress = Status.builder().id(UUID.randomUUID()).name("Đang làm").category(StatusCategory.IN_PROGRESS).build();
        done = Status.builder().id(UUID.randomUUID()).name("Hoàn thành").category(StatusCategory.DONE).build();
    }

    @Test
    @DisplayName("Quyền xác nhận dựa trên PROJECT_ADMIN của dự án")
    void canConfirmCompletion_usesProjectAdminPermission() {
        when(projectMemberService.hasPermission(projectId, memberId, PermissionKey.PROJECT_ADMIN)).thenReturn(false);
        assertThat(gate.canConfirmCompletion(projectId, memberId)).isFalse();
        assertThat(gate.canConfirmCompletion(projectId, null)).isFalse();
    }

    @Test
    @DisplayName("Gửi yêu cầu mới thay thế (CANCELLED) yêu cầu cũ đang chờ và ghi lịch sử")
    void submit_replacesExistingPendingRequest() {
        WorkItemCompletionRequest old = WorkItemCompletionRequest.builder()
                .id(UUID.randomUUID()).workItemId(item.getId()).status(CompletionRequestStatus.PENDING)
                .requestedBy(memberId).requestedAt(LocalDateTime.now().minusDays(1)).build();
        when(requestRepository.findFirstByWorkItemIdAndStatus(item.getId(), CompletionRequestStatus.PENDING))
                .thenReturn(Optional.of(old));
        when(requestRepository.save(any(WorkItemCompletionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkItemCompletionRequest created = gate.submit(item, UUID.randomUUID(), inProgress, done,
                null, false, "Xong rồi", memberId);

        assertThat(old.getStatus()).isEqualTo(CompletionRequestStatus.CANCELLED);
        verify(requestRepository).saveAndFlush(old);
        assertThat(created.getStatus()).isEqualTo(CompletionRequestStatus.PENDING);
        assertThat(created.getTargetStatusId()).isEqualTo(done.getId());
        assertThat(created.getRequestedBy()).isEqualTo(memberId);

        ArgumentCaptor<WorkItemChangelog> log = ArgumentCaptor.forClass(WorkItemChangelog.class);
        verify(changelogRepository).save(log.capture());
        assertThat(log.getValue().getFieldName()).isEqualTo(WorkItemCompletionGate.CHANGELOG_FIELD);
        assertThat(log.getValue().getOldValue()).isEqualTo("Đang làm");
        assertThat(log.getValue().getNewValue()).contains("chờ Admin xác nhận").contains("Hoàn thành");
    }

    @Test
    @DisplayName("Trạng thái vào DONE → yêu cầu đang chờ được đánh dấu APPROVED")
    void onStatusChanged_enteringDone_approvesPending() {
        WorkItemCompletionRequest pending = WorkItemCompletionRequest.builder()
                .id(UUID.randomUUID()).workItemId(item.getId()).status(CompletionRequestStatus.PENDING).build();
        when(requestRepository.findFirstByWorkItemIdAndStatus(item.getId(), CompletionRequestStatus.PENDING))
                .thenReturn(Optional.of(pending));
        UUID adminId = UUID.randomUUID();

        Optional<WorkItemCompletionRequest> closed = gate.onStatusChanged(item, done, true, adminId);

        assertThat(closed).contains(pending);
        assertThat(pending.getStatus()).isEqualTo(CompletionRequestStatus.APPROVED);
        assertThat(pending.getDecidedBy()).isEqualTo(adminId);
        assertThat(pending.getDecidedAt()).isNotNull();
        verify(requestRepository).save(pending);
    }

    @Test
    @DisplayName("Trạng thái đổi sang nhóm khác trước khi duyệt → yêu cầu tự hủy")
    void onStatusChanged_toOtherStatus_cancelsPending() {
        WorkItemCompletionRequest pending = WorkItemCompletionRequest.builder()
                .id(UUID.randomUUID()).workItemId(item.getId()).status(CompletionRequestStatus.PENDING).build();
        when(requestRepository.findFirstByWorkItemIdAndStatus(item.getId(), CompletionRequestStatus.PENDING))
                .thenReturn(Optional.of(pending));

        gate.onStatusChanged(item, inProgress, false, memberId);

        assertThat(pending.getStatus()).isEqualTo(CompletionRequestStatus.CANCELLED);
        assertThat(pending.getDecisionNote()).contains("Đang làm");
    }
}
