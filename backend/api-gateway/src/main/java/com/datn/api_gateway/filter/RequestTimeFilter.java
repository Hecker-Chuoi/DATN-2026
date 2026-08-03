package com.datn.api_gateway.filter;

import com.datn.api_gateway.constant.GatewayConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that tracks request processing time.
 * <p>
 * This filter records the start time of each request as an exchange attribute.
 * Other filters (e.g., {@link LoggingFilter}) can use this attribute to
 * calculate and log the total execution time.
 * </p>
 *
 * <h3>How it works:</h3>
 * <ol>
 *   <li><b>Pre-filter:</b> Stores {@code System.currentTimeMillis()} in exchange attributes</li>
 *   <li><b>Post-filter:</b> Calculates and logs the elapsed time. Also logs a WARNING
 *       if the request takes longer than the configured threshold (default: 3000ms)</li>
 * </ol>
 *
 * <h3>Order:</h3>
 * <p>
 * Runs after {@link CorrelationIdFilter} and {@link LoggingFilter}
 * (order = HIGHEST_PRECEDENCE + 2). The start time is stored early in the chain,
 * and the post-filter calculation happens after the response is received.
 * </p>
 *
 * @see GatewayConstant#REQUEST_START_TIME_ATTR
 */
@Slf4j
@Component
public class RequestTimeFilter implements GlobalFilter, Ordered {

    /** Threshold in milliseconds. Requests exceeding this duration trigger a warning log. */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 3000;

    /**
     * Records request start time and logs slow requests.
     *
     * @param exchange the current server exchange
     * @param chain    the gateway filter chain
     * @return a {@link Mono} to indicate when request processing is complete
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ── Pre-filter: Record start time ──
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(GatewayConstant.REQUEST_START_TIME_ATTR, startTime);

        String correlationId = (String) exchange.getAttributes()
                .getOrDefault(GatewayConstant.CORRELATION_ID_ATTR, "N/A");

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    // ── Post-filter: Calculate execution time ──
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Log warning for slow requests that exceed the threshold
                    if (executionTime > SLOW_REQUEST_THRESHOLD_MS) {
                        log.warn("[SLOW-REQUEST] [{}] {} {} took {}ms (threshold: {}ms)",
                                correlationId,
                                exchange.getRequest().getMethod(),
                                exchange.getRequest().getURI().getPath(),
                                executionTime,
                                SLOW_REQUEST_THRESHOLD_MS);
                    }

                    log.debug("[REQUEST-TIME] [{}] {} {} completed in {}ms",
                            correlationId,
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getURI().getPath(),
                            executionTime);
                }));
    }

    /**
     * Runs after CorrelationIdFilter and LoggingFilter.
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE} + 2
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
