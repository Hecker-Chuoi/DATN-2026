package com.datn.workservice.service.impl;

import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.CreateMilestoneRequest;
import com.datn.workservice.dto.request.UpdateMilestoneRequest;
import com.datn.workservice.dto.response.MilestoneResponse;
import com.datn.workservice.entity.Milestone;
import com.datn.workservice.enums.MilestoneStatus;
import com.datn.workservice.enums.TaskStatus;
import com.datn.workservice.repository.MilestoneRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.TaskRepository;
import com.datn.workservice.service.MilestoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    @Override
    @Transactional
    public MilestoneResponse createMilestone(CreateMilestoneRequest request) {
        if (!projectRepository.existsById(request.getProjectId())) {
            throw new ResourceNotFoundException("Project not found with id: " + request.getProjectId());
        }

        Milestone milestone = Milestone.builder()
                .projectId(request.getProjectId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .status(request.getStatus() != null ? request.getStatus() : MilestoneStatus.OPEN)
                .build();

        milestone = milestoneRepository.save(milestone);
        return enrichMilestoneResponse(milestone);
    }

    @Override
    public List<MilestoneResponse> getMilestonesByProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }

        return milestoneRepository.findByProjectId(projectId).stream()
                .map(this::enrichMilestoneResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MilestoneResponse getMilestoneById(UUID milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found with id: " + milestoneId));
        return enrichMilestoneResponse(milestone);
    }

    @Override
    @Transactional
    public MilestoneResponse updateMilestone(UUID milestoneId, UpdateMilestoneRequest request) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found with id: " + milestoneId));

        if (request.getName() != null) milestone.setName(request.getName().trim());
        if (request.getDescription() != null) milestone.setDescription(request.getDescription());
        if (request.getDueDate() != null) milestone.setDueDate(request.getDueDate());
        if (request.getStatus() != null) milestone.setStatus(request.getStatus());

        milestone = milestoneRepository.save(milestone);
        return enrichMilestoneResponse(milestone);
    }

    @Override
    @Transactional
    public void deleteMilestone(UUID milestoneId) {
        if (!milestoneRepository.existsById(milestoneId)) {
            throw new ResourceNotFoundException("Milestone not found with id: " + milestoneId);
        }
        milestoneRepository.deleteById(milestoneId);
    }

    private MilestoneResponse enrichMilestoneResponse(Milestone m) {
        MilestoneResponse res = MilestoneResponse.from(m);
        long total = taskRepository.countByMilestoneId(m.getId());
        long completed = taskRepository.countByMilestoneIdAndStatus(m.getId(), TaskStatus.DONE);

        int progress = total > 0 ? (int) Math.round(((double) completed / total) * 100) : 0;

        res.setTotalTasks(total);
        res.setCompletedTasks(completed);
        res.setProgressPercentage(progress);

        return res;
    }
}
