package com.datn.workservice.service;

import com.datn.workservice.dto.request.BacklogFilterRequest;
import com.datn.workservice.dto.response.BacklogResponse;

import java.util.UUID;

public interface BacklogService {

    /** Tra ve Sprint (ACTIVE + FUTURE), cong viec cua chung va Backlog trong mot lan goi. */
    BacklogResponse getBacklog(UUID projectId, BacklogFilterRequest filter);
}
