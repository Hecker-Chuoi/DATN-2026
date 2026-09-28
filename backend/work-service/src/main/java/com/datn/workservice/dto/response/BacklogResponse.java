package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Toan bo du lieu man hinh Backlog trong MOT lan goi.
 *
 * Man hinh Backlog cua Jira can Sprint, cong viec trong Sprint va Backlog cung luc;
 * tach thanh nhieu request se gay nhay man hinh khi tai.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacklogResponse {

    private UUID projectId;
    private String projectKey;

    private SprintResponse activeSprint;

    /** ACTIVE truoc, roi cac Sprint FUTURE theo ngay bat dau. Khong gom Sprint da dong. */
    private List<BacklogGroupResponse> sprints;

    private BacklogGroupResponse backlog;

    private List<EpicOptionResponse> epics;
    private List<StatusResponse> statuses;

    private Integer totalBacklogItems;
    private LocalDateTime generatedAt;
}
