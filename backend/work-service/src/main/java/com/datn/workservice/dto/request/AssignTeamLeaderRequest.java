package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignTeamLeaderRequest {

    @NotNull(message = "ID của Trưởng nhóm mới không được để trống")
    private UUID newLeaderUserId;
}
