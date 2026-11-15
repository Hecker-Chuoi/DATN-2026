package com.datn.workspaceservice.service;

import com.datn.common.dto.ApiResponse;
import com.datn.common.dto.PageResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ForbiddenException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workspaceservice.client.IdentityClient;
import com.datn.workspaceservice.client.dto.UserResponse;
import com.datn.workspaceservice.dto.request.AddWorkspaceMemberRequest;
import com.datn.workspaceservice.dto.response.WorkspaceInvitationResponse;
import com.datn.workspaceservice.dto.response.WorkspaceMemberResponse;
import com.datn.workspaceservice.entity.WorkspaceInvitation;
import com.datn.workspaceservice.entity.WorkspaceMember;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.repository.WorkspaceInvitationRepository;
import com.datn.workspaceservice.repository.WorkspaceMemberRepository;
import com.datn.workspaceservice.repository.WorkspaceRepository;
import com.datn.workspaceservice.service.impl.WorkspaceMemberServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceMemberServiceTest {

    @Mock
    private WorkspaceMemberRepository memberRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private WorkspaceInvitationRepository invitationRepository;
    @Mock
    private com.datn.workspaceservice.notification.WorkspaceNotificationPublisher notifications;
    @Mock
    private IdentityClient identityClient;

    @InjectMocks
    private WorkspaceMemberServiceImpl service;

    private UUID workspaceId;
    private UUID ownerId;
    private UUID adminId;
    private UUID plainMemberId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        plainMemberId = UUID.randomUUID();
        when(workspaceRepository.existsByIdAndDeletedAtIsNull(workspaceId)).thenReturn(true);
    }

    private void givenMember(UUID userId, WorkspaceRole role) {
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(WorkspaceMember.builder()
                        .id(UUID.randomUUID()).workspaceId(workspaceId).userId(userId).role(role).build()));
    }

    private AddWorkspaceMemberRequest addRequest(String identifier, WorkspaceRole role) {
        AddWorkspaceMemberRequest r = new AddWorkspaceMemberRequest();
        r.setIdentifier(identifier);
        r.setRole(role);
        return r;
    }

    /** identity trả users.id riêng kèm keycloakId; workspace-service dùng keycloakId (sub). */
    private void givenIdentityResolves(UUID newUserSub) {
        when(identityClient.getUserByIdentifier(anyString())).thenReturn(ApiResponse.success(UserResponse.builder()
                .id(UUID.randomUUID()).keycloakId(newUserSub.toString()).username("newbie").build()));
    }

    @Test
    @DisplayName("Thêm thành viên trùng -> 400, KHÔNG mutate hàng sẵn có")
    void addMember_duplicate_isRejected() {
        givenMember(ownerId, WorkspaceRole.OWNER);
        UUID target = UUID.randomUUID();
        givenIdentityResolves(target);
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, target)).thenReturn(true);

        assertThatThrownBy(() -> service.invite(workspaceId, addRequest("a@b.c", WorkspaceRole.MEMBER), ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã là thành viên");

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN cấp vai trò OWNER -> 403")
    void addMember_adminGrantingOwner_isRejected() {
        givenMember(adminId, WorkspaceRole.ADMIN);

        assertThatThrownBy(() -> service.invite(workspaceId, addRequest("a@b.c", WorkspaceRole.OWNER), adminId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Chỉ OWNER");

        // Không được gọi identity-service khi đã chắc chắn từ chối.
        verify(identityClient, never()).getUserByIdentifier(anyString());
    }

    @Test
    @DisplayName("MEMBER và GUEST không được thêm thành viên -> 403")
    void addMember_byMemberOrGuest_isRejected() {
        for (WorkspaceRole role : List.of(WorkspaceRole.MEMBER, WorkspaceRole.GUEST)) {
            UUID caller = UUID.randomUUID();
            givenMember(caller, role);

            assertThatThrownBy(() -> service.invite(workspaceId, addRequest("a@b.c", WorkspaceRole.MEMBER), caller))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    @DisplayName("Mời thành viên lưu Keycloak sub (keycloakId), không lưu users.id của identity")
    void invite_storesKeycloakSub() {
        givenMember(ownerId, WorkspaceRole.OWNER);
        UUID identityRowId = UUID.randomUUID();
        UUID keycloakSub = UUID.randomUUID();
        when(identityClient.getUserByIdentifier(anyString())).thenReturn(
                ApiResponse.success(UserResponse.builder()
                        .id(identityRowId)
                        .keycloakId(keycloakSub.toString())
                        .username("newbie")
                        .build()));
        when(invitationRepository.save(any(WorkspaceInvitation.class))).thenAnswer(i -> {
            WorkspaceInvitation inv = i.getArgument(0);
            inv.setId(UUID.randomUUID());
            return inv;
        });

        WorkspaceInvitationResponse result =
                service.invite(workspaceId, addRequest("newbie", WorkspaceRole.MEMBER), ownerId);

        assertThat(result.getInviteeUserId()).isEqualTo(keycloakSub).isNotEqualTo(identityRowId);
    }

    @Test
    @DisplayName("Người dùng chưa liên kết Keycloak (không có keycloakId) -> 404, không tạo lời mời")
    void invite_userWithoutKeycloakId_isNotFound() {
        givenMember(ownerId, WorkspaceRole.OWNER);
        when(identityClient.getUserByIdentifier(anyString())).thenReturn(
                ApiResponse.success(UserResponse.builder().id(UUID.randomUUID()).username("legacy").build()));

        assertThatThrownBy(() -> service.invite(workspaceId, addRequest("legacy", WorkspaceRole.MEMBER), ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Identifier không tồn tại -> 404")
    void addMember_unknownIdentifier_isNotFound() {
        givenMember(ownerId, WorkspaceRole.OWNER);
        when(identityClient.getUserByIdentifier(anyString())).thenReturn(ApiResponse.success(null));

        assertThatThrownBy(() -> service.invite(workspaceId, addRequest("ghost", WorkspaceRole.MEMBER), ownerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy người dùng");
    }

    @Test
    @DisplayName("getMembers với 50 thành viên chỉ gọi batch identity-service ĐÚNG 1 lần")
    void getMembers_hydratesInSingleBatchCall() {
        givenMember(ownerId, WorkspaceRole.OWNER);

        List<WorkspaceMember> members = new ArrayList<>();
        List<UserResponse> users = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID uid = UUID.randomUUID();
            members.add(WorkspaceMember.builder()
                    .id(UUID.randomUUID()).workspaceId(workspaceId).userId(uid)
                    .role(WorkspaceRole.MEMBER).build());
            users.add(UserResponse.builder().id(uid).username("user" + i).email("user" + i + "@x.io").build());
        }
        when(memberRepository.findByWorkspaceId(workspaceId)).thenReturn(members);
        when(identityClient.getUsersByIds(anyList())).thenReturn(ApiResponse.success(users));

        PageResponse<WorkspaceMemberResponse> page =
                service.getMembers(workspaceId, ownerId, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(50);
        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent().get(0).getUsername()).isNotNull();

        // Đây là điểm dễ hỏng nhất: một lời gọi Feign cho mỗi member sẽ thành 50
        // round-trip thay vì batching.
        verify(identityClient, times(1)).getUsersByIds(anyList());
        verify(identityClient, never()).getUserById(any());
    }

    @Test
    @DisplayName("getMembers: thành viên lưu Keycloak sub, identity trả users.id khác -> vẫn hiện đúng tên")
    void getMembers_resolvesNamesByKeycloakSub() {
        givenMember(ownerId, WorkspaceRole.OWNER);
        UUID binhSub = UUID.randomUUID();
        when(memberRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(WorkspaceMember.builder()
                .id(UUID.randomUUID()).workspaceId(workspaceId).userId(binhSub).role(WorkspaceRole.MEMBER).build()));
        when(identityClient.getUsersByIds(anyList())).thenReturn(ApiResponse.success(List.of(UserResponse.builder()
                .id(UUID.randomUUID()).keycloakId(binhSub.toString()).username("binh").email("binh@x.io").build())));

        PageResponse<WorkspaceMemberResponse> page =
                service.getMembers(workspaceId, ownerId, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).singleElement().satisfies(m -> {
            assertThat(m.getUserId()).isEqualTo(binhSub);
            assertThat(m.getUsername()).isEqualTo("binh");
        });
    }

    @Test
    @DisplayName("identity-service lỗi -> vẫn trả danh sách, chỉ thiếu tên/email")
    void getMembers_degradesWhenIdentityFails() {
        givenMember(ownerId, WorkspaceRole.OWNER);
        when(memberRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(
                WorkspaceMember.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                        .userId(plainMemberId).role(WorkspaceRole.MEMBER).build()));
        when(identityClient.getUsersByIds(anyList())).thenThrow(new RuntimeException("connection refused"));

        PageResponse<WorkspaceMemberResponse> page =
                service.getMembers(workspaceId, ownerId, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo(plainMemberId);
        assertThat(page.getContent().get(0).getUsername()).isNull();
    }

    @Test
    @DisplayName("Người ngoài xem danh sách -> 404 chứ KHÔNG phải 403 (không rò rỉ sự tồn tại)")
    void getMembers_byNonMember_returns404NotForbidden() {
        UUID outsider = UUID.randomUUID();
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, outsider)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMembers(workspaceId, outsider, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class)
                .isNotInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Không tìm thấy workspace");
    }

    @Test
    @DisplayName("findRole của người không phải thành viên -> Optional.empty")
    void findRole_nonMember_isEmpty() {
        UUID outsider = UUID.randomUUID();
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, outsider)).thenReturn(Optional.empty());

        assertThat(service.findRole(workspaceId, outsider)).isEmpty();
        assertThat(service.findRole(null, outsider)).isEmpty();
        assertThat(service.findRole(workspaceId, null)).isEmpty();
    }

    @Test
    @DisplayName("Lọc theo role chỉ trả về đúng nhóm vai trò đó")
    void getMembers_roleFilter() {
        givenMember(ownerId, WorkspaceRole.OWNER);
        when(memberRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(
                WorkspaceMember.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                        .userId(ownerId).role(WorkspaceRole.OWNER).build(),
                WorkspaceMember.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                        .userId(plainMemberId).role(WorkspaceRole.MEMBER).build()));
        when(identityClient.getUsersByIds(anyList())).thenReturn(ApiResponse.success(List.of()));

        PageResponse<WorkspaceMemberResponse> page = service.getMembers(
                workspaceId, ownerId, null, WorkspaceRole.MEMBER, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getRole()).isEqualTo(WorkspaceRole.MEMBER);
    }
}
