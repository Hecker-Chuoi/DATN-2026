package com.datn.workservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Bo loc cua thanh cong cu tren man hinh Backlog (binh qua @ModelAttribute). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacklogFilterRequest {
    private UUID assigneeId;
    private UUID epicId;
    private List<UUID> typeIds;
    /** Tim theo ma hoac tieu de. */
    private String q;
}
