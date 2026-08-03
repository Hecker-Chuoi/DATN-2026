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

/**
 * Global filter that ensures every request has a Correlation ID.
 * <p>
 * This filter runs FIRST (highest precedence) because other filters
 * (LoggingFilter, RequestTimeFilter) may reference the Correlation ID
 * in their log output.
 * </p>
 *
 * <h3>Behavior:</h3>
 * <ul>
 *   <li>If the incoming request already has an {@code X-Correlation-Id} header,
 *       it is preserved and forwarded as-is to downstream services.</li>
 *   <li>If no {@code X-Correlation-Id} header is present, a new UUID is generated
 *       and added to both the request (forwarded downstream) and the response
 *       (returned to the client).</li>
 * </ul>
 *
 * <h3>Why Correlation ID?</h3>
 * <p>
 * In a microservice architecture, a single client request may traverse multiple
 * services. The Correlation ID allows tracing the complete request chain across
 * all services for debugging and monitoring.
 * </p>
 *
 * @see GatewayConstant#CORRELATION_ID_HEADER
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    /**
     * Processes the request to ensure a Correlation ID is present.
     * <p>
     * Pre-filter: Check/generate Correlation ID and add to request headers.
     * Post-filter: Add Correlation ID to response headers for client visibility.
     * </p>
     *
     * @param exchange the current server exchange
     * @param chain    the gateway filter chain
     * @return a {@link Mono} to indicate when request processing is complete
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

        // Continue the filter chain with the mutated request
        // Post-filter: Add Correlation ID to the response headers
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() ->
                        exchange.getResponse().getHeaders()
                                .addIfAbsent(GatewayConstant.CORRELATION_ID_HEADER, finalCorrelationId)
                ));
    }

    /**
     * This filter runs FIRST — before LoggingFilter and RequestTimeFilter.
     * Other filters can then reference the Correlation ID in their logs.
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE}
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
