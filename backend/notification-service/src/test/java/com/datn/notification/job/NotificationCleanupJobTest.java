package com.datn.notification.job;

import com.datn.notification.cluster.ClusterJobLock;
import com.datn.notification.repository.NotificationRepository;
import com.datn.notification.repository.ProjectMuteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupJobTest {

    @Mock NotificationRepository repository;
    @Mock ProjectMuteRepository mutes;
    @Mock PlatformTransactionManager txManager;
    @Mock ObjectProvider<StringRedisTemplate> redis;

    @Test
    void purge_deletesOldNotificationsAndExpiredMutes_insideTransaction() {
        ClusterJobLock lock = new ClusterJobLock(redis, false);
        NotificationCleanupJob job = new NotificationCleanupJob(repository, mutes, lock, txManager, 30, 90);
        when(repository.purgeDeletedBefore(any())).thenReturn(2);

        Instant before = Instant.now();
        job.purge();
        Instant after = Instant.now();

        verify(txManager).getTransaction(any());
        // mốc cắt = lúc job chạy (nằm giữa before và after) trừ đi số ngày giữ lại
        verify(repository).purgeDeletedBefore(org.mockito.ArgumentMatchers.argThat(t ->
                !t.isBefore(before.minus(Duration.ofDays(30))) && !t.isAfter(after.minus(Duration.ofDays(30)))));
        verify(repository).purgeReadBefore(org.mockito.ArgumentMatchers.argThat(t ->
                !t.isBefore(before.minus(Duration.ofDays(90))) && !t.isAfter(after.minus(Duration.ofDays(90)))));
        verify(mutes).deleteExpired(any());
        verify(txManager).commit(any());
    }

    @Test
    void purge_skippedWhenAnotherInstanceHoldsTheLock() {
        ClusterJobLock lock = mock(ClusterJobLock.class);
        when(lock.runExclusively(eq("notification-cleanup"), any(), any(), any())).thenReturn(false);
        new NotificationCleanupJob(repository, mutes, lock, txManager, 30, 90).purge();
        assertThat(org.mockito.Mockito.mockingDetails(repository).getInvocations()).isEmpty();
    }
}
