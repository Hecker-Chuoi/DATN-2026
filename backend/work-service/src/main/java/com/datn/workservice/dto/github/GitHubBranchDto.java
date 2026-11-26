package com.datn.workservice.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Một nhánh của GitHub Repository (GET /repos/{owner}/{repo}/branches).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubBranchDto {

    private String name;

    @JsonProperty("protected")
    private Boolean isProtected;
}
