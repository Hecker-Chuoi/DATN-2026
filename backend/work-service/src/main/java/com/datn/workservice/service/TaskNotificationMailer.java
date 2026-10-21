package com.datn.workservice.service;

import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.NotificationClient;
import com.datn.workservice.client.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Mailer bất đồng bộ (@Async) sử dụng HTML Templates tại notification-service:
 * - task-assigned.html (Gán task mới / Thay đổi Assignee)
 * - comment-mention.html (Tag tên trong bình luận)
 * - due-date-reminder.html (Nhắc nhở công việc sắp đến hạn)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskNotificationMailer {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationClient notificationClient;
    private final IdentityClient identityClient;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * 1. Gửi mail thông báo khi được gán Task mới (Task Assigned) dùng template task-assigned.html.
     */
    @Async
    public void sendTaskAssigned(UUID assigneeId, String workItemKey, String summary, String projectName, UUID assignerId, LocalDate dueDate) {
        if (assigneeId == null) return;
        try {
            UserResponse assignee = fetchUser(assigneeId);
            if (assignee == null || assignee.getEmail() == null || assignee.getEmail().isBlank()) {
                log.warn("Skip task assigned email for user {}: email is missing", assigneeId);
                return;
            }

            UserResponse assigner = fetchUser(assignerId);
            String assignerName = assigner != null ? getDisplayName(assigner) : "Một thành viên";
            String assigneeName = getDisplayName(assignee);
            String link = frontendUrl + "/projects/item/" + workItemKey;
            String dueDateStr = dueDate != null ? dueDate.format(DATE_FORMATTER) : "Không có";
            String pName = projectName != null ? projectName : "Dự án";

            notificationClient.sendTemplate(new NotificationClient.TemplateEmailRequest(
                    assignee.getEmail(),
                    "[DATN-2026] Bạn được phân công công việc [" + workItemKey + "]",
                    "task-assigned",
                    Map.of(
                            "recipient_name", assigneeName,
                            "assigner_name", assignerName,
                            "work_item_key", workItemKey,
                            "summary", summary,
                            "project_name", pName,
                            "due_date", dueDateStr,
                            "link", link
                    )
            ));

            log.info("Sent Task Assigned template email to {} for item {}", assignee.getEmail(), workItemKey);
        } catch (Exception e) {
            log.warn("Failed to send Task Assigned email for item {}: {}", workItemKey, e.getMessage());
        }
    }

    /**
     * 2. Gửi mail khi được @Mention trong bình luận dùng template comment-mention.html.
     */
    @Async
    public void sendCommentMention(String targetEmail, String targetName, String authorName, String workItemKey, String summary, String commentBody) {
        if (targetEmail == null || targetEmail.isBlank()) return;
        try {
            String link = frontendUrl + "/projects/item/" + workItemKey;
            String snippet = commentBody.length() > 200 ? commentBody.substring(0, 197) + "..." : commentBody;

            notificationClient.sendTemplate(new NotificationClient.TemplateEmailRequest(
                    targetEmail,
                    "[DATN-2026] " + authorName + " đã nhắc đến bạn trong [" + workItemKey + "]",
                    "comment-mention",
                    Map.of(
                            "recipient_name", targetName,
                            "author_name", authorName,
                            "work_item_key", workItemKey,
                            "summary", summary,
                            "comment_body", snippet,
                            "link", link
                    )
            ));

            log.info("Sent @Mention template email to {} for item {}", targetEmail, workItemKey);
        } catch (Exception e) {
            log.warn("Failed to send @Mention email to {}: {}", targetEmail, e.getMessage());
        }
    }

    /**
     * 3. Gửi mail nhắc nhở sắp đến hạn (Due Date Reminder) dùng template due-date-reminder.html.
     */
    @Async
    public void sendDueDateReminder(UUID assigneeId, String workItemKey, String summary, LocalDate dueDate) {
        if (assigneeId == null) return;
        try {
            UserResponse assignee = fetchUser(assigneeId);
            if (assignee == null || assignee.getEmail() == null || assignee.getEmail().isBlank()) return;

            String assigneeName = getDisplayName(assignee);
            String link = frontendUrl + "/projects/item/" + workItemKey;
            String dueDateStr = dueDate != null ? dueDate.format(DATE_FORMATTER) : "";

            notificationClient.sendTemplate(new NotificationClient.TemplateEmailRequest(
                    assignee.getEmail(),
                    "[DATN-2026] Nhắc nhở: Công việc [" + workItemKey + "] sắp đến hạn",
                    "due-date-reminder",
                    Map.of(
                            "recipient_name", assigneeName,
                            "work_item_key", workItemKey,
                            "summary", summary,
                            "due_date", dueDateStr,
                            "link", link
                    )
            ));

            log.info("Sent Due Date Reminder template email to {} for item {}", assignee.getEmail(), workItemKey);
        } catch (Exception e) {
            log.warn("Failed to send Due Date Reminder email to assignee {}: {}", assigneeId, e.getMessage());
        }
    }

    private UserResponse fetchUser(UUID userId) {
        if (userId == null) return null;
        try {
            var res = identityClient.getUserById(userId);
            return res != null ? res.getData() : null;
        } catch (Exception e) {
            log.warn("Could not fetch user {} from identity-service: {}", userId, e.getMessage());
            return null;
        }
    }

    private String getDisplayName(UserResponse user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) return user.getFirstName();
        return user.getUsername();
    }
}
