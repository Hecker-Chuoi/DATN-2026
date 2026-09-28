package com.datn.workservice.service.report;

import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemChangelog;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.WorkItemChangelogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dung lai trang thai cua cong viec tai moi thoi diem bang cach phat lai work_item_changelogs.
 *
 * Day la nguon du lieu cho Burndown cua Sprint DANG CHAY va cho CFD - bang
 * sprint_report_snapshots co UNIQUE(sprint_id) nen khong luu duoc so lieu theo tung ngay.
 *
 * LUU Y QUAN TRONG: changelog luu TEN status/sprint chu khong luu UUID
 * (xem WorkItemAuditServiceImpl), nen phai tra cuu nguoc theo ten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkItemStateReplayer {

    /** Cac truong can cho Burndown/CFD; nhung truong con lai duoc loc bo ngay o tang truy van. */
    public static final List<String> REPLAY_FIELDS =
            List.of("STATUS", "STORY_POINTS", "SPRINT", "SPRINT_SCOPE");

    private final WorkItemChangelogRepository changelogRepository;

    /** Mot lan doi trang thai / doi diem cua mot cong viec. */
    public record StateEvent(LocalDateTime at, BigDecimal storyPoints, UUID statusId,
                             String statusName, StatusCategory category, UUID sprintId) {}

    /** Chuoi su kien theo thu tu thoi gian cua mot cong viec. */
    public static class ItemStateTimeline {
        private final List<StateEvent> events;

        ItemStateTimeline(List<StateEvent> events) {
            this.events = events;
        }

        public List<StateEvent> getEvents() {
            return Collections.unmodifiableList(events);
        }

        /** Trang thai tai thoi diem ts; null neu cong viec chua ton tai luc do. */
        public StateEvent valueAt(LocalDateTime ts) {
            StateEvent found = null;
            for (StateEvent e : events) {
                if (e.at() != null && e.at().isAfter(ts)) break;
                found = e;
            }
            return found;
        }
    }

    /**
     * @param items             cong viec can dung lai
     * @param statusById        tra cuu status theo id
     * @param statusByLowerName tra cuu status theo ten viet thuong (changelog luu ten)
     */
    public Map<UUID, ItemStateTimeline> replay(List<WorkItem> items,
                                               Map<UUID, Status> statusById,
                                               Map<String, Status> statusByLowerName) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> ids = items.stream().map(WorkItem::getId).toList();

        // MOT truy van cho toan bo cong viec.
        Map<UUID, List<WorkItemChangelog>> logsByItem = changelogRepository
                .findByWorkItemIdInAndFieldNameInOrderByChangedAtAsc(ids, REPLAY_FIELDS)
                .stream()
                .collect(Collectors.groupingBy(WorkItemChangelog::getWorkItemId));

        Map<UUID, ItemStateTimeline> result = new HashMap<>();
        for (WorkItem item : items) {
            result.put(item.getId(),
                    buildTimeline(item, logsByItem.getOrDefault(item.getId(), List.of()),
                            statusById, statusByLowerName));
        }
        return result;
    }

    private ItemStateTimeline buildTimeline(WorkItem item, List<WorkItemChangelog> logs,
                                            Map<UUID, Status> statusById,
                                            Map<String, Status> statusByLowerName) {
        // Gia tri ban dau = gia tri CU cua lan thay doi dau tien; neu chua tung doi thi lay
        // gia tri hien tai cua cong viec.
        BigDecimal sp = item.getStoryPoints();
        Status status = item.getStatusId() == null ? null : statusById.get(item.getStatusId());
        UUID sprintId = item.getSprintId();

        WorkItemChangelog firstSp = firstOf(logs, "STORY_POINTS");
        if (firstSp != null) {
            sp = parseDecimal(firstSp.getOldValue());
        }
        WorkItemChangelog firstStatus = firstOf(logs, "STATUS");
        if (firstStatus != null) {
            Status resolved = lookupStatus(firstStatus.getOldValue(), statusByLowerName);
            if (resolved != null) status = resolved;
        }
        WorkItemChangelog firstScope = firstOf(logs, "SPRINT_SCOPE");
        if (firstScope != null) {
            sprintId = parseUuid(firstScope.getOldValue());
        }

        List<StateEvent> events = new ArrayList<>();
        events.add(event(item.getCreatedAt(), sp, status, sprintId));

        for (WorkItemChangelog log : logs) {
            switch (log.getFieldName()) {
                case "STATUS" -> {
                    Status next = lookupStatus(log.getNewValue(), statusByLowerName);
                    if (next != null) {
                        status = next;
                    } else {
                        // Status bi doi ten hoac xoa: giu nguyen nhom truoc do thay vi bo qua ca the.
                        WorkItemStateReplayer.log.debug(
                                "Khong phan giai duoc status '{}' cua cong viec {}",
                                log.getNewValue(), log.getWorkItemId());
                    }
                    events.add(event(log.getChangedAt(), sp, status, sprintId));
                }
                case "STORY_POINTS" -> {
                    sp = parseDecimal(log.getNewValue());
                    events.add(event(log.getChangedAt(), sp, status, sprintId));
                }
                case "SPRINT_SCOPE" -> {
                    sprintId = parseUuid(log.getNewValue());
                    events.add(event(log.getChangedAt(), sp, status, sprintId));
                }
                default -> { /* SPRINT (ten) chi de hien thi, da co SPRINT_SCOPE mang UUID */ }
            }
        }
        return new ItemStateTimeline(events);
    }

    private StateEvent event(LocalDateTime at, BigDecimal sp, Status status, UUID sprintId) {
        return new StateEvent(at, sp,
                status == null ? null : status.getId(),
                status == null ? null : status.getName(),
                status == null ? null : status.getCategory(),
                sprintId);
    }

    private WorkItemChangelog firstOf(List<WorkItemChangelog> logs, String field) {
        return logs.stream().filter(l -> field.equals(l.getFieldName())).findFirst().orElse(null);
    }

    private Status lookupStatus(String name, Map<String, Status> statusByLowerName) {
        if (name == null || name.isBlank()) return null;
        return statusByLowerName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
