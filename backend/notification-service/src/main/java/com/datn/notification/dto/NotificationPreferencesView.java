package com.datn.notification.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Màn "Cài đặt thông báo": 9 nhóm × 2 kênh và các dự án đang tắt tiếng. */
public record NotificationPreferencesView(List<CategoryView> categories, List<MutedProjectView> mutedProjects) {

    /**
     * @param inAppLocked    cả nhóm luôn hiện trong chuông (không tắt được)
     * @param emailAvailable nhóm có loại nào gửi email không (không thì công tắc email bị ẩn)
     * @param alwaysInApp    các loại trong nhóm vẫn hiện trong chuông dù đã tắt nhóm
     */
    public record CategoryView(String category, boolean inApp, boolean email, boolean inAppLocked,
                               boolean emailAvailable, List<String> alwaysInApp) {}

    /** @param mutedUntil null = tắt đến khi bật lại */
    public record MutedProjectView(UUID projectId, String projectName, Instant mutedUntil, Instant mutedAt) {}
}
