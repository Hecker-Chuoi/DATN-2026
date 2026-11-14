package com.datn.workservice.notification;

import com.datn.workservice.cluster.ClusterJobLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock NotificationJobService jobs;
    @Mock ObjectProvider<StringRedisTemplate> redis;

    private NotificationScheduler scheduler(ClusterJobLock lock, boolean enabled) {
        NotificationScheduler s = new NotificationScheduler(jobs, lock);
        ReflectionTestUtils.setField(s, "enabled", enabled);
        return s;
    }

    @Test
    void singleInstance_runsAllJobsForAllProjects() {
        scheduler(new ClusterJobLock(redis, false), true).runDaily();
        verify(jobs).runAll(any(), any(), isNull());
    }

    @Test
    void disabled_doesNothing() {
        scheduler(new ClusterJobLock(redis, false), false).runDaily();
        verify(jobs, never()).runAll(any(), any(), any());
    }

    @Test
    void cluster_usesSharedLock_andSkipsWhenAnotherInstanceRuns() {
        ClusterJobLock lock = mock(ClusterJobLock.class);
        when(lock.runExclusively(eq("notification-daily"), eq(Duration.ofMinutes(30)), eq(Duration.ofMinutes(5)), any()))
                .thenReturn(false);
        scheduler(lock, true).runDaily();
        verify(lock).runExclusively(eq("notification-daily"), eq(Duration.ofMinutes(30)), eq(Duration.ofMinutes(5)), any());
        verify(jobs, never()).runAll(any(), any(), any());
    }
}
