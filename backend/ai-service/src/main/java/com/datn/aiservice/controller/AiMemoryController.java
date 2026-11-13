package com.datn.aiservice.controller;

import com.datn.aiservice.dto.MemoryView;
import com.datn.aiservice.dto.SettingsView;
import com.datn.aiservice.dto.UpdateMemoryRequest;
import com.datn.aiservice.entity.AiUserSettings;
import com.datn.aiservice.enums.MemoryCategory;
import com.datn.aiservice.enums.MemoryScope;
import com.datn.aiservice.service.MemoryService;
import com.datn.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Quản lý bộ nhớ dài hạn và cài đặt của chính user. */
@RestController
@RequestMapping("/api/v1/ai/agent")
@RequiredArgsConstructor
public class AiMemoryController {

    private final MemoryService memoryService;

    @GetMapping("/memories")
    public ResponseEntity<ApiResponse<List<MemoryView>>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(memoryService.list(jwt.getSubject()).stream().map(MemoryView::of).toList()));
    }

    @PatchMapping("/memories/{id}")
    public ResponseEntity<ApiResponse<MemoryView>> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                          @RequestBody UpdateMemoryRequest req) {
        MemoryScope scope = req.scope() == null ? null : MemoryScope.valueOf(req.scope().toUpperCase());
        MemoryCategory category = req.category() == null ? null : MemoryCategory.valueOf(req.category().toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(
                MemoryView.of(memoryService.update(jwt.getSubject(), id, req.content(), scope, category)), "Đã cập nhật"));
    }

    @DeleteMapping("/memories/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        memoryService.delete(jwt.getSubject(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa mục ghi nhớ"));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> settings(@AuthenticationPrincipal Jwt jwt) {
        AiUserSettings s = memoryService.settings(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(new SettingsView(s.isMemoryEnabled(), s.getDailyRequestLimit())));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> updateSettings(@AuthenticationPrincipal Jwt jwt,
                                                                    @RequestBody SettingsView req) {
        AiUserSettings s = memoryService.updateSettings(jwt.getSubject(), req.memoryEnabled());
        return ResponseEntity.ok(ApiResponse.success(new SettingsView(s.isMemoryEnabled(), s.getDailyRequestLimit()), "Đã lưu cài đặt"));
    }
}
