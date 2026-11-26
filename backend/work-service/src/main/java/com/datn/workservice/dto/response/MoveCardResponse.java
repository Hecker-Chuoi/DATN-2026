package com.datn.workservice.dto.response;

import com.datn.workservice.enums.StatusCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveCardResponse {

    private UUID itemId;
    private String code;
    private String newRank;

    private UUID statusId;
    private String statusName;
    private StatusCategory statusCategory;
    private UUID columnId;

    /** Phien ban moi - FE PHAI luu lai, neu khong lan keo sau se gui version cu va bi 409. */
    private Integer version;

    private Boolean statusChanged;
    private UUID appliedTransitionId;
    private String appliedTransitionName;

    /** Canh bao khong chan (vi du da vuot WIP nhung nguoi dung chon bo qua). */
    private String warning;

    /**
     * true: nguoi keo khong phai Admin du an nen the KHONG doi cot; he thong chi tao yeu cau
     * "Tam hoan thanh" cho Admin xac nhan (BR-W03). FE nen tai lai bang va bao cho nguoi dung.
     */
    private Boolean completionPending;
}
