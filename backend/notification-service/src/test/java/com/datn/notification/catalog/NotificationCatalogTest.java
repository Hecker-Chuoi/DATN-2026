package com.datn.notification.catalog;

import com.datn.common.notification.NotificationEvent;
import com.datn.common.notification.NotificationType;
import com.datn.notification.enums.NotificationCategory;
import com.datn.notification.enums.NotificationSeverity;
import com.datn.notification.service.MailTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.datn.common.notification.NotificationType.*;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationCatalogTest {

    private static final String WS = "11111111-1111-1111-1111-111111111111";
    private static final String PROJECT = "22222222-2222-2222-2222-222222222222";
    private static final String ITEM = "33333333-3333-3333-3333-333333333333";
    private static final String ME = "44444444-4444-4444-4444-444444444444";
    private static final String OTHER = "55555555-5555-5555-5555-555555555555";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    private final NotificationCatalog catalog = new NotificationCatalog();

    /** Sự kiện mẫu đủ mọi trường mà các loại có thể đọc. */
    private static NotificationEvent sample(NotificationType type) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectType", "SOFTWARE_SCRUM");
        data.put("priority", "HIGH");
        data.put("dueDate", "2026-11-20");
        data.put("oldDueDate", "2026-11-25");
        data.put("newDueDate", "2026-11-20");
        data.put("toStatus", "Đang thực hiện");
        data.put("toCategory", "IN_PROGRESS");
        data.put("childCode", "MOC-3");
        data.put("childCodes", List.of("MOC-3", "MOC-4"));
        data.put("targetCode", "MOC-2");
        data.put("sprintName", "Giai đoạn 1");
        data.put("blockerCode", "MOC-2");
        data.put("reason", "Thiếu số liệu");
        data.put("note", "Đã khảo sát đủ 3 địa điểm");
        data.put("count", 2);
        data.put("codes", List.of("MOC-2", "MOC-5"));
        data.put("commentId", "66666666-6666-6666-6666-666666666666");
        data.put("snippet", "@An ưu tiên khung 7h–9h sáng");
        data.put("startDate", "2026-11-14");
        data.put("endDate", "2026-11-28");
        data.put("itemCounts", Map.of(ME, 3));
        data.put("completedCount", 5);
        data.put("totalCount", 6);
        data.put("targetName", "Giai đoạn 2");
        data.put("incompleteCounts", Map.of(ME, 1));
        data.put("daysOverdue", 2);
        data.put("openCounts", Map.of(ME, 2));
        data.put("newEndDate", "2026-11-30");
        data.put("daysLeft", 1);
        data.put("overdue", 2);
        data.put("dueToday", 1);
        data.put("pendingApprovals", 1);
        data.put("unassigned", 3);
        data.put("days", 6);
        data.put("status", "Đang thực hiện");
        data.put("openCount", 8);
        data.put("roleName", "MEMBER");
        data.put("role", "MEMBER");
        data.put("memberId", OTHER);
        data.put("accepted", true);
        data.put("newRole", "ADMINISTRATOR");
        data.put("newLeadId", ME);
        data.put("newAssigneeId", OTHER);
        data.put("points", 3);
        data.put("newStartDate", "2026-11-01");
        data.put("endChanged", true);
        return NotificationEvent.builder()
                .eventId("evt-1")
                .type(type.name())
                .actorId(OTHER)
                .workspaceId(WS)
                .workspaceName("Mộc Group")
                .projectId(PROJECT)
                .projectKey("MOC")
                .projectName("Mộc Coffee")
                .entityType("WORK_ITEM")
                .entityId(ITEM)
                .entityCode("MOC-2")
                .entityTitle("Khảo sát lưu lượng khách")
                .data(data)
                .build();
    }

    private static RenderContext ctx(NotificationEvent e) {
        return new RenderContext(e, ME, "An", 0, id -> id.equals(OTHER) ? "Bình" : "Chi", TODAY);
    }

    @Test
    void everyTypeHasASpec() {
        Set<NotificationType> missing = EnumSet.allOf(NotificationType.class);
        missing.removeAll(catalog.all().keySet());
        assertThat(missing).isEmpty();
        long visible = Arrays.stream(NotificationType.values()).filter(t -> !t.isInternal()).count();
        assertThat(visible).as("tài liệu danh mục có 53 loại").isEqualTo(53);
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void everyVisibleTypeRendersCompleteVietnameseContent(NotificationType type) {
        if (type.isInternal()) return;
        NotificationSpec spec = catalog.find(type).orElseThrow();
        Rendered r = spec.render(ctx(sample(type)));

        assertThat(r.title()).isNotBlank();
        assertThat(r.title()).doesNotContain("null");
        assertThat(r.body()).doesNotContain("null");
        assertThat(r.link()).startsWith("/").doesNotContain("null").doesNotContain("{");
        // Chỉ được dùng thẻ <b>
        String withoutBold = r.title().replace("<b>", "").replace("</b>", "");
        assertThat(withoutBold).doesNotContain("<").doesNotContain(">");
    }

    @Test
    void categoriesMatchCatalogGroups() {
        Map<NotificationCategory, Long> counts = catalog.all().values().stream()
                .filter(s -> !s.type().isInternal())
                .collect(Collectors.groupingBy(NotificationSpec::category, Collectors.counting()));
        assertThat(counts).containsEntry(NotificationCategory.TASK, 10L)
                .containsEntry(NotificationCategory.DEPENDENCY, 2L)
                .containsEntry(NotificationCategory.APPROVAL, 5L)
                .containsEntry(NotificationCategory.COMMENT, 3L)
                .containsEntry(NotificationCategory.SPRINT, 9L)
                .containsEntry(NotificationCategory.REMINDER, 8L)
                .containsEntry(NotificationCategory.MEMBER, 11L)
                .containsEntry(NotificationCategory.PROJECT, 3L)
                .containsEntry(NotificationCategory.CONTRIBUTION, 2L);
    }

    @Test
    void actionRequiredOnlyForTheSixDocumentedTypes() {
        Set<NotificationType> action = catalog.all().values().stream()
                .filter(NotificationSpec::actionRequired).map(NotificationSpec::type).collect(Collectors.toSet());
        assertThat(action).containsExactlyInAnyOrder(COMPLETION_REQUESTED, COMPLETION_PENDING_REMINDER,
                SPRINT_OVERDUE, NEXT_SPRINT_DUE, PROJECT_INVITATION_RECEIVED, WORKSPACE_INVITATION_RECEIVED);
    }

    @Test
    void emailOnlyForTheSevenDocumentedTypes_andTemplatesRenderWithoutPlaceholders() {
        MailTemplateService templates = new MailTemplateService();
        Set<NotificationType> withEmail = catalog.all().values().stream()
                .filter(s -> s.email() != null).map(NotificationSpec::type).collect(Collectors.toSet());
        assertThat(withEmail).containsExactlyInAnyOrder(TASK_ASSIGNED, COMPLETION_REQUESTED, COMMENT_MENTION,
                TASK_DUE_SOON, TASK_OVERDUE, PROJECT_INVITATION_RECEIVED, WORKSPACE_INVITATION_RECEIVED);

        for (NotificationType type : withEmail) {
            NotificationSpec.EmailSpec email = catalog.find(type).orElseThrow().email();
            RenderContext c = ctx(sample(type));
            Map<String, String> vars = NotificationCatalog.withRecipient(
                    email.vars().apply(c, "http://localhost:5173/browse/MOC-2"), "Chi");
            String html = templates.render(email.template(), vars);
            assertThat(html).as("template %s của %s", email.template(), type).doesNotContain("{{");
            assertThat(email.subject().apply(c)).isNotBlank().doesNotContain("null");
        }
    }

    @Test
    void taskAssigned_isWarningWhenHighPriorityOrDueSoon_otherwiseInfo() {
        NotificationSpec spec = catalog.find(TASK_ASSIGNED).orElseThrow();

        NotificationEvent high = sample(TASK_ASSIGNED);
        assertThat(spec.render(ctx(high)).severity()).isEqualTo(NotificationSeverity.WARNING);

        NotificationEvent normal = sample(TASK_ASSIGNED);
        normal.getData().put("priority", "MEDIUM");
        normal.getData().put("dueDate", "2026-12-20");
        Rendered r = spec.render(ctx(normal));
        assertThat(r.severity()).isNull(); // dùng mức mặc định INFO
        assertThat(r.title()).isEqualTo("<b>An</b> đã giao cho bạn <b>MOC-2</b> · Khảo sát lưu lượng khách");
        assertThat(r.body()).isEqualTo("Ưu tiên Trung bình · Hạn chót 20/12/2026");
        assertThat(r.link()).isEqualTo("/browse/MOC-2");

        NotificationEvent dueTomorrow = sample(TASK_ASSIGNED);
        dueTomorrow.getData().put("priority", "LOW");
        dueTomorrow.getData().put("dueDate", "2026-11-15");
        assertThat(spec.render(ctx(dueTomorrow)).severity()).isEqualTo(NotificationSeverity.WARNING);
    }

    @Test
    void titleEscapesUserText() {
        NotificationEvent e = sample(TASK_ASSIGNED);
        e.setEntityTitle("<script>alert(1)</script>");
        Rendered r = catalog.find(TASK_ASSIGNED).orElseThrow().render(ctx(e));
        assertThat(r.title()).contains("&lt;script&gt;").doesNotContain("<script>");
    }

    @Test
    void mergedNotificationNamesOtherActors() {
        NotificationEvent e = sample(COMMENT_ADDED);
        RenderContext merged = new RenderContext(e, ME, "An", 2, id -> "x", TODAY);
        Rendered r = catalog.find(COMMENT_ADDED).orElseThrow().render(merged);
        assertThat(r.title()).isEqualTo("<b>An</b> và 2 người khác đã bình luận trong <b>MOC-2</b>");
        assertThat(r.link()).isEqualTo("/browse/MOC-2?comment=66666666-6666-6666-6666-666666666666");
    }

    @Test
    void statusChangedToDone_isSuccess() {
        NotificationEvent e = sample(TASK_STATUS_CHANGED);
        e.getData().put("toCategory", "DONE");
        e.getData().put("toStatus", "Hoàn thành");
        Rendered r = catalog.find(TASK_STATUS_CHANGED).orElseThrow().render(ctx(e));
        assertThat(r.severity()).isEqualTo(NotificationSeverity.SUCCESS);
        assertThat(r.title()).isEqualTo("<b>An</b> đã chuyển <b>MOC-2</b> sang “Hoàn thành”");
    }

    @Test
    void sprintWording_followsSprintName() {
        NotificationEvent phase = sample(SPRINT_OVERDUE);
        phase.setEntityTitle("Giai đoạn 1");
        assertThat(catalog.find(SPRINT_OVERDUE).orElseThrow().render(ctx(phase)).body())
                .isEqualTo("Hãy hoàn tất giai đoạn hoặc dời ngày kết thúc");

        NotificationEvent sprint = sample(SPRINT_OVERDUE);
        sprint.getData().put("sprintName", "Sprint 3");
        assertThat(catalog.find(SPRINT_OVERDUE).orElseThrow().render(ctx(sprint)).body())
                .isEqualTo("Hãy hoàn tất chu kỳ hoặc dời ngày kết thúc");
    }

    @Test
    void sprintCompleted_personalLineIsWarningOnlyForPeopleWithOpenWork() {
        NotificationSpec spec = catalog.find(SPRINT_COMPLETED).orElseThrow();
        NotificationEvent e = sample(SPRINT_COMPLETED);
        Rendered mine = spec.render(ctx(e));
        assertThat(mine.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(mine.body()).isEqualTo("Riêng bạn: 1 công việc chưa xong được chuyển sang Giai đoạn 2");

        e.getData().put("incompleteCounts", new HashMap<>(Map.of(OTHER, 4)));
        Rendered other = spec.render(ctx(e));
        assertThat(other.severity()).isNull();
        assertThat(other.link()).isEqualTo("/w/" + WS + "/projects/" + PROJECT + "/reports?tab=progress&sprint=" + ITEM);
    }

    @Test
    void leadChanged_differsForNewAndOldLead() {
        NotificationSpec spec = catalog.find(PROJECT_LEAD_CHANGED).orElseThrow();
        NotificationEvent e = sample(PROJECT_LEAD_CHANGED);
        assertThat(spec.render(ctx(e)).title()).isEqualTo("Bạn trở thành Trưởng dự án <b>Mộc Coffee</b>");

        e.getData().put("newLeadId", OTHER);
        assertThat(spec.render(ctx(e)).title()).isEqualTo("<b>Bình</b> trở thành Trưởng dự án <b>Mộc Coffee</b> thay bạn");
    }

    @Test
    void contributionAdjusted_negativeIsWarning() {
        NotificationEvent e = sample(CONTRIBUTION_ADJUSTED);
        e.getData().put("points", -2.5);
        Rendered r = catalog.find(CONTRIBUTION_ADJUSTED).orElseThrow().render(ctx(e));
        assertThat(r.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(r.title()).isEqualTo("<b>An</b> đã trừ 2.5 điểm đóng góp của bạn");
    }

    @Test
    void removedFromProject_linkNeverPointsIntoTheProject() {
        Rendered r = catalog.find(PROJECT_MEMBER_REMOVED).orElseThrow().render(ctx(sample(PROJECT_MEMBER_REMOVED)));
        assertThat(r.link()).isEqualTo("/w/" + WS + "/projects");
    }

    @Test
    void kanbanProject_planLinkGoesToBoard() {
        NotificationEvent e = sample(PROJECT_DAILY_DIGEST);
        e.getData().put("projectType", "SOFTWARE_KANBAN");
        Rendered r = catalog.find(PROJECT_DAILY_DIGEST).orElseThrow().render(ctx(e));
        assertThat(r.link()).isEqualTo("/w/" + WS + "/projects/" + PROJECT + "/board");
        assertThat(r.title()).isEqualTo("<b>Mộc Coffee</b> hôm nay: 2 quá hạn · 1 đến hạn · 1 chờ xác nhận");
    }

    @Test
    void resolutionRules() {
        assertThat(catalog.find(COMPLETION_APPROVED).orElseThrow().resolves()).containsExactly(COMPLETION_REQUESTED);
        assertThat(catalog.find(COMPLETION_REJECTED).orElseThrow().resolves()).containsExactly(COMPLETION_REQUESTED);
        assertThat(catalog.find(COMPLETION_CANCELLED).orElseThrow().resolves()).containsExactly(COMPLETION_REQUESTED);
        assertThat(catalog.find(SPRINT_COMPLETED).orElseThrow().resolves()).containsExactly(SPRINT_OVERDUE);
        assertThat(catalog.find(SPRINT_STARTED).orElseThrow().resolves()).containsExactly(NEXT_SPRINT_DUE);
        assertThat(catalog.find(PROJECT_INVITATION_ANSWERED).orElseThrow().resolves()).containsExactly(PROJECT_INVITATION_RECEIVED);
        assertThat(catalog.find(PROJECT_INVITATION_CANCELLED).orElseThrow().resolves()).containsExactly(PROJECT_INVITATION_RECEIVED);
        assertThat(catalog.find(WORKSPACE_INVITATION_ANSWERED).orElseThrow().resolves()).containsExactly(WORKSPACE_INVITATION_RECEIVED);
        assertThat(catalog.find(COMPLETION_PENDING_REMINDER).orElseThrow().resolveByProject()).isTrue();
        assertThat(catalog.find(COMPLETION_WITHDRAWN).orElseThrow().resolves()).containsExactly(COMPLETION_REQUESTED);
        assertThat(catalog.find(SPRINT_OVERDUE).orElseThrow().resolves()).containsExactly(SPRINT_OVERDUE);
        assertThat(COMPLETION_WITHDRAWN.isInternal()).isTrue();
    }

    @Test
    void groupKeysOnlyForGroupableTypes() {
        Set<NotificationType> grouped = catalog.all().values().stream()
                .filter(s -> s.groupKey(sample(s.type())) != null).map(NotificationSpec::type).collect(Collectors.toSet());
        assertThat(grouped).containsExactlyInAnyOrder(TASK_STATUS_CHANGED, TASK_SPRINT_CHANGED, COMMENT_ADDED,
                SPRINT_SCOPE_ADDED, SPRINT_SCOPE_REMOVED);
    }
}
