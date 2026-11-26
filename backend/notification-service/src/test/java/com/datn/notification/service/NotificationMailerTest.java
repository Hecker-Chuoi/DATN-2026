package com.datn.notification.service;

import com.datn.common.notification.NotificationEvent;
import com.datn.common.notification.NotificationType;
import com.datn.notification.catalog.NotificationCatalog;
import com.datn.notification.catalog.NotificationSpec;
import com.datn.notification.catalog.RenderContext;
import com.datn.notification.client.UserDirectory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationMailerTest {

    private static final String SUB = "bbbbbbbb-2222-4222-8222-000000000002";

    private final EmailService email = mock(EmailService.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final NotificationMailer mailer =
            new NotificationMailer(email, new MailTemplateService(), users, "http://localhost:5173/");
    private final NotificationSpec assigned = new NotificationCatalog().find(NotificationType.TASK_ASSIGNED).orElseThrow();

    private RenderContext ctx() {
        Map<String, Object> data = new LinkedHashMap<>(Map.of("priority", "HIGH", "dueDate", "2026-11-20"));
        NotificationEvent e = NotificationEvent.builder().type("TASK_ASSIGNED").actorId("a")
                .projectName("Mộc Coffee").entityCode("MOC-2").entityTitle("Khảo sát <mặt bằng>").data(data).build();
        return new RenderContext(e, SUB, "An", 0, id -> "x", LocalDate.of(2026, 11, 14));
    }

    @Test
    void sendsRenderedTemplateWithAbsoluteLinkToRecipientEmail() throws Exception {
        when(users.find(SUB)).thenReturn(Optional.of(new UserDirectory.UserInfo("1", SUB, "binh", "binh@example.com", "Bình", null)));

        mailer.sendAfterCommit(assigned.email(), ctx(), "/browse/MOC-2");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).sendHtml(eq("binh@example.com"), eq("[DATN-2026] Bạn được phân công công việc [MOC-2]"), html.capture());
        assertThat(html.getValue())
                .contains("http://localhost:5173/browse/MOC-2")
                .contains("Bình")
                .contains("An")
                .contains("20/11/2026")
                .contains("Khảo sát &lt;mặt bằng&gt;") // tên công việc được escape
                .doesNotContain("{{");
    }

    @Test
    void skipsWhenRecipientHasNoEmail() throws Exception {
        when(users.find(SUB)).thenReturn(Optional.empty());
        mailer.sendAfterCommit(assigned.email(), ctx(), "/browse/MOC-2");
        verify(email, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void smtpFailureNeverPropagates() throws Exception {
        when(users.find(SUB)).thenReturn(Optional.of(new UserDirectory.UserInfo("1", SUB, "binh", "binh@example.com", null, null)));
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down")).when(email).sendHtml(any(), any(), any());
        mailer.sendAfterCommit(assigned.email(), ctx(), "/browse/MOC-2"); // không ném lỗi
    }
}
