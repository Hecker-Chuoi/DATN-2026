package com.datn.workservice.dto.response;

import com.datn.workservice.enums.SwimlaneStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardSwimlaneResponse {

    /** null voi cac luong suy ra tu du lieu (theo Epic / nguoi thuc hien / do uu tien). */
    private UUID id;

    /** Khoa dung de gom the vao luong; null la luong "khong thuoc nhom nao". */
    private String swimlaneKey;

    private String name;
    private SwimlaneStrategy strategy;
    private Integer swimlaneOrder;
    private String customJql;

    /** true khi chien luoc chua duoc ho tro (vi du CUSTOM_JQL) - UI bao cho nguoi dung biet. */
    private Boolean unsupported;

    private Integer cardCount;
}
