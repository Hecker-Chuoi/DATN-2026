package com.datn.workservice.security;

import com.datn.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubOAuthStateServiceTest {

    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-17T08:00:00Z");

    private GitHubOAuthStateService at(Instant instant) {
        return new GitHubOAuthStateService("state-key", "", Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void validStateForSameUserAndProjectPasses() {
        String state = at(now).issue(projectId, userId);
        assertThatCode(() -> at(now.plusSeconds(60)).verify(state, projectId, userId)).doesNotThrowAnyException();
    }

    @Test
    void stateIssuedForAnotherUserIsRejected() {
        // Kịch bản OAuth CSRF: state của kẻ tấn công bị dùng trong phiên của nạn nhân
        String attackerState = at(now).issue(projectId, UUID.randomUUID());
        assertThatThrownBy(() -> at(now).verify(attackerState, projectId, userId)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void stateForAnotherProjectIsRejected() {
        String state = at(now).issue(UUID.randomUUID(), userId);
        assertThatThrownBy(() -> at(now).verify(state, projectId, userId)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void tamperedOrMissingStateIsRejected() {
        String state = at(now).issue(projectId, userId);
        assertThatThrownBy(() -> at(now).verify(state + "x", projectId, userId)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> at(now).verify(null, projectId, userId)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> at(now).verify(projectId + ":" + userId, projectId, userId)).isInstanceOf(BadRequestException.class);
        // Ký bằng khoá khác
        String foreign = new GitHubOAuthStateService("other-key", "", Clock.fixed(now, ZoneOffset.UTC)).issue(projectId, userId);
        assertThatThrownBy(() -> at(now).verify(foreign, projectId, userId)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void expiredStateIsRejected() {
        String state = at(now).issue(projectId, userId);
        assertThatThrownBy(() -> at(now.plusSeconds(GitHubOAuthStateService.TTL_SECONDS + 1)).verify(state, projectId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hết hạn");
    }
}
