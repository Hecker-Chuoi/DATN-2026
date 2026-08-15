package com.datn.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Propagates the JWT access token from the {@code access_token} httpOnly cookie
 * to downstream microservices in the standard {@code Authorization: Bearer <token>} header.
 */
@Slf4j
@Component
public class TokenRelayFilter implements GlobalFilter, Ordered {

    private static final String COOKIE_NAME = "access_token";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTH_HEADER);

        // 1. If Authorization header is already present, do nothing (keep it)
        if (authHeader != null && !authHeader.isBlank()) {
            return chain.filter(exchange);
        }

        // 2. If no Authorization header but access_token cookie exists, relay it
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
        if (cookie != null && !cookie.getValue().isBlank()) {
            String token = cookie.getValue();
            
            log.debug("Relaying access_token cookie to downstream as Bearer token");
            
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(AUTH_HEADER, BEARER_PREFIX + token)
                    .build();
            
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // 3. No token present (public endpoints or unauthenticated requests)
        return chain.filter(exchange);
    }

    /**
     * Run this filter early (after CorrelationIdFilter) but before the request is routed downstream.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
