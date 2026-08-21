package com.datn.workservice.dto.request;

import com.datn.workservice.enums.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectFilter {
    private String search;
    private ProjectStatus status;
    private UUID teamId;
    private Boolean isTemplate;
}
