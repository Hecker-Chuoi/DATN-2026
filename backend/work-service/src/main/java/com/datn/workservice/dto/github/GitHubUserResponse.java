package com.datn.workservice.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubUserResponse {

    private Long id;

    private String login;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String name;

    private String email;
}
