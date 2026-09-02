package com.datn.workservice.service;

import com.datn.workservice.dto.request.CreateWorkItemRequest;
import com.datn.workservice.dto.request.UpdateWorkItemRequest;
import com.datn.workservice.dto.request.WorkItemFilterRequest;
import com.datn.common.dto.PageResponse;
import com.datn.workservice.dto.response.WorkItemDetailResponse;
import com.datn.workservice.dto.response.WorkItemResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WorkItemService {

    WorkItemDetailResponse createWorkItem(UUID projectId, CreateWorkItemRequest request, UUID userId);

    WorkItemDetailResponse getWorkItemDetail(UUID itemId);

    WorkItemDetailResponse getWorkItemByCode(String code);

    PageResponse<WorkItemResponse> listWorkItems(UUID projectId, WorkItemFilterRequest filter, Pageable pageable);

    List<WorkItemResponse> listSubtasks(UUID parentId);

    WorkItemDetailResponse updateWorkItem(UUID itemId, UpdateWorkItemRequest request, UUID userId);

    void updateRank(UUID itemId, String newRank, UUID userId);

    void deleteWorkItem(UUID itemId, UUID userId);
}
