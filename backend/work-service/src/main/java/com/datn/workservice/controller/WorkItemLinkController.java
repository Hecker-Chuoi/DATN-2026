package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CreateWorkItemLinkRequest;
import com.datn.workservice.dto.response.WorkItemLinkResponse;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.WorkItemLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class WorkItemLinkController {

    private final WorkItemLinkService linkService;

    @GetMapping({"/items/{itemId}/links", "/work-items/{itemId}/links"})
    public ResponseEntity<ApiResponse<List<WorkItemLinkResponse>>> getLinks(
            @PathVariable("itemId") UUID workItemId
    ) {
        List<WorkItemLinkResponse> links = linkService.getLinks(workItemId);
        return ResponseEntity.ok(ApiResponse.success(links));
    }

    @PostMapping({"/items/{itemId}/links", "/work-items/{itemId}/links"})
    public ResponseEntity<ApiResponse<WorkItemLinkResponse>> createLink(
            @PathVariable("itemId") UUID workItemId,
            @Valid @RequestBody CreateWorkItemLinkRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = resolveUserId(jwt);
        WorkItemLinkResponse response = linkService.createLink(workItemId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã thiết lập liên kết thành công"));
    }

    @DeleteMapping({"/items/{itemId}/links/{linkId}", "/work-items/{itemId}/links/{linkId}"})
    public ResponseEntity<ApiResponse<Void>> deleteLink(
            @PathVariable("itemId") UUID workItemId,
            @PathVariable("linkId") UUID linkId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = resolveUserId(jwt);
        linkService.deleteLink(workItemId, linkId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa liên kết thành công"));
    }

    private UUID resolveUserId(Jwt jwt) {
        return SecurityUtils.getCurrentUserIdOptional().orElseGet(() -> {
            if (jwt != null) {
                String sub = jwt.getSubject();
                try {
                    return UUID.fromString(sub);
                } catch (Exception ignored) {
                }
            }
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        });
    }
}
