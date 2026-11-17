package com.datn.common.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phong bì sự kiện thông báo đi qua Kafka topic {@value #TOPIC}.
 *
 * Phía phát ghi sự kiện vào outbox trong cùng transaction với nghiệp vụ; relay đặt {@code eventId}
 * (bằng id dòng outbox) rồi gửi lên Kafka. notification-service đọc, dựng nội dung tiếng Việt và lưu.
 *
 * Mọi id là chuỗi UUID. {@code actorId} và {@code recipients[].userId} luôn là Keycloak {@code sub}.
 * {@code data} chỉ chứa id, enum, ngày, số và chuỗi ngắn (không gửi mô tả hay toàn văn bình luận).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationEvent {

    public static final String TOPIC = "notification-events";
    public static final String AGGREGATE_TYPE = "NOTIFICATION";

    private String eventId;
    private String type;
    private String source;
    private String occurredAt;

    private String actorId;

    private String workspaceId;
    private String workspaceName;
    private String projectId;
    private String projectKey;
    private String projectName;

    /** WORK_ITEM, SPRINT, PROJECT, INVITATION, WORKSPACE, CONTRIBUTION… */
    private String entityType;
    private String entityId;
    /** Mã hiển thị, ví dụ MOC-2. */
    private String entityCode;
    /** Tiêu đề ngắn: tóm tắt công việc, tên chu kỳ… */
    private String entityTitle;

    @Builder.Default
    private List<Recipient> recipients = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> data = new LinkedHashMap<>();

    /** Job theo lịch tự đặt để chạy lại không sinh trùng; sự kiện thường để null và dùng eventId. */
    private String dedupKey;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Recipient {
        private String userId;
        private String reason;
    }
}
