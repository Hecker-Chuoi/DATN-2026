package com.datn.workservice.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClusterJobLockTest {

    private static final String KEY = ClusterJobLock.KEY_PREFIX + "daily";

    @Mock ObjectProvider<StringRedisTemplate> provider;
    @Mock StringRedisTemplate template;
    @Mock ValueOperations<String, String> ops;

    private final AtomicInteger runs = new AtomicInteger();

    @BeforeEach
    void setUp() {
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.opsForValue()).thenReturn(ops);
    }

    private ClusterJobLock lock(boolean enabled) {
        return new ClusterJobLock(provider, enabled, "node-a");
    }

    @Test
    void clusterOff_runsDirectly_withoutTouchingRedis() {
        assertThat(lock(false).runExclusively("daily", Duration.ofMinutes(30), Duration.ofMinutes(5), runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(1);
        verifyNoInteractions(provider);
    }

    @Test
    void acquired_runsOnce_thenKeepsLockForRemainingMinimumHold() {
        when(ops.setIfAbsent(KEY, "node-a", Duration.ofMinutes(30))).thenReturn(true);

        assertThat(lock(true).runExclusively("daily", Duration.ofMinutes(30), Duration.ofMinutes(5), runs::incrementAndGet)).isTrue();

        assertThat(runs).hasValue(1);
        verify(template).execute(any(RedisScript.class), eq(List.of(KEY)), eq("node-a"),
                argThat(hold -> Long.parseLong((String) hold) > 290_000 && Long.parseLong((String) hold) <= 300_000));
    }

    @Test
    void heldByAnotherInstance_skips() {
        when(ops.setIfAbsent(KEY, "node-a", Duration.ofMinutes(30))).thenReturn(false);

        assertThat(lock(true).runExclusively("daily", Duration.ofMinutes(30), Duration.ofMinutes(5), runs::incrementAndGet)).isFalse();

        assertThat(runs).hasValue(0);
        verify(template, never()).execute(any(RedisScript.class), any(List.class), anyString(), anyString());
    }

    @Test
    void redisDown_stillRuns_soDailyRemindersAreNotLost() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenThrow(new RuntimeException("connection refused"));
        assertThat(lock(true).runExclusively("daily", Duration.ofMinutes(30), Duration.ofMinutes(5), runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(1);
    }

    @Test
    void noRedisBean_stillRuns() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertThat(lock(true).runExclusively("daily", Duration.ofMinutes(30), Duration.ofMinutes(5), runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(1);
    }

    @Test
    void failingJob_stillReleasesLock() {
        when(ops.setIfAbsent(KEY, "node-a", Duration.ofMinutes(30))).thenReturn(true);
        assertThatThrownBy(() -> lock(true).runExclusively("daily", Duration.ofMinutes(30), Duration.ZERO, () -> {
            throw new IllegalStateException("lỗi job");
        })).isInstanceOf(IllegalStateException.class);
        verify(template).execute(any(RedisScript.class), eq(List.of(KEY)), eq("node-a"), eq("0"));
    }
}
