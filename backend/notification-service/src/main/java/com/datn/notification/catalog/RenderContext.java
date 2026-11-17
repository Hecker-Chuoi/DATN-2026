package com.datn.notification.catalog;

import com.datn.common.notification.NotificationEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Dữ liệu và hàm tiện ích để dựng nội dung một thông báo cho MỘT người nhận.
 *
 * Quy ước: {@code title} là HTML đã escape, chỉ dùng thẻ &lt;b&gt; (các hàm {@link #b}, {@link #actor()},
 * {@link #item()}… tự escape). {@code body} là văn bản thuần, frontend hiển thị dạng text.
 */
public final class RenderContext {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");

    private static final Map<String, String> PRIORITY_LABELS = Map.of(
            "HIGHEST", "Khẩn cấp",
            "HIGH", "Cao",
            "MEDIUM", "Trung bình",
            "LOW", "Thấp",
            "LOWEST", "Rất thấp");

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "ADMINISTRATOR", "Quản trị viên",
            "ADMIN", "Quản trị viên",
            "OWNER", "Chủ sở hữu",
            "MEMBER", "Thành viên",
            "VIEWER", "Người xem",
            "GUEST", "Khách");

    private final NotificationEvent event;
    private final String recipientId;
    private final String actorName;
    private final int others;
    private final Function<String, String> nameLookup;
    private final LocalDate today;

    public RenderContext(NotificationEvent event, String recipientId, String actorName, int others,
                         Function<String, String> nameLookup, LocalDate today) {
        this.event = event;
        this.recipientId = recipientId;
        this.actorName = actorName == null || actorName.isBlank() ? "Một thành viên" : actorName;
        this.others = Math.max(0, others);
        this.nameLookup = nameLookup == null ? id -> "Một thành viên" : nameLookup;
        this.today = today == null ? LocalDate.now(ZONE) : today;
    }

    // ─────────────────────────── Văn bản ───────────────────────────

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Chữ đậm (đã escape). */
    public static String b(String text) {
        return "<b>" + esc(text) + "</b>";
    }

    /** Người thao tác, có tính số người khác khi đã gộp: "<b>An</b> và 2 người khác". */
    public String actor() {
        return others > 0 ? b(actorName) + " và " + others + " người khác" : b(actorName);
    }

    public String actorName() {
        return actorName;
    }

    public int others() {
        return others;
    }

    /** Mã công việc in đậm, ví dụ <b>MOC-2</b>. */
    public String item() {
        String code = event.getEntityCode();
        return b(code != null && !code.isBlank() ? code : summary());
    }

    public String code() {
        return Objects.toString(event.getEntityCode(), "");
    }

    /** Tiêu đề / tóm tắt của đối tượng (văn bản thuần). */
    public String summary() {
        return Objects.toString(event.getEntityTitle(), "");
    }

    public String project() {
        return b(projectName());
    }

    public String projectName() {
        String n = event.getProjectName();
        return n == null || n.isBlank() ? "dự án" : n;
    }

    public String workspace() {
        return b(workspaceName());
    }

    public String workspaceName() {
        String n = event.getWorkspaceName();
        return n == null || n.isBlank() ? "không gian làm việc" : n;
    }

    /** Tên chu kỳ: data.sprintName, nếu không có thì entityTitle. */
    public String sprintName() {
        String s = str("sprintName");
        if (s == null || s.isBlank()) s = event.getEntityTitle();
        return s == null || s.isBlank() ? "Chu kỳ" : s;
    }

    public String sprint() {
        return b(sprintName());
    }

    /** "giai đoạn" nếu tên chu kỳ có chữ "giai đoạn", ngược lại "chu kỳ" (giống sprintTerm ở frontend). */
    public String term() {
        return sprintName().toLowerCase(Locale.ROOT).contains("giai đoạn") ? "giai đoạn" : "chu kỳ";
    }

    public String priorityLabel(String priority) {
        if (priority == null) return "";
        return PRIORITY_LABELS.getOrDefault(priority.toUpperCase(Locale.ROOT), priority);
    }

    public String roleLabel(String role) {
        if (role == null || role.isBlank()) return "Thành viên";
        return ROLE_LABELS.getOrDefault(role.toUpperCase(Locale.ROOT), role);
    }

    public String userName(String userId) {
        return nameLookup.apply(userId);
    }

    /** Trích dẫn trong ngoặc kép, cắt bớt cho gọn. */
    public static String quote(String text, int max) {
        if (text == null || text.isBlank()) return "";
        String t = text.strip().replaceAll("\\s+", " ");
        if (t.length() > max) t = t.substring(0, max - 1).strip() + "…";
        return "“" + t + "”";
    }

    public static String count(long n, String unit) {
        return n + " " + unit;
    }

    // ─────────────────────────── Dữ liệu ───────────────────────────

    public NotificationEvent event() {
        return event;
    }

    public String recipientId() {
        return recipientId;
    }

    public LocalDate today() {
        return today;
    }

    public Object raw(String key) {
        return event.getData() == null ? null : event.getData().get(key);
    }

    public String str(String key) {
        Object v = raw(key);
        return v == null ? null : String.valueOf(v);
    }

    public boolean has(String key) {
        String s = str(key);
        return s != null && !s.isBlank();
    }

    public long num(String key) {
        return toLong(raw(key));
    }

    public boolean bool(String key) {
        Object v = raw(key);
        if (v instanceof Boolean bv) return bv;
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }

    public List<String> list(String key) {
        Object v = raw(key);
        if (v instanceof Collection<?> c) return c.stream().map(String::valueOf).toList();
        if (v == null) return List.of();
        return List.of(String.valueOf(v));
    }

    /** Giá trị dành riêng cho người nhận hiện tại trong một map {userId: giá trị}. */
    public Object forRecipient(String key) {
        Object v = raw(key);
        if (v instanceof Map<?, ?> m) return m.get(recipientId);
        return null;
    }

    public long numForRecipient(String key) {
        return toLong(forRecipient(key));
    }

    public LocalDate date(String key) {
        String s = str(key);
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    public static String fmt(LocalDate d) {
        return d == null ? "" : d.format(DMY);
    }

    public static String fmtShort(LocalDate d) {
        return d == null ? "" : d.format(DM);
    }

    /** Số ngày từ hôm nay tới {@code d} (âm nếu đã qua). */
    public long daysFromToday(LocalDate d) {
        return d == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(today, d);
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return 0;
        try {
            return (long) Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ─────────────────────────── Đường dẫn ───────────────────────────

    /** /browse/{mã}: trang chuyển hướng tới chi tiết công việc. */
    public String browse() {
        return "/browse/" + code();
    }

    public String browseComment() {
        String commentId = str("commentId");
        return commentId == null ? browse() : browse() + "?comment=" + commentId;
    }

    /** /w/{ws}/projects/{pid}{sub}. */
    public String projectPath(String sub) {
        String ws = event.getWorkspaceId();
        String pid = event.getProjectId();
        if (ws == null || pid == null) return "/workspaces";
        return "/w/" + ws + "/projects/" + pid + (sub == null ? "" : sub);
    }

    /** Kho việc & Kế hoạch, hoặc Bảng nếu dự án Kanban (không có Kho việc). */
    public String planPath() {
        return "SOFTWARE_KANBAN".equals(str("projectType")) ? projectPath("/board") : projectPath("/backlog");
    }

    public String workspacePath(String sub) {
        String ws = event.getWorkspaceId();
        if (ws == null) return "/workspaces";
        return "/w/" + ws + (sub == null ? "" : sub);
    }
}
