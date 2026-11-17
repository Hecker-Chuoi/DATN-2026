package com.datn.workspaceservice.service.impl;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workspaceservice.client.IdentityClient;
import com.datn.workspaceservice.client.dto.UserResponse;
import com.datn.workspaceservice.dto.request.CreateWorkspaceRequest;
import com.datn.workspaceservice.dto.request.UpdateWorkspaceRequest;
import com.datn.workspaceservice.dto.response.WorkspaceResponse;
import com.datn.workspaceservice.entity.Workspace;
import com.datn.workspaceservice.entity.WorkspaceMember;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.repository.WorkspaceMemberRepository;
import com.datn.workspaceservice.repository.WorkspaceRepository;
import com.datn.workspaceservice.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final int MAX_SLUG_LENGTH = 100;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final IdentityClient identityClient;

    @Override
    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, UUID currentUserId) {
        String slug = resolveSlug(request.getSlug(), request.getName());

        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .createdBy(currentUserId)
                .build());

        // Người tạo trở thành OWNER trong CÙNG transaction. Nếu tách ra, một lỗi
        // ở bước hai sẽ để lại workspace không có OWNER nào - đúng cái brick mà
        // assertNotLastOwner tồn tại để ngăn.
        memberRepository.save(WorkspaceMember.builder()
                .workspaceId(workspace.getId())
                .userId(currentUserId)
                .role(WorkspaceRole.OWNER)
                .build());

        log.info("Đã tạo workspace {} (slug={}) với OWNER {}", workspace.getId(), slug, currentUserId);
        return toResponse(workspace, WorkspaceRole.OWNER, 1L, 1L);
    }

    @Override
    @Transactional
    public synchronized WorkspaceResponse createDefaultWorkspace(UUID requestedUserId) {
        // Quy ước toàn hệ thống: user id là Keycloak sub. Bên gọi (identity-service) có thể gửi users.id
        // của nó, nên tra identity (nhận được cả hai loại id) rồi đổi sang sub trước khi đọc / ghi.
        UserResponse user = null;
        try {
            ApiResponse<UserResponse> userRes = identityClient.getUserById(requestedUserId);
            user = userRes == null ? null : userRes.getData();
        } catch (Exception e) {
            log.warn("Không lấy được thông tin user {} từ identity-service: {}", requestedUserId, e.getMessage());
        }
        UUID userId = keycloakSubOr(user, requestedUserId);

        List<WorkspaceMember> existingMemberships = memberRepository.findByUserId(userId);
        if (!existingMemberships.isEmpty()) {
            Workspace firstWs = workspaceRepository.findByIdAndDeletedAtIsNull(existingMemberships.get(0).getWorkspaceId())
                    .orElse(null);
            if (firstWs != null) {
                return toResponse(firstWs, existingMemberships.get(0).getRole(), 1L, 1L);
            }
        }

        String displayName = "Cá nhân";
        String username = "user";
        if (user != null) {
            if (user.getFullName() != null && !user.getFullName().isBlank()) {
                displayName = user.getFullName().trim();
            } else if (user.getUsername() != null && !user.getUsername().isBlank()) {
                displayName = user.getUsername().trim();
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                username = user.getUsername().trim();
            }
        }

        String wsName = "Không gian của " + displayName;
        String slug = resolveSlug(null, "ws-" + username);

        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(wsName)
                .slug(slug)
                .description("Không gian làm việc mặc định của " + displayName)
                .createdBy(userId)
                .build());

        memberRepository.save(WorkspaceMember.builder()
                .workspaceId(workspace.getId())
                .userId(userId)
                .role(WorkspaceRole.OWNER)
                .build());

        log.info("Đã tự động khởi tạo default workspace {} (slug={}) cho user {}", workspace.getId(), slug, userId);
        return toResponse(workspace, WorkspaceRole.OWNER, 1L, 1L);
    }

    /** Keycloak sub của user (lấy từ identity-service); không có thì giữ nguyên id được gửi tới. */
    private static UUID keycloakSubOr(UserResponse user, UUID fallback) {
        if (user == null || user.getKeycloakId() == null || user.getKeycloakId().isBlank()) return fallback;
        try {
            return UUID.fromString(user.getKeycloakId());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override
    @Transactional
    public List<WorkspaceResponse> getMyWorkspaces(UUID currentUserId) {
        List<WorkspaceMember> memberships = memberRepository.findByUserId(currentUserId);
        if (memberships.isEmpty()) {
            log.info("User {} chưa thuộc workspace nào, tự động khởi tạo default workspace...", currentUserId);
            try {
                WorkspaceResponse defaultWs = createDefaultWorkspace(currentUserId);
                return List.of(defaultWs);
            } catch (Exception e) {
                log.error("Lỗi khi tự động tạo default workspace cho user {}: {}", currentUserId, e.getMessage(), e);
                return List.of();
            }
        }

        Map<UUID, WorkspaceRole> roleByWorkspace = memberships.stream()
                .collect(Collectors.toMap(WorkspaceMember::getWorkspaceId, WorkspaceMember::getRole, (a, b) -> a));

        // findByIdInAndDeletedAtIsNull lọc luôn workspace đã soft-delete, nên hàng
        // membership còn sót lại của một workspace đã xóa không lọt ra ngoài.
        List<Workspace> workspaces =
                workspaceRepository.findByIdInAndDeletedAtIsNull(List.copyOf(roleByWorkspace.keySet()));

        if (workspaces.isEmpty()) {
            log.info("Tất cả workspace của user {} đã bị xóa, tự động tạo default workspace mới...", currentUserId);
            try {
                WorkspaceResponse defaultWs = createDefaultWorkspace(currentUserId);
                return List.of(defaultWs);
            } catch (Exception e) {
                log.error("Lỗi khi tạo default workspace: {}", e.getMessage());
                return List.of();
            }
        }

        // Một query duy nhất cho toàn bộ thành viên của các workspace này, thay vì
        // một query cho mỗi workspace (N+1).
        Map<UUID, List<WorkspaceMember>> membersByWorkspace =
                memberRepository.findByWorkspaceIdIn(workspaces.stream().map(Workspace::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(WorkspaceMember::getWorkspaceId));

        return workspaces.stream()
                .map(ws -> {
                    List<WorkspaceMember> all = membersByWorkspace.getOrDefault(ws.getId(), List.of());
                    long owners = all.stream().filter(m -> m.getRole() == WorkspaceRole.OWNER).count();
                    return toResponse(ws, roleByWorkspace.get(ws.getId()), all.size(), owners);
                })
                .sorted(Comparator.comparing(WorkspaceResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspaceById(UUID workspaceId, UUID currentUserId) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> notFound(workspaceId));

        // Không rò rỉ sự tồn tại: người ngoài nhận 404 giống như workspace không có.
        WorkspaceRole myRole = memberRepository.findByWorkspaceIdAndUserId(workspaceId, currentUserId)
                .map(WorkspaceMember::getRole)
                .orElseThrow(() -> notFound(workspaceId));

        List<WorkspaceMember> all = memberRepository.findByWorkspaceId(workspaceId);
        long owners = all.stream().filter(m -> m.getRole() == WorkspaceRole.OWNER).count();
        return toResponse(workspace, myRole, all.size(), owners);
    }

    @Override
    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request, UUID currentUserId) {
        // Bậc vai trò đã được WorkspaceRoleGuard kiểm tra ở controller (ADMIN).
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> notFound(workspaceId));

        workspace.setName(request.getName().trim());
        workspace.setDescription(request.getDescription());
        workspace.setAvatarUrl(request.getAvatarUrl());
        Workspace saved = workspaceRepository.save(workspace);

        WorkspaceRole myRole = memberRepository.findByWorkspaceIdAndUserId(workspaceId, currentUserId)
                .map(WorkspaceMember::getRole)
                .orElse(null);
        List<WorkspaceMember> all = memberRepository.findByWorkspaceId(workspaceId);
        long owners = all.stream().filter(m -> m.getRole() == WorkspaceRole.OWNER).count();

        log.info("Đã cập nhật workspace {}", workspaceId);
        return toResponse(saved, myRole, all.size(), owners);
    }

    @Override
    @Transactional
    public void deleteWorkspace(UUID workspaceId, UUID currentUserId) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> notFound(workspaceId));

        // Soft delete: giữ nguyên toàn bộ hàng membership, nên restore sẽ trả lại
        // workspace kèm đúng OWNER cũ. Cũng vì thế deleteWorkspace KHÔNG gọi
        // assertNotLastOwner - không có OWNER nào bị mất ở đây.
        workspace.setDeletedAt(LocalDateTime.now());
        workspaceRepository.save(workspace);

        log.info("Đã chuyển workspace {} vào thùng rác (bởi {})", workspaceId, currentUserId);
    }

    @Override
    @Transactional
    public WorkspaceResponse restoreWorkspace(UUID workspaceId, UUID currentUserId) {
        // CHÚ Ý: dùng findById (không phải findByIdAndDeletedAtIsNull) vì đối tượng
        // cần restore chính là hàng đã bị soft-delete.
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> notFound(workspaceId));

        if (workspace.getDeletedAt() == null) {
            throw new BadRequestException("Workspace này chưa bị xóa");
        }

        workspace.setDeletedAt(null);
        Workspace saved = workspaceRepository.save(workspace);

        WorkspaceRole myRole = memberRepository.findByWorkspaceIdAndUserId(workspaceId, currentUserId)
                .map(WorkspaceMember::getRole)
                .orElse(null);
        List<WorkspaceMember> all = memberRepository.findByWorkspaceId(workspaceId);
        long owners = all.stream().filter(m -> m.getRole() == WorkspaceRole.OWNER).count();

        log.info("Đã phục hồi workspace {}", workspaceId);
        return toResponse(saved, myRole, all.size(), owners);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getWorkspaceIdsOfUser(UUID userId) {
        List<UUID> ids = memberRepository.findByUserId(userId).stream()
                .map(WorkspaceMember::getWorkspaceId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        // Loại workspace đã soft-delete để work-service không scope query vào chúng.
        return workspaceRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .map(Workspace::getId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActive(UUID workspaceId) {
        return workspaceRepository.existsByIdAndDeletedAtIsNull(workspaceId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ResourceNotFoundException notFound(UUID workspaceId) {
        return new ResourceNotFoundException("Không tìm thấy workspace: " + workspaceId);
    }

    /**
     * Slug do client gửi thì phải là duy nhất; bỏ trống thì tự sinh từ name và
     * thêm hậu tố -2, -3... cho tới khi không trùng.
     */
    private String resolveSlug(String requestedSlug, String name) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            String slug = requestedSlug.trim().toLowerCase(Locale.ROOT);
            if (workspaceRepository.existsBySlug(slug)) {
                throw new BadRequestException("Slug đã được sử dụng: " + slug);
            }
            return slug;
        }

        String base = slugify(name);
        if (base.isEmpty()) {
            base = "workspace";
        }
        String candidate = base;
        int suffix = 2;
        while (workspaceRepository.existsBySlug(candidate)) {
            String tail = "-" + suffix;
            int keep = Math.min(base.length(), MAX_SLUG_LENGTH - tail.length());
            candidate = base.substring(0, keep) + tail;
            suffix++;
        }
        return candidate;
    }

    /** Bỏ dấu tiếng Việt, hạ chữ thường, thay ký tự lạ bằng dấu gạch ngang. */
    private String slugify(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d")
                .replace("Đ", "D");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > MAX_SLUG_LENGTH ? slug.substring(0, MAX_SLUG_LENGTH) : slug;
    }

    private WorkspaceResponse toResponse(Workspace ws, WorkspaceRole myRole, long memberCount, long ownerCount) {
        return WorkspaceResponse.builder()
                .id(ws.getId())
                .name(ws.getName())
                .slug(ws.getSlug())
                .description(ws.getDescription())
                .avatarUrl(ws.getAvatarUrl())
                .createdBy(ws.getCreatedBy())
                .createdAt(ws.getCreatedAt())
                .updatedAt(ws.getUpdatedAt())
                .deletedAt(ws.getDeletedAt())
                .myRole(myRole)
                .memberCount(memberCount)
                .ownerCount(ownerCount)
                .build();
    }
}
