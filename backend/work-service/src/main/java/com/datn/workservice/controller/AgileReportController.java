package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.response.BurndownResponse;
import com.datn.workservice.dto.response.CfdResponse;
import com.datn.workservice.dto.response.VelocityResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.ProjectIdSource;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.service.AgileReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class AgileReportController {

    private final AgileReportService reportService;

    @GetMapping("/sprints/{sprintId}/burndown")
    @RequireProjectPermission(value = PermissionKey.PROJECT_VIEW, source = ProjectIdSource.SPRINT, param = "sprintId")
    public ResponseEntity<ApiResponse<BurndownResponse>> getBurndown(
            @PathVariable UUID sprintId,
            @RequestParam(required = false) Boolean excludeWeekends
    ) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getBurndown(sprintId, excludeWeekends)));
    }

    @GetMapping("/projects/{projectId}/velocity")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<VelocityResponse>> getVelocity(
            @PathVariable UUID projectId,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getVelocity(projectId, limit)));
    }

    @GetMapping("/projects/{projectId}/cfd")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<CfdResponse>> getCfd(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID sprintId
    ) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getCfd(projectId, sprintId)));
    }
}
