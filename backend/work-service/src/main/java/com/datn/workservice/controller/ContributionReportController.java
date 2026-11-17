package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.ManualAdjustmentRequest;
import com.datn.workservice.dto.response.ContributionLedgerEntryResponse;
import com.datn.workservice.dto.response.ContributionOverviewResponse;
import com.datn.workservice.dto.response.MemberContributionDetailResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.ContributionReportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work/projects/{projectId}/reports/contributions")
@RequiredArgsConstructor
public class ContributionReportController {

    private final ContributionReportService reportService;

    /**
     * Màn hình 1: Lấy tổng quan báo cáo đóng góp thành viên
     */
    @GetMapping
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<ContributionOverviewResponse>> getContributionOverview(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID sprintId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ContributionOverviewResponse res = reportService.getContributionOverview(
                projectId, sprintId, fromDate, toDate, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    /**
     * Màn hình 2: Lấy danh sách Sổ ghi công (Timeline bất biến)
     */
    @GetMapping("/ledger")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<ContributionLedgerEntryResponse>>> getLedgerEntries(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID sprintId
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        List<ContributionLedgerEntryResponse> entries = reportService.getLedgerEntries(
                projectId, sprintId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(entries));
    }

    /**
     * Màn hình 3: Xem chi tiết đóng góp của một thành viên
     */
    @GetMapping("/members/{targetUserId}")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<MemberContributionDetailResponse>> getMemberDetail(
            @PathVariable UUID projectId,
            @PathVariable UUID targetUserId,
            @RequestParam(required = false) UUID sprintId
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        MemberContributionDetailResponse detail = reportService.getMemberDetail(
                projectId, targetUserId, sprintId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * Project Leader điều chỉnh điểm thủ công
     */
    @PostMapping("/adjust")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<ContributionLedgerEntryResponse>> manualAdjustment(
            @PathVariable UUID projectId,
            @Valid @RequestBody ManualAdjustmentRequest request
    ) {
        UUID leaderUserId = SecurityUtils.getCurrentUserId();
        ContributionLedgerEntryResponse entry = reportService.manualAdjustment(projectId, request, leaderUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(entry, "Ghi nhận điều chỉnh điểm thành công"));
    }

    /**
     * Xuất file CSV / Excel báo cáo đóng góp
     */
    @GetMapping("/export")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public void exportContribution(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID sprintId,
            HttpServletResponse response
    ) {
        reportService.exportContributionCsv(projectId, sprintId, response);
    }
}
