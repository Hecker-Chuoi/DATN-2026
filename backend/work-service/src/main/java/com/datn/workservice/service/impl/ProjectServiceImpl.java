package com.datn.workservice.service.impl;

import com.datn.common.dto.PageResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.CloneProjectRequest;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.ProjectFilter;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.ProjectStatus;
import com.datn.workservice.enums.TaskStatus;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.ProjectService;
import com.datn.workservice.specification.ProjectSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final ProjectCloneRepository projectCloneRepository;

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, UUID currentUserId) {
        UUID creator = currentUserId != null ? currentUserId : UUID.randomUUID();

        String key = request.getKey().trim().toUpperCase();
        if (projectRepository.findByKey(key).isPresent()) {
            throw new BadRequestException("Mã dự án (Key) '" + key + "' đã tồn tại. Vui lòng chọn mã khác.");
        }
        if (request.getTeamId() != null && !teamRepository.existsById(request.getTeamId())) {
            throw new ResourceNotFoundException("Team not found with id: " + request.getTeamId());
        }

        Project project = Project.builder()
                .teamId(request.getTeamId())
                .key(key)
                .name(request.getName().trim())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : ProjectStatus.PLANNING)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .createdBy(creator)
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : false)
                .isTemplate(request.getIsTemplate() != null ? request.getIsTemplate() : false)
                .build();

        project = projectRepository.save(project);
        return enrichProjectResponse(project);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getAllProjects(ProjectFilter filter, Pageable pageable) {
        if (filter == null) {
            filter = new ProjectFilter();
        }

        Specification<Project> spec = Specification.allOf();

        if (filter.getIsTemplate() != null) {
            spec = spec.and(ProjectSpecification.isTemplate(filter.getIsTemplate()));
        }

        spec = spec.and(ProjectSpecification.search(filter.getSearch()))
                .and(ProjectSpecification.hasStatus(filter.getStatus()))
                .and(ProjectSpecification.hasTeamId(filter.getTeamId()));

        Page<Project> page = projectRepository.findAll(spec, pageable);
        List<ProjectResponse> content = page.getContent().stream()
                .map(this::enrichProjectResponse)
                .collect(Collectors.toList());

        return PageResponse.of(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast(),
                page.isFirst()
        );
    }

    @Override
    public List<ProjectResponse> getProjectsByTeam(UUID teamId) {
        return projectRepository.findByTeamId(teamId).stream()
                .map(this::enrichProjectResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectResponse> getTemplateProjects() {
        return projectRepository.findByIsTemplateTrue().stream()
                .map(this::enrichProjectResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProjectResponse getProjectById(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
        return enrichProjectResponse(project);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getMyProjects(UUID userId, ProjectFilter filter, Pageable pageable) {
        if (userId == null) {
            log.warn("getMyProjects: No authenticated userId found in token, returning empty PageResponse");
            return PageResponse.of(Collections.emptyList(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true, true);
        }

        if (filter == null) {
            filter = new ProjectFilter();
        }

        Specification<Project> spec = ProjectSpecification.isNotTemplate()
                .and(ProjectSpecification.isAccessibleByUser(userId))
                .and(ProjectSpecification.search(filter.getSearch()))
                .and(ProjectSpecification.hasStatus(filter.getStatus()))
                .and(ProjectSpecification.hasTeamId(filter.getTeamId()));

        Page<Project> page = projectRepository.findAll(spec, pageable);
        List<ProjectResponse> content = page.getContent().stream()
                .map(this::enrichProjectResponse)
                .collect(Collectors.toList());

        return PageResponse.of(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast(),
                page.isFirst()
        );
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        if (request.getName() != null) project.setName(request.getName().trim());
        if (request.getDescription() != null) project.setDescription(request.getDescription());
        if (request.getStatus() != null) project.setStatus(request.getStatus());
        if (request.getStartDate() != null) project.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) project.setEndDate(request.getEndDate());
        if (request.getTeamId() != null) project.setTeamId(request.getTeamId());
        if (request.getIsPublic() != null) project.setIsPublic(request.getIsPublic());
        if (request.getIsTemplate() != null) project.setIsTemplate(request.getIsTemplate());

        project = projectRepository.save(project);
        return enrichProjectResponse(project);
    }

    @Override
    @Transactional
    public void deleteProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }
        projectRepository.deleteById(projectId);
    }

    @Override
    @Transactional
    public ProjectResponse cloneProject(CloneProjectRequest request, UUID currentUserId) {
        UUID cloner = currentUserId != null ? currentUserId : UUID.randomUUID();

        Project source = projectRepository.findById(request.getSourceProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Source Project/Template not found with id: " + request.getSourceProjectId()));

        String newKey = request.getNewKey().trim().toUpperCase();
        if (projectRepository.findByKey(newKey).isPresent()) {
            throw new BadRequestException("Mã dự án (Key) '" + newKey + "' đã tồn tại trong hệ thống. Vui lòng chọn mã khác.");
        }

        UUID teamId = request.getTeamId() != null ? request.getTeamId() : source.getTeamId();

        // 1. Tính toán độ lệch ngày động (Dynamic Timeline Offset)
        LocalDate targetStartDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        LocalDate sourceStartDate = source.getStartDate();

        List<Milestone> sourceMilestones = milestoneRepository.findByProjectId(source.getId());
        List<Task> sourceTasks = taskRepository.findByProjectId(source.getId());

        if (sourceStartDate == null) {
            // Tìm mốc ngày sớm nhất từ task hoặc milestone nguồn
            Optional<LocalDate> earliestTaskDate = sourceTasks.stream()
                    .map(Task::getStartDate)
                    .filter(Objects::nonNull)
                    .map(LocalDateTime::toLocalDate)
                    .min(LocalDate::compareTo);
            Optional<LocalDate> earliestMsDate = sourceMilestones.stream()
                    .map(Milestone::getDueDate)
                    .filter(Objects::nonNull)
                    .map(LocalDateTime::toLocalDate)
                    .min(LocalDate::compareTo);

            sourceStartDate = earliestTaskDate.orElse(earliestMsDate.orElse(targetStartDate));
        }

        long dayOffset = ChronoUnit.DAYS.between(sourceStartDate, targetStartDate);

        LocalDate newEndDate = null;
        if (source.getEndDate() != null) {
            newEndDate = source.getEndDate().plusDays(dayOffset);
        }

        // 2. Tạo Project mới
        Project clonedProject = Project.builder()
                .teamId(teamId)
                .key(newKey)
                .name(request.getNewName().trim())
                .description(request.getNewDescription() != null ? request.getNewDescription() : source.getDescription())
                .status(ProjectStatus.PLANNING)
                .startDate(targetStartDate)
                .endDate(newEndDate)
                .createdBy(cloner)
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : false)
                .isTemplate(false)
                .build();

        clonedProject = projectRepository.save(clonedProject);

        // 3. Sao chép Labels từ nguồn sang dự án mới
        Map<UUID, UUID> labelMapping = new HashMap<>();
        List<TaskLabel> sourceLabels = taskLabelRepository.findByProjectId(source.getId());
        for (TaskLabel sLabel : sourceLabels) {
            TaskLabel nLabel = TaskLabel.builder()
                    .projectId(clonedProject.getId())
                    .name(sLabel.getName())
                    .color(sLabel.getColor())
                    .build();
            nLabel = taskLabelRepository.save(nLabel);
            labelMapping.put(sLabel.getId(), nLabel.getId());
        }

        // 4. Sao chép Milestones từ nguồn sang dự án mới (với dueDate tịnh tiến theo dayOffset)
        Map<UUID, UUID> milestoneMapping = new HashMap<>();
        for (Milestone sMs : sourceMilestones) {
            LocalDateTime nDueDate = sMs.getDueDate() != null ? sMs.getDueDate().plusDays(dayOffset) : null;
            Milestone nMs = Milestone.builder()
                    .projectId(clonedProject.getId())
                    .name(sMs.getName())
                    .description(sMs.getDescription())
                    .dueDate(nDueDate)
                    .status(sMs.getStatus())
                    .build();
            nMs = milestoneRepository.save(nMs);
            milestoneMapping.put(sMs.getId(), nMs.getId());
        }

        // 5. Sao chép Tasks và Subtasks mẫu từ nguồn (Two-pass clone để bảo toàn quan hệ cha - con & tịnh tiến ngày)
        Map<UUID, UUID> taskMapping = new HashMap<>();
        Map<UUID, Task> newTasksByOldId = new HashMap<>();
        int taskSeq = 1;

        // Pass 1: Tạo tất cả tasks và subtasks mới, tịnh tiến startDate & dueDate
        for (Task sTask : sourceTasks) {
            UUID nMilestoneId = sTask.getMilestoneId() != null ? milestoneMapping.get(sTask.getMilestoneId()) : null;
            LocalDateTime nStartDate = sTask.getStartDate() != null ? sTask.getStartDate().plusDays(dayOffset) : null;
            LocalDateTime nDueDate = sTask.getDueDate() != null ? sTask.getDueDate().plusDays(dayOffset) : null;

            Task nTask = Task.builder()
                    .code(newKey + "-" + (taskSeq++))
                    .projectId(clonedProject.getId())
                    .teamId(teamId)
                    .milestoneId(nMilestoneId)
                    .parentTaskId(null) // Sẽ được liên kết chính xác ở Pass 2
                    .title(sTask.getTitle())
                    .description(sTask.getDescription())
                    .type(sTask.getType())
                    .status(TaskStatus.TODO)
                    .priority(sTask.getPriority())
                    .estimate(sTask.getEstimate())
                    .startDate(nStartDate)
                    .dueDate(nDueDate)
                    .reporterId(cloner)
                    .position(sTask.getPosition() != null ? sTask.getPosition() : (taskSeq * 1000))
                    .build();
            nTask = taskRepository.save(nTask);
            taskMapping.put(sTask.getId(), nTask.getId());
            newTasksByOldId.put(sTask.getId(), nTask);
        }

        // Pass 2: Ánh xạ lại parentTaskId cho các subtasks sang Task mới tương ứng
        for (Task sTask : sourceTasks) {
            if (sTask.getParentTaskId() != null) {
                UUID newParentId = taskMapping.get(sTask.getParentTaskId());
                if (newParentId != null) {
                    Task nTask = newTasksByOldId.get(sTask.getId());
                    if (nTask != null) {
                        nTask.setParentTaskId(newParentId);
                        taskRepository.save(nTask);
                    }
                }
            }
        }

        // 6. Ghi log lịch sử nhân bản
        ProjectClone cloneLog = ProjectClone.builder()
                .sourceProjectId(source.getId())
                .clonedProjectId(clonedProject.getId())
                .clonedBy(cloner)
                .build();
        projectCloneRepository.save(cloneLog);

        log.info("Successfully cloned project '{}' ({}) into new project '{}' ({}) with dayOffset={} days",
                source.getName(), source.getId(), clonedProject.getName(), clonedProject.getId(), dayOffset);

        return enrichProjectResponse(clonedProject);
    }

    private ProjectResponse enrichProjectResponse(Project project) {
        ProjectResponse res = ProjectResponse.from(project);

        // Lấy tên Team nếu có
        if (project.getTeamId() != null) {
            teamRepository.findById(project.getTeamId()).ifPresent(t -> res.setTeamName(t.getName()));
        }

        // Đếm số lượng task
        long count = taskRepository.countByProjectId(project.getId());
        res.setTaskCount(count);

        return res;
    }
}
