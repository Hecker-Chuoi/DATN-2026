package com.datn.workservice.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubIssueDto {

    private Long id;

    private Integer number;

    private String title;

    private String body;

    private String state;

    @JsonProperty("html_url")
    private String htmlUrl;

    private GitHubUserResponse user;

    /** Người được assign trên GitHub (webhook issues / API đều gửi kèm). */
    private java.util.List<GitHubUserResponse> assignees;

    @JsonProperty("pull_request")
    private Map<String, Object> pullRequest;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("closed_at")
    private String closedAt;

    /**
     * GitHub trả về cả Pull Request trong endpoint /issues.
     * Nếu pull_request khác null thì thực thể này là một Pull Request, không phải Issue thuần túy.
     */
    public boolean isPullRequest() {
        return pullRequest != null;
    }
}
