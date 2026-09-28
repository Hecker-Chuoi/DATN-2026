package com.datn.workservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Thay the toan bo cau hinh cot cua bang. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoardColumnsRequest {

    @Valid
    @NotEmpty(message = "Bảng phải có ít nhất một cột")
    private List<BoardColumnConfigRequest> columns;

    /** Cho phep xoa cot dang con the (the se roi ra khoi bang cho toi khi anh xa lai). */
    private Boolean force;
}
