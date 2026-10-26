package com.datn.workspaceservice.service.impl;

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
import com.datn.workspaceservice.entity.Workspace;
import com.datn.workspaceservice.entity.WorkspaceInvitation;
import com.datn.workspaceservice.entity.WorkspaceMember;
import com.datn.workspaceservice.enums.InvitationStatus;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.repository.WorkspaceInvitationRepository;
import com.datn.workspaceservice.repository.WorkspaceMemberRepository;
import com.datn.workspaceservice.repository.WorkspaceRepository;
import com.datn.workspaceservice.service.InvitationMailer;
import com.datn.workspaceservice.service.WorkspaceMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceMemberServiceImpl implements WorkspaceMemberService {

    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceInvitationRepository invitationRepository;
    private final IdentityClient identityClient;
    private final InvitationMailer mailer;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceRole> findRole(UUID workspaceId, UUID userId) {
        if (workspaceId == null || userId == null) {
            return Optional.empty();
        }
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMember::getRole);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOwners(UUID workspaceId) {
        return memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WorkspaceMemberResponse> getMembers(UUID workspaceId,
                                                            UUID currentUserId,
                                                            String search,
                                                            WorkspaceRole roleFilter,
                                                            Pageable pageable) {
        requireMembership(workspaceId, currentUserId);

        // Nạp toàn bộ thành viên rồi lọc và phân trang trong bộ nhớ.
        //
        // Vì sao không phân trang ở SQL: bộ lọc search chạy trên username/email,
        // vốn nằm ở identity-service chứ không có trong bảng workspace_members.
        // Phân trang ở SQL rồi mới lọc theo tên sẽ cho ra tổng số trang sai.
        // Danh sách thành viên một workspace có quy mô hàng chục nên đánh đổi này
        // chấp nhận được, và đổi lại chỉ có MỘT lời gọi batch sang identity-service.
        List<WorkspaceMember> all = memberRepository.findByWorkspaceId(workspaceId);
        Map<UUID, UserResponse> userMap = hydrateUsers(all);

        String needle = search == null ? null : search.trim().toLowerCase(Locale.ROOT);

        List<WorkspaceMemberResponse> filtered = all.stream()
                .filter(m -> roleFilter == null || m.getRole() == roleFilter)
                .map(m -> toResponse(m, userMap.get(m.getUserId())))
                .filter(r -> matchesSearch(r, needle))
                .sorted(Comparator
                        .comparingInt((WorkspaceMemberResponse r) -> r.getRole().ordinal())
                        .thenComparing(WorkspaceMemberResponse::getJoinedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        int pageSize = Math.max(1, pageable.getPageSize());
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int from = Math.min(pageNumber * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        List<WorkspaceMemberResponse> content = List.copyOf(filtered.subList(from, to));

        int totalPages = (int) Math.ceil((double) filtered.size() / pageSize);
        return PageResponse.of(content, pageNumber, pageSize, filtered.size(), totalPages,
                to >= filtered.size(), pageNumber == 0);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceMemberResponse getMyMembership(UUID workspaceId, UUID currentUserId) {
        WorkspaceMember member = requireMembership(workspaceId, currentUserId);
        return toResponse(member, hydrateUsers(List.of(member)).get(member.getUserId()));
    }

    @Override
    @Transactional
    public WorkspaceInvitationResponse invite(UUID workspaceId,
                                              AddWorkspaceMemberRequest request,
                                              UUID currentUserId) {
        WorkspaceRole callerRole = requireAtLeast(workspaceId, currentUserId, WorkspaceRole.ADMIN);

        // Chỉ OWNER được cấp vai trò OWNER cho người khác.
        if (request.getRole() == WorkspaceRole.OWNER && callerRole != WorkspaceRole.OWNER) {
            throw new ForbiddenException("Chỉ OWNER mới có thể cấp vai trò OWNER");
        }

        UUID targetUserId = resolveUserIdByIdentifier(request.getIdentifier());

        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId)) {
            throw new BadRequestException("Người dùng đã là thành viên của workspace này");
        }
        if (invitationRepository.existsByWorkspaceIdAndInviteeUserIdAndStatus(
                workspaceId, targetUserId, InvitationStatus.PENDING)) {
            throw new BadRequestException("Đã có lời mời đang chờ người dùng này trả lời");
        }

        WorkspaceInvitation saved = invitationRepository.save(WorkspaceInvitation.builder()
                .workspaceId(workspaceId)
                .inviteeUserId(targetUserId)
                .invitedBy(currentUserId)
                .role(request.getRole())
                .build());

        log.info("Workspace invitation created: workspace={}, invitee={}, by={}, role={}",
                workspaceId, targetUserId, currentUserId, request.getRole());
        WorkspaceInvitationResponse res = toInvitationResponse(saved);
        mailer.send(res);
        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceInvitationResponse> listMyInvitations(UUID userId) {
        return invitationRepository.findByInviteeUserIdAndStatusOrderByCreatedAtDesc(userId, InvitationStatus.PENDING)
                .stream().map(this::toInvitationResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceInvitationResponse> listInvitations(UUID workspaceId) {
        return invitationRepository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, InvitationStatus.PENDING)
                .stream().map(this::toInvitationResponse).toList();
    }

    @Override
    @Transactional
    public WorkspaceInvitationResponse acceptInvitation(UUID invitationId, UUID userId) {
        WorkspaceInvitation inv = requirePendingForInvitee(invitationId, userId);
        if (!workspaceRepository.existsByIdAndDeletedAtIsNull(inv.getWorkspaceId())) {
            throw new BadRequestException("Workspace không còn tồn tại");
        }
        if (!memberRepository.existsByWorkspaceIdAndUserId(inv.getWorkspaceId(), userId)) {
            memberRepository.save(WorkspaceMember.builder()
                    .workspaceId(inv.getWorkspaceId())
                    .userId(userId)
                    .role(inv.getRole())
                    .invitedBy(inv.getInvitedBy())
                    .build());
        }
        inv.setStatus(InvitationStatus.ACCEPTED);
        inv.setRespondedAt(LocalDateTime.now());
        log.info("Workspace invitation {} accepted by {}", invitationId, userId);
        return toInvitationResponse(invitationRepository.save(inv));
    }

    @Override
    @Transactional
    public WorkspaceInvitationResponse declineInvitation(UUID invitationId, UUID userId) {
        WorkspaceInvitation inv = requirePendingForInvitee(invitationId, userId);
        inv.setStatus(InvitationStatus.DECLINED);
        inv.setRespondedAt(LocalDateTime.now());
        return toInvitationResponse(invitationRepository.save(inv));
    }

    @Override
    @Transactional
    public WorkspaceInvitationResponse cancelInvitation(UUID invitationId, UUID userId) {
        WorkspaceInvitation inv = requirePending(invitationId);
        boolean allowed = userId.equals(inv.getInvitedBy())
                || findRole(inv.getWorkspaceId(), userId).map(r -> r.atLeast(WorkspaceRole.ADMIN)).orElse(false);
        if (!allowed) {
            throw new ForbiddenException("Bạn không có quyền hủy lời mời này");
        }
        inv.setStatus(InvitationStatus.CANCELLED);
        inv.setRespondedAt(LocalDateTime.now());
        return toInvitationResponse(invitationRepository.save(inv));
    }

    @Override
    @Transactional
    public WorkspaceMemberResponse updateMemberRole(UUID workspaceId,
                                                    UUID targetUserId,
                                                    WorkspaceRole newRole,
                                                    UUID currentUserId) {
        WorkspaceRole callerRole = requireAtLeast(workspaceId, currentUserId, WorkspaceRole.ADMIN);

        if (Objects.equals(targetUserId, currentUserId)) {
            throw new BadRequestException("Bạn không thể tự thay đổi vai trò của chính mình");
        }

        WorkspaceMember target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không phải thành viên của workspace này"));

        // Mọi thao tác liên quan tới vai trò OWNER (hạ một OWNER xuống, hoặc nâng
        // người khác lên OWNER) đều chỉ OWNER được làm.
        boolean touchesOwner = target.getRole() == WorkspaceRole.OWNER || newRole == WorkspaceRole.OWNER;
        if (touchesOwner && callerRole != WorkspaceRole.OWNER) {
            throw new ForbiddenException("Chỉ OWNER mới có thể thay đổi vai trò liên quan tới OWNER");
        }

        // Chỉ chặn khi thao tác này LÀM MẤT một OWNER. Update OWNER -> OWNER là
        // no-op và không được throw.
        if (newRole != WorkspaceRole.OWNER) {
            assertNotLastOwner(workspaceId, target);
        }

        target.setRole(newRole);
        WorkspaceMember saved = memberRepository.save(target);

        log.info("Đã đổi vai trò user {} trong workspace {} thành {}", targetUserId, workspaceId, newRole);
        return toResponse(saved, hydrateUsers(List.of(saved)).get(targetUserId));
    }

    @Override
    @Transactional
    public void removeMember(UUID workspaceId, UUID targetUserId, UUID currentUserId) {
        WorkspaceRole callerRole = requireAtLeast(workspaceId, currentUserId, WorkspaceRole.ADMIN);

        if (Objects.equals(targetUserId, currentUserId)) {
            throw new BadRequestException("Bạn không thể tự xóa chính mình (dùng chức năng Rời workspace)");
        }

        WorkspaceMember target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không phải thành viên của workspace này"));

        if (target.getRole() == WorkspaceRole.OWNER && callerRole != WorkspaceRole.OWNER) {
            throw new ForbiddenException("Chỉ OWNER mới có thể xóa một OWNER khác");
        }

        assertNotLastOwner(workspaceId, target);
        assertNotLeadingAnyProject(workspaceId, targetUserId);

        memberRepository.delete(target);
        cleanupProjectMemberships(workspaceId, targetUserId);
        log.info("Đã xóa user {} khỏi workspace {}", targetUserId, workspaceId);
    }

    @Override
    @Transactional
    public void leaveWorkspace(UUID workspaceId, UUID currentUserId) {
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Bạn không phải thành viên của workspace này"));

        assertNotLastOwner(workspaceId, member);
        assertNotLeadingAnyProject(workspaceId, currentUserId);

        memberRepository.delete(member);
        cleanupProjectMemberships(workspaceId, currentUserId);
        log.info("User {} đã rời workspace {}", currentUserId, workspaceId);
    }

    private void assertNotLeadingAnyProject(UUID workspaceId, UUID userId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM projects WHERE workspace_id = ? AND lead_user_id = ? AND deleted_at IS NULL",
                    Integer.class,
                    workspaceId,
                    userId
            );
            if (count != null && count > 0) {
                throw new BadRequestException("Không thể xóa thành viên này (hoặc không thể rời Workspace) vì người dùng đang là Project Lead của một hoặc nhiều dự án trong Workspace này. Vui lòng bàn giao vai trò Lead cho thành viên khác trước.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Bảng projects có thể chưa sẵn sàng hoặc lỗi query: {}", e.getMessage());
        }
    }

    private void cleanupProjectMemberships(UUID workspaceId, UUID userId) {
        try {
            jdbcTemplate.update(
                    "DELETE FROM role_assignments WHERE user_id = ? AND project_id IN (SELECT id FROM projects WHERE workspace_id = ?)",
                    userId,
                    workspaceId
            );
        } catch (Exception e) {
            log.debug("Không dọn dẹp được role_assignments: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bất biến: workspace LUÔN có ít nhất một OWNER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Chặn mọi thao tác làm workspace mất OWNER cuối cùng.
     * <p>
     * Phải gọi TRƯỚC mọi mutation làm mất vai trò OWNER của một hàng thành viên.
     * Method tự guard theo vai trò của target nên gọi vô điều kiện vẫn an toàn.
     * <p>
     * Vì sao phải khóa ghi trước khi đếm: hai OWNER gọi /leave đồng thời sẽ CÙNG
     * đọc được count = 2, CÙNG pass, CÙNG xóa, và workspace về zero OWNER - lúc
     * đó không ai còn quyền nâng vai trò cho ai nữa, workspace bị brick vĩnh viễn.
     * lockOwners giữ SELECT ... FOR UPDATE trên các hàng OWNER trong transaction,
     * nên transaction thứ hai phải chờ transaction thứ nhất commit rồi đọc lại
     * count = 1 và throw. Đây đúng là lớp lỗi mà ProjectMemberServiceImpl mắc khi
     * kiểm tra project lead bằng một phép đọc thuần, chỉ là hậu quả nặng hơn.
     * <p>
     * Dùng BadRequestException (400) theo tiền lệ gần nhất trong codebase, không
     * dùng ForbiddenException: người gọi CÓ quyền, chỉ là chuyển trạng thái này
     * không hợp lệ.
     */
    private void assertNotLastOwner(UUID workspaceId, WorkspaceMember target) {
        if (target.getRole() != WorkspaceRole.OWNER) {
            return;
        }

        memberRepository.lockOwners(workspaceId);
        long owners = memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER);
        if (owners <= 1) {
            throw new BadRequestException(
                    "Không thể thực hiện: workspace phải có ít nhất một OWNER. "
                            + "Hãy chuyển quyền sở hữu cho thành viên khác trước.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Người không phải thành viên nhận 404, KHÔNG phải 403: workspace là ranh
     * giới tenant nên trả 403 sẽ tiết lộ rằng workspace đó có tồn tại.
     */
    private WorkspaceMember requireMembership(UUID workspaceId, UUID userId) {
        if (!workspaceRepository.existsByIdAndDeletedAtIsNull(workspaceId)) {
            throw new ResourceNotFoundException("Không tìm thấy workspace: " + workspaceId);
        }
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy workspace: " + workspaceId));
    }

    private WorkspaceRole requireAtLeast(UUID workspaceId, UUID userId, WorkspaceRole min) {
        WorkspaceRole role = requireMembership(workspaceId, userId).getRole();
        if (!role.atLeast(min)) {
            throw new ForbiddenException(String.format(
                    "Bạn cần vai trò %s trở lên trong workspace này (vai trò hiện tại: %s)", min, role));
        }
        return role;
    }

    private UUID resolveUserIdByIdentifier(String identifier) {
        ApiResponse<UserResponse> response;
        try {
            response = identityClient.getUserByIdentifier(identifier);
        } catch (Exception e) {
            log.warn("Không tra được user theo identifier [{}]: {}", identifier, e.getMessage());
            throw new ResourceNotFoundException("Không tìm thấy người dùng: " + identifier);
        }
        if (response == null || response.getData() == null || response.getData().getId() == null) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng: " + identifier);
        }
        // Luôn lưu users.id, không bao giờ lưu keycloakId - xem CurrentUserResolver.
        return response.getData().getId();
    }

    /**
     * Hydrate tên/email của nhiều thành viên bằng MỘT lời gọi batch.
     * <p>
     * Không bao giờ gọi getUserById theo từng member (tránh N+1 calls)
     * làm vậy và tốn 4+ round-trip chỉ để xóa một thành viên.
     * <p>
     * Khi identity-service lỗi thì degrade - trả map rỗng để các trường hiển thị
     * là null, thay vì để cả request 500. Danh sách thành viên vẫn dùng được.
     */
    private Map<UUID, UserResponse> hydrateUsers(List<WorkspaceMember> members) {
        return hydrateUserIds(members.stream().map(WorkspaceMember::getUserId).toList());
    }

    private Map<UUID, UserResponse> hydrateUserIds(List<UUID> userIds) {
        List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ApiResponse<List<UserResponse>> response = identityClient.getUsersByIds(ids);
            if (response == null || response.getData() == null) {
                return Collections.emptyMap();
            }
            return response.getData().stream()
                    .filter(u -> u.getId() != null)
                    // Chỉ khóa theo users.id. KHÔNG khóa kép theo cả keycloakId như
                    // ProjectMemberServiceImpl phải làm - ở đây id đã được chuẩn hóa.
                    .collect(Collectors.toMap(UserResponse::getId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.debug("Không hydrate được thông tin user từ identity-service: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private boolean matchesSearch(WorkspaceMemberResponse r, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(r.getUsername(), needle)
                || containsIgnoreCase(r.getEmail(), needle)
                || containsIgnoreCase(r.getFullName(), needle);
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    // ─── Lời mời ─────────────────────────────────────────────────────────────

    private WorkspaceInvitation requirePending(UUID invitationId) {
        WorkspaceInvitation inv = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lời mời: " + invitationId));
        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Lời mời đã được xử lý trước đó");
        }
        return inv;
    }

    private WorkspaceInvitation requirePendingForInvitee(UUID invitationId, UUID userId) {
        WorkspaceInvitation inv = requirePending(invitationId);
        if (!userId.equals(inv.getInviteeUserId())) {
            throw new ForbiddenException("Lời mời này không dành cho bạn");
        }
        return inv;
    }

    /** ponytail: 1 query workspace + 1 batch identity cho mỗi lời mời; gom batch khi danh sách dài. */
    private WorkspaceInvitationResponse toInvitationResponse(WorkspaceInvitation inv) {
        Map<UUID, UserResponse> users = hydrateUserIds(List.of(inv.getInvitedBy(), inv.getInviteeUserId()));
        UserResponse inviter = users.get(inv.getInvitedBy());
        UserResponse invitee = users.get(inv.getInviteeUserId());
        return WorkspaceInvitationResponse.builder()
                .id(inv.getId())
                .workspaceId(inv.getWorkspaceId())
                .workspaceName(workspaceRepository.findById(inv.getWorkspaceId()).map(Workspace::getName).orElse(null))
                .role(inv.getRole())
                .invitedBy(inv.getInvitedBy())
                .inviterName(displayName(inviter))
                .inviteeUserId(inv.getInviteeUserId())
                .inviteeName(displayName(invitee))
                .inviteeEmail(invitee == null ? null : invitee.getEmail())
                .status(inv.getStatus())
                .createdAt(inv.getCreatedAt())
                .build();
    }

    private static String displayName(UserResponse u) {
        if (u == null) return null;
        String full = u.getFullName();
        if (full != null && !full.isBlank()) return full;
        return u.getUsername() != null ? u.getUsername() : u.getEmail();
    }

    private WorkspaceMemberResponse toResponse(WorkspaceMember member, UserResponse user) {
        return WorkspaceMemberResponse.builder()
                .id(member.getId())
                .workspaceId(member.getWorkspaceId())
                .userId(member.getUserId())
                .role(member.getRole())
                .invitedBy(member.getInvitedBy())
                .joinedAt(member.getJoinedAt())
                .username(user == null ? null : user.getUsername())
                .email(user == null ? null : user.getEmail())
                .fullName(user == null ? null : user.getFullName())
                .build();
    }
}
