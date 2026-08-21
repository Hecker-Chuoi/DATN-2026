package com.datn.workservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreatedEvent implements Serializable {

    private UUID commentId;
    private UUID taskId;
    private UUID authorId;
    private List<UUID> mentionedUserIds;
    private String contentSnippet;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
