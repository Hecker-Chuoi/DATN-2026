package com.datn.api_gateway.filter;

import com.datn.api_gateway.constant.GatewayConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    /**
     * Processes the request to ensure a Correlation ID is present.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Check if the incoming request already has a Correlation ID
        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(GatewayConstant.CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            // Generate a new Correlation ID if not provided by the client
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new Correlation ID: {}", correlationId);
        } else {
            log.debug("Using existing Correlation ID: {}", correlationId);
        }

        // Store in exchange attributes for other filters to access
        exchange.getAttributes().put(GatewayConstant.CORRELATION_ID_ATTR, correlationId);

        // Mutate the request to add/override the Correlation ID header
        // This header will be forwarded to all downstream services
        final String finalCorrelationId = correlationId;
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(GatewayConstant.CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Register a callback to add the Correlation ID to the response headers before commit
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().addIfAbsent(GatewayConstant.CORRELATION_ID_HEADER, finalCorrelationId);
            return Mono.empty();
        });

        // Continue the filter chain with the mutated request
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * This filter runs FIRST — before LoggingFilter and RequestTimeFilter.
     * Other filters can then reference the Correlation ID in their logs.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
