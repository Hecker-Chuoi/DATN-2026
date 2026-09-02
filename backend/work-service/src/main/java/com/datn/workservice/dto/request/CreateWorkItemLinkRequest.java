package com.datn.workservice.dto.request;

import com.datn.workservice.enums.LinkType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkItemLinkRequest {

    @NotNull(message = "ID công việc đích (Target WorkItem ID) không được để trống")
    private UUID targetItemId;

    @NotNull(message = "Loại liên kết (LinkType) không được để trống")
    private LinkType linkType; // BLOCKS, RELATES_TO, DUPLICATES, CAUSES
}
