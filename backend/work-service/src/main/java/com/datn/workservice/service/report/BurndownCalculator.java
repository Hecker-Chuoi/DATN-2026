package com.datn.workservice.service.report;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.dto.response.BurndownPointResponse;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.service.report.WorkItemStateReplayer.ItemStateTimeline;
import com.datn.workservice.service.report.WorkItemStateReplayer.StateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tinh chuoi diem Burndown cua mot Sprint bang cach phat lai lich su thay doi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BurndownCalculator {

    /**
     * @param items     cong viec thuoc Sprint (da loc Sub-task o noi goi)
     * @param timelines lich su thay doi tuong ung
     * @param cutoff    tinh toi thoi diem nao (hom nay voi Sprint dang chay, ngay dong voi Sprint da dong)
     */
    public List<BurndownPointResponse> buildSeries(Sprint sprint,
                                                   List<WorkItem> items,
                                                   Map<UUID, ItemStateTimeline> timelines,
                                                   LocalDateTime cutoff,
                                                   boolean excludeWeekends) {
        LocalDateTime start = sprint.getStartDate() != null ? sprint.getStartDate() : sprint.getCreatedAt();
        LocalDateTime end = sprint.getEndDate();
        if (start == null || end == null) {
            throw new BadRequestException("Sprint chưa có ngày bắt đầu/kết thúc nên không thể vẽ Burndown");
        }

        LocalDate startDay = start.toLocalDate();
        LocalDate endDay = end.toLocalDate();
        LocalDate cutoffDay = (cutoff == null ? LocalDateTime.now() : cutoff).toLocalDate();
        if (cutoffDay.isAfter(endDay)) cutoffDay = endDay;
        if (cutoffDay.isBefore(startDay)) cutoffDay = startDay;

        // Cam ket = tong diem tai thoi diem Sprint bat dau.
        BigDecimal committed = BigDecimal.ZERO;
        for (WorkItem item : items) {
            StateEvent at = timelineOf(timelines, item).valueAt(start);
            if (at != null && at.storyPoints() != null) {
                committed = committed.add(at.storyPoints());
            }
        }

        List<LocalDate> workingDays = workingDays(startDay, endDay, excludeWeekends);
        LocalDate today = LocalDate.now();

        List<BurndownPointResponse> points = new ArrayList<>();
        for (LocalDate day = startDay; !day.isAfter(cutoffDay); day = day.plusDays(1)) {
            // Cuoi ngay, tru ngay cutoff thi lay dung moc cutoff.
            LocalDateTime ts = day.equals(cutoffDay) && cutoff != null ? cutoff : day.atTime(23, 59, 59);

            BigDecimal remaining = BigDecimal.ZERO;
            BigDecimal completed = BigDecimal.ZERO;
            BigDecimal scope = BigDecimal.ZERO;
            int remainingItems = 0;
            int completedItems = 0;

            for (WorkItem item : items) {
                StateEvent st = timelineOf(timelines, item).valueAt(ts);
                if (st == null) continue; // cong viec chua ton tai tai thoi diem nay

                BigDecimal sp = st.storyPoints() == null ? BigDecimal.ZERO : st.storyPoints();
                scope = scope.add(sp);
                if (st.category() == StatusCategory.DONE) {
                    completed = completed.add(sp);
                    completedItems++;
                } else {
                    remaining = remaining.add(sp);
                    remainingItems++;
                }
            }

            points.add(BurndownPointResponse.builder()
                    .date(day)
                    .remainingPoints(remaining)
                    .completedPoints(completed)
                    .scopePoints(scope)
                    .idealRemaining(idealAt(day, workingDays, committed))
                    .remainingItems(remainingItems)
                    .completedItems(completedItems)
                    .isToday(day.equals(today))
                    .build());
        }
        return points;
    }

    /**
     * Duong ly thuyet giam deu tren cac NGAY LAM VIEC; cuoi tuan di ngang
     * (giu nguyen gia tri cua ngay lam viec gan nhat truoc do).
     */
    private BigDecimal idealAt(LocalDate day, List<LocalDate> workingDays, BigDecimal committed) {
        if (workingDays.size() <= 1) return committed;

        int idx = workingDays.indexOf(day);
        if (idx < 0) {
            // Ngay nghi: lay gia tri cua ngay lam viec gan nhat truoc do.
            int prev = -1;
            for (int i = 0; i < workingDays.size(); i++) {
                if (!workingDays.get(i).isAfter(day)) prev = i; else break;
            }
            if (prev < 0) return committed;
            idx = prev;
        }
        double ratio = 1.0 - ((double) idx / (workingDays.size() - 1));
        BigDecimal value = committed.multiply(BigDecimal.valueOf(ratio));
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(committed) > 0) return committed;
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private List<LocalDate> workingDays(LocalDate start, LocalDate end, boolean excludeWeekends) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (excludeWeekends
                    && (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY)) {
                continue;
            }
            days.add(d);
        }
        return days.isEmpty() ? List.of(start) : days;
    }

    private ItemStateTimeline timelineOf(Map<UUID, ItemStateTimeline> timelines, WorkItem item) {
        ItemStateTimeline t = timelines.get(item.getId());
        if (t != null) return t;
        // Khong co lich su: coi nhu giu nguyen trang thai hien tai tu luc tao.
        return new ItemStateTimeline(List.of());
    }

    /** Sub-task khong tinh diem rieng - diem da nam o Story/Task cha. */
    public boolean isSubtask(WorkItem item, Map<UUID, WorkItemType> typeById) {
        WorkItemType t = item.getTypeId() == null ? null : typeById.get(item.getTypeId());
        return t != null && Boolean.TRUE.equals(t.getIsSubtask());
    }

    public BigDecimal sumPoints(List<WorkItem> items) {
        return items.stream().map(WorkItem::getStoryPoints)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isDone(WorkItem item, Map<UUID, Status> statusById) {
        Status s = item.getStatusId() == null ? null : statusById.get(item.getStatusId());
        return s != null && s.getCategory() == StatusCategory.DONE;
    }
}
