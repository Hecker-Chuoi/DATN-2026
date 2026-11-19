package com.datn.workservice.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepoDto {

    private Long id;

    private String name;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("private")
    private Boolean isPrivate;

    private String description;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("stargazers_count")
    private Integer stargazersCount;
}
