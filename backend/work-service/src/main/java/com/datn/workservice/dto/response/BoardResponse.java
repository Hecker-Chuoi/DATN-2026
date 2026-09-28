package com.datn.workservice.dto.response;

import com.datn.workservice.enums.BoardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private BoardType type;
    private String filterJql;

    private Integer columnCount;
    private Integer swimlaneCount;

    /** Sprint bang SCRUM dang hien thi (null voi bang KANBAN). */
    private UUID activeSprintId;
    private String activeSprintName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
