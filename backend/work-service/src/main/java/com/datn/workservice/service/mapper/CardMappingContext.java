package com.datn.workservice.service.mapper;

import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemType;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Du lieu tra cuu da nap san theo lo cho mot lan ve Board/Backlog.
 *
 * Muc dich la tranh N+1: WorkItemServiceImpl.mapToSummaryResponse ban ra khoang 8 truy van
 * cho MOI the, nen mot bang 200 the se ton ~2000 truy van. Context nay nap mot lan roi dung chung.
 */
@Getter
@Builder
public class CardMappingContext {

    @Builder.Default
    private Map<UUID, Status> statusById = Collections.emptyMap();

    /** Tra cuu theo ten viet thuong - changelog luu TEN status chu khong luu UUID. */
    @Builder.Default
    private Map<String, Status> statusByLowerName = Collections.emptyMap();

    @Builder.Default
    private Map<UUID, WorkItemType> typeById = Collections.emptyMap();

    @Builder.Default
    private Map<UUID, WorkItem> parentById = Collections.emptyMap();

    @Builder.Default
    private Map<UUID, List<WorkItem>> childrenByParentId = Collections.emptyMap();

    /** Cong viec dang bi chan boi it nhat mot blocker chua DONE. */
    @Builder.Default
    private Set<UUID> blockedItemIds = Collections.emptySet();

    @Builder.Default
    private Map<UUID, String> sprintNameById = Collections.emptyMap();

    @Builder.Default
    private Map<UUID, String> projectKeyById = Collections.emptyMap();

    @Builder.Default
    private Map<UUID, UserResponse> userById = Collections.emptyMap();

    @Builder.Default
    private Map<UUID, List<String>> labelsByItemId = Collections.emptyMap();

    public boolean isSubtaskType(UUID typeId) {
        WorkItemType type = typeId == null ? null : typeById.get(typeId);
        return type != null && Boolean.TRUE.equals(type.getIsSubtask());
    }

    public Integer hierarchyLevelOf(UUID typeId) {
        WorkItemType type = typeId == null ? null : typeById.get(typeId);
        return type == null ? null : type.getHierarchyLevel();
    }
}
