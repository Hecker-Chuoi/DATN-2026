package com.datn.workservice.dto.response;

import com.datn.workservice.enums.BoardType;
import com.datn.workservice.enums.CustomFieldType;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.SwimlaneStrategy;
import com.datn.workservice.enums.TemplateCategory;
import com.datn.workservice.enums.WorkItemPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Xem trước toàn bộ "bản thiết kế" của một dự án mẫu trước khi dùng nó.
 * Mọi mốc thời gian được trả về dưới dạng số ngày tương đối (offset) so với mốc gốc của mẫu,
 * vì khi tạo dự án chúng sẽ được dời theo ngày bắt đầu người dùng chọn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplatePreviewResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID teamId;
    private String name;
    private String key;
    private String description;
    private ProjectType projectType;
    private TemplateCategory templateCategory;
    private long usageCount;

    private Timeline timeline;
    private Stats stats;

    private List<StatusItem> statuses;
    private List<TypeItem> workItemTypes;
    private List<WorkflowItem> workflows;
    private List<BoardItem> boards;
    private List<SprintItem> sprints;
    private List<TreeNode> workItems;
    private boolean workItemsTruncated;
    private List<NamedItem> labels;
    private List<NamedItem> components;
    private List<VersionItem> versions;
    private List<CustomFieldItem> customFields;
    private List<RoleItem> roles;

    private TemplateIntegrityReport integrity;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Timeline {
        /** Mốc gốc của mẫu (ngày bắt đầu dự án mẫu, hoặc ngày sớm nhất của sprint/công việc). */
        private LocalDate anchorDate;
        private LocalDate templateEndDate;
        /** Số ngày từ mốc gốc đến mốc muộn nhất (hạn dự án, sprint, công việc, version). */
        private Long spanDays;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Stats {
        private long statusCount;
        private long workItemTypeCount;
        private long workflowCount;
        private long transitionCount;
        private long boardCount;
        private long sprintCount;
        private long workItemCount;
        private long epicCount;
        private long backlogItemCount;
        private long linkCount;
        private BigDecimal totalStoryPoints;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusItem {
        private UUID id;
        private String name;
        private StatusCategory category;
        private String colorHex;
        private String description;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TypeItem {
        private UUID id;
        private String name;
        private String code;
        private String iconName;
        private String colorHex;
        private Integer hierarchyLevel;
        private Boolean isSubtask;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WorkflowItem {
        private UUID id;
        private String name;
        private Boolean isDefault;
        private List<String> appliesToTypes;
        private List<TransitionItem> transitions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TransitionItem {
        private String name;
        /** null = transition khởi tạo (Create). */
        private String fromStatus;
        private String toStatus;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BoardItem {
        private String name;
        private BoardType type;
        private List<ColumnItem> columns;
        private List<SwimlaneItem> swimlanes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ColumnItem {
        private String name;
        private Integer order;
        private Integer minWipLimit;
        private Integer maxWipLimit;
        private List<String> statuses;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SwimlaneItem {
        private String name;
        private SwimlaneStrategy strategy;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SprintItem {
        private UUID id;
        private String name;
        private String goal;
        /** Ngày bắt đầu tính từ mốc gốc; null nếu sprint chưa có ngày. */
        private Long startOffsetDays;
        private Long durationDays;
        private long itemCount;
        private BigDecimal storyPoints;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TreeNode {
        private UUID id;
        private String code;
        private String summary;
        private String typeName;
        private String typeIcon;
        private String typeColor;
        private Integer hierarchyLevel;
        private WorkItemPriority priority;
        private BigDecimal storyPoints;
        private String sprintName;
        private Long dueOffsetDays;
        @Builder.Default
        private List<TreeNode> children = new ArrayList<>();
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class NamedItem {
        private String name;
        private String description;
        private String colorHex;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class VersionItem {
        private String name;
        private Long releaseOffsetDays;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CustomFieldItem {
        private String name;
        private String fieldKey;
        private CustomFieldType fieldType;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RoleItem {
        private String name;
        private String description;
        private List<String> permissions;
    }
}
