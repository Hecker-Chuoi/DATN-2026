package com.datn.workservice.dto.response;

import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.enums.WorkItemResolution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The cong viec dung cho Board va Backlog.
 *
 * Hep hon WorkItemResponse mot cach co chu dich: man hinh Board co the tra ve hang tram the,
 * nen chi mang nhung truong that su duoc ve. Cac co canh bao can join them
 * (hasTimelineConflict / hasDependencyConflict) van nam o WorkItemDetailResponse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardCardResponse {

    private UUID id;
    private String code;
    private String summary;

    private UUID projectId;
    private String projectKey;

    // Loai cong viec
    private UUID typeId;
    private String typeName;
    private String typeCode;
    private String typeColor;
    private String typeIcon;
    private Integer hierarchyLevel;
    private Boolean isSubtask;

    // Trang thai & vi tri tren bang
    private UUID statusId;
    private String statusName;
    private StatusCategory statusCategory;
    private String statusColorHex;
    private UUID columnId;

    /** Khoa lan boi (epicId / assigneeId / priority) do SwimlaneGrouper gan. */
    private String swimlaneKey;
    private String swimlaneLabel;

    // Phan cap
    private UUID parentId;
    private String parentCode;
    private String parentSummary;
    private UUID epicId;
    private String epicCode;
    private String epicSummary;
    private String epicColor;

    private UUID sprintId;
    private String sprintName;

    private WorkItemPriority priority;
    private WorkItemResolution resolution;

    private UUID assigneeId;
    private String assigneeName;
    private String assigneeAvatar;

    private BigDecimal storyPoints;
    private Long originalEstimateSeconds;
    private Long remainingEstimateSeconds;
    private Long timeSpentSeconds;

    // Cuon du lieu tu cong viec con
    private Long totalTimeSpentSeconds;
    private Long totalRemainingEstimateSeconds;
    private Integer subtaskCount;
    private Integer subtaskDoneCount;

    /** Con it nhat mot cong viec tien de (IS_BLOCKED_BY) chua DONE. */
    private Boolean isBlocked;

    private LocalDate startDate;
    private LocalDate dueDate;
    private Boolean isOverdue;

    private List<String> labels;

    /** Đang "Tạm hoàn thành" — chờ Admin dự án xác nhận (BR-W03). */
    private Boolean completionPending;

    /** LexoRank - quyet dinh thu tu trong cot / trong Sprint. */
    private String rank;

    /** Bat buoc: FE gui lai gia tri nay lam clientVersion khi keo tha (optimistic locking). */
    private Integer version;
}
