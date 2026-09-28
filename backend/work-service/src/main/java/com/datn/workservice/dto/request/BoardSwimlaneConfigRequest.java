package com.datn.workservice.dto.request;

import com.datn.workservice.enums.SwimlaneStrategy;
import jakarta.validation.constraints.NotBlank;
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
public class BoardSwimlaneConfigRequest {

    private UUID id;

    @NotBlank(message = "Tên luồng là bắt buộc")
    private String name;

    @NotNull(message = "Chiến lược phân luồng là bắt buộc")
    private SwimlaneStrategy strategy;

    private String customJql;

    @NotNull(message = "Thứ tự luồng là bắt buộc")
    private Integer swimlaneOrder;
}
