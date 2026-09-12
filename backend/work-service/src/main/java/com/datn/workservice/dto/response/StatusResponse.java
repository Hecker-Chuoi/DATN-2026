package com.datn.workservice.dto.response;

import com.datn.workservice.enums.StatusCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private StatusCategory category;
    private String description;
    private String colorHex;
    private LocalDateTime createdAt;
}
