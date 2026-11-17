package com.datn.notification.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.UnauthorizedException;
import com.datn.notification.dto.NotificationPage;
import com.datn.notification.dto.NotificationResponse;
import com.datn.notification.service.NotificationService;
import com.datn.notification.service.NotificationService.Counts;
import com.datn.notification.service.NotificationService.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Thông báo của người dùng hiện tại. Qua gateway: {@code /notification/api/v1/notifications/**}.
 * Người dùng chỉ thấy và sửa được thông báo của chính mình (lọc theo Keycloak sub).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    public record ReadRequest(Boolean read) {}

    /** Danh sách mới nhất trước, phân trang theo con trỏ. filter = all | unread | action. */
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPage>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "projectId", required = false) UUID projectId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                service.list(me(jwt), Filter.parse(filter), category, projectId, cursor, size)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Counts>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(service.counts(me(jwt))));
    }

    /** Đánh dấu đã đọc ({"read": true}) hoặc chưa đọc ({"read": false}). */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationResponse>> setRead(
            @AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id, @RequestBody(required = false) ReadRequest body) {
        boolean read = body == null || body.read() == null || body.read();
        return ResponseEntity.ok(ApiResponse.success(service.setRead(me(jwt), id, read)));
    }

    /** Đánh dấu tất cả đã đọc; filter=action chỉ áp dụng cho tab "Cần xử lý". */
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readAll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "projectId", required = false) UUID projectId) {
        UUID me = me(jwt);
        int updated = service.readAll(me, Filter.parse(filter), category, projectId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated, "counts", service.counts(me))));
    }

    /** Xoá mềm (có thể hoàn tác trong 30 ngày). */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Counts>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        UUID me = me(jwt);
        service.delete(me, id);
        return ResponseEntity.ok(ApiResponse.success(service.counts(me), "Đã xoá thông báo"));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<NotificationResponse>> restore(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.restore(me(jwt), id), "Đã khôi phục thông báo"));
    }

    /** Xoá mọi thông báo đã đọc (chỉ hỗ trợ filter=read). */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteRead(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(name = "filter", defaultValue = "read") String filter) {
        if (!"read".equalsIgnoreCase(filter)) {
            throw new com.datn.common.exception.BadRequestException("Chỉ hỗ trợ xoá các thông báo đã đọc (filter=read)");
        }
        UUID me = me(jwt);
        int deleted = service.deleteAllRead(me);
        return ResponseEntity.ok(ApiResponse.success(Map.of("deleted", deleted, "counts", service.counts(me))));
    }

    /** Keycloak sub của người gọi (dùng chung cho các controller cùng gói). */
    static UUID me(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new UnauthorizedException("Chưa đăng nhập");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Token không hợp lệ");
        }
    }
}
