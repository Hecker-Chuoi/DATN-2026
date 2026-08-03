package com.datn.api_gateway.filter;

import com.datn.api_gateway.constant.GatewayConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    /**
     * Logs request details on entry and response details on exit.
     *
     * @param exchange the current server exchange
     * @param chain    the gateway filter chain
     * @return a {@link Mono} to indicate when request processing is complete
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Retrieve Correlation ID (set by CorrelationIdFilter which runs before this)
        String correlationId = (String) exchange.getAttributes()
                .getOrDefault(GatewayConstant.CORRELATION_ID_ATTR, "N/A");

        // Extract request details
        String method = request.getMethod().name();
        String uri = request.getURI().getPath();
        String clientIp = extractClientIp(request);

        // ── Pre-filter: Log incoming request ──
        log.info("[GATEWAY-IN]  [{}] {} {} | IP: {}", correlationId, method, uri, clientIp);

        // Record start time for execution time calculation
        long startTime = System.currentTimeMillis();

        // Continue the filter chain
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    // ── Post-filter: Log outgoing response ──
                    ServerHttpResponse response = exchange.getResponse();
                    long executionTime = System.currentTimeMillis() - startTime;

                    log.info("[GATEWAY-OUT] [{}] Status: {} | Time: {}ms",
                            correlationId,
                            response.getStatusCode() != null ? response.getStatusCode().value() : "UNKNOWN",
                            executionTime);
                }));
    }

    /**
     * Extracts the client IP address from the request.
     * <p>
     * Checks the {@code X-Forwarded-For} header first (for proxied requests),
     * then falls back to the remote address from the connection.
     * </p>
     *
     * @param request the incoming HTTP request
     * @return client IP address string, or "UNKNOWN" if not determinable
     */
    private String extractClientIp(ServerHttpRequest request) {
        // Check X-Forwarded-For header (set by reverse proxies like Nginx)
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For may contain multiple IPs; the first is the original client
            return forwardedFor.split(",")[0].trim();
        }

        // Fallback to direct connection remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "UNKNOWN";
    }

    /**
     * Runs after CorrelationIdFilter (HIGHEST_PRECEDENCE)
     * so that the Correlation ID is already available.
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE} + 1
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
