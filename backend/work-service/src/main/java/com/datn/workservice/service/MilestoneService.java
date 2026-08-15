package com.datn.workservice.service;

import com.datn.workservice.dto.request.CreateMilestoneRequest;
import com.datn.workservice.dto.request.UpdateMilestoneRequest;
import com.datn.workservice.dto.response.MilestoneResponse;

import java.util.List;
import java.util.UUID;

public interface MilestoneService {

    MilestoneResponse createMilestone(CreateMilestoneRequest request);

    List<MilestoneResponse> getMilestonesByProject(UUID projectId);

    MilestoneResponse getMilestoneById(UUID milestoneId);

    MilestoneResponse updateMilestone(UUID milestoneId, UpdateMilestoneRequest request);

    void deleteMilestone(UUID milestoneId);
}
