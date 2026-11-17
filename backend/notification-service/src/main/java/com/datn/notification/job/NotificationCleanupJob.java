package com.datn.notification.job;

import com.datn.notification.cluster.ClusterJobLock;
import com.datn.notification.repository.NotificationRepository;
import com.datn.notification.repository.ProjectMuteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * 03:00 mỗi ngày: xoá hẳn thông báo đã xoá mềm quá 30 ngày (hết hạn hoàn tác)
 * và thông báo đã đọc quá 90 ngày; bỏ các lượt tắt tiếng dự án đã hết hạn.
 * Chạy nhiều instance thì {@link ClusterJobLock} đảm bảo chỉ một instance chạy.
 */
@Slf4j
@Component
public class NotificationCleanupJob {

    private final NotificationRepository repository;
    private final ProjectMuteRepository mutes;
    private final ClusterJobLock jobLock;
    private final TransactionTemplate tx;
    private final Duration deletedRetention;
    private final Duration readRetention;

    public NotificationCleanupJob(NotificationRepository repository,
                                  ProjectMuteRepository mutes,
                                  ClusterJobLock jobLock,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${app.notifications.purge-deleted-after-days:30}") long deletedDays,
                                  @Value("${app.notifications.purge-read-after-days:90}") long readDays) {
        this.repository = repository;
        this.mutes = mutes;
        this.jobLock = jobLock;
        this.tx = new TransactionTemplate(transactionManager);
        this.deletedRetention = Duration.ofDays(deletedDays);
        this.readRetention = Duration.ofDays(readDays);
    }

    @Scheduled(cron = "${app.notifications.cleanup-cron:0 0 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void purge() {
        jobLock.runExclusively("notification-cleanup", Duration.ofMinutes(10), Duration.ofMinutes(1),
                () -> tx.executeWithoutResult(status -> purgeNow()));
    }

    void purgeNow() {
        Instant now = Instant.now();
        int deleted = repository.purgeDeletedBefore(now.minus(deletedRetention));
        int read = repository.purgeReadBefore(now.minus(readRetention));
        int expiredMutes = mutes.deleteExpired(now);
        if (deleted + read + expiredMutes > 0) {
            log.info("Dọn thông báo: {} đã xoá mềm, {} đã đọc lâu ngày, {} lượt tắt tiếng hết hạn", deleted, read, expiredMutes);
        }
    }
}
