package com.datn.workservice.dto.request;

import com.datn.workservice.enums.BoardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBoardRequest {

    @NotBlank(message = "Tên bảng là bắt buộc")
    @Size(max = 255, message = "Tên bảng tối đa 255 ký tự")
    private String name;

    /** Bo trong thi suy ra tu loai du an. */
    private BoardType type;

    private String filterJql;

    /**
     * Mac dinh true: bang khong co cot nao thi khong the hien thi hay keo tha duoc,
     * nen tu sinh 3 cot theo nhom trang thai.
     */
    private Boolean seedDefaultColumns;
}
