package com.datn.workservice.dto.request;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Thay the toan bo cau hinh luong. Danh sach rong = tat phan luong. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoardSwimlanesRequest {

    @Valid
    private List<BoardSwimlaneConfigRequest> swimlanes;
}
