package com.datn.workservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteSprintRequest {

    /**
     * Noi chuyen cac cong viec chua hoan thanh: "BACKLOG" (hoac de trong) de tra ve Backlog,
     * hoac UUID cua mot Sprint FUTURE/ACTIVE khac trong cung du an.
     * Dung String vi gia tri khong phai luc nao cung la UUID.
     */
    private String targetSprintIdForIncomplete;
}
