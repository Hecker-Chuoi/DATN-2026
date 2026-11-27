package com.datn.workservice.service.ai;

import com.datn.common.dto.PageResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ForbiddenException;
import com.datn.workservice.dto.request.*;
import com.datn.workservice.dto.response.*;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mỗi tool của trợ lý AI = một method: (1) kiểm quyền của X-User-Id trong dự án bằng
 * {@code hasPermission}, (2) đổi mã/tên → id, (3) gọi service có sẵn (giữ nguyên quy tắc nghiệp vụ,
 * validator, changelog), (4) trả JSON gọn (tối đa 20 phần tử).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiToolService {

    private static final int MAX_ITEMS = 20;
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    private final ProjectMemberService projectMemberService;
    private final ProjectService projectService;
    private final WorkItemService workItemService;
    private final WorkItemRepository workItemRepository;
    private final SprintService sprintService;
    private final WorkflowService workflowService;
    private final WorkflowEngineService workflowEngineService;
    private final AgileReportService agileReportService;
    private final ContributionReportService contributionReportService;
    private final WorkItemCommentService commentService;
    private final WorkItemLinkService linkService;
    private final WorkItemAttachmentService attachmentService;
    private final AiToolResolver resolver;
    private final AttachmentTextExtractor textExtractor;

    // ═══════════════════════════ ĐỌC ═══════════════════════════

    public Map<String, Object> searchWorkItems(UUID userId, Map<String, Object> in) {
        UUID projectId = requireProject(in);
        require(projectId, userId, PermissionKey.PROJECT_VIEW);

        WorkItemFilterRequest f = new WorkItemFilterRequest();
        f.setKeyword(str(in, "keyword"));
        UUID typeId = resolver.typeId(projectId, str(in, "typeName"));
        if (typeId != null) f.setTypeIds(List.of(typeId));
        UUID statusId = resolver.statusId(projectId, str(in, "statusName"));
        if (statusId != null) f.setStatusIds(List.of(statusId));
        StatusCategory cat = category(str(in, "statusCategory"));
        if (cat != null) f.setStatusCategories(List.of(cat));
        if (resolver.priority(in.get("priority")) != null) f.setPriorities(List.of(resolver.priority(in.get("priority"))));

        boolean unassignedOnly = false;
        String assignee = str(in, "assigneeName");
        if (assignee != null) {
            if (Set.of("unassigned", "none", "chưa giao", "chua giao").contains(assignee.toLowerCase(Locale.ROOT))) {
                unassignedOnly = true;
            } else {
                f.setAssigneeIds(List.of(resolver.memberId(projectId, assignee, userId)));
            }
        }
        String sprintName = str(in, "sprintName");
        if (sprintName != null) {
            if ("backlog".equalsIgnoreCase(sprintName)) f.setIsBacklog(true);
            else f.setSprintId(resolver.sprint(projectId, sprintName).getId());
        }

        int limit = limit(in);
        PageResponse<WorkItemResponse> page = workItemService.listWorkItems(projectId, f,
                PageRequest.of(0, unassignedOnly ? 100 : limit, Sort.by(Sort.Direction.DESC, "updatedAt")));
        List<WorkItemResponse> items = page.getContent();
        long total = page.getTotalElements();
        if (unassignedOnly) {
            items = items.stream().filter(i -> i.getAssigneeId() == null).toList();
            total = items.size();
            items = items.stream().limit(limit).toList();
        }
        return Map.of("totalFound", total, "items", items.stream().map(AiToolService::compact).toList());
    }

    public Map<String, Object> getWorkItem(UUID userId, Map<String, Object> in) {
        WorkItem item = resolver.item(uuid(in, "workItemId"), str(in, "code"));
        require(item.getProjectId(), userId, PermissionKey.PROJECT_VIEW);
        WorkItemDetailResponse d = workItemService.getWorkItemDetail(item.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", d.getCode());
        out.put("summary", d.getSummary());
        out.put("description", cut(d.getDescription(), 2000));
        out.put("type", d.getTypeName());
        out.put("status", d.getStatusName());
        out.put("statusCategory", d.getStatusCategory());
        out.put("priority", d.getPriority());
        out.put("resolution", d.getResolution());
        out.put("assignee", d.getAssigneeName());
        out.put("reporter", d.getReporterName());
        out.put("storyPoints", d.getStoryPoints());
        out.put("startDate", d.getStartDate());
        out.put("dueDate", d.getDueDate());
        out.put("sprint", d.getSprintName());
        out.put("labels", d.getLabels());
        out.put("isBlocked", d.getIsBlocked());
        if (d.getParent() != null) out.put("parent", Map.of("code", d.getParent().getCode(), "summary", d.getParent().getSummary()));
        out.put("subtasks", nullSafe(d.getSubtasks()).stream().limit(MAX_ITEMS).map(AiToolService::compact).toList());
        out.put("childStories", nullSafe(d.getChildStories()).stream().limit(MAX_ITEMS).map(AiToolService::compact).toList());
        out.put("availableTransitions", nullSafe(d.getAvailableTransitions()).stream()
                .map(t -> Map.of("name", t.getName(), "toStatus", t.getToStatusName())).toList());
        out.put("attachmentCount", d.getAttachmentCount());
        out.put("commentCount", d.getCommentCount());
        List<Map<String, Object>> comments = new ArrayList<>();
        flattenComments(commentService.getCommentsTree(item.getId()), comments);
        out.put("latestComments", comments.stream()
                .sorted((a, b) -> String.valueOf(b.get("createdAt")).compareTo(String.valueOf(a.get("createdAt"))))
                .limit(5).toList());
        out.put("createdAt", d.getCreatedAt());
        out.put("updatedAt", d.getUpdatedAt());
        return out;
    }

    public Map<String, Object> getMyIssues(UUID userId, Map<String, Object> in) {
        UUID projectId = uuid(in, "projectId");
        StatusCategory cat = category(str(in, "statusCategory"));
        List<StatusCategory> cats = cat != null ? List.of(cat) : List.of(StatusCategory.TODO, StatusCategory.IN_PROGRESS);
        int limit = limit(in);

        List<UUID> projectIds = projectId != null ? List.of(projectId)
                : workItemRepository.findByAssigneeId(userId).stream()
                        .filter(w -> w.getDeletedAt() == null).map(WorkItem::getProjectId).distinct().limit(10).toList();

        List<Map<String, Object>> items = new ArrayList<>();
        long total = 0;
        for (UUID pid : projectIds) {
            if (!projectMemberService.hasPermission(pid, userId, PermissionKey.PROJECT_VIEW)) continue;
            WorkItemFilterRequest f = new WorkItemFilterRequest();
            f.setAssigneeIds(List.of(userId));
            f.setStatusCategories(cats);
            PageResponse<WorkItemResponse> page = workItemService.listWorkItems(pid, f,
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "dueDate")));
            total += page.getTotalElements();
            page.getContent().forEach(w -> items.add(compact(w)));
        }
        return Map.of("totalFound", total, "items", items.stream().limit(limit).toList(),
                "note", cat == null ? "Chỉ gồm việc chưa xong (TODO, IN_PROGRESS)" : "Lọc theo " + cat);
    }

    public Map<String, Object> getProjectMetadata(UUID userId, Map<String, Object> in) {
        UUID projectId = requireProject(in);
        require(projectId, userId, PermissionKey.PROJECT_VIEW);
        ProjectDetailResponse p = projectService.getProjectById(projectId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.getId());
        out.put("key", p.getKey());
        out.put("name", p.getName());
        out.put("description", cut(p.getDescription(), 500));
        out.put("projectType", p.getProjectType());
        out.put("lead", p.getLeadUserName());
        out.put("memberCount", p.getMemberCount());
        out.put("workItemCount", p.getWorkItemCount());
        out.put("startDate", p.getStartDate());
        out.put("endDate", p.getEndDate());
        out.put("workItemTypes", nullSafe(p.getWorkItemTypes()).stream().map(t -> Map.of(
                "name", t.getName(), "hierarchyLevel", t.getHierarchyLevel(), "isSubtask", Boolean.TRUE.equals(t.getIsSubtask()))).toList());
        out.put("statuses", workflowService.getProjectStatuses(projectId).stream()
                .map(s -> Map.of("name", s.getName(), "category", s.getCategory())).toList());
        out.put("sprints", sprintService.listByProject(projectId, null).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getName());
            m.put("state", s.getState());
            m.put("startDate", s.getStartDate());
            m.put("endDate", s.getEndDate());
            return m;
        }).toList());
        return out;
    }

    public Map<String, Object> listProjectMembers(UUID userId, Map<String, Object> in) {
        UUID projectId = requireProject(in);
        require(projectId, userId, PermissionKey.PROJECT_VIEW);
        List<Map<String, Object>> members = projectMemberService.getProjectMembers(projectId).stream().map(m -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("userId", m.getUserId());
            r.put("fullName", m.getFullName());
            r.put("username", m.getUsername());
            r.put("email", m.getEmail());
            r.put("role", m.getRoleName());
            r.put("isMe", userId.equals(m.getUserId()));
            return r;
        }).toList();
        return Map.of("totalFound", members.size(), "members", members);
    }

    public Map<String, Object> listSprints(UUID userId, Map<String, Object> in) {
        UUID projectId = requireProject(in);
        require(projectId, userId, PermissionKey.PROJECT_VIEW);
        String state = str(in, "state");
        Set<SprintState> states = state == null ? null : Set.of(SprintState.valueOf(state.toUpperCase(Locale.ROOT)));
        List<Map<String, Object>> sprints = sprintService.listByProject(projectId, states).stream()
                .map(AiToolService::compactSprint).toList();
        return Map.of("totalFound", sprints.size(), "sprints", sprints);
    }

    public Map<String, Object> getSprintProgress(UUID userId, Map<String, Object> in) {
        UUID projectId = requireProject(in);
        require(projectId, userId, PermissionKey.PROJECT_VIEW);
        SprintResponse s = sprintService.getById(resolver.sprint(projectId, str(in, "sprintName")).getId());

        WorkItemFilterRequest f = new WorkItemFilterRequest();
        f.setSprintId(s.getId());
        f.setStatusCategories(List.of(StatusCategory.TODO, StatusCategory.IN_PROGRESS));
        PageResponse<WorkItemResponse> open = workItemService.listWorkItems(projectId, f,
                PageRequest.of(0, MAX_ITEMS, Sort.by(Sort.Direction.ASC, "dueDate")));

        Map<String, Object> out = new LinkedHashMap<>(compactSprint(s));
        out.put("todoItems", s.getTodoItems());
        out.put("inProgressItems", s.getInProgressItems());
        out.put("doneItems", s.getDoneItems());
        out.put("isOverdue", s.getIsOverdue());
        out.put("openItemsTotal", open.getTotalElements());
        out.put("openItems", open.getContent().stream().map(AiToolService::compact).toList());
        return out;
    }

    public Object getAgileReport(UUID userId, Map<String, Object> in) {
        UUID projectId = requireProject(in);
        require(projectId, userId, PermissionKey.PROJECT_VIEW);
        String type = Optional.ofNullable(str(in, "reportType")).orElse("burndown").toLowerCase(Locale.ROOT);
        switch (type) {
            case "velocity" -> {
                VelocityResponse v = agileReportService.getVelocity(projectId, 6);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("averageVelocity", v.getAverageVelocity());
                out.put("averageCommitment", v.getAverageCommitment());
                out.put("commitmentReliabilityPercent", v.getCommitmentReliabilityPercent());
                out.put("sprints", nullSafe(v.getSprints()).stream().map(p -> Map.of(
                        "name", String.valueOf(p.getSprintName()),
                        "committed", String.valueOf(p.getCommittedStoryPoints()),
                        "completed", String.valueOf(p.getCompletedStoryPoints()))).toList());
                return out;
            }
            case "cfd" -> {
                String sprintName = str(in, "sprintName");
                UUID sprintId = sprintName == null ? null : resolver.sprint(projectId, sprintName).getId();
                return agileReportService.getCfd(projectId, sprintId);
            }
            default -> {
                SprintResponse s = resolver.sprint(projectId, str(in, "sprintName"));
                BurndownResponse b = agileReportService.getBurndown(s.getId(), true);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("sprintName", b.getSprintName());
                out.put("state", b.getState());
                out.put("startDate", b.getStartDate());
                out.put("endDate", b.getEndDate());
                out.put("committedStoryPoints", b.getCommittedStoryPoints());
                out.put("completedStoryPoints", b.getCompletedStoryPoints());
                out.put("remainingStoryPoints", b.getRemainingStoryPoints());
                out.put("totalItems", b.getTotalItems());
                out.put("completedItems", b.getCompletedItems());
                out.put("points", nullSafe(b.getPoints()).stream().limit(40).map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", p.getDate());
                    m.put("remaining", p.getRemainingPoints());
                    m.put("ideal", p.getIdealRemaining());
                    m.put("isToday", p.getIsToday());
                    return m;
                }).toList());
                return out;
            }
        }
    }

    public Map<String, Object> getContributionReport(UUID userId, Map<String, Object> in) {
        UUID projectId = requireProject(in);
        require(projectId, userId, PermissionKey.PROJECT_VIEW);
        String sprintName = str(in, "sprintName");
        UUID sprintId = sprintName == null ? null : resolver.sprint(projectId, sprintName).getId();
        ContributionOverviewResponse r = contributionReportService.getContributionOverview(projectId, sprintId, null, null, userId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sprintName", r.getSprintName());
        out.put("totalPoints", r.getTotalPoints());
        out.put("totalTasksCompleted", r.getTotalTasksCompleted());
        out.put("averageCycleTimeDays", r.getAverageCycleTimeDays());
        out.put("onTimeRatePercent", r.getOnTimeRatePercent());
        out.put("members", nullSafe(r.getMembers()).stream().map(m -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", m.getName());
            x.put("points", m.getPoints());
            x.put("percentage", m.getPercentage());
            x.put("completedTasks", m.getCompletedTasks());
            x.put("onTimeRatePercent", m.getOnTimeRatePercent());
            x.put("reopenedCount", m.getReopenedCount());
            x.put("bugsReported", m.getBugsReported());
            return x;
        }).toList());
        out.put("alerts", r.getAlerts());
        out.put("unassignedCompletedCount", r.getUnassignedCompletedCount());
        return out;
    }

    public Map<String, Object> listAttachments(UUID userId, Map<String, Object> in) {
        WorkItem item = resolver.item(uuid(in, "workItemId"), str(in, "code"));
        require(item.getProjectId(), userId, PermissionKey.PROJECT_VIEW);
        List<Map<String, Object>> files = attachmentService.getAttachments(item.getId()).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fileName", a.getFileName());
            m.put("contentType", a.getContentType());
            m.put("size", a.getFormattedFileSize());
            m.put("uploadedBy", a.getUploaderName());
            m.put("uploadedAt", a.getUploadedAt());
            return m;
        }).toList();
        return Map.of("code", item.getCode(), "totalFound", files.size(), "attachments", files);
    }

    public Map<String, Object> readAttachment(UUID userId, Map<String, Object> in) {
        WorkItem item = resolver.item(uuid(in, "workItemId"), str(in, "code"));
        require(item.getProjectId(), userId, PermissionKey.PROJECT_VIEW);
        List<WorkItemAttachmentResponse> all = attachmentService.getAttachments(item.getId());
        if (all.isEmpty()) throw new BadRequestException("Công việc " + item.getCode() + " không có tệp đính kèm");

        String fileName = str(in, "fileName");
        WorkItemAttachmentResponse att;
        if (fileName == null) {
            if (all.size() > 1) {
                throw new BadRequestException("Có " + all.size() + " tệp, hãy chỉ rõ fileName: "
                        + all.stream().map(WorkItemAttachmentResponse::getFileName).collect(Collectors.joining(", ")));
            }
            att = all.get(0);
        } else {
            List<WorkItemAttachmentResponse> m = all.stream()
                    .filter(a -> a.getFileName() != null && a.getFileName().toLowerCase(Locale.ROOT).contains(fileName.toLowerCase(Locale.ROOT)))
                    .toList();
            if (m.isEmpty()) throw new BadRequestException("Không có tệp nào khớp '" + fileName + "'. Tệp hiện có: "
                    + all.stream().map(WorkItemAttachmentResponse::getFileName).collect(Collectors.joining(", ")));
            if (m.size() > 1) throw new BadRequestException("Nhiều tệp khớp '" + fileName + "': "
                    + m.stream().map(WorkItemAttachmentResponse::getFileName).collect(Collectors.joining(", ")));
            att = m.get(0);
        }
        if (att.getFileSize() != null && att.getFileSize() > MAX_ATTACHMENT_BYTES) {
            throw new BadRequestException("Tệp " + att.getFileName() + " lớn hơn 10 MB, trợ lý không đọc được");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", item.getCode());
        out.put("fileName", att.getFileName());
        out.put("contentType", att.getContentType());
        out.put("size", att.getFormattedFileSize());
        try {
            Resource res = attachmentService.loadAttachmentResource(att.getId());
            try (InputStream is = res.getInputStream()) {
                AttachmentTextExtractor.Extracted ex = textExtractor.extract(is, att.getContentType(), att.getFileName());
                out.put("text", ex.text());
                out.put("truncated", ex.truncated());
                if (ex.note() != null) out.put("note", ex.note());
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Không đọc được tệp {}: {}", att.getFileName(), e.toString());
            throw new BadRequestException("Không đọc được nội dung tệp " + att.getFileName() + ": " + e.getMessage());
        }
        return out;
    }

    // ═══════════════════════════ GHI ═══════════════════════════

    public Map<String, Object> createWorkItem(UUID userId, Map<String, Object> in) {
        UUID projectId = requireProject(in);
        require(projectId, userId, PermissionKey.ISSUE_CREATE);

        String parentCode = str(in, "parentCode");
        String sprintName = str(in, "sprintName");
        CreateWorkItemRequest req = CreateWorkItemRequest.builder()
                .projectId(projectId)
                .typeId(resolver.typeId(projectId, str(in, "typeName")))
                .summary(str(in, "summary"))
                .description(str(in, "description"))
                .priority(Optional.ofNullable(resolver.priority(in.get("priority"))).orElse(com.datn.workservice.enums.WorkItemPriority.MEDIUM))
                .assigneeId(resolver.memberId(projectId, str(in, "assigneeName"), userId))
                .parentId(parentCode == null ? null : resolver.item(null, parentCode).getId())
                .sprintId(sprintName == null ? null : resolver.sprint(projectId, sprintName).getId())
                .storyPoints(decimal(in.get("storyPoints")))
                .startDate(resolver.date(in.get("startDate")))
                .dueDate(resolver.date(in.get("dueDate")))
                .build();
        if (req.getTypeId() == null) throw new BadRequestException("Thiếu typeName (loại công việc)");
        if (req.getSummary() == null) throw new BadRequestException("Thiếu summary (tiêu đề)");

        WorkItemDetailResponse r = workItemService.createWorkItem(projectId, req, userId);
        return Map.of("created", true, "code", r.getCode(), "summary", r.getSummary(),
                "type", String.valueOf(r.getTypeName()), "status", String.valueOf(r.getStatusName()),
                "assignee", String.valueOf(r.getAssigneeName()));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createSubtasks(UUID userId, Map<String, Object> in) {
        WorkItem parent = resolver.item(uuid(in, "workItemId"), str(in, "parentCode"));
        UUID projectId = parent.getProjectId();
        require(projectId, userId, PermissionKey.ISSUE_CREATE);

        Object raw = in.get("subtasks");
        if (!(raw instanceof List<?> list) || list.isEmpty()) throw new BadRequestException("Thiếu danh sách subtasks");

        List<Map<String, Object>> created = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (Object o : list) {
            Map<String, Object> s = o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of("summary", String.valueOf(o));
            String summary = str(s, "summary");
            try {
                CreateSubtaskRequest req = CreateSubtaskRequest.builder()
                        .summary(summary)
                        .description(str(s, "description"))
                        .priority(Optional.ofNullable(resolver.priority(s.get("priority"))).orElse(com.datn.workservice.enums.WorkItemPriority.MEDIUM))
                        .assigneeId(resolver.memberId(projectId, str(s, "assigneeName"), userId))
                        .dueDate(resolver.date(s.get("dueDate")))
                        .build();
                WorkItemDetailResponse r = workItemService.createSubtask(parent.getId(), req, userId);
                created.add(Map.of("code", r.getCode(), "summary", r.getSummary()));
            } catch (Exception e) {
                errors.add(Map.of("summary", String.valueOf(summary), "error", String.valueOf(e.getMessage())));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("parentCode", parent.getCode());
        out.put("createdCount", created.size());
        out.put("created", created);
        out.put("errors", errors);
        return out;
    }

    public Map<String, Object> updateWorkItem(UUID userId, Map<String, Object> in) {
        WorkItem item = resolver.item(uuid(in, "workItemId"), str(in, "code"));
        require(item.getProjectId(), userId, PermissionKey.ISSUE_EDIT);

        UpdateWorkItemRequest req = UpdateWorkItemRequest.builder()
                .summary(str(in, "summary"))
                .description(str(in, "description"))
                .priority(resolver.priority(in.get("priority")))
                .storyPoints(decimal(in.get("storyPoints")))
                .startDate(resolver.date(in.get("startDate")))
                .dueDate(resolver.date(in.get("dueDate")))
                .typeId(resolver.typeId(item.getProjectId(), str(in, "typeName")))
                .build();
        WorkItemDetailResponse r = workItemService.updateWorkItem(item.getId(), req, userId);
        return Map.of("updated", true, "item", compactDetail(r));
    }

    public Map<String, Object> transitionWorkItem(UUID userId, Map<String, Object> in) {
        WorkItem item = resolver.item(uuid(in, "workItemId"), str(in, "code"));
        require(item.getProjectId(), userId, PermissionKey.ISSUE_TRANSITION);
        String target = str(in, "targetStatus");
        if (target == null) throw new BadRequestException("Thiếu targetStatus");

        List<AvailableTransitionResponse> available = workflowEngineService.getAvailableTransitions(item.getId(), userId);
        AvailableTransitionResponse t = available.stream()
                .filter(a -> target.equalsIgnoreCase(a.getToStatusName()) || target.equalsIgnoreCase(a.getName()))
                .findFirst()
                .or(() -> available.stream().filter(a -> AiToolResolver.containsIgnoreCase(a.getToStatusName(), target)
                        || AiToolResolver.containsIgnoreCase(a.getName(), target)).findFirst())
                .orElseThrow(() -> new BadRequestException("Không thể chuyển " + item.getCode() + " sang '" + target
                        + "' từ trạng thái hiện tại. Các bước khả dụng: "
                        + (available.isEmpty() ? "không có" : available.stream()
                        .map(a -> a.getName() + " → " + a.getToStatusName()).collect(Collectors.joining(", ")))));

        ExecuteTransitionRequest req = ExecuteTransitionRequest.builder()
                .transitionId(t.getTransitionId())
                .resolution(str(in, "resolution"))
                .comment(str(in, "comment"))
                .build();
        workflowEngineService.executeTransition(item.getId(), t.getTransitionId(), req, userId);
        return Map.of("transitioned", true, "code", item.getCode(), "transition", t.getName(), "newStatus", t.getToStatusName());
    }

    public Map<String, Object> assignWorkItem(UUID userId, Map<String, Object> in) {
        WorkItem item = resolver.item(uuid(in, "workItemId"), str(in, "code"));
        require(item.getProjectId(), userId, PermissionKey.ISSUE_ASSIGN);
        String name = str(in, "assigneeName");
        if (name == null) throw new BadRequestException("Thiếu assigneeName");
        if (Set.of("none", "unassigned", "unassign", "không ai", "khong ai").contains(name.toLowerCase(Locale.ROOT))) {
            // UpdateWorkItemRequest bỏ qua assigneeId null nên không bỏ giao được qua đường này
            throw new BadRequestException("Bỏ giao việc chưa hỗ trợ qua trợ lý; hãy hướng dẫn người dùng làm trên giao diện");
        }
        UUID assigneeId = resolver.memberId(item.getProjectId(), name, userId);
        WorkItemDetailResponse r = workItemService.updateWorkItem(item.getId(),
                UpdateWorkItemRequest.builder().assigneeId(assigneeId).build(), userId);
        return Map.of("assigned", true, "code", r.getCode(), "assignee", String.valueOf(r.getAssigneeName()));
    }

    public Map<String, Object> moveToSprint(UUID userId, Map<String, Object> in) {
        UUID projectId = requireProject(in);
        require(projectId, userId, PermissionKey.SPRINT_MANAGE);
        Object raw = in.get("codes");
        if (!(raw instanceof List<?> codes) || codes.isEmpty()) throw new BadRequestException("Thiếu danh sách codes");
        String sprintName = str(in, "sprintName");
        if (sprintName == null) throw new BadRequestException("Thiếu sprintName");

        List<WorkItem> items = codes.stream().map(c -> resolver.item(null, String.valueOf(c))).toList();
        for (WorkItem w : items) {
            if (!projectId.equals(w.getProjectId())) {
                throw new BadRequestException("Công việc " + w.getCode() + " không thuộc dự án đang mở");
            }
        }
        List<String> moved = items.stream().map(WorkItem::getCode).toList();
        if ("backlog".equalsIgnoreCase(sprintName)) {
            Map<UUID, List<UUID>> bySprint = items.stream().filter(w -> w.getSprintId() != null)
                    .collect(Collectors.groupingBy(WorkItem::getSprintId, Collectors.mapping(WorkItem::getId, Collectors.toList())));
            bySprint.forEach((sprintId, ids) -> sprintService.removeItemsFromSprint(sprintId, ids, userId));
            return Map.of("moved", moved, "target", "backlog");
        }
        SprintResponse sprint = resolver.sprint(projectId, sprintName);
        sprintService.addItemsToSprint(sprint.getId(), items.stream().map(WorkItem::getId).toList(), userId);
        return Map.of("moved", moved, "target", sprint.getName());
    }

    public Map<String, Object> addComment(UUID userId, Map<String, Object> in) {
        WorkItem item = resolver.item(uuid(in, "workItemId"), str(in, "code"));
        require(item.getProjectId(), userId, PermissionKey.COMMENT_CREATE);
        String body = str(in, "body");
        if (body == null) throw new BadRequestException("Thiếu nội dung bình luận");
        WorkItemCommentResponse c = commentService.addComment(item.getId(),
                CreateWorkItemCommentRequest.builder().body(body).build(), userId);
        return Map.of("commented", true, "code", item.getCode(), "commentId", c.getId());
    }

    public Map<String, Object> linkWorkItems(UUID userId, Map<String, Object> in) {
        WorkItem source = resolver.item(null, str(in, "sourceCode"));
        WorkItem target = resolver.item(null, str(in, "targetCode"));
        require(source.getProjectId(), userId, PermissionKey.ISSUE_EDIT);
        if (!source.getProjectId().equals(target.getProjectId())) {
            require(target.getProjectId(), userId, PermissionKey.ISSUE_EDIT);
        }
        LinkType type;
        try {
            type = LinkType.valueOf(Optional.ofNullable(str(in, "linkType")).orElse("RELATES_TO").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("linkType phải là BLOCKS, RELATES_TO, DUPLICATES hoặc CAUSES");
        }
        WorkItemLinkResponse l = linkService.createLink(source.getId(),
                CreateWorkItemLinkRequest.builder().targetItemId(target.getId()).linkType(type).build(), userId);
        return Map.of("linked", true, "linkId", l.getId(), "source", source.getCode(), "target", target.getCode(), "linkType", type.name());
    }

    // ═══════════════════════════ Helpers ═══════════════════════════

    private void require(UUID projectId, UUID userId, PermissionKey key) {
        if (projectId == null) throw new BadRequestException("Chưa mở dự án nào – hãy mở dự án trước khi dùng công cụ này");
        if (!projectMemberService.hasPermission(projectId, userId, key)) {
            throw new ForbiddenException("Bạn không có quyền " + key.name() + " trong dự án này");
        }
    }

    private static UUID requireProject(Map<String, Object> in) {
        UUID id = uuid(in, "projectId");
        if (id == null) throw new BadRequestException("Chưa mở dự án nào – hãy mở dự án trước khi dùng công cụ này");
        return id;
    }

    private static Map<String, Object> compact(WorkItemResponse w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", w.getCode());
        m.put("summary", w.getSummary());
        m.put("type", w.getTypeName());
        m.put("status", w.getStatusName());
        m.put("statusCategory", w.getStatusCategory());
        m.put("assignee", w.getAssigneeName());
        m.put("priority", w.getPriority());
        m.put("storyPoints", w.getStoryPoints());
        m.put("dueDate", w.getDueDate());
        m.put("sprint", w.getSprintName());
        if (w.getParentCode() != null) m.put("parentCode", w.getParentCode());
        return m;
    }

    private static Map<String, Object> compactDetail(WorkItemDetailResponse d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", d.getCode());
        m.put("summary", d.getSummary());
        m.put("type", d.getTypeName());
        m.put("status", d.getStatusName());
        m.put("assignee", d.getAssigneeName());
        m.put("priority", d.getPriority());
        m.put("storyPoints", d.getStoryPoints());
        m.put("startDate", d.getStartDate());
        m.put("dueDate", d.getDueDate());
        m.put("sprint", d.getSprintName());
        return m;
    }

    private static Map<String, Object> compactSprint(SprintResponse s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.getName());
        m.put("state", s.getState());
        m.put("goal", s.getGoal());
        m.put("startDate", s.getStartDate());
        m.put("endDate", s.getEndDate());
        m.put("daysRemaining", s.getDaysRemaining());
        m.put("totalItems", s.getTotalItems());
        m.put("completedItems", s.getCompletedItems());
        m.put("totalStoryPoints", s.getTotalStoryPoints());
        m.put("completedStoryPoints", s.getCompletedStoryPoints());
        m.put("remainingStoryPoints", s.getRemainingStoryPoints());
        return m;
    }

    private static void flattenComments(List<WorkItemCommentResponse> tree, List<Map<String, Object>> out) {
        for (WorkItemCommentResponse c : nullSafe(tree)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("author", c.getAuthorName());
            m.put("body", cut(c.getBody(), 300));
            m.put("createdAt", c.getCreatedAt());
            out.add(m);
            flattenComments(c.getReplies(), out);
        }
    }

    private static StatusCategory category(String s) {
        if (s == null) return null;
        try {
            return StatusCategory.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("statusCategory phải là TODO, IN_PROGRESS hoặc DONE");
        }
    }

    private static int limit(Map<String, Object> in) {
        Object v = in.get("limit");
        if (v instanceof Number n) return Math.max(1, Math.min(MAX_ITEMS, n.intValue()));
        return MAX_ITEMS;
    }

    private static String str(Map<String, Object> in, String key) {
        Object v = in.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static UUID uuid(Map<String, Object> in, String key) {
        String s = str(in, key);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(key + " không phải UUID hợp lệ");
        }
    }

    private static BigDecimal decimal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BadRequestException("storyPoints phải là số");
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static <T> List<T> nullSafe(List<T> l) {
        return l == null ? List.of() : l;
    }
}
