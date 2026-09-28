package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.BacklogFilterRequest;
import com.datn.workservice.dto.response.BacklogResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.service.BacklogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class BacklogController {

    private final BacklogService backlogService;

    @GetMapping("/projects/{projectId}/backlog")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<BacklogResponse>> getBacklog(
            @PathVariable UUID projectId,
            @ModelAttribute BacklogFilterRequest filter
    ) {
        return ResponseEntity.ok(ApiResponse.success(backlogService.getBacklog(projectId, filter)));
    }
}
