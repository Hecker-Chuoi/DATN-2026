package com.datn.workservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Giao / hủy giao việc. {@code assigneeId = null} nghĩa là HỦY GIAO (khác với PUT /items/{id},
 * nơi null được hiểu là "không đổi").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeAssigneeRequest {

    private UUID assigneeId;
}
