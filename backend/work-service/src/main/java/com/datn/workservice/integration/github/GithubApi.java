package com.datn.workservice.integration.github;

import com.datn.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Gọi GitHub REST API bằng RestClient (không DTO, trả JsonNode). Token truyền từng lời gọi.
 * 401 -> {@link Unauthorized} để caller đánh dấu connection invalid; 4xx khác -> BadRequest kèm thông điệp GitHub.
 */
@Component
@RequiredArgsConstructor
public class GithubApi {

    public static class Unauthorized extends RuntimeException {
        public Unauthorized() { super("GitHub token không còn hiệu lực"); }
    }

    private static final String API = "https://api.github.com";

    private final GithubProperties props;
    private final RestClient rc = RestClient.create();

    /** Đổi authorization code lấy access token (OAuth App). */
    public JsonNode exchangeCode(String code, String redirectUri) {
        JsonNode res = rc.post().uri("https://github.com/login/oauth/access_token")
                .header(HttpHeaders.ACCEPT, "application/json")
                .body(Map.of("client_id", props.getClientId(), "client_secret", props.getClientSecret(),
                        "code", code, "redirect_uri", redirectUri))
                .retrieve().body(JsonNode.class);
        if (res == null || res.hasNonNull("error")) {
            throw new BadRequestException("GitHub từ chối mã đăng nhập: "
                    + (res == null ? "không có phản hồi" : res.path("error_description").asText(res.path("error").asText())));
        }
        return res;
    }

    public JsonNode get(String token, String path) { return exchange(HttpMethod.GET, token, path, null); }

    public JsonNode post(String token, String path, Object body) { return exchange(HttpMethod.POST, token, path, body); }

    public void delete(String token, String path) { exchange(HttpMethod.DELETE, token, path, null); }

    private JsonNode exchange(HttpMethod method, String token, String path, Object body) {
        RestClient.RequestBodySpec spec = rc.method(method).uri(API + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (body != null) {
            spec.body(body);
        }
        return spec.retrieve()
                .onStatus(s -> s.value() == 401, (req, res) -> { throw new Unauthorized(); })
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new BadRequestException("GitHub (" + res.getStatusCode().value() + "): " + readBody(res));
                })
                .body(JsonNode.class);
    }

    private static String readBody(org.springframework.http.client.ClientHttpResponse res) {
        try {
            return new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
