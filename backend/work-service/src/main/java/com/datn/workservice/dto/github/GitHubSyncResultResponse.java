package com.datn.workservice.dto.github;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubSyncResultResponse {

    private Integer totalFound;

    private Integer importedCount;

    private Integer updatedCount;

    private LocalDateTime lastSyncedAt;
}
