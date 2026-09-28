package com.datn.workspaceservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workspaceservice.dto.request.CreateWorkspaceRequest;
import com.datn.workspaceservice.dto.response.WorkspaceResponse;
import com.datn.workspaceservice.entity.Workspace;
import com.datn.workspaceservice.entity.WorkspaceMember;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.repository.WorkspaceMemberRepository;
import com.datn.workspaceservice.repository.WorkspaceRepository;
import com.datn.workspaceservice.service.impl.WorkspaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private WorkspaceMemberRepository memberRepository;

    @InjectMocks
    private WorkspaceServiceImpl service;

    private UUID userId;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(i -> {
            Workspace w = i.getArgument(0);
            if (w.getId() == null) {
                w.setId(workspaceId);
            }
            return w;
        });
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CreateWorkspaceRequest createRequest(String name, String slug) {
        CreateWorkspaceRequest r = new CreateWorkspaceRequest();
        r.setName(name);
        r.setSlug(slug);
        return r;
    }

    @Test
    @DisplayName("Tạo workspace đồng thời tạo hàng membership OWNER cho người tạo")
    void create_alsoCreatesOwnerMembership() {
        WorkspaceResponse result = service.createWorkspace(createRequest("Acme Corp", null), userId);

        ArgumentCaptor<WorkspaceMember> captor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(result.getMyRole()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(result.getOwnerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Slug tự sinh từ tên, bỏ dấu tiếng Việt")
    void create_slugifiesVietnameseName() {
        WorkspaceResponse result = service.createWorkspace(createRequest("Đội Kỹ Thuật Số", null), userId);

        assertThat(result.getSlug()).isEqualTo("doi-ky-thuat-so");
    }

    @Test
    @DisplayName("Slug tự sinh bị trùng -> thêm hậu tố -2")
    void create_slugCollision_appendsSuffix() {
        when(workspaceRepository.existsBySlug("acme")).thenReturn(true);
        when(workspaceRepository.existsBySlug("acme-2")).thenReturn(false);

        WorkspaceResponse result = service.createWorkspace(createRequest("Acme", null), userId);

        assertThat(result.getSlug()).isEqualTo("acme-2");
    }

    @Test
    @DisplayName("Slug do client chỉ định mà trùng -> 400 (không tự đổi sau lưng người dùng)")
    void create_explicitDuplicateSlug_isRejected() {
        when(workspaceRepository.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.createWorkspace(createRequest("Acme", "taken"), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Slug đã được sử dụng");
    }

    @Test
    @DisplayName("getMyWorkspaces loại workspace đã soft-delete và workspace không có membership")
    void getMyWorkspaces_excludesDeletedAndNonMember() {
        UUID activeWs = UUID.randomUUID();
        UUID deletedWs = UUID.randomUUID();

        when(memberRepository.findByUserId(userId)).thenReturn(List.of(
                WorkspaceMember.builder().workspaceId(activeWs).userId(userId).role(WorkspaceRole.OWNER).build(),
                WorkspaceMember.builder().workspaceId(deletedWs).userId(userId).role(WorkspaceRole.MEMBER).build()));

        // Repository chỉ trả về workspace còn sống -> workspace đã xóa tự rụng,
        // dù hàng membership của nó vẫn còn (soft delete giữ nguyên membership).
        when(workspaceRepository.findByIdInAndDeletedAtIsNull(anyList())).thenReturn(List.of(
                Workspace.builder().id(activeWs).name("Active").slug("active")
                        .createdBy(userId).createdAt(LocalDateTime.now()).build()));
        when(memberRepository.findByWorkspaceIdIn(anyList())).thenReturn(List.of(
                WorkspaceMember.builder().workspaceId(activeWs).userId(userId).role(WorkspaceRole.OWNER).build()));

        List<WorkspaceResponse> result = service.getMyWorkspaces(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(activeWs);
        assertThat(result.get(0).getMyRole()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(result.get(0).getOwnerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Không tham gia workspace nào -> danh sách rỗng, không query thêm")
    void getMyWorkspaces_noMembership_returnsEmpty() {
        when(memberRepository.findByUserId(userId)).thenReturn(List.of());

        assertThat(service.getMyWorkspaces(userId)).isEmpty();
        verify(workspaceRepository, never()).findByIdInAndDeletedAtIsNull(anyList());
    }

    @Test
    @DisplayName("Xem workspace đã soft-delete -> 404")
    void getById_softDeleted_isNotFound() {
        when(workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWorkspaceById(workspaceId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Người ngoài xem workspace -> 404 (không rò rỉ sự tồn tại)")
    void getById_nonMember_isNotFound() {
        when(workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)).thenReturn(
                Optional.of(Workspace.builder().id(workspaceId).name("Secret").slug("secret").build()));
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWorkspaceById(workspaceId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Xóa workspace là SOFT delete: set deletedAt, không gọi delete()")
    void delete_isSoftDelete() {
        Workspace ws = Workspace.builder().id(workspaceId).name("Acme").slug("acme").build();
        when(workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)).thenReturn(Optional.of(ws));

        service.deleteWorkspace(workspaceId, userId);

        assertThat(ws.getDeletedAt()).isNotNull();
        verify(workspaceRepository, never()).delete(any());
        // Membership giữ nguyên để restore trả lại workspace kèm đúng OWNER cũ.
        verify(memberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Restore xóa deletedAt")
    void restore_clearsDeletedAt() {
        Workspace ws = Workspace.builder().id(workspaceId).name("Acme").slug("acme")
                .deletedAt(LocalDateTime.now()).build();
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(ws));
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(
                Optional.of(WorkspaceMember.builder().workspaceId(workspaceId).userId(userId)
                        .role(WorkspaceRole.OWNER).build()));
        when(memberRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of());

        WorkspaceResponse result = service.restoreWorkspace(workspaceId, userId);

        assertThat(ws.getDeletedAt()).isNull();
        assertThat(result.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Restore workspace chưa bị xóa -> 400")
    void restore_notDeleted_isRejected() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(
                Workspace.builder().id(workspaceId).name("Acme").slug("acme").build()));

        assertThatThrownBy(() -> service.restoreWorkspace(workspaceId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chưa bị xóa");
    }

    @Test
    @DisplayName("getWorkspaceIdsOfUser loại workspace đã soft-delete")
    void getWorkspaceIdsOfUser_excludesDeleted() {
        UUID activeWs = UUID.randomUUID();
        when(memberRepository.findByUserId(userId)).thenReturn(List.of(
                WorkspaceMember.builder().workspaceId(activeWs).userId(userId).role(WorkspaceRole.MEMBER).build(),
                WorkspaceMember.builder().workspaceId(UUID.randomUUID()).userId(userId)
                        .role(WorkspaceRole.MEMBER).build()));
        when(workspaceRepository.findByIdInAndDeletedAtIsNull(anyList())).thenReturn(List.of(
                Workspace.builder().id(activeWs).build()));

        assertThat(service.getWorkspaceIdsOfUser(userId)).containsExactly(activeWs);
    }
}
