package com.datn.workservice.dto.request;

import com.datn.workservice.enums.WorkItemPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubtaskRequest {

    @NotBlank(message = "Tiêu đề Sub-task không được để trống")
    @Size(max = 500, message = "Tiêu đề Sub-task tối đa 500 ký tự")
    private String summary;

    private String description;

    @Builder.Default
    private WorkItemPriority priority = WorkItemPriority.MEDIUM;

    private UUID assigneeId;

    private Long originalEstimateSeconds;

    private LocalDate dueDate;

    private UUID typeId;
}
