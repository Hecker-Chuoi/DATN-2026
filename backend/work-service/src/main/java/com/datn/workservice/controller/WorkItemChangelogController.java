package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.response.WorkItemChangelogResponse;
import com.datn.workservice.service.WorkItemAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class WorkItemChangelogController {

    /**
     * ──────────────────────────── DANH SÁCH API ────────────────────────────
     * GET /api/v1/work/items/{itemId}/changelogs | /work-items/{itemId}/changelogs : Lấy lịch sử thay đổi của công việc
     * ────────────────────────────────────────────────────────────────────────
     */

    private final WorkItemAuditService auditService;

    @GetMapping({"/items/{itemId}/changelogs", "/work-items/{itemId}/changelogs"})
    public ResponseEntity<ApiResponse<List<WorkItemChangelogResponse>>> getChangelogs(
            @PathVariable("itemId") UUID workItemId
    ) {
        List<WorkItemChangelogResponse> changelogs = auditService.getChangelogs(workItemId);
        return ResponseEntity.ok(ApiResponse.success(changelogs));
    }
}
