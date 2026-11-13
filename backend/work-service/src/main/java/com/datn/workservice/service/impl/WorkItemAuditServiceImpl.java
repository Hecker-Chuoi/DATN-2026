package com.datn.workservice.service.impl;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.response.WorkItemChangelogResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.WorkItemAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemAuditServiceImpl implements WorkItemAuditService {

    private final WorkItemChangelogRepository changelogRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final StatusRepository statusRepository;
    private final SprintRepository sprintRepository;
    private final ComponentRepository componentRepository;
    private final VersionRepository versionRepository;
    private final WorkItemRepository workItemRepository;
    private final IdentityClient identityClient;

    private static final Map<String, String> FIELD_NAME_MAP = Map.ofEntries(
            Map.entry("SUMMARY", "Tiêu đề (Summary)"),
            Map.entry("DESCRIPTION", "Mô tả (Description)"),
            Map.entry("TYPE", "Loại công việc (Issue Type)"),
            Map.entry("STATUS", "Trạng thái (Status)"),
            Map.entry("PRIORITY", "Độ ưu tiên (Priority)"),
            Map.entry("ASSIGNEE", "Người thực hiện (Assignee)"),
            Map.entry("REPORTER", "Người tạo (Reporter)"),
            Map.entry("STORY_POINTS", "Điểm Story Points"),
            Map.entry("ORIGINAL_ESTIMATE", "Ước lượng ban đầu (Original Estimate)"),
            Map.entry("REMAINING_ESTIMATE", "Thời gian còn lại (Remaining Estimate)"),
            Map.entry("TIME_SPENT", "Thời gian đã làm (Time Spent)"),
            Map.entry("DUE_DATE", "Hạn chót (Due Date)"),
            Map.entry("START_DATE", "Ngày bắt đầu (Start Date)"),
            Map.entry("SPRINT", "Sprint"),
            Map.entry("COMPONENT", "Phân hệ (Component)"),
            Map.entry("FIX_VERSION", "Phiên bản phát hành (Fix Version)"),
            Map.entry("PARENT", "Công việc cha (Parent)"),
            Map.entry("RESOLUTION", "Kết quả xử lý (Resolution)"),
            Map.entry("ATTACHMENT", "Tệp đính kèm (Attachment)"),
            Map.entry("LINK", "Liên kết công việc (Issue Link)"),
            Map.entry("COMPLETION", "Xác nhận hoàn thành")
    );

    @Override
    @Transactional
    public void recordDiff(WorkItem oldItem, WorkItem newItem, UUID userId) {
        if (oldItem == null || newItem == null || userId == null) return;
        List<WorkItemChangelog> logs = new ArrayList<>();

        // 1. Summary
        if (!Objects.equals(oldItem.getSummary(), newItem.getSummary())) {
            logs.add(buildChangelog(newItem.getId(), "SUMMARY", oldItem.getSummary(), newItem.getSummary(), userId));
        }

        // 2. Description
        if (!Objects.equals(oldItem.getDescription(), newItem.getDescription())) {
            String oldDesc = oldItem.getDescription() != null ? (oldItem.getDescription().length() > 50 ? oldItem.getDescription().substring(0, 47) + "..." : oldItem.getDescription()) : null;
            String newDesc = newItem.getDescription() != null ? (newItem.getDescription().length() > 50 ? newItem.getDescription().substring(0, 47) + "..." : newItem.getDescription()) : null;
            logs.add(buildChangelog(newItem.getId(), "DESCRIPTION", oldDesc, newDesc, userId));
        }

        // 3. Type
        if (!Objects.equals(oldItem.getTypeId(), newItem.getTypeId())) {
            String oldName = resolveTypeName(oldItem.getTypeId());
            String newName = resolveTypeName(newItem.getTypeId());
            logs.add(buildChangelog(newItem.getId(), "TYPE", oldName, newName, userId));
        }

        // 4. Status
        if (!Objects.equals(oldItem.getStatusId(), newItem.getStatusId())) {
            String oldName = resolveStatusName(oldItem.getStatusId());
            String newName = resolveStatusName(newItem.getStatusId());
            logs.add(buildChangelog(newItem.getId(), "STATUS", oldName, newName, userId));
        }

        // 5. Priority
        if (!Objects.equals(oldItem.getPriority(), newItem.getPriority())) {
            String oldVal = oldItem.getPriority() != null ? oldItem.getPriority().name() : null;
            String newVal = newItem.getPriority() != null ? newItem.getPriority().name() : null;
            logs.add(buildChangelog(newItem.getId(), "PRIORITY", oldVal, newVal, userId));
        }

        // 6. Assignee
        if (!Objects.equals(oldItem.getAssigneeId(), newItem.getAssigneeId())) {
            String oldAssignee = resolveUserName(oldItem.getAssigneeId());
            String newAssignee = resolveUserName(newItem.getAssigneeId());
            logs.add(buildChangelog(newItem.getId(), "ASSIGNEE", oldAssignee, newAssignee, userId));
        }

        // 7. Story Points
        if (!Objects.equals(oldItem.getStoryPoints(), newItem.getStoryPoints())) {
            String oldVal = oldItem.getStoryPoints() != null ? oldItem.getStoryPoints().toPlainString() : null;
            String newVal = newItem.getStoryPoints() != null ? newItem.getStoryPoints().toPlainString() : null;
            logs.add(buildChangelog(newItem.getId(), "STORY_POINTS", oldVal, newVal, userId));
        }

        // 8. Due Date
        if (!Objects.equals(oldItem.getDueDate(), newItem.getDueDate())) {
            String oldVal = oldItem.getDueDate() != null ? oldItem.getDueDate().toString() : null;
            String newVal = newItem.getDueDate() != null ? newItem.getDueDate().toString() : null;
            logs.add(buildChangelog(newItem.getId(), "DUE_DATE", oldVal, newVal, userId));
        }

        // 9. Start Date
        if (!Objects.equals(oldItem.getStartDate(), newItem.getStartDate())) {
            String oldVal = oldItem.getStartDate() != null ? oldItem.getStartDate().toString() : null;
            String newVal = newItem.getStartDate() != null ? newItem.getStartDate().toString() : null;
            logs.add(buildChangelog(newItem.getId(), "START_DATE", oldVal, newVal, userId));
        }

        // 10. Sprint
        if (!Objects.equals(oldItem.getSprintId(), newItem.getSprintId())) {
            String oldSprint = resolveSprintName(oldItem.getSprintId());
            String newSprint = resolveSprintName(newItem.getSprintId());
            logs.add(buildChangelog(newItem.getId(), "SPRINT", oldSprint, newSprint, userId));
        }

        // 11. Component
        if (!Objects.equals(oldItem.getComponentId(), newItem.getComponentId())) {
            String oldComp = resolveComponentName(oldItem.getComponentId());
            String newComp = resolveComponentName(newItem.getComponentId());
            logs.add(buildChangelog(newItem.getId(), "COMPONENT", oldComp, newComp, userId));
        }

        // 12. Fix Version
        if (!Objects.equals(oldItem.getFixVersionId(), newItem.getFixVersionId())) {
            String oldVer = resolveVersionName(oldItem.getFixVersionId());
            String newVer = resolveVersionName(newItem.getFixVersionId());
            logs.add(buildChangelog(newItem.getId(), "FIX_VERSION", oldVer, newVer, userId));
        }

        // 13. Parent
        if (!Objects.equals(oldItem.getParentId(), newItem.getParentId())) {
            String oldParent = resolveParentCode(oldItem.getParentId());
            String newParent = resolveParentCode(newItem.getParentId());
            logs.add(buildChangelog(newItem.getId(), "PARENT", oldParent, newParent, userId));
        }

        // 14. Resolution
        if (!Objects.equals(oldItem.getResolution(), newItem.getResolution())) {
            String oldVal = oldItem.getResolution() != null ? oldItem.getResolution().name() : null;
            String newVal = newItem.getResolution() != null ? newItem.getResolution().name() : null;
            logs.add(buildChangelog(newItem.getId(), "RESOLUTION", oldVal, newVal, userId));
        }

        if (!logs.isEmpty()) {
            changelogRepository.saveAll(logs);
            log.info("Saved {} changelog records for WorkItem ID {}", logs.size(), newItem.getId());
        }
    }

    @Override
    @Transactional
    public void recordCustomEvent(UUID workItemId, String fieldName, String oldValue, String newValue, UUID userId) {
        if (workItemId == null || fieldName == null || userId == null) return;
        WorkItemChangelog logEntry = buildChangelog(workItemId, fieldName, oldValue, newValue, userId);
        changelogRepository.save(logEntry);
    }

    @Override
    @Transactional
    public void recordAssigneeChange(UUID workItemId, UUID oldAssigneeId, UUID newAssigneeId, UUID userId) {
        if (workItemId == null || userId == null || Objects.equals(oldAssigneeId, newAssigneeId)) return;
        changelogRepository.save(buildChangelog(workItemId, "ASSIGNEE",
                resolveUserName(oldAssigneeId), resolveUserName(newAssigneeId), userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkItemChangelogResponse> getChangelogs(UUID workItemId) {
        List<WorkItemChangelog> logs = changelogRepository.findByWorkItemIdOrderByChangedAtDesc(workItemId);
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect distinct author IDs for batch user lookup
        Set<UUID> userIds = logs.stream()
                .map(WorkItemChangelog::getChangedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserResponse> userMap = fetchUsersBatch(userIds);

        return logs.stream().map(entry -> {
            UserResponse user = userMap.get(entry.getChangedBy());
            String authorName = user != null ? (user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername()) : null;
            if (authorName == null && entry.getChangedBy() != null) {
                authorName = "Người dùng (" + entry.getChangedBy().toString().substring(0, 8) + ")";
            }

            String fieldDisplayName = FIELD_NAME_MAP.getOrDefault(entry.getFieldName().toUpperCase(), entry.getFieldName());

            return WorkItemChangelogResponse.builder()
                    .id(entry.getId())
                    .workItemId(entry.getWorkItemId())
                    .fieldName(entry.getFieldName())
                    .fieldDisplayName(fieldDisplayName)
                    .oldValue(entry.getOldValue())
                    .newValue(entry.getNewValue())
                    .changedBy(entry.getChangedBy())
                    .authorName(authorName)
                    .authorAvatar(null)
                    .changedAt(entry.getChangedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private WorkItemChangelog buildChangelog(UUID workItemId, String fieldName, String oldValue, String newValue, UUID userId) {
        return WorkItemChangelog.builder()
                .workItemId(workItemId)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(userId)
                .changedAt(LocalDateTime.now())
                .build();
    }

    private String resolveTypeName(UUID typeId) {
        if (typeId == null) return null;
        return workItemTypeRepository.findById(typeId).map(WorkItemType::getName).orElse(typeId.toString());
    }

    private String resolveStatusName(UUID statusId) {
        if (statusId == null) return null;
        return statusRepository.findById(statusId).map(Status::getName).orElse(statusId.toString());
    }

    private String resolveSprintName(UUID sprintId) {
        if (sprintId == null) return null;
        return sprintRepository.findById(sprintId).map(Sprint::getName).orElse(sprintId.toString());
    }

    private String resolveComponentName(UUID compId) {
        if (compId == null) return null;
        return componentRepository.findById(compId).map(Component::getName).orElse(compId.toString());
    }

    private String resolveVersionName(UUID verId) {
        if (verId == null) return null;
        return versionRepository.findById(verId).map(Version::getName).orElse(verId.toString());
    }

    private String resolveParentCode(UUID parentId) {
        if (parentId == null) return null;
        return workItemRepository.findById(parentId).map(WorkItem::getCode).orElse(parentId.toString());
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) return null;
        try {
            ApiResponse<UserResponse> res = identityClient.getUserById(userId);
            if (res != null && res.getData() != null) {
                UserResponse u = res.getData();
                return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
            }
        } catch (Exception e) {
            log.debug("Could not fetch user name for {}: {}", userId, e.getMessage());
        }
        return userId.toString();
    }

    private Map<UUID, UserResponse> fetchUsersBatch(Set<UUID> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        try {
            ApiResponse<List<UserResponse>> res = identityClient.getUsersByIds(new ArrayList<>(userIds));
            if (res != null && res.getData() != null) {
                return res.getData().stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserResponse::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user batch for changelogs: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
