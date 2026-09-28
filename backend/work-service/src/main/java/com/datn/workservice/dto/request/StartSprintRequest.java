package com.datn.workservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartSprintRequest {

    /** Bo trong thi lay startDate da luu, khong co nua thi lay thoi diem hien tai. */
    private LocalDateTime startDate;

    /** Bat buoc: Sprint phai co ngay ket thuc de ve duoc Burndown. */
    private LocalDateTime endDate;

    private String goal;

    /** Cho phep bat dau Sprint rong (mac dinh false - Jira canh bao truong hop nay). */
    private Boolean allowEmpty;
}
