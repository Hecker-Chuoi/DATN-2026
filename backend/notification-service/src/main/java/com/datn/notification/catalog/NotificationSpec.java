package com.datn.notification.catalog;

import com.datn.common.notification.NotificationEvent;
import com.datn.common.notification.NotificationType;
import com.datn.notification.enums.NotificationCategory;
import com.datn.notification.enums.NotificationSeverity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Cấu hình của một loại thông báo: nhóm, mức mặc định, cờ "Cần xử lý", cách gộp,
 * các loại nó đóng lại, email, và hàm dựng nội dung.
 */
public final class NotificationSpec {

    private final NotificationType type;
    private final NotificationCategory category;
    private final NotificationSeverity severity;
    private final Function<RenderContext, Rendered> renderer;

    private boolean actionRequired;
    private Function<NotificationEvent, String> groupKey;
    private final Set<NotificationType> resolves = EnumSet.noneOf(NotificationType.class);
    private boolean resolveByProject;
    private EmailSpec email;

    NotificationSpec(NotificationType type, NotificationCategory category, NotificationSeverity severity,
                     Function<RenderContext, Rendered> renderer) {
        this.type = type;
        this.category = category;
        this.severity = severity;
        this.renderer = renderer;
    }

    /** Có cờ "Cần xử lý". */
    NotificationSpec action() {
        this.actionRequired = true;
        return this;
    }

    /** Gộp các thông báo chưa đọc cùng khoá (R4). */
    NotificationSpec group(Function<NotificationEvent, String> keyFn) {
        this.groupKey = keyFn;
        return this;
    }

    /** Khi sự kiện loại này tới, đóng các thông báo "Cần xử lý" của các loại sau trên cùng đối tượng (R9). */
    NotificationSpec resolves(NotificationType... types) {
        this.resolves.addAll(Set.of(types));
        return this;
    }

    /** Đóng theo dự án thay vì theo đối tượng (dùng cho loại tổng hợp theo dự án). */
    NotificationSpec resolvingByProject() {
        this.resolveByProject = true;
        return this;
    }

    NotificationSpec email(String template,
                           Function<RenderContext, String> subject,
                           BiFunction<RenderContext, String, Map<String, String>> vars) {
        this.email = new EmailSpec(template, subject, vars);
        return this;
    }

    public NotificationType type() { return type; }
    public NotificationCategory category() { return category; }
    public NotificationSeverity severity() { return severity; }
    public boolean actionRequired() { return actionRequired; }
    public Set<NotificationType> resolves() { return resolves; }
    public boolean resolveByProject() { return resolveByProject; }
    public EmailSpec email() { return email; }

    public String groupKey(NotificationEvent event) {
        return groupKey == null ? null : groupKey.apply(event);
    }

    public Rendered render(RenderContext ctx) {
        return renderer.apply(ctx);
    }

    /**
     * @param template tên file trong resources/templates (không đuôi .html)
     * @param subject  tiêu đề email
     * @param vars     (ctx, link tuyệt đối) → biến của template; recipient_name do service tự thêm
     */
    public record EmailSpec(String template,
                            Function<RenderContext, String> subject,
                            BiFunction<RenderContext, String, Map<String, String>> vars) {}
}
