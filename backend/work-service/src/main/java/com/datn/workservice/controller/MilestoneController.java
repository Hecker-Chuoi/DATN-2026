package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CreateMilestoneRequest;
import com.datn.workservice.dto.request.UpdateMilestoneRequest;
import com.datn.workservice.dto.response.MilestoneResponse;
import com.datn.workservice.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    @PostMapping("/milestones")
    public ResponseEntity<ApiResponse<MilestoneResponse>> createMilestone(@Valid @RequestBody CreateMilestoneRequest request) {
        MilestoneResponse res = milestoneService.createMilestone(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(res, "Tạo cột mốc thành công"));
    }

    @GetMapping("/projects/{projectId}/milestones")
    public ResponseEntity<ApiResponse<List<MilestoneResponse>>> getMilestonesByProject(@PathVariable("projectId") UUID projectId) {
        List<MilestoneResponse> res = milestoneService.getMilestonesByProject(projectId);
        return ResponseEntity.ok(ApiResponse.success(res, "Lấy danh sách cột mốc thành công"));
    }

    @GetMapping("/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> getMilestoneById(@PathVariable("milestoneId") UUID milestoneId) {
        MilestoneResponse res = milestoneService.getMilestoneById(milestoneId);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @PutMapping("/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> updateMilestone(
            @PathVariable("milestoneId") UUID milestoneId,
            @Valid @RequestBody UpdateMilestoneRequest request) {
        MilestoneResponse res = milestoneService.updateMilestone(milestoneId, request);
        return ResponseEntity.ok(ApiResponse.success(res, "Cập nhật cột mốc thành công"));
    }

    @DeleteMapping("/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<Void>> deleteMilestone(@PathVariable("milestoneId") UUID milestoneId) {
        milestoneService.deleteMilestone(milestoneId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa cột mốc thành công"));
    }
}
