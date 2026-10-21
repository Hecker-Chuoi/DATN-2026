package com.datn.workservice.dto.github;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectRepoRequest {

    @NotBlank(message = "Tên đầy đủ của Repository không được để trống (vd: owner/repo)")
    private String repoFullName;

    @NotBlank(message = "Đường dẫn Repository không được để trống")
    private String repoUrl;

    private String defaultBranch;

    @Builder.Default
    private Boolean syncIssues = true;
}
