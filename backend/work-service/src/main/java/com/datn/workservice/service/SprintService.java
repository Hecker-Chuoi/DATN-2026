package com.datn.workservice.service;

import com.datn.workservice.dto.request.CompleteSprintRequest;
import com.datn.workservice.dto.request.CreateSprintRequest;
import com.datn.workservice.dto.request.StartSprintRequest;
import com.datn.workservice.dto.request.UpdateSprintRequest;
import com.datn.workservice.dto.response.SprintCompletionResponse;
import com.datn.workservice.dto.response.SprintResponse;
import com.datn.workservice.dto.response.BoardCardResponse;
import com.datn.workservice.enums.SprintState;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SprintService {

    /** @param states loc theo trang thai; null/rong nghia la lay tat ca. */
    List<SprintResponse> listByProject(UUID projectId, Set<SprintState> states);

    SprintResponse getById(UUID sprintId);

    SprintResponse create(UUID projectId, CreateSprintRequest request, UUID userId);

    SprintResponse update(UUID sprintId, UpdateSprintRequest request, UUID userId);

    /** Chi cho phep xoa Sprint FUTURE; cong viec ben trong duoc tra ve Backlog. */
    void delete(UUID sprintId, UUID userId);

    /** FUTURE -> ACTIVE. Moi du an chi duoc co toi da mot Sprint ACTIVE. */
    SprintResponse startSprint(UUID sprintId, StartSprintRequest request, UUID userId);

    /** ACTIVE -> CLOSED, kem chup snapshot va chuyen cong viec chua xong. */
    SprintCompletionResponse completeSprint(UUID sprintId, CompleteSprintRequest request, UUID userId);

    List<BoardCardResponse> addItemsToSprint(UUID sprintId, List<UUID> itemIds, UUID userId);

    List<BoardCardResponse> removeItemsFromSprint(UUID sprintId, List<UUID> itemIds, UUID userId);
}
