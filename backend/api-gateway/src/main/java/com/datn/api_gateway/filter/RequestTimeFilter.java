package com.datn.api_gateway.filter;

import com.datn.api_gateway.constant.GatewayConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RequestTimeFilter implements GlobalFilter, Ordered {

    /** Threshold in milliseconds. Requests exceeding this duration trigger a warning log. */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 3000;

    /**
     * Records request start time and logs slow requests.
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
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
