package com.datn.workservice.service;

import com.datn.workservice.dto.request.CreateWorkItemTypeRequest;
import com.datn.workservice.dto.request.UpdateWorkItemTypeRequest;
import com.datn.workservice.dto.response.WorkItemTypeResponse;

import java.util.List;
import java.util.UUID;

public interface WorkItemTypeService {

    List<WorkItemTypeResponse> getProjectWorkItemTypes(UUID projectId);

    WorkItemTypeResponse createWorkItemType(UUID projectId, CreateWorkItemTypeRequest request, UUID currentUserId);

    WorkItemTypeResponse updateWorkItemType(UUID projectId, UUID typeId, UpdateWorkItemTypeRequest request, UUID currentUserId);

    void deleteWorkItemType(UUID projectId, UUID typeId, UUID currentUserId);
}
