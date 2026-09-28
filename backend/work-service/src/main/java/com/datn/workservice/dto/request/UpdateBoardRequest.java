package com.datn.workservice.dto.request;

import com.datn.workservice.enums.BoardType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoardRequest {

    @Size(max = 255, message = "Tên bảng tối đa 255 ký tự")
    private String name;

    private BoardType type;

    private String filterJql;
}
