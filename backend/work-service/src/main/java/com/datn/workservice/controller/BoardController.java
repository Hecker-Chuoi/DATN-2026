package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CreateBoardRequest;
import com.datn.workservice.dto.request.MoveCardRequest;
import com.datn.workservice.dto.request.UpdateBoardColumnsRequest;
import com.datn.workservice.dto.request.UpdateBoardRequest;
import com.datn.workservice.dto.request.UpdateBoardSwimlanesRequest;
import com.datn.workservice.dto.response.BoardColumnResponse;
import com.datn.workservice.dto.response.BoardDataResponse;
import com.datn.workservice.dto.response.BoardResponse;
import com.datn.workservice.dto.response.BoardSwimlaneResponse;
import com.datn.workservice.dto.response.MoveCardResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.ProjectIdSource;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.BoardService;
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
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/projects/{projectId}/boards")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<BoardResponse>>> listByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(boardService.listByProject(projectId)));
    }

    @PostMapping("/projects/{projectId}/boards")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<BoardResponse>> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateBoardRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(boardService.create(projectId, request, userId), "Tạo bảng thành công"));
    }

    @GetMapping("/boards/{boardId}")
    @RequireProjectPermission(value = PermissionKey.PROJECT_VIEW, source = ProjectIdSource.BOARD, param = "boardId")
    public ResponseEntity<ApiResponse<BoardResponse>> getById(@PathVariable UUID boardId) {
        return ResponseEntity.ok(ApiResponse.success(boardService.getById(boardId)));
    }

    @PutMapping("/boards/{boardId}")
    @RequireProjectPermission(value = PermissionKey.PROJECT_ADMIN, source = ProjectIdSource.BOARD, param = "boardId")
    public ResponseEntity<ApiResponse<BoardResponse>> update(
            @PathVariable UUID boardId,
            @Valid @RequestBody UpdateBoardRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                boardService.update(boardId, request, userId), "Cập nhật bảng thành công"));
    }

    @DeleteMapping("/boards/{boardId}")
    @RequireProjectPermission(value = PermissionKey.PROJECT_ADMIN, source = ProjectIdSource.BOARD, param = "boardId")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID boardId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        boardService.delete(boardId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xoá bảng"));
    }

    /** Toan bo du lieu de ve bang. `sprintId` chi dung voi bang SCRUM. */
    @GetMapping("/boards/{boardId}/data")
    @RequireProjectPermission(value = PermissionKey.PROJECT_VIEW, source = ProjectIdSource.BOARD, param = "boardId")
    public ResponseEntity<ApiResponse<BoardDataResponse>> getBoardData(
            @PathVariable UUID boardId,
            @RequestParam(required = false) UUID sprintId
    ) {
        return ResponseEntity.ok(ApiResponse.success(boardService.getBoardData(boardId, sprintId)));
    }

    /** Keo tha the. Doi cot se di qua may trang thai workflow. */
    @PutMapping("/boards/{boardId}/items/{itemId}/move")
    @RequireProjectPermission(value = PermissionKey.ISSUE_TRANSITION, source = ProjectIdSource.BOARD, param = "boardId")
    public ResponseEntity<ApiResponse<MoveCardResponse>> moveCard(
            @PathVariable UUID boardId,
            @PathVariable UUID itemId,
            @Valid @RequestBody MoveCardRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(boardService.moveCard(boardId, itemId, request, userId)));
    }

    @GetMapping("/boards/{boardId}/columns")
    @RequireProjectPermission(value = PermissionKey.PROJECT_VIEW, source = ProjectIdSource.BOARD, param = "boardId")
    public ResponseEntity<ApiResponse<List<BoardColumnResponse>>> getColumns(@PathVariable UUID boardId) {
        return ResponseEntity.ok(ApiResponse.success(boardService.getColumns(boardId)));
    }

    @PutMapping("/boards/{boardId}/columns")
    @RequireProjectPermission(value = PermissionKey.PROJECT_ADMIN, source = ProjectIdSource.BOARD, param = "boardId")
    public ResponseEntity<ApiResponse<List<BoardColumnResponse>>> updateColumns(
            @PathVariable UUID boardId,
            @Valid @RequestBody UpdateBoardColumnsRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                boardService.updateColumns(boardId, request, userId), "Đã cập nhật cấu hình cột"));
    }

    @GetMapping("/boards/{boardId}/swimlanes")
    @RequireProjectPermission(value = PermissionKey.PROJECT_VIEW, source = ProjectIdSource.BOARD, param = "boardId")
    public ResponseEntity<ApiResponse<List<BoardSwimlaneResponse>>> getSwimlanes(@PathVariable UUID boardId) {
        return ResponseEntity.ok(ApiResponse.success(boardService.getSwimlanes(boardId)));
    }

    @PutMapping("/boards/{boardId}/swimlanes")
    @RequireProjectPermission(value = PermissionKey.PROJECT_ADMIN, source = ProjectIdSource.BOARD, param = "boardId")
    public ResponseEntity<ApiResponse<List<BoardSwimlaneResponse>>> updateSwimlanes(
            @PathVariable UUID boardId,
            @Valid @RequestBody UpdateBoardSwimlanesRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                boardService.updateSwimlanes(boardId, request, userId), "Đã cập nhật phân luồng"));
    }
}
