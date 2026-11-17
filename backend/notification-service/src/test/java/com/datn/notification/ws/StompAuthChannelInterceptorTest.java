package com.datn.notification.ws;

import com.datn.notification.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private static final String SUB = "44444444-4444-4444-4444-444444444444";

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(decoder, new SecurityConfig().keycloakJwtAuthenticationConverter());

    private static Message<byte[]> frame(StompCommand command, String authHeader, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authHeader != null) accessor.setNativeHeader("Authorization", authHeader);
        if (destination != null) accessor.setDestination(destination);
        if (user != null) accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Jwt jwt(Instant exp) {
        return Jwt.withTokenValue("t").header("alg", "none").subject(SUB)
                .claim("realm_access", Map.of("roles", java.util.List.of("USER")))
                .issuedAt(Instant.now().minusSeconds(10)).expiresAt(exp).build();
    }

    @Test
    void connect_withoutToken_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connect_withBadToken_isRejected() {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("bad"));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer bad", null, null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connect_withValidToken_setsPrincipalToKeycloakSub() {
        when(decoder.decode("good")).thenReturn(jwt(Instant.now().plusSeconds(300)));
        Message<?> out = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), null);
        Principal user = StompHeaderAccessor.wrap(out).getUser();
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo(SUB);
    }

    @Test
    void subscribe_onlyOwnQueueAllowed() {
        when(decoder.decode("good")).thenReturn(jwt(Instant.now().plusSeconds(300)));
        Principal user = StompHeaderAccessor.wrap(
                interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), null)).getUser();

        interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/notifications", user), null);

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/user/" + "someone-else" + "/queue/notifications", user), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/queue/notifications", user), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribe_withoutConnect_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/notifications", null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void send_isNeverAllowed() {
        when(decoder.decode("good")).thenReturn(jwt(Instant.now().plusSeconds(300)));
        Principal user = StompHeaderAccessor.wrap(
                interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), null)).getUser();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, null, "/app/x", user), null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
