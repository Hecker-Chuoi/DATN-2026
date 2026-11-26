package com.datn.workservice.dto.github;

import com.datn.workservice.enums.GitHubLinkType;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemGitHubLinkDto {

    private UUID id;

    private GitHubLinkType linkType;

    private Integer githubNumber;

    private String title;

    private String url;

    private String status;

    private String authorName;

    private String authorAvatarUrl;

    /** PR do người khác mở, không phải tài khoản GitHub của người được giao (chỉ đúng khi so được). */
    private boolean authorNotAssignee;
}
