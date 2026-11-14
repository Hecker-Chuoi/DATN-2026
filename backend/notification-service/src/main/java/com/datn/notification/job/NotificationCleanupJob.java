package com.datn.notification.job;

import com.datn.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 03:00 mỗi ngày: xoá hẳn thông báo đã xoá mềm quá 30 ngày (hết hạn hoàn tác)
 * và thông báo đã đọc quá 90 ngày.
 */
@Slf4j
@Component
public class NotificationCleanupJob {

    private final NotificationRepository repository;
    private final Duration deletedRetention;
    private final Duration readRetention;

    public NotificationCleanupJob(NotificationRepository repository,
                                  @Value("${app.notifications.purge-deleted-after-days:30}") long deletedDays,
                                  @Value("${app.notifications.purge-read-after-days:90}") long readDays) {
        this.repository = repository;
        this.deletedRetention = Duration.ofDays(deletedDays);
        this.readRetention = Duration.ofDays(readDays);
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        int deleted = repository.purgeDeletedBefore(now.minus(deletedRetention));
        int read = repository.purgeReadBefore(now.minus(readRetention));
        if (deleted + read > 0) {
            log.info("Dọn thông báo: {} đã xoá mềm, {} đã đọc lâu ngày", deleted, read);
        }
    }
}
