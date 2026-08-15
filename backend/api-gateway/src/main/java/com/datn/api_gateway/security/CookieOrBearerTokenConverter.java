package com.datn.api_gateway.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.HttpCookie;
import reactor.core.publisher.Mono;

/**
 * Extracts a Bearer JWT from either the {@code Authorization: Bearer ...}
 * header
 */
public class CookieOrBearerTokenConverter implements ServerAuthenticationConverter {

    private static final String COOKIE_NAME = "access_token";

    /** Delegates to Spring Security's standard header extractor. */
    private final ServerBearerTokenAuthenticationConverter headerConverter = new ServerBearerTokenAuthenticationConverter();

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        // 1. Try Authorization: Bearer <token> header first (API clients, Postman,
        // etc.)
        return headerConverter.convert(exchange)
                // 2. Fall back to httpOnly cookie (browser-based SPA flow)
                .switchIfEmpty(Mono.defer(() -> fromCookie(exchange)));
    }

    private Mono<Authentication> fromCookie(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
        if (cookie == null || cookie.getValue().isBlank()) {
            return Mono.empty(); // no token → Spring Security marks request unauthenticated
        }
        return Mono.just(new BearerTokenAuthenticationToken(cookie.getValue()));
    }
}
