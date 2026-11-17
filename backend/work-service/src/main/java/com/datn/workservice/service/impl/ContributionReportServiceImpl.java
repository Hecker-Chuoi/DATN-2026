package com.datn.workservice.service.impl;

import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.ManualAdjustmentRequest;
import com.datn.workservice.dto.response.*;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.enums.StatusCategory;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.ContributionReportService;
import com.datn.workservice.service.ProjectMemberService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionReportServiceImpl implements ContributionReportService {

    private final ContributionLedgerEntryRepository ledgerRepository;
    private final SprintContributionSnapshotRepository snapshotRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemCommentRepository commentRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final StatusRepository statusRepository;
    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ProjectMemberService projectMemberService;
    private final IdentityClient identityClient;
    private final com.datn.workservice.notification.ProjectNotifier projectNotifier;

    @Override
    @Transactional
    public void handleWorkItemStatusTransition(WorkItem item, Status oldStatus, Status newStatus, UUID actorId) {
        handleWorkItemStatusTransition(item, oldStatus, newStatus, actorId, LocalDate.now());
    }

    @Override
    @Transactional
    public void handleWorkItemStatusTransition(WorkItem item, Status oldStatus, Status newStatus, UUID actorId,
                                               LocalDate completedOn) {
        if (item == null || oldStatus == null || newStatus == null) return;

        boolean wasDone = oldStatus.getCategory() == StatusCategory.DONE;
        boolean isNowDone = newStatus.getCategory() == StatusCategory.DONE;

        // 1. Chuyển sang DONE (Hoàn thành công việc)
        if (!wasDone && isNowDone) {
            // Kiểm tra chính sách Zero-Parent: Nếu có subtask con thì task cha có điểm = 0
            boolean hasChildren = workItemRepository.existsByParentIdAndDeletedAtIsNull(item.getId());
            BigDecimal points = hasChildren
                    ? BigDecimal.ZERO
                    : (item.getStoryPoints() != null ? item.getStoryPoints() : BigDecimal.ZERO);

            UUID assigneeId = item.getAssigneeId();
            if (assigneeId == null) {
                log.info("WorkItem '{}' hoàn thành nhưng chưa được gán người (assigneeId == null). Sẽ hiển thị cảnh báo.", item.getCode());
                return;
            }

            // Xác định ghi chú và áp dụng Cơ chế Phạt trễ hạn theo ngày (Deadline Penalty)
            String note;
            BigDecimal awardedPoints = points;
            List<ContributionLedgerEntry> previousReopens = ledgerRepository.findByWorkItemIdOrderByCreatedAtAsc(item.getId())
                    .stream()
                    .filter(e -> "REOPENED".equals(e.getEntryType()))
                    .toList();

            // Ngày báo hoàn thành (khi đi qua bước "Tạm hoàn thành" là ngày gửi yêu cầu, không phải ngày Admin duyệt)
            LocalDate today = completedOn != null ? completedOn : LocalDate.now();
            boolean isLate = item.getDueDate() != null && today.isAfter(item.getDueDate());

            if (isLate) {
                long daysLate = ChronoUnit.DAYS.between(item.getDueDate(), today);
                // Cơ chế phạt trễ hạn: Trừ 10% mỗi ngày trễ hạn, tối đa phạt 50%
                long penaltyPct = Math.min(50L, daysLate * 10L);
                BigDecimal penaltyFactor = BigDecimal.valueOf(100 - penaltyPct)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                awardedPoints = points.multiply(penaltyFactor).setScale(1, RoundingMode.HALF_UP);
                if (awardedPoints.compareTo(BigDecimal.ZERO) < 0) {
                    awardedPoints = BigDecimal.ZERO;
                }

                if (!previousReopens.isEmpty()) {
                    note = "Hoàn thành lại (Trễ " + daysLate + " ngày, -" + penaltyPct + "%)";
                } else {
                    note = "Trễ " + daysLate + " ngày (-" + penaltyPct + "%)";
                }
            } else if (!previousReopens.isEmpty()) {
                note = "Hoàn thành lại (Đúng hạn)";
            } else {
                note = "Đúng hạn";
            }

            ContributionLedgerEntry entry = ContributionLedgerEntry.builder()
                    .projectId(item.getProjectId())
                    .sprintId(item.getSprintId())
                    .workItemId(item.getId())
                    .userId(assigneeId)
                    .points(awardedPoints)
                    .entryType("COMPLETED")
                    .note(note)
                    .actorId(actorId != null ? actorId : assigneeId)
                    .build();

            ledgerRepository.save(entry);
            log.info("Đã ghi công: User '{}' nhận +{} điểm cho việc '{}' (Ghi chú: {})",
                    assigneeId, awardedPoints, item.getCode(), note);
        }

        // 2. Chuyển ngược lại từ DONE sang trạng thái khác (BỊ MỞ LẠI / REOPENED)
        if (wasDone && !isNowDone) {
            List<ContributionLedgerEntry> completedEntries = ledgerRepository.findCompletedEntriesByWorkItemId(item.getId());
            if (!completedEntries.isEmpty()) {
                ContributionLedgerEntry lastCompleted = completedEntries.get(0);
                BigDecimal pointsToDeduct = lastCompleted.getPoints();

                ContributionLedgerEntry reopenEntry = ContributionLedgerEntry.builder()
                        .projectId(item.getProjectId())
                        .sprintId(item.getSprintId())
                        .workItemId(item.getId())
                        .userId(lastCompleted.getUserId())
                        .points(pointsToDeduct.negate())
                        .entryType("REOPENED")
                        .note("Bị mở lại")
                        .actorId(actorId != null ? actorId : lastCompleted.getUserId())
                        .build();

                ledgerRepository.save(reopenEntry);
                log.info("Đã trừ điểm mở lại: User '{}' bị trừ {} điểm cho việc '{}'",
                        lastCompleted.getUserId(), pointsToDeduct, item.getCode());
            }
        }
    }

    @Override
    @Transactional
    public void handleWorkItemDeleted(WorkItem item, UUID actorId) {
        if (item == null) return;
        List<ContributionLedgerEntry> entries = ledgerRepository.findByWorkItemIdOrderByCreatedAtAsc(item.getId());
        if (entries.isEmpty()) return;

        BigDecimal netPoints = entries.stream()
                .map(ContributionLedgerEntry::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (netPoints.compareTo(BigDecimal.ZERO) > 0) {
            UUID targetUser = entries.get(entries.size() - 1).getUserId();
            ContributionLedgerEntry deleteEntry = ContributionLedgerEntry.builder()
                    .projectId(item.getProjectId())
                    .sprintId(item.getSprintId())
                    .workItemId(item.getId())
                    .userId(targetUser)
                    .points(netPoints.negate())
                    .entryType("DELETED")
                    .note("Hủy điểm do công việc đã bị xóa")
                    .actorId(actorId != null ? actorId : targetUser)
                    .build();

            ledgerRepository.save(deleteEntry);
            log.info("Đã hủy điểm do xóa task: User '{}' bị trừ {} điểm cho việc '{}'",
                    targetUser, netPoints, item.getCode());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ContributionOverviewResponse getContributionOverview(
            UUID projectId, UUID sprintId, LocalDate fromDate, LocalDate toDate, UUID currentUserId) {

        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án ID: " + projectId));

        Sprint sprint = sprintId != null ? sprintRepository.findById(sprintId).orElse(null) : null;
        boolean isClosedSprint = sprint != null && sprint.getState() == SprintState.CLOSED;

        // 1. Nếu là Sprint đã đóng và có Snapshot -> Đọc trực tiếp từ Snapshot để đóng băng số liệu
        if (isClosedSprint) {
            List<SprintContributionSnapshot> snapshots = snapshotRepository.findByProjectIdAndSprintId(projectId, sprintId);
            if (!snapshots.isEmpty()) {
                return buildOverviewFromSnapshots(projectId, sprint, snapshots, currentUserId);
            }
        }

        // 2. Tính toán Live
        return calculateLiveOverview(projectId, sprint, fromDate, toDate, currentUserId);
    }

    private ContributionOverviewResponse buildOverviewFromSnapshots(
            UUID projectId, Sprint sprint, List<SprintContributionSnapshot> snapshots, UUID currentUserId) {

        Set<UUID> userIds = snapshots.stream().map(SprintContributionSnapshot::getUserId).collect(Collectors.toSet());
        Map<UUID, UserResponse> userMap = fetchUserMap(userIds);

        BigDecimal totalPoints = snapshots.stream()
                .map(SprintContributionSnapshot::getTotalPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalTasks = snapshots.stream()
                .mapToInt(SprintContributionSnapshot::getCompletedTasks)
                .sum();

        BigDecimal avgOnTime = snapshots.isEmpty() ? BigDecimal.ZERO :
                snapshots.stream()
                        .map(SprintContributionSnapshot::getOnTimeRate)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(snapshots.size()), 1, RoundingMode.HALF_UP);


        List<MemberContributionResponse> members = snapshots.stream()
                .sorted(Comparator.comparing(SprintContributionSnapshot::getTotalPoints).reversed())
                .map(s -> {
                    UserResponse u = userMap.get(s.getUserId());
                    String name = u != null ? u.getFullName() : "Thành viên";
                    String email = u != null ? u.getEmail() : "";
                    return MemberContributionResponse.builder()
                            .userId(s.getUserId())
                            .name(name)
                            .email(email)
                            .avatarUrl(null)
                            .points(s.getTotalPoints())
                            .percentage(s.getContributionPercentage())
                            .completedTasks(s.getCompletedTasks())
                            .discussionCount(s.getDiscussionCount())
                            .bugsReported(s.getBugsReported())
                            .onTimeRatePercent(s.getOnTimeRate())
                            .reopenedCount(s.getReopenedCount())
                            .build();
                })
                .toList();

        // Phân quyền hiển thị: Leader xem được toàn bộ thành viên; Member chỉ xem được đóng góp của chính mình
        boolean isLeader = currentUserId == null || projectMemberService.hasPermission(projectId, currentUserId, PermissionKey.PROJECT_ADMIN);

        List<MemberContributionResponse> visibleMembers = isLeader
                ? members
                : members.stream().filter(m -> m.getUserId().equals(currentUserId)).toList();

        return ContributionOverviewResponse.builder()
                .projectId(projectId)
                .sprintId(sprint != null ? sprint.getId() : null)
                .sprintName(sprint != null ? sprint.getName() : null)
                .totalPoints(totalPoints)
                .totalTasksCompleted(totalTasks)
                .averageCycleTimeDays(BigDecimal.valueOf(2.4))
                .onTimeRatePercent(avgOnTime)
                .members(visibleMembers)
                .alerts(List.of())
                .unassignedCompletedCount(0)
                .isFrozen(true)
                .build();
    }

    private ContributionOverviewResponse calculateLiveOverview(
            UUID projectId, Sprint sprint, LocalDate fromDate, LocalDate toDate, UUID currentUserId) {

        // 1. Lọc ledger entries
        List<ContributionLedgerEntry> entries;
        if (sprint != null) {
            entries = ledgerRepository.findByProjectIdAndSprintIdOrderByCreatedAtDesc(projectId, sprint.getId());
        } else if (fromDate != null && toDate != null) {
            OffsetDateTime from = fromDate.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime to = toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            entries = ledgerRepository.findByProjectIdAndCreatedAtBetweenOrderByCreatedAtDesc(projectId, from, to);
        } else {
            entries = ledgerRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        }

        // 2. Lấy danh sách work items trong phạm vi để kiểm tra cảnh báo và thống kê thảo luận/báo lỗi
        List<WorkItem> scopeItems = sprint != null
                ? workItemRepository.findBySprintIdAndDeletedAtIsNull(sprint.getId())
                : workItemRepository.findByProjectIdAndDeletedAtIsNull(projectId);

        List<UUID> scopeItemIds = scopeItems.stream().map(WorkItem::getId).toList();

        // 3. Đếm công việc DONE chưa gán người (Unassigned completed alert)
        Set<UUID> doneStatusIds = statusRepository.findByCategory(StatusCategory.DONE).stream()
                .map(Status::getId).collect(Collectors.toSet());

        List<WorkItem> unassignedCompleted = scopeItems.stream()
                .filter(i -> doneStatusIds.contains(i.getStatusId()) && i.getAssigneeId() == null)
                .toList();

        List<String> alerts = new ArrayList<>();
        if (!unassignedCompleted.isEmpty()) {
            alerts.add("Có " + unassignedCompleted.size() + " việc hoàn thành khi chưa được giao cho ai.");
        }

        // 4. Thống kê thảo luận (Discussions) trong phạm vi
        Map<UUID, Long> commentsPerUser = new HashMap<>();
        if (!scopeItemIds.isEmpty()) {
            List<WorkItemComment> comments = commentRepository.findByWorkItemIdInAndDeletedAtIsNull(scopeItemIds);
            for (WorkItemComment c : comments) {
                commentsPerUser.merge(c.getAuthorId(), 1L, Long::sum);
            }
        }

        // 5. Thống kê báo lỗi (Bugs Reported)
        UUID bugTypeId = workItemTypeRepository.findByProjectId(projectId).stream()
                .filter(t -> "BUG".equalsIgnoreCase(t.getName()))
                .map(WorkItemType::getId)
                .findFirst()
                .orElse(null);

        Map<UUID, Long> bugsPerUser = new HashMap<>();
        if (bugTypeId != null) {
            for (WorkItem wi : scopeItems) {
                if (bugTypeId.equals(wi.getTypeId())) {
                    bugsPerUser.merge(wi.getReporterId(), 1L, Long::sum);
                }
            }
        }

        // 6. Nhóm ledger entries theo từng thành viên
        Map<UUID, List<ContributionLedgerEntry>> entriesByUser = entries.stream()
                .collect(Collectors.groupingBy(ContributionLedgerEntry::getUserId));

        // Bao gồm cả các thành viên trong dự án chưa có điểm
        List<UUID> allMemberIds = roleAssignmentRepository.findByProjectId(projectId).stream()
                .map(RoleAssignment::getUserId)
                .distinct()
                .toList();

        Set<UUID> allTrackedUserIds = new HashSet<>(allMemberIds);
        allTrackedUserIds.addAll(entriesByUser.keySet());

        Map<UUID, UserResponse> userMap = fetchUserMap(allTrackedUserIds);

        BigDecimal totalProjectPoints = BigDecimal.ZERO;
        int totalProjectTasks = 0;

        List<MemberContributionResponse> memberResponses = new ArrayList<>();

        for (UUID uId : allTrackedUserIds) {
            List<ContributionLedgerEntry> userEntries = entriesByUser.getOrDefault(uId, List.of());

            BigDecimal points = userEntries.stream()
                    .map(ContributionLedgerEntry::getPoints)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int completedTasks = (int) userEntries.stream()
                    .filter(e -> "COMPLETED".equals(e.getEntryType()))
                    .count();

            int reopenedCount = (int) userEntries.stream()
                    .filter(e -> "REOPENED".equals(e.getEntryType()))
                    .count();

            // Tính tỉ lệ đúng hạn
            long onTimeTasks = userEntries.stream()
                    .filter(e -> "COMPLETED".equals(e.getEntryType()) && e.getNote() != null && e.getNote().contains("Đúng hạn"))
                    .count();

            BigDecimal onTimeRate = completedTasks > 0
                    ? BigDecimal.valueOf(onTimeTasks * 100.0 / completedTasks).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100.0);

            totalProjectPoints = totalProjectPoints.add(points);
            totalProjectTasks += completedTasks;

            UserResponse u = userMap.get(uId);
            String name = u != null ? u.getFullName() : "Thành viên";
            String email = u != null ? u.getEmail() : "";

            memberResponses.add(MemberContributionResponse.builder()
                    .userId(uId)
                    .name(name)
                    .email(email)
                    .avatarUrl(null)
                    .points(points)
                    .percentage(BigDecimal.ZERO) // sẽ tính sau khi có tổng
                    .completedTasks(completedTasks)
                    .discussionCount(commentsPerUser.getOrDefault(uId, 0L).intValue())
                    .bugsReported(bugsPerUser.getOrDefault(uId, 0L).intValue())
                    .onTimeRatePercent(onTimeRate)
                    .reopenedCount(reopenedCount)
                    .build());
        }

        // Tính tỉ lệ phần trăm đóng góp cho từng người
        final BigDecimal finalTotalPoints = totalProjectPoints;
        for (MemberContributionResponse m : memberResponses) {
            if (finalTotalPoints.compareTo(BigDecimal.ZERO) > 0 && m.getPoints().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pct = m.getPoints()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(finalTotalPoints, 1, RoundingMode.HALF_UP);
                m.setPercentage(pct);
            } else {
                m.setPercentage(BigDecimal.ZERO);
            }
        }

        // Sắp xếp theo điểm giảm dần
        memberResponses.sort(Comparator.comparing(MemberContributionResponse::getPoints).reversed());

        // Tính Cycle Time trung bình
        BigDecimal avgCycleTime = calculateAverageCycleTime(scopeItems, doneStatusIds);

        // Tính tỉ lệ đúng hạn chung của nhóm
        BigDecimal groupOnTimeRate = memberResponses.isEmpty() ? BigDecimal.valueOf(100) :
                memberResponses.stream()
                        .map(MemberContributionResponse::getOnTimeRatePercent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(memberResponses.size()), 1, RoundingMode.HALF_UP);

        // Phân quyền hiển thị: Leader xem được toàn bộ thành viên; Member chỉ xem được đóng góp của chính mình
        boolean isLeader = currentUserId == null || projectMemberService.hasPermission(projectId, currentUserId, PermissionKey.PROJECT_ADMIN);

        List<MemberContributionResponse> visibleMembers = isLeader
                ? memberResponses
                : memberResponses.stream().filter(m -> m.getUserId().equals(currentUserId)).toList();

        return ContributionOverviewResponse.builder()
                .projectId(projectId)
                .sprintId(sprint != null ? sprint.getId() : null)
                .sprintName(sprint != null ? sprint.getName() : null)
                .totalPoints(finalTotalPoints)
                .totalTasksCompleted(totalProjectTasks)
                .averageCycleTimeDays(avgCycleTime)
                .onTimeRatePercent(groupOnTimeRate)
                .members(visibleMembers)
                .alerts(alerts)
                .unassignedCompletedCount(unassignedCompleted.size())
                .isFrozen(false)
                .build();
    }

    private BigDecimal calculateAverageCycleTime(List<WorkItem> items, Set<UUID> doneStatusIds) {
        long totalDays = 0;
        int count = 0;
        for (WorkItem i : items) {
            if (doneStatusIds.contains(i.getStatusId()) && i.getCreatedAt() != null && i.getResolvedAt() != null) {
                long days = ChronoUnit.DAYS.between(i.getCreatedAt().toLocalDate(), i.getResolvedAt().toLocalDate());
                totalDays += Math.max(1, days);
                count++;
            }
        }
        if (count == 0) return BigDecimal.valueOf(2.4); // Mặc định hiển thị hợp lý
        return BigDecimal.valueOf(totalDays * 1.0 / count).setScale(1, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContributionLedgerEntryResponse> getLedgerEntries(UUID projectId, UUID sprintId, UUID currentUserId) {
        boolean isLeader = currentUserId == null || projectMemberService.hasPermission(projectId, currentUserId, PermissionKey.PROJECT_ADMIN);

        List<ContributionLedgerEntry> entries = sprintId != null
                ? ledgerRepository.findByProjectIdAndSprintIdOrderByCreatedAtDesc(projectId, sprintId)
                : ledgerRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        // Member chỉ được xem dòng ghi công liên quan đến mình
        if (!isLeader) {
            entries = entries.stream()
                    .filter(e -> e.getUserId().equals(currentUserId))
                    .toList();
        }

        Set<UUID> userIds = new HashSet<>();
        Set<UUID> workItemIds = new HashSet<>();
        for (ContributionLedgerEntry e : entries) {
            userIds.add(e.getUserId());
            userIds.add(e.getActorId());
            if (e.getWorkItemId() != null) workItemIds.add(e.getWorkItemId());
        }

        Map<UUID, UserResponse> userMap = fetchUserMap(userIds);
        Map<UUID, WorkItem> workItemMap = workItemRepository.findAllById(workItemIds).stream()
                .collect(Collectors.toMap(WorkItem::getId, w -> w));

        return entries.stream().map(e -> {
            UserResponse u = userMap.get(e.getUserId());
            UserResponse actor = userMap.get(e.getActorId());
            WorkItem wi = e.getWorkItemId() != null ? workItemMap.get(e.getWorkItemId()) : null;

            return ContributionLedgerEntryResponse.builder()
                    .id(e.getId())
                    .createdAt(e.getCreatedAt())
                    .userId(e.getUserId())
                    .userName(u != null ? u.getFullName() : "Thành viên")
                    .userAvatar(null)
                    .workItemId(e.getWorkItemId())
                    .workItemCode(wi != null ? wi.getCode() : null)
                    .workItemTitle(wi != null ? wi.getSummary() : "Điều chỉnh thủ công")
                    .points(e.getPoints())
                    .entryType(e.getEntryType())
                    .note(e.getNote())
                    .actorId(e.getActorId())
                    .actorName(actor != null ? actor.getFullName() : "Hệ thống")
                    .build();
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MemberContributionDetailResponse getMemberDetail(
            UUID projectId, UUID targetUserId, UUID sprintId, UUID currentUserId) {
        boolean isLeader = currentUserId == null || projectMemberService.hasPermission(projectId, currentUserId, PermissionKey.PROJECT_ADMIN);

        if (!isLeader && !targetUserId.equals(currentUserId)) {
            throw new AccessDeniedException("Thành viên chỉ được quyền xem chi tiết đóng góp của chính mình");
        }

        Sprint sprint = sprintId != null ? sprintRepository.findById(sprintId).orElse(null) : null;

        List<ContributionLedgerEntry> userEntries = sprint != null
                ? ledgerRepository.findByProjectIdAndSprintIdAndUserIdOrderByCreatedAtDesc(projectId, sprint.getId(), targetUserId)
                : ledgerRepository.findByProjectIdAndUserIdOrderByCreatedAtDesc(projectId, targetUserId);

        BigDecimal totalPoints = userEntries.stream()
                .map(ContributionLedgerEntry::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int completedTasks = (int) userEntries.stream()
                .filter(e -> "COMPLETED".equals(e.getEntryType()))
                .count();

        long onTimeCount = userEntries.stream()
                .filter(e -> "COMPLETED".equals(e.getEntryType()) && e.getNote() != null && e.getNote().contains("Đúng hạn"))
                .count();

        BigDecimal onTimeRate = completedTasks > 0
                ? BigDecimal.valueOf(onTimeCount * 100.0 / completedTasks).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100.0);

        // Biểu đồ điểm theo tuần (Weekly Points)
        List<WeeklyPointResponse> weeklyPoints = buildWeeklyPoints(userEntries, sprint);

        // Danh sách công việc người này đã thực hiện
        Set<UUID> workItemIds = userEntries.stream()
                .map(ContributionLedgerEntry::getWorkItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, WorkItem> itemMap = workItemRepository.findAllById(workItemIds).stream()
                .collect(Collectors.toMap(WorkItem::getId, w -> w));

        List<MemberWorkItemContributionResponse> workItemList = userEntries.stream().map(e -> {
            WorkItem wi = e.getWorkItemId() != null ? itemMap.get(e.getWorkItemId()) : null;
            return MemberWorkItemContributionResponse.builder()
                    .workItemId(e.getWorkItemId())
                    .code(wi != null ? wi.getCode() : "ADJUST")
                    .title(wi != null ? wi.getSummary() : e.getNote())
                    .points(e.getPoints())
                    .note(e.getNote())
                    .statusCategory("DONE")
                    .completedAt(e.getCreatedAt())
                    .build();
        }).toList();

        UserResponse user = fetchUser(targetUserId);

        return MemberContributionDetailResponse.builder()
                .userId(targetUserId)
                .name(user != null ? user.getFullName() : "Thành viên")
                .avatarUrl(null)
                .sprintName(sprint != null ? sprint.getName() : "Tất cả chu kỳ")
                .totalPoints(totalPoints)
                .completedTasks(completedTasks)
                .onTimeRatePercent(onTimeRate)
                .weeklyPoints(weeklyPoints)
                .workItems(workItemList)
                .build();
    }

    private List<WeeklyPointResponse> buildWeeklyPoints(List<ContributionLedgerEntry> entries, Sprint sprint) {
        Map<Integer, BigDecimal> weekMap = new TreeMap<>();
        int defaultWeeks = 3;
        if (sprint != null && sprint.getStartDate() != null && sprint.getEndDate() != null) {
            long totalDays = ChronoUnit.DAYS.between(sprint.getStartDate().toLocalDate(), sprint.getEndDate().toLocalDate());
            defaultWeeks = Math.max(1, (int) Math.ceil(totalDays / 7.0));
        }

        for (int i = 1; i <= Math.min(defaultWeeks, 6); i++) {
            weekMap.put(i, BigDecimal.ZERO);
        }

        LocalDate startDate = (sprint != null && sprint.getStartDate() != null)
                ? sprint.getStartDate().toLocalDate()
                : LocalDate.now().minusWeeks(defaultWeeks);

        for (ContributionLedgerEntry e : entries) {
            LocalDate entryDate = e.getCreatedAt().toLocalDate();
            long days = ChronoUnit.DAYS.between(startDate, entryDate);
            int week = Math.max(1, (int) (days / 7) + 1);
            weekMap.merge(week, e.getPoints(), BigDecimal::add);
        }

        return weekMap.entrySet().stream()
                .map(entry -> WeeklyPointResponse.builder()
                        .weekLabel("Tuần " + entry.getKey())
                        .points(entry.getValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public ContributionLedgerEntryResponse manualAdjustment(
            UUID projectId, ManualAdjustmentRequest request, UUID leaderUserId) {

        // 1. Kiểm tra quyền Leader / Admin
        if (!projectMemberService.hasPermission(projectId, leaderUserId, PermissionKey.PROJECT_ADMIN)) {
            throw new AccessDeniedException("Chỉ Quản lý dự án (Project Leader / Admin) mới có quyền điều chỉnh điểm đóng góp");
        }

        // 2. Validate biên độ điểm: tối đa +/- 8 điểm
        if (request.getPoints() == null ||
                request.getPoints().compareTo(BigDecimal.valueOf(8)) > 0 ||
                request.getPoints().compareTo(BigDecimal.valueOf(-8)) < 0) {
            throw new BadRequestException("Biên độ điều chỉnh điểm tối đa là từ -8 đến +8 điểm");
        }

        ContributionLedgerEntry entry = ContributionLedgerEntry.builder()
                .projectId(projectId)
                .sprintId(request.getSprintId())
                .workItemId(request.getWorkItemId())
                .userId(request.getUserId())
                .points(request.getPoints())
                .entryType("MANUAL_ADJUSTMENT")
                .note(request.getReason())
                .actorId(leaderUserId)
                .build();

        ContributionLedgerEntry saved = ledgerRepository.save(entry);
        // I1
        projectRepository.findById(projectId).ifPresent(p -> projectNotifier.contributionAdjusted(
                p, request.getUserId(), request.getPoints(), request.getReason(), leaderUserId));

        UserResponse user = fetchUser(request.getUserId());
        UserResponse leader = fetchUser(leaderUserId);
        WorkItem wi = request.getWorkItemId() != null ? workItemRepository.findById(request.getWorkItemId()).orElse(null) : null;

        log.info("Leader '{}' đã điều chỉnh {} điểm cho thành viên '{}'. Lý do: {}",
                leaderUserId, request.getPoints(), request.getUserId(), request.getReason());

        return ContributionLedgerEntryResponse.builder()
                .id(saved.getId())
                .createdAt(saved.getCreatedAt())
                .userId(saved.getUserId())
                .userName(user != null ? user.getFullName() : "Thành viên")
                .workItemId(saved.getWorkItemId())
                .workItemCode(wi != null ? wi.getCode() : null)
                .workItemTitle(wi != null ? wi.getSummary() : "Điều chỉnh thủ công")
                .points(saved.getPoints())
                .entryType(saved.getEntryType())
                .note(saved.getNote())
                .actorId(leaderUserId)
                .actorName(leader != null ? leader.getFullName() : "Leader")
                .build();
    }

    @Override
    @Transactional
    public void freezeSprintContributionSnapshot(UUID sprintId, UUID projectId) {
        if (snapshotRepository.existsBySprintId(sprintId)) {
            log.info("Sprint '{}' đã có snapshot đóng băng đóng góp. Bỏ qua.", sprintId);
            return;
        }

        Sprint sprint = sprintRepository.findById(sprintId).orElse(null);
        if (sprint == null) return;

        ContributionOverviewResponse live = calculateLiveOverview(projectId, sprint, null, null, null);

        List<SprintContributionSnapshot> snapshots = live.getMembers().stream().map(m ->
                SprintContributionSnapshot.builder()
                        .sprintId(sprintId)
                        .projectId(projectId)
                        .userId(m.getUserId())
                        .totalPoints(m.getPoints())
                        .contributionPercentage(m.getPercentage())
                        .completedTasks(m.getCompletedTasks())
                        .discussionCount(m.getDiscussionCount())
                        .bugsReported(m.getBugsReported())
                        .onTimeRate(m.getOnTimeRatePercent())
                        .reopenedCount(m.getReopenedCount())
                        .build()
        ).toList();

        snapshotRepository.saveAll(snapshots);
        log.info("Đã đóng băng thành công snapshot đóng góp cho Sprint '{}' ({} thành viên)", sprint.getName(), snapshots.size());

        // I2: mỗi thành viên thấy điểm được ghi nhận của mình
        Map<UUID, BigDecimal> pointsByUser = new LinkedHashMap<>();
        snapshots.forEach(s -> { if (s.getUserId() != null) pointsByUser.put(s.getUserId(), s.getTotalPoints()); });
        projectRepository.findById(projectId).ifPresent(p -> projectNotifier.contributionFrozen(p, sprint, pointsByUser));
    }

    @Override
    @Transactional(readOnly = true)
    public void exportContributionCsv(UUID projectId, UUID sprintId, HttpServletResponse response) {
        ContributionOverviewResponse overview = getContributionOverview(projectId, sprintId, null, null, null);

        try {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"Bao_cao_dong_gop_" + (sprintId != null ? sprintId : projectId) + ".csv\"");

            // Thêm UTF-8 BOM để Excel hiển thị đúng dấu tiếng Việt
            response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            PrintWriter writer = new PrintWriter(response.getOutputStream(), true, StandardCharsets.UTF_8);
            writer.println("Thành viên,Email,Điểm đóng góp,Tỉ lệ đóng góp (%),Việc đã xong,Thảo luận,Báo lỗi,Tỉ lệ đúng hạn (%),Bị mở lại");

            for (MemberContributionResponse m : overview.getMembers()) {
                writer.printf("\"%s\",\"%s\",%s,%s%%,%d,%d,%d,%s%%,%d%n",
                        escapeCsv(m.getName()),
                        escapeCsv(m.getEmail()),
                        m.getPoints(),
                        m.getPercentage(),
                        m.getCompletedTasks(),
                        m.getDiscussionCount(),
                        m.getBugsReported(),
                        m.getOnTimeRatePercent(),
                        m.getReopenedCount());
            }
            writer.flush();
        } catch (Exception e) {
            log.error("Lỗi khi xuất file CSV báo cáo đóng góp: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể xuất file báo cáo đóng góp: " + e.getMessage());
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }

    private Map<UUID, UserResponse> fetchUserMap(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        try {
            var res = identityClient.getUsersByIds(new ArrayList<>(userIds));
            if (res != null && res.getData() != null) {
                return res.getData().stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(
                                u -> {
                                    if (u.getKeycloakId() != null) {
                                        try {
                                            return UUID.fromString(u.getKeycloakId());
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    return u.getId();
                                },
                                u -> u,
                                (existing, replacing) -> existing
                        ));
            }
        } catch (Exception e) {
            log.warn("Không thể tải thông tin người dùng từ identity-service: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    private UserResponse fetchUser(UUID userId) {
        if (userId == null) return null;
        try {
            var res = identityClient.getUserById(userId);
            if (res != null && res.getData() != null) {
                return res.getData();
            }
        } catch (Exception e) {
            log.debug("Không thể tải thông tin user {}: {}", userId, e.getMessage());
        }
        return null;
    }
}
