package com.datn.workservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignTaskRequest {

    /**
     * ID của người dùng được gán việc.
     * Để giá trị null nếu muốn hủy gán (Unassigned).
     */
    private UUID assigneeId;
}
