package com.datn.workspaceservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ForbiddenException;
import com.datn.workspaceservice.client.IdentityClient;
import com.datn.workspaceservice.entity.WorkspaceMember;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.repository.WorkspaceMemberRepository;
import com.datn.workspaceservice.repository.WorkspaceRepository;
import com.datn.workspaceservice.service.impl.WorkspaceMemberServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bất biến "workspace luôn có ít nhất một OWNER" được tách thành class test
 * riêng vì đây là thứ chưa từng được implement ở bất kỳ đâu trong codebase:
 * Đảm bảo một workspace luôn có ít nhất một OWNER quản trị, không rơi vào trạng thái
 * mồ côi khi người quản trị cuối cùng rời đi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceLastOwnerGuardTest {

    @Mock
    private WorkspaceMemberRepository memberRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private IdentityClient identityClient;

    @Mock
    private com.datn.workspaceservice.notification.WorkspaceNotificationPublisher notifications;

    @InjectMocks
    private WorkspaceMemberServiceImpl service;

    private UUID workspaceId;
    private UUID ownerA;
    private UUID ownerB;
    private UUID adminUser;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        ownerA = UUID.randomUUID();
        ownerB = UUID.randomUUID();
        adminUser = UUID.randomUUID();
        when(workspaceRepository.existsByIdAndDeletedAtIsNull(workspaceId)).thenReturn(true);
    }

    private WorkspaceMember member(UUID userId, WorkspaceRole role) {
        return WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(userId)
                .role(role)
                .build();
    }

    private void givenMember(UUID userId, WorkspaceRole role) {
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(member(userId, role)));
    }

    private void givenOwnerCount(long count) {
        when(memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER)).thenReturn(count);
    }

    // ── leaveWorkspace ───────────────────────────────────────────────────────

    @Test
    @DisplayName("OWNER duy nhất rời workspace -> 400 và KHÔNG xóa hàng nào")
    void leave_soleOwner_isRejected() {
        givenMember(ownerA, WorkspaceRole.OWNER);
        givenOwnerCount(1);

        assertThatThrownBy(() -> service.leaveWorkspace(workspaceId, ownerA))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ít nhất một OWNER")
                .hasMessageContaining("chuyển quyền sở hữu");

        verify(memberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Một trong hai OWNER rời workspace -> thành công")
    void leave_oneOfTwoOwners_succeeds() {
        givenMember(ownerA, WorkspaceRole.OWNER);
        givenOwnerCount(2);

        service.leaveWorkspace(workspaceId, ownerA);

        verify(memberRepository).delete(any(WorkspaceMember.class));
    }

    @Test
    @DisplayName("ADMIN/MEMBER/GUEST rời workspace -> thành công và KHÔNG cần đếm OWNER")
    void leave_nonOwner_skipsOwnerCount() {
        for (WorkspaceRole role : List.of(WorkspaceRole.ADMIN, WorkspaceRole.MEMBER, WorkspaceRole.GUEST)) {
            UUID userId = UUID.randomUUID();
            givenMember(userId, role);

            service.leaveWorkspace(workspaceId, userId);
        }

        // Guard tự return sớm khi target không phải OWNER: không khóa, không đếm.
        verify(memberRepository, never()).lockOwners(any());
        verify(memberRepository, never()).countByWorkspaceIdAndRole(any(), any());
    }

    @Test
    @DisplayName("Khóa ghi các hàng OWNER phải diễn ra TRƯỚC khi đếm")
    void leave_locksOwnersBeforeCounting() {
        givenMember(ownerA, WorkspaceRole.OWNER);
        givenOwnerCount(2);

        service.leaveWorkspace(workspaceId, ownerA);

        // Thứ tự này là điều ngăn hai OWNER cùng gọi /leave đọc được count = 2,
        // cùng pass, rồi cùng xóa và đưa workspace về zero OWNER.
        InOrder inOrder = inOrder(memberRepository);
        inOrder.verify(memberRepository).lockOwners(workspaceId);
        inOrder.verify(memberRepository).countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER);
    }

    // ── removeMember ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Xóa OWNER cuối cùng -> 400")
    void removeMember_lastOwner_isRejected() {
        givenMember(ownerA, WorkspaceRole.OWNER);
        givenMember(ownerB, WorkspaceRole.OWNER);
        givenOwnerCount(1);

        assertThatThrownBy(() -> service.removeMember(workspaceId, ownerB, ownerA))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ít nhất một OWNER");

        verify(memberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("ADMIN không được xóa một OWNER -> 403")
    void removeMember_adminCannotRemoveOwner() {
        givenMember(adminUser, WorkspaceRole.ADMIN);
        givenMember(ownerA, WorkspaceRole.OWNER);

        assertThatThrownBy(() -> service.removeMember(workspaceId, ownerA, adminUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Chỉ OWNER");
    }

    @Test
    @DisplayName("Tự xóa chính mình -> 400, chỉ dẫn dùng chức năng Rời workspace")
    void removeMember_self_isRejected() {
        givenMember(ownerA, WorkspaceRole.OWNER);

        assertThatThrownBy(() -> service.removeMember(workspaceId, ownerA, ownerA))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Rời workspace");
    }

    // ── updateMemberRole ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Hạ OWNER cuối cùng xuống ADMIN -> 400")
    void updateRole_demoteLastOwner_isRejected() {
        givenMember(ownerA, WorkspaceRole.OWNER);
        givenMember(ownerB, WorkspaceRole.OWNER);
        givenOwnerCount(1);

        assertThatThrownBy(() -> service.updateMemberRole(workspaceId, ownerB, WorkspaceRole.ADMIN, ownerA))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ít nhất một OWNER");

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hạ một OWNER xuống ADMIN khi còn 2 OWNER -> thành công")
    void updateRole_demoteOwner_whenTwoOwners_succeeds() {
        givenMember(ownerA, WorkspaceRole.OWNER);
        givenMember(ownerB, WorkspaceRole.OWNER);
        givenOwnerCount(2);
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(i -> i.getArgument(0));

        service.updateMemberRole(workspaceId, ownerB, WorkspaceRole.ADMIN, ownerA);

        verify(memberRepository).save(any(WorkspaceMember.class));
    }

    @Test
    @DisplayName("Cập nhật OWNER -> OWNER (no-op) khi chỉ có 1 OWNER -> KHÔNG được throw")
    void updateRole_ownerToOwner_noop_isAllowedEvenAsSoleOwner() {
        givenMember(ownerA, WorkspaceRole.OWNER);
        givenMember(ownerB, WorkspaceRole.OWNER);
        givenOwnerCount(1);
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(i -> i.getArgument(0));

        // Regression guard cho điều kiện newRole != OWNER: thao tác này không làm
        // mất OWNER nào nên guard không được kích hoạt.
        service.updateMemberRole(workspaceId, ownerB, WorkspaceRole.OWNER, ownerA);

        verify(memberRepository).save(any(WorkspaceMember.class));
        verify(memberRepository, never()).lockOwners(any());
    }

    @Test
    @DisplayName("OWNER nâng ADMIN lên OWNER -> thành công")
    void updateRole_ownerPromotesToOwner_succeeds() {
        givenMember(ownerA, WorkspaceRole.OWNER);
        givenMember(adminUser, WorkspaceRole.ADMIN);
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.updateMemberRole(workspaceId, adminUser, WorkspaceRole.OWNER, ownerA);

        assertThat(result.getRole()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("ADMIN nâng người khác lên OWNER -> 403")
    void updateRole_adminPromotesToOwner_isRejected() {
        givenMember(adminUser, WorkspaceRole.ADMIN);
        UUID target = UUID.randomUUID();
        givenMember(target, WorkspaceRole.MEMBER);

        assertThatThrownBy(() -> service.updateMemberRole(workspaceId, target, WorkspaceRole.OWNER, adminUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("OWNER");
    }

    @Test
    @DisplayName("Tự đổi vai trò của chính mình -> 400")
    void updateRole_self_isRejected() {
        givenMember(ownerA, WorkspaceRole.OWNER);

        assertThatThrownBy(() -> service.updateMemberRole(workspaceId, ownerA, WorkspaceRole.ADMIN, ownerA))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chính mình");
    }
}
