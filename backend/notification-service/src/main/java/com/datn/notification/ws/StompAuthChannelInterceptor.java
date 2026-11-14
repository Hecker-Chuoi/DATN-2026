package com.datn.notification.ws;

import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;

/**
 * Bảo vệ kênh STOMP:
 * <ul>
 *   <li>CONNECT: bắt buộc header {@code Authorization: Bearer <access token>}; giải mã bằng JwtDecoder của Keycloak.
 *       Principal = Keycloak sub (để {@code convertAndSendToUser(sub, …)} tới đúng người).</li>
 *   <li>SUBSCRIBE: chỉ cho {@code /user/queue/notifications}.</li>
 *   <li>SEND: không có kênh nào nhận từ client.</li>
 * </ul>
 * Không nhận token qua query string vì token sẽ bị ghi vào log truy cập.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String ALLOWED_DESTINATION = "/user/queue/notifications";

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> jwtConverter;

    public StompAuthChannelInterceptor(@Lazy JwtDecoder jwtDecoder,
                                       @Lazy Converter<Jwt, AbstractAuthenticationToken> keycloakJwtAuthenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.jwtConverter = keycloakJwtAuthenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            requireUser(accessor.getUser());
            if (!ALLOWED_DESTINATION.equals(accessor.getDestination())) {
                throw new AccessDeniedException("Kênh không hợp lệ");
            }
        } else if (command == StompCommand.SEND) {
            throw new AccessDeniedException("Không hỗ trợ gửi tin từ trình duyệt");
        }
        return message;
    }

    private Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Thiếu token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7).trim());
            AbstractAuthenticationToken auth = jwtConverter.convert(jwt);
            if (auth == null || jwt.getSubject() == null) {
                throw new AccessDeniedException("Token không hợp lệ");
            }
            return auth; // getName() = sub
        } catch (JwtException e) {
            throw new AccessDeniedException("Token không hợp lệ hoặc đã hết hạn");
        }
    }

    private static void requireUser(Principal user) {
        if (user == null) {
            throw new AccessDeniedException("Chưa xác thực");
        }
        if (user instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
            Instant exp = token.getToken().getExpiresAt();
            if (exp != null && exp.isBefore(Instant.now())) {
                throw new AccessDeniedException("Phiên đã hết hạn");
            }
        }
    }
}
