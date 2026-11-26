package com.datn.workservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin dự án từ chối yêu cầu "Tạm hoàn thành".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectCompletionRequest {

    @Size(max = 1000, message = "Lý do từ chối tối đa 1000 ký tự")
    private String reason;
}
