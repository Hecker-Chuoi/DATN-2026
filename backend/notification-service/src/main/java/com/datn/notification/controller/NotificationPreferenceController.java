package com.datn.notification.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.notification.dto.NotificationPreferencesView;
import com.datn.notification.service.NotificationPreferenceService;
import com.datn.notification.service.NotificationPreferenceService.CategoryChange;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.datn.notification.controller.NotificationController.me;

/**
 * Cài đặt bật / tắt thông báo của người dùng hiện tại. Qua gateway:
 * {@code /notification/api/v1/notifications/preferences/**}.
 */
@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    public record UpdateRequest(List<CategoryChange> categories) {}

    /** @param until null = tắt đến khi bật lại */
    public record MuteRequest(String projectName, Instant until) {}

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPreferencesView>> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(service.view(me(jwt))));
    }

    /** Body: {"categories": [{"category": "COMMENT", "inApp": false, "email": true}]}; trường bỏ trống = giữ nguyên. */
    @PutMapping
    public ResponseEntity<ApiResponse<NotificationPreferencesView>> update(@AuthenticationPrincipal Jwt jwt,
                                                                          @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.update(me(jwt), request == null ? null : request.categories()), "Đã lưu cài đặt thông báo"));
    }

    @PutMapping("/projects/{projectId}/mute")
    public ResponseEntity<ApiResponse<NotificationPreferencesView>> mute(@AuthenticationPrincipal Jwt jwt,
                                                                        @PathVariable("projectId") UUID projectId,
                                                                        @RequestBody(required = false) MuteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.mute(me(jwt), projectId, request == null ? null : request.projectName(),
                        request == null ? null : request.until()),
                "Đã tắt thông báo của dự án"));
    }

    @DeleteMapping("/projects/{projectId}/mute")
    public ResponseEntity<ApiResponse<NotificationPreferencesView>> unmute(@AuthenticationPrincipal Jwt jwt,
                                                                          @PathVariable("projectId") UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(service.unmute(me(jwt), projectId), "Đã bật lại thông báo của dự án"));
    }
}
