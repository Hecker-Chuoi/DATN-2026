package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.CloneProjectRequest;
import com.datn.workservice.dto.request.CreateProjectFromTemplateRequest;
import com.datn.workservice.dto.request.SaveAsTemplateRequest;
import com.datn.workservice.dto.response.TemplateInstantiationResponse;
import com.datn.workservice.dto.response.TemplateIntegrityReport;
import com.datn.workservice.dto.response.TemplatePreviewResponse;
import com.datn.workservice.dto.response.TemplateSummaryResponse;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.ProjectClone;
import com.datn.workservice.entity.ProjectRole;
import com.datn.workservice.entity.RoleAssignment;
import com.datn.workservice.enums.CloneType;
import com.datn.workservice.enums.DefaultAssigneeType;
import com.datn.workservice.enums.TemplateCategory;
import com.datn.workservice.repository.ProjectCloneRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.ProjectRoleRepository;
import com.datn.workservice.repository.RoleAssignmentRepository;
import com.datn.workservice.security.WorkspaceAccessChecker;
import com.datn.workservice.service.ProjectAccessService;
import com.datn.workservice.service.ProjectMemberService;
import com.datn.workservice.service.ProjectService;
import com.datn.workservice.service.ProjectTemplateService;
import com.datn.workservice.service.template.ProjectBlueprint;
import com.datn.workservice.service.template.ProjectBlueprintLoader;
import com.datn.workservice.service.template.ProjectCloneEngine;
import com.datn.workservice.service.template.TemplateIntegrityValidator;
import com.datn.workservice.service.template.TemplatePreviewBuilder;
import com.datn.workservice.service.template.TemplateDataSeeder;
import com.datn.workservice.specification.ProjectSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectTemplateServiceImpl implements ProjectTemplateService {

    private static final String ROLE_ADMINISTRATOR = "ADMINISTRATOR";

    private final ProjectRepository projectRepository;
    private final ProjectCloneRepository projectCloneRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ProjectBlueprintLoader blueprintLoader;
    private final ProjectCloneEngine cloneEngine;
    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final ProjectAccessService projectAccessService;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final TemplateDataSeeder templateDataSeeder;

    // ─── THƯ VIỆN MẪU & XEM TRƯỚC ─────────────────────────────────────────────

    @Override
    @Transactional
    public List<TemplateSummaryResponse> listTemplates(List<UUID> workspaceIds, TemplateCategory category, String search) {
        Specification<Project> spec = ProjectSpecification.withFilters(workspaceIds, null, search, true, false);
        if (category != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("templateCategory"), category));
        }
        List<TemplateSummaryResponse> list = projectRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .map(t -> TemplatePreviewBuilder.summary(blueprintLoader.load(t), usageCount(t.getId())))
                .toList();

        if (list.isEmpty()) {
            try {
                templateDataSeeder.seedDefaultTemplates();
                return projectRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                        .map(t -> TemplatePreviewBuilder.summary(blueprintLoader.load(t), usageCount(t.getId())))
                        .toList();
            } catch (Exception e) {
                log.warn("Tự động seed các mẫu mặc định khi xem thư viện gặp thông báo: {}", e.getMessage());
            }
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public TemplatePreviewResponse getPreview(UUID templateId, UUID currentUserId) {
        Project template = requireTemplate(templateId);
        assertCanUseTemplate(template, currentUserId);
        return TemplatePreviewBuilder.preview(blueprintLoader.load(template), usageCount(templateId));
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateIntegrityReport checkIntegrity(UUID projectId) {
        return TemplateIntegrityValidator.validate(blueprintLoader.load(requireProject(projectId)));
    }

    // ─── TẠO DỰ ÁN TỪ MẪU ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public TemplateInstantiationResponse createFromTemplate(UUID templateId, CreateProjectFromTemplateRequest request,
                                                            UUID currentUserId) {
        Project template = requireTemplate(templateId);
        assertCanUseTemplate(template, currentUserId);
        workspaceAccessChecker.requireAtLeast(request.getWorkspaceId(), currentUserId, "MEMBER");

        UUID leadUserId = request.getLeadUserId() != null ? request.getLeadUserId() : currentUserId;
        projectService.assertCanCreateProject(request.getTeamId(), currentUserId, leadUserId);

        ProjectBlueprint blueprint = blueprintLoader.load(template);
        assertValid(TemplateIntegrityValidator.validate(blueprint), "Mẫu chưa hợp lệ, không thể dùng để tạo dự án");

        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        long offset = dateOffset(blueprint, startDate);
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : shiftedEnd(blueprint, startDate, offset);
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("Hạn chót dự án phải sau ngày bắt đầu");
        }

        Project target = Project.builder()
                .workspaceId(request.getWorkspaceId())
                .teamId(request.getTeamId())
                .name(request.getName().trim())
                .key(normalizeKey(request.getKey()))
                .description(request.getDescription() != null ? request.getDescription() : template.getDescription())
                .iconUrl(template.getIconUrl())
                .leadUserId(leadUserId)
                .defaultAssigneeType(template.getDefaultAssigneeType() != null
                        ? template.getDefaultAssigneeType() : DefaultAssigneeType.PROJECT_LEADER)
                .projectType(template.getProjectType())
                .isTemplate(false)
                .startDate(startDate)
                .endDate(endDate)
                .createdBy(currentUserId)
                .build();

        ProjectCloneEngine.Options options = new ProjectCloneEngine.Options(
                !Boolean.FALSE.equals(request.getIncludeSprints()),
                !Boolean.FALSE.equals(request.getIncludeWorkItems()),
                !Boolean.FALSE.equals(request.getIncludeVersions()),
                offset,
                request.getCustomizations());
        return cloneAndFinalize(blueprint, target, options, CloneType.FROM_TEMPLATE, currentUserId);
    }

    // ─── LƯU DỰ ÁN THÀNH MẪU ───────────────────────────────────────────────────

    @Override
    @Transactional
    public TemplateInstantiationResponse saveAsTemplate(UUID projectId, SaveAsTemplateRequest request, UUID currentUserId) {
        Project source = requireProject(projectId);
        UUID teamId = request.getTeamId() != null ? request.getTeamId() : source.getTeamId();
        projectService.assertCanCreateProject(teamId, currentUserId, currentUserId);

        Project target = Project.builder()
                .workspaceId(source.getWorkspaceId())
                .teamId(teamId)
                .name(request.getName().trim())
                .key(normalizeKey(request.getKey()))
                .description(request.getDescription() != null ? request.getDescription() : source.getDescription())
                .iconUrl(source.getIconUrl())
                .leadUserId(currentUserId)
                .defaultAssigneeType(source.getDefaultAssigneeType())
                .projectType(source.getProjectType())
                .isTemplate(true)
                .templateCategory(request.getTemplateCategory() != null ? request.getTemplateCategory() : TemplateCategory.OTHER)
                .startDate(source.getStartDate())
                .endDate(source.getEndDate())
                .createdBy(currentUserId)
                .build();

        // Mẫu giữ nguyên mốc thời gian của dự án nguồn (offset = 0); dời lịch xảy ra khi dùng mẫu.
        ProjectCloneEngine.Options options = new ProjectCloneEngine.Options(
                !Boolean.FALSE.equals(request.getIncludeSprints()),
                !Boolean.FALSE.equals(request.getIncludeWorkItems()),
                !Boolean.FALSE.equals(request.getIncludeVersions()),
                0);
        return cloneAndFinalize(blueprintLoader.load(source), target, options, CloneType.SAVE_AS_TEMPLATE, currentUserId);
    }

    // ─── NHÂN BẢN DỰ ÁN ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TemplateInstantiationResponse duplicateProject(UUID projectId, CloneProjectRequest request, UUID currentUserId) {
        Project source = requireProject(projectId);
        projectService.assertCanCreateProject(source.getTeamId(), currentUserId, currentUserId);

        ProjectBlueprint blueprint = blueprintLoader.load(source);
        long offset = dateOffset(blueprint, request.getStartDate());

        Project target = Project.builder()
                .workspaceId(source.getWorkspaceId())
                .teamId(source.getTeamId())
                .name(request.getName().trim())
                .key(normalizeKey(request.getKey()))
                .description(source.getDescription())
                .iconUrl(source.getIconUrl())
                .leadUserId(currentUserId)
                .defaultAssigneeType(source.getDefaultAssigneeType())
                .projectType(source.getProjectType())
                .isTemplate(source.getIsTemplate())
                .templateCategory(source.getTemplateCategory())
                .startDate(request.getStartDate())
                .endDate(shiftedEnd(blueprint, request.getStartDate(), offset))
                .createdBy(currentUserId)
                .build();

        ProjectCloneEngine.Options options = new ProjectCloneEngine.Options(
                !Boolean.FALSE.equals(request.getIncludeSprints()),
                !Boolean.FALSE.equals(request.getIncludeItems()),
                true,
                offset);
        return cloneAndFinalize(blueprint, target, options, CloneType.DUPLICATE, currentUserId);
    }

    // ─── LÕI CHUNG ─────────────────────────────────────────────────────────────

    /**
     * Lưu dự án đích → nhân bản sâu → gán thành viên → ghi nhật ký project_clones →
     * kiểm tra toàn vẹn dự án đích. Lỗi toàn vẹn ở bước cuối ném ngoại lệ để rollback toàn bộ.
     */
    private TemplateInstantiationResponse cloneAndFinalize(ProjectBlueprint source, Project target,
                                                           ProjectCloneEngine.Options options,
                                                           CloneType cloneType, UUID currentUserId) {
        if (projectRepository.existsByKeyIgnoreCase(target.getKey())) {
            throw new BadRequestException("Mã dự án '" + target.getKey() + "' đã tồn tại trong hệ thống");
        }
        Project saved = projectRepository.save(target);

        Map<String, Integer> stats = cloneEngine.cloneInto(source, saved, options, currentUserId);
        assignInitialMembers(saved, currentUserId);

        Map<String, Object> optionMap = new LinkedHashMap<>();
        optionMap.put("includeSprints", options.includeSprints());
        optionMap.put("includeWorkItems", options.includeWorkItems());
        optionMap.put("includeVersions", options.includeVersions());
        projectCloneRepository.save(ProjectClone.builder()
                .sourceProjectId(source.project().getId())
                .clonedProjectId(saved.getId())
                .clonedBy(currentUserId)
                .cloneType(cloneType)
                .dateOffsetDays((int) options.dateOffsetDays())
                .cloneOptions(optionMap)
                .cloneStats(new LinkedHashMap<>(stats))
                .build());

        TemplateIntegrityReport report = TemplateIntegrityValidator.validate(blueprintLoader.load(saved));
        assertValid(report, "Dự án tạo ra vi phạm ràng buộc");

        log.info("{}: {} ({}) -> {} ({}), offset={} ngày", cloneType, source.project().getKey(),
                source.project().getId(), saved.getKey(), saved.getId(), options.dateOffsetDays());

        return TemplateInstantiationResponse.builder()
                .project(projectService.toProjectResponse(saved))
                .sourceProjectId(source.project().getId())
                .cloneType(cloneType)
                .dateOffsetDays(options.dateOffsetDays())
                .stats(stats)
                .integrity(report)
                .build();
    }

    /**
     * Thành viên của mẫu KHÔNG được sao chép. Dự án mới có: Lead + người tạo là ADMINISTRATOR,
     * cộng thành viên nhóm (nếu gắn nhóm) theo quy tắc LEADER→ADMINISTRATOR, VIEWER→VIEWER, còn lại MEMBER.
     */
    private void assignInitialMembers(Project project, UUID currentUserId) {
        ProjectRole adminRole = projectRoleRepository.findByProjectIdAndName(project.getId(), ROLE_ADMINISTRATOR)
                .orElseThrow(() -> new BadRequestException("Mẫu thiếu vai trò ADMINISTRATOR"));
        Set<UUID> admins = new LinkedHashSet<>();
        admins.add(project.getLeadUserId());
        admins.add(currentUserId);
        for (UUID userId : admins) {
            if (userId != null) {
                roleAssignmentRepository.save(RoleAssignment.builder()
                        .projectId(project.getId())
                        .userId(userId)
                        .roleId(adminRole.getId())
                        .build());
            }
        }
        projectMemberService.syncTeamMembersToProject(project.getId());
    }

    /**
     * Mẫu dùng được khi: là mẫu còn sống, và người dùng là Global ADMIN, thành viên của chính mẫu,
     * hoặc thành viên (bất kỳ vai trò) của workspace chứa mẫu.
     */
    private void assertCanUseTemplate(Project template, UUID userId) {
        if (projectAccessService.isGlobalAdmin()) {
            return;
        }
        // Mẫu hệ thống thuộc Default Workspace: thành viên mọi workspace đều có thể xem và clone
        if (TemplateDataSeeder.DEFAULT_WORKSPACE_ID.equals(template.getWorkspaceId())) {
            return;
        }
        if (userId != null && (userId.equals(template.getLeadUserId()) || userId.equals(template.getCreatedBy())
                || !roleAssignmentRepository.findByProjectIdAndUserId(template.getId(), userId).isEmpty())) {
            return;
        }
        workspaceAccessChecker.requireMember(template.getWorkspaceId(), userId);
    }

    /** Dời mốc gốc của nguồn về {@code startDate}; nguồn không có mốc thời gian thì không dời. */
    static long dateOffset(ProjectBlueprint blueprint, LocalDate startDate) {
        LocalDate anchor = blueprint.anchorDate();
        return anchor == null || startDate == null ? 0 : ChronoUnit.DAYS.between(anchor, startDate);
    }

    /** Hạn chót mặc định = mốc muộn nhất của nguồn sau khi dời; giữ đúng độ dài timeline. */
    static LocalDate shiftedEnd(ProjectBlueprint blueprint, LocalDate startDate, long offset) {
        LocalDate latest = blueprint.latestDate();
        if (latest == null) {
            return null;
        }
        LocalDate shifted = latest.plusDays(offset);
        return startDate != null && shifted.isBefore(startDate) ? null : shifted;
    }

    private static void assertValid(TemplateIntegrityReport report, String prefix) {
        if (!report.isValid()) {
            throw new BadRequestException(prefix + ": " + report.getErrors().stream()
                    .map(TemplateIntegrityReport.Issue::getMessage)
                    .collect(Collectors.joining("; ")));
        }
    }

    private long usageCount(UUID templateId) {
        return projectCloneRepository.countBySourceProjectIdAndCloneType(templateId, CloneType.FROM_TEMPLATE);
    }

    private Project requireProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));
        if (project.getDeletedAt() != null) {
            throw new BadRequestException("Dự án đã bị lưu trữ");
        }
        return project;
    }

    private Project requireTemplate(UUID templateId) {
        Project project = projectRepository.findById(templateId)
                .filter(p -> Boolean.TRUE.equals(p.getIsTemplate()) && p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án mẫu với ID: " + templateId));
        return project;
    }

    private static String normalizeKey(String key) {
        return key.trim().toUpperCase();
    }
}
