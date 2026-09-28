package com.datn.workservice.dto.response;

import com.datn.workservice.enums.StatusCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Epic dung cho bo loc va bang mau ben canh man hinh Backlog. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpicOptionResponse {
    private UUID id;
    private String code;
    private String summary;
    private String colorHex;
    private StatusCategory statusCategory;
    private Integer childCount;
    private BigDecimal totalStoryPoints;
    private BigDecimal doneStoryPoints;
}
