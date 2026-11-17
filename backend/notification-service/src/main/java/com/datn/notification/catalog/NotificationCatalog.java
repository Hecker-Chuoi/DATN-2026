package com.datn.notification.catalog;

import com.datn.common.notification.NotificationEvent;
import com.datn.common.notification.NotificationType;
import com.datn.notification.enums.NotificationCategory;
import com.datn.notification.enums.NotificationSeverity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.datn.common.notification.NotificationType.*;
import static com.datn.notification.catalog.RenderContext.b;
import static com.datn.notification.catalog.RenderContext.fmt;
import static com.datn.notification.catalog.RenderContext.fmtShort;
import static com.datn.notification.catalog.RenderContext.quote;
import static com.datn.notification.enums.NotificationCategory.*;
import static com.datn.notification.enums.NotificationSeverity.*;

/**
 * Danh mục 53 loại thông báo: nội dung tiếng Việt, mức độ, nhóm, gộp, "Cần xử lý" và email.
 * Khớp 1:1 với docs/services/notification/1_DANH_MUC_LOAI_THONG_BAO.md (mã A1…I2 ghi ở từng mục).
 */
@Component
public class NotificationCatalog {

    private final Map<NotificationType, NotificationSpec> specs = new EnumMap<>(NotificationType.class);

    public NotificationCatalog() {
        defineTask();
        defineDependency();
        defineApproval();
        defineComment();
        defineSprint();
        defineReminder();
        defineMember();
        defineProject();
        defineContribution();
        defineInternal();
    }

    public Optional<NotificationSpec> find(NotificationType type) {
        return Optional.ofNullable(type == null ? null : specs.get(type));
    }

    public Map<NotificationType, NotificationSpec> all() {
        return specs;
    }

    private NotificationSpec def(NotificationType type, NotificationCategory category, NotificationSeverity severity,
                                 Function<RenderContext, Rendered> renderer) {
        NotificationSpec spec = new NotificationSpec(type, category, severity, renderer);
        specs.put(type, spec);
        return spec;
    }

    private static Function<NotificationEvent, String> byEntity(String prefix) {
        return e -> e.getEntityId() == null ? null : prefix + ":" + e.getEntityId();
    }

    private static String joinCodes(List<String> codes) {
        if (codes.isEmpty()) return "";
        if (codes.size() <= 5) return String.join(", ", codes);
        return String.join(", ", codes.subList(0, 5)) + " và " + (codes.size() - 5) + " việc khác";
    }

    // ═══════════════════════ A. Công việc ═══════════════════════

    private void defineTask() {
        // A1
        def(TASK_ASSIGNED, TASK, INFO, c -> {
            String priority = c.str("priority");
            LocalDate due = c.date("dueDate");
            String body = "Ưu tiên " + c.priorityLabel(priority == null ? "MEDIUM" : priority)
                    + (due != null ? " · Hạn chót " + fmt(due) : "");
            boolean urgent = "HIGH".equals(priority) || "HIGHEST".equals(priority)
                    || (due != null && c.daysFromToday(due) <= 2);
            Rendered r = Rendered.of(c.actor() + " đã giao cho bạn " + c.item() + " · " + RenderContext.esc(c.summary()),
                    body, c.browse());
            return urgent ? r.withSeverity(WARNING) : r;
        }).email("task-assigned",
                c -> "[DATN-2026] Bạn được phân công công việc [" + c.code() + "]",
                (c, link) -> Map.of(
                        "assigner_name", c.actorName(),
                        "work_item_key", c.code(),
                        "summary", c.summary(),
                        "project_name", c.projectName(),
                        "due_date", c.date("dueDate") != null ? fmt(c.date("dueDate")) : "Không có",
                        "link", link));

        // A2
        def(TASK_UNASSIGNED, TASK, INFO, c -> Rendered.of(
                c.has("newAssigneeId")
                        ? c.actor() + " đã giao " + c.item() + " cho người khác"
                        : c.actor() + " đã huỷ giao " + c.item(),
                "Bạn không còn là người thực hiện công việc này", c.browse()));

        // A3
        def(TASK_STATUS_CHANGED, TASK, INFO, c -> {
            Rendered r = Rendered.of(c.actor() + " đã chuyển " + c.item() + " sang “" + RenderContext.esc(c.str("toStatus")) + "”",
                    c.summary(), c.browse());
            return "DONE".equals(c.str("toCategory")) ? r.withSeverity(SUCCESS) : r;
        }).group(byEntity("A3"));

        // A4
        def(TASK_PRIORITY_RAISED, TASK, WARNING, c -> Rendered.of(
                c.actor() + " đã nâng ưu tiên " + c.item() + " lên " + RenderContext.esc(c.priorityLabel(c.str("priority"))),
                c.summary(), c.browse()));

        // A5
        def(TASK_DUE_DATE_CHANGED, TASK, INFO, c -> {
            LocalDate oldDue = c.date("oldDueDate");
            LocalDate newDue = c.date("newDueDate");
            if (newDue == null) {
                return Rendered.of(c.actor() + " đã bỏ hạn chót của " + c.item(), c.summary(), c.browse());
            }
            if (oldDue == null) {
                return Rendered.of(c.actor() + " đã đặt hạn chót " + c.item() + " là " + fmt(newDue), c.summary(), c.browse());
            }
            long diff = java.time.temporal.ChronoUnit.DAYS.between(newDue, oldDue);
            if (diff > 0) {
                return Rendered.of(c.actor() + " đã dời hạn chót " + c.item() + " sớm hơn " + diff + " ngày",
                        "Từ " + fmt(oldDue) + " sang " + fmt(newDue), c.browse()).withSeverity(WARNING);
            }
            return Rendered.of(c.actor() + " đã dời hạn chót " + c.item() + " sang " + fmt(newDue),
                    "Từ " + fmt(oldDue) + " sang " + fmt(newDue), c.browse());
        });

        // A6
        def(TASK_DELETED, TASK, WARNING, c -> Rendered.of(
                c.actor() + " đã xoá " + c.item() + " · " + RenderContext.esc(c.summary()),
                "Công việc không còn trong dự án " + c.projectName(), c.projectPath("")));

        // A7
        def(PARENT_AUTO_REOPENED, TASK, WARNING, c -> Rendered.of(
                c.item() + " tự mở lại vì công việc con " + b(c.str("childCode"))
                        + " chuyển sang “" + RenderContext.esc(c.str("toStatus")) + "”",
                "Do " + c.actorName() + " thao tác trên " + c.str("childCode"), c.browse()));

        // A8
        def(SUBTASKS_AUTO_RESOLVED, TASK, INFO, c -> {
            List<String> codes = c.list("childCodes");
            return Rendered.of(c.actor() + " đã hoàn thành " + c.item() + " kèm " + codes.size() + " công việc con của bạn",
                    joinCodes(codes), c.browse());
        });

        // A9
        def(TASK_MARKED_DUPLICATE, TASK, INFO, c -> Rendered.of(
                c.item() + " được đóng vì trùng lặp với " + b(c.str("targetCode")),
                "Do " + c.actorName() + " tạo liên kết", c.browse()));

        // A10
        def(TASK_SPRINT_CHANGED, TASK, INFO, c -> Rendered.of(
                c.has("sprintName")
                        ? c.actor() + " đã chuyển " + c.item() + " sang " + b(c.str("sprintName"))
                        : c.actor() + " đã chuyển " + c.item() + " về Kho việc",
                c.summary(), c.browse())).group(byEntity("A10"));
    }

    // ═══════════════════════ B. Phụ thuộc ═══════════════════════

    private void defineDependency() {
        // B1
        def(TASK_BLOCKED, DEPENDENCY, WARNING, c -> Rendered.of(
                c.item() + " đang bị cản trở bởi " + b(c.str("blockerCode")),
                "Chưa thể bắt đầu cho tới khi " + c.str("blockerCode") + " hoàn thành", c.browse()));

        // B2
        def(TASK_UNBLOCKED, DEPENDENCY, SUCCESS, c -> {
            String blocker = c.str("blockerCode");
            String body = switch (String.valueOf(c.str("reason"))) {
                case "LINK_REMOVED" -> "Liên kết cản trở với " + blocker + " đã được gỡ";
                case "DELETED" -> blocker + " đã bị xoá";
                default -> blocker + " vừa hoàn thành";
            };
            return Rendered.of(c.item() + " đã hết bị cản trở, có thể bắt đầu", body, c.browse());
        });
    }

    // ═══════════════════════ C. Xác nhận hoàn thành ═══════════════════════

    private void defineApproval() {
        // C1
        def(COMPLETION_REQUESTED, APPROVAL, INFO, c -> Rendered.of(
                c.actor() + " báo đã hoàn thành " + c.item() + ", chờ bạn xác nhận",
                c.has("note") ? "Ghi chú: " + quote(c.str("note"), 180) : c.summary(), c.browse()))
                .action()
                .resolves(COMPLETION_REQUESTED)
                .email("notification",
                        c -> "[DATN-2026] " + c.actorName() + " báo đã hoàn thành [" + c.code() + "], chờ bạn xác nhận",
                        (c, link) -> Map.of(
                                "heading", c.actorName() + " báo đã hoàn thành công việc và chờ bạn xác nhận.",
                                "item_line", "[" + c.code() + "] " + c.summary(),
                                "project_name", c.projectName(),
                                "detail", c.has("note") ? "Ghi chú: " + quote(c.str("note"), 300) : "Không có ghi chú",
                                "accent_color", "#7c3aed",
                                "button_label", "Xem và xác nhận",
                                "link", link));

        // C2
        def(COMPLETION_APPROVED, APPROVAL, SUCCESS, c -> Rendered.of(
                c.actor() + " đã xác nhận hoàn thành " + c.item(),
                "Điểm đóng góp đã được ghi cho người thực hiện", c.browse()))
                .resolves(COMPLETION_REQUESTED);

        // C3
        def(COMPLETION_REJECTED, APPROVAL, WARNING, c -> Rendered.of(
                c.actor() + " chưa xác nhận hoàn thành " + c.item(),
                c.has("reason") ? "Lý do: " + quote(c.str("reason"), 180) : "Công việc giữ nguyên trạng thái",
                c.browse()))
                .resolves(COMPLETION_REQUESTED);

        // C4
        def(COMPLETION_PENDING_REMINDER, APPROVAL, WARNING, c -> Rendered.of(
                c.project() + " có " + c.num("count") + " yêu cầu xác nhận hoàn thành chờ quá 2 ngày",
                joinCodes(c.list("codes")), c.projectPath("/board")))
                .action()
                .resolves(COMPLETION_PENDING_REMINDER)
                .resolvingByProject();

        // C5
        def(COMPLETION_CANCELLED, APPROVAL, INFO, c -> Rendered.of(
                "Yêu cầu xác nhận hoàn thành " + c.item() + " đã bị huỷ",
                "AUTO_STATUS_CHANGED".equals(c.str("reason"))
                        ? c.actorName() + " đã đổi trạng thái sang “" + c.str("toStatus") + "” trước khi duyệt"
                        : "Do " + c.actorName() + " rút lại yêu cầu",
                c.browse()))
                .resolves(COMPLETION_REQUESTED);
    }

    // ═══════════════════════ D. Bình luận ═══════════════════════

    private void defineComment() {
        // D1
        def(COMMENT_MENTION, COMMENT, INFO, c -> Rendered.of(
                c.actor() + " đã nhắc đến bạn trong " + c.item(),
                quote(c.str("snippet"), 200), c.browseComment()))
                .email("comment-mention",
                        c -> "[DATN-2026] " + c.actorName() + " đã nhắc đến bạn trong [" + c.code() + "]",
                        (c, link) -> Map.of(
                                "author_name", c.actorName(),
                                "work_item_key", c.code(),
                                "summary", c.summary(),
                                "comment_body", quote(c.str("snippet"), 200),
                                "link", link));

        // D2
        def(COMMENT_ADDED, COMMENT, INFO, c -> Rendered.of(
                c.actor() + " đã bình luận trong " + c.item(),
                quote(c.str("snippet"), 200), c.browseComment()))
                .group(byEntity("D2"));

        // D3
        def(COMMENT_REPLIED, COMMENT, INFO, c -> Rendered.of(
                c.actor() + " đã trả lời bình luận của bạn trong " + c.item(),
                quote(c.str("snippet"), 200), c.browseComment()));
    }

    // ═══════════════════════ E. Chu kỳ / Giai đoạn ═══════════════════════

    private void defineSprint() {
        // E1
        def(SPRINT_STARTED, SPRINT, INFO, c -> {
            LocalDate start = c.date("startDate");
            LocalDate end = c.date("endDate");
            long mine = c.numForRecipient("itemCounts");
            String range = start != null && end != null ? fmtShort(start) + " → " + fmt(end) + " · " : "";
            String body = range + (mine > 0
                    ? "Bạn có " + mine + " công việc trong " + c.term() + " này"
                    : "Bạn chưa có công việc nào trong " + c.term() + " này");
            return Rendered.of(c.sprint() + " của " + c.project() + " đã bắt đầu", body, c.projectPath("/board"));
        }).resolves(NEXT_SPRINT_DUE);

        // E2
        def(SPRINT_COMPLETED, SPRINT, INFO, c -> {
            long open = c.numForRecipient("incompleteCounts");
            String title = c.sprint() + " đã hoàn tất: " + c.num("completedCount") + "/" + c.num("totalCount") + " công việc xong";
            if (open > 0) {
                return Rendered.of(title, "Riêng bạn: " + open + " công việc chưa xong được chuyển sang "
                        + c.str("targetName"), c.projectPath("/reports?tab=progress&sprint=" + c.event().getEntityId()))
                        .withSeverity(WARNING);
            }
            return Rendered.of(title, "Xem báo cáo của " + c.term() + " này",
                    c.projectPath("/reports?tab=progress&sprint=" + c.event().getEntityId()));
        }).resolves(SPRINT_OVERDUE);

        // E3
        def(SPRINT_OVERDUE, SPRINT, URGENT, c -> Rendered.of(
                c.sprint() + " đã quá ngày kết thúc " + c.num("daysOverdue") + " ngày",
                "Hãy hoàn tất " + c.term() + " hoặc dời ngày kết thúc", c.planPath()))
                .action()
                .resolves(SPRINT_OVERDUE); // nhắc ngày 3, 7 thay cho lần nhắc trước

        // E4
        def(SPRINT_ENDING_SOON, SPRINT, WARNING, c -> {
            long mine = c.numForRecipient("openCounts");
            String body = mine > 0 ? "Bạn còn " + mine + " công việc chưa xong"
                    : "Kết thúc " + fmt(c.date("endDate"));
            return Rendered.of(c.sprint() + " kết thúc vào ngày mai", body, c.projectPath("/board"));
        });

        // E5
        def(SPRINT_SCOPE_ADDED, SPRINT, INFO, c -> {
            List<String> codes = c.list("codes");
            return Rendered.of(codes.size() == 1
                            ? c.actor() + " đã thêm " + b(codes.get(0)) + " của bạn vào " + c.sprint() + " đang chạy"
                            : c.actor() + " đã thêm " + codes.size() + " công việc của bạn vào " + c.sprint() + " đang chạy",
                    joinCodes(codes), c.projectPath("/board"));
        }).group(e -> e.getEntityId() == null ? null : "E5:" + e.getEntityId());

        // E6
        def(NEXT_SPRINT_DUE, SPRINT, WARNING, c -> Rendered.of(
                "Đã đến ngày bắt đầu " + c.sprint(),
                "Bấm “Bắt đầu " + c.term() + "” ở Kho việc & Kế hoạch khi đã sẵn sàng", c.planPath()))
                .action();

        // E7
        def(SPRINT_SCOPE_REMOVED, SPRINT, INFO, c -> {
            List<String> codes = c.list("codes");
            return Rendered.of(codes.size() == 1
                            ? c.actor() + " đã đưa " + b(codes.get(0)) + " ra khỏi " + c.sprint()
                            : c.actor() + " đã đưa " + codes.size() + " công việc của bạn ra khỏi " + c.sprint(),
                    "Công việc trở về Kho việc", c.planPath());
        }).group(e -> e.getEntityId() == null ? null : "E7:" + e.getEntityId());

        // E8
        def(SPRINT_DATES_CHANGED, SPRINT, INFO, c -> Rendered.of(
                c.sprint() + " đổi ngày kết thúc sang " + fmt(c.date("newEndDate")),
                "Do " + c.actorName() + " cập nhật", c.planPath()))
                .resolves(SPRINT_OVERDUE);

        // E9
        def(SPRINT_DELETED, SPRINT, INFO, c -> Rendered.of(
                c.actor() + " đã xoá " + c.sprint(),
                c.numForRecipient("itemCounts") + " công việc của bạn đã trở về Kho việc", c.planPath()));
    }

    // ═══════════════════════ F. Nhắc hạn ═══════════════════════

    private void defineReminder() {
        // F1
        def(TASK_DUE_SOON, REMINDER, WARNING, c -> {
            long days = c.num("daysLeft");
            LocalDate due = c.date("dueDate");
            String title = days <= 1
                    ? c.item() + " đến hạn vào ngày mai (" + fmtShort(due) + ")"
                    : c.item() + " đến hạn sau " + days + " ngày (" + fmtShort(due) + ")";
            return Rendered.of(title, c.summary(), c.browse());
        }).email("due-date-reminder",
                c -> "[DATN-2026] Nhắc nhở: Công việc [" + c.code() + "] sắp đến hạn",
                (c, link) -> Map.of(
                        "work_item_key", c.code(),
                        "summary", c.summary(),
                        "due_date", fmt(c.date("dueDate")),
                        "link", link));

        // F2
        def(TASK_DUE_TODAY, REMINDER, WARNING, c -> Rendered.of(
                c.item() + " đến hạn hôm nay", c.summary(), c.browse()));

        // F3
        def(TASK_OVERDUE, REMINDER, URGENT, c -> Rendered.of(
                c.item() + " đã quá hạn " + c.num("daysOverdue") + " ngày",
                "Hạn chót " + fmt(c.date("dueDate")) + " · " + c.summary(), c.browse()))
                .email("notification",
                        c -> "[DATN-2026] Công việc [" + c.code() + "] đã quá hạn",
                        (c, link) -> Map.of(
                                "heading", "Công việc của bạn đã quá hạn " + c.num("daysOverdue") + " ngày.",
                                "item_line", "[" + c.code() + "] " + c.summary(),
                                "project_name", c.projectName(),
                                "detail", "Hạn chót: " + fmt(c.date("dueDate")),
                                "accent_color", "#dc2626",
                                "button_label", "Xem công việc",
                                "link", link));

        // F4
        def(PROJECT_DAILY_DIGEST, REMINDER, WARNING, c -> {
            List<String> parts = new java.util.ArrayList<>();
            if (c.num("overdue") > 0) parts.add(c.num("overdue") + " quá hạn");
            if (c.num("dueToday") > 0) parts.add(c.num("dueToday") + " đến hạn");
            if (c.num("pendingApprovals") > 0) parts.add(c.num("pendingApprovals") + " chờ xác nhận");
            long unassigned = c.num("unassigned");
            if (parts.isEmpty() && unassigned > 0) parts.add(unassigned + " việc chưa giao");
            String body = unassigned > 0
                    ? unassigned + " công việc trong chu kỳ đang chạy chưa có người thực hiện"
                    : "Xem chi tiết ở Kho việc & Kế hoạch";
            return Rendered.of(c.project() + " hôm nay: " + RenderContext.esc(String.join(" · ", parts)), body, c.planPath());
        });

        // F5
        def(TASK_START_TODAY, REMINDER, INFO, c -> Rendered.of(
                "Hôm nay là ngày bắt đầu " + c.item(), c.summary(), c.browse()));

        // F6
        def(TASK_STALE, REMINDER, INFO, c -> Rendered.of(
                c.item() + " đã " + c.num("days") + " ngày chưa được cập nhật",
                "Đang ở trạng thái “" + c.str("status") + "”", c.browse()));

        // F7
        def(PROJECT_ENDING_SOON, REMINDER, WARNING, c -> Rendered.of(
                c.project() + " kết thúc sau " + c.num("daysLeft") + " ngày (" + fmt(c.date("endDate")) + ")",
                "Còn " + c.num("openCount") + " công việc chưa hoàn thành", c.planPath()));

        // F8
        def(PROJECT_OVERDUE, REMINDER, URGENT, c -> Rendered.of(
                c.project() + " đã quá ngày kết thúc " + c.num("daysOverdue") + " ngày",
                "Còn " + c.num("openCount") + " công việc chưa hoàn thành", c.planPath()));
    }

    // ═══════════════════════ G. Thành viên & quyền ═══════════════════════

    private void defineMember() {
        // G1
        def(PROJECT_INVITATION_RECEIVED, MEMBER, INFO, c -> Rendered.of(
                c.actor() + " mời bạn tham gia dự án " + c.project(),
                "Vai trò: " + c.roleLabel(c.str("roleName")), "/invitations"))
                .action()
                .email("invite-member",
                        c -> "[DATN-2026] Lời mời tham gia dự án " + c.projectName(),
                        (c, link) -> Map.of(
                                "inviter_name", c.actorName(),
                                "target_kind", "dự án",
                                "target_name", c.projectName(),
                                "role", c.roleLabel(c.str("roleName")),
                                "link", link));

        // G2
        def(PROJECT_MEMBER_REMOVED, MEMBER, URGENT, c -> Rendered.of(
                "Bạn không còn là thành viên dự án " + c.project(),
                "Do " + c.actorName() + " thực hiện", c.workspacePath("/projects")));

        // G3
        def(MEMBER_REMOVED_OPEN_ITEMS, MEMBER, WARNING, c -> {
            List<String> codes = c.list("codes");
            return Rendered.of(b(c.userName(c.str("memberId"))) + " rời " + c.project() + ", còn "
                            + codes.size() + " công việc chưa xong đứng tên",
                    joinCodes(codes) + ": hãy giao lại cho người khác", c.planPath());
        });

        // G4
        def(PROJECT_INVITATION_ANSWERED, MEMBER, SUCCESS, c -> {
            boolean accepted = c.bool("accepted");
            Rendered r = Rendered.of(c.actor() + (accepted ? " đã chấp nhận" : " đã từ chối") + " lời mời vào " + c.project(),
                    "Vai trò: " + c.roleLabel(c.str("roleName")), c.projectPath("/members"));
            return accepted ? r : r.withSeverity(INFO);
        }).resolves(PROJECT_INVITATION_RECEIVED);

        // G5
        def(PROJECT_MEMBER_ADDED, MEMBER, INFO, c -> Rendered.of(
                c.actor() + " đã thêm bạn vào dự án " + c.project(),
                "Vai trò: " + c.roleLabel(c.str("roleName")), c.projectPath("")));

        // G6
        def(PROJECT_ROLE_CHANGED, MEMBER, INFO, c -> {
            Rendered r = Rendered.of(
                    "Vai trò của bạn trong " + c.project() + " đổi sang " + RenderContext.esc(c.roleLabel(c.str("newRole"))),
                    "Do " + c.actorName() + " thực hiện", c.projectPath(""));
            return c.bool("downgraded") ? r.withSeverity(WARNING) : r;
        });

        // G7
        def(PROJECT_LEAD_CHANGED, MEMBER, INFO, c -> {
            String newLead = c.str("newLeadId");
            if (newLead != null && newLead.equals(c.recipientId())) {
                return Rendered.of("Bạn trở thành Trưởng dự án " + c.project(),
                        "Do " + c.actorName() + " chỉ định", c.projectPath(""));
            }
            return Rendered.of(b(c.userName(newLead)) + " trở thành Trưởng dự án " + c.project() + " thay bạn",
                    "Do " + c.actorName() + " chỉ định", c.projectPath(""));
        });

        // G8
        def(WORKSPACE_INVITATION_RECEIVED, MEMBER, INFO, c -> Rendered.of(
                c.actor() + " mời bạn vào không gian làm việc " + c.workspace(),
                "Vai trò: " + c.roleLabel(c.str("role")), "/invitations"))
                .action()
                .email("invite-member",
                        c -> "[DATN-2026] Lời mời tham gia không gian làm việc " + c.workspaceName(),
                        (c, link) -> Map.of(
                                "inviter_name", c.actorName(),
                                "target_kind", "không gian làm việc",
                                "target_name", c.workspaceName(),
                                "role", c.roleLabel(c.str("role")),
                                "link", link));

        // G9
        def(WORKSPACE_MEMBER_REMOVED, MEMBER, URGENT, c -> Rendered.of(
                "Bạn không còn là thành viên không gian làm việc " + c.workspace(),
                "Do " + c.actorName() + " thực hiện", "/workspaces"));

        // G10
        def(WORKSPACE_ROLE_CHANGED, MEMBER, INFO, c -> Rendered.of(
                "Vai trò của bạn trong " + c.workspace() + " đổi sang " + RenderContext.esc(c.roleLabel(c.str("newRole"))),
                "Do " + c.actorName() + " thực hiện", c.workspacePath("/members")));

        // G11
        def(WORKSPACE_INVITATION_ANSWERED, MEMBER, INFO, c -> Rendered.of(
                c.actor() + (c.bool("accepted") ? " đã chấp nhận" : " đã từ chối") + " lời mời vào " + c.workspace(),
                "Vai trò: " + c.roleLabel(c.str("role")), c.workspacePath("/members")))
                .resolves(WORKSPACE_INVITATION_RECEIVED);
    }

    // ═══════════════════════ H. Dự án ═══════════════════════

    private void defineProject() {
        // H1
        def(PROJECT_ARCHIVED, PROJECT, WARNING, c -> Rendered.of(
                c.actor() + " đã lưu trữ dự án " + c.project(),
                "Có thể khôi phục lại dự án nếu cần", c.workspacePath("/projects")));

        // H2
        def(PROJECT_RESTORED, PROJECT, INFO, c -> Rendered.of(
                c.actor() + " đã khôi phục dự án " + c.project(),
                "Dự án hoạt động trở lại", c.projectPath("")));

        // H3
        def(PROJECT_SCHEDULE_CHANGED, PROJECT, INFO, c -> {
            LocalDate end = c.date("newEndDate");
            LocalDate start = c.date("newStartDate");
            String title = c.bool("endChanged")
                    ? c.project() + (end != null ? " đổi ngày kết thúc sang " + fmt(end) : " bỏ ngày kết thúc")
                    : c.project() + (start != null ? " đổi ngày bắt đầu sang " + fmt(start) : " bỏ ngày bắt đầu");
            return Rendered.of(title, "Do " + c.actorName() + " cập nhật", c.projectPath(""));
        });
    }

    // ═══════════════════════ I. Đóng góp ═══════════════════════

    private void defineContribution() {
        // I1
        def(CONTRIBUTION_ADJUSTED, CONTRIBUTION, SUCCESS, c -> {
            double points = 0;
            try {
                points = Double.parseDouble(String.valueOf(c.str("points")));
            } catch (NumberFormatException ignored) {
                // giữ 0
            }
            String p = trimNumber(Math.abs(points));
            String body = c.has("reason") ? "Lý do: " + quote(c.str("reason"), 180) : "";
            String link = c.projectPath("/reports?tab=contributions");
            return points >= 0
                    ? Rendered.of(c.actor() + " đã cộng " + p + " điểm đóng góp cho bạn", body, link)
                    : Rendered.of(c.actor() + " đã trừ " + p + " điểm đóng góp của bạn", body, link).withSeverity(WARNING);
        });

        // I2
        def(CONTRIBUTION_FROZEN, CONTRIBUTION, INFO, c -> {
            Object mine = c.forRecipient("points");
            String body = mine != null
                    ? "Bạn được ghi nhận " + trimNumber(toDouble(mine)) + " điểm"
                    : "Xem chi tiết ở Báo cáo → Quản lý đóng góp";
            return Rendered.of("Bảng đóng góp " + c.sprint() + " đã được chốt", body,
                    c.projectPath("/reports?tab=contributions&sprint=" + c.event().getEntityId()));
        });
    }

    // ═══════════════════════ Nội bộ ═══════════════════════

    private void defineInternal() {
        def(PROJECT_INVITATION_CANCELLED, MEMBER, INFO, c -> Rendered.of("", "", null))
                .resolves(PROJECT_INVITATION_RECEIVED);
        def(WORKSPACE_INVITATION_CANCELLED, MEMBER, INFO, c -> Rendered.of("", "", null))
                .resolves(WORKSPACE_INVITATION_RECEIVED);
        def(COMPLETION_WITHDRAWN, APPROVAL, INFO, c -> Rendered.of("", "", null))
                .resolves(COMPLETION_REQUESTED);
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String trimNumber(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    /** Biến chung mọi email: tên người nhận do service thêm. */
    public static Map<String, String> withRecipient(Map<String, String> vars, String recipientName) {
        Map<String, String> all = new LinkedHashMap<>(vars);
        all.put("recipient_name", recipientName);
        return all;
    }
}
