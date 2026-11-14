package com.datn.notification.service;

import com.datn.notification.cluster.RedisPushRelay;
import com.datn.notification.dto.PushMessage;
import com.datn.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Đẩy thông điệp tới mọi tab đang mở của một người qua STOMP ({@code /user/{sub}/queue/notifications}).
 *
 * Luôn chạy SAU khi transaction commit, để trình duyệt không bao giờ thấy một thay đổi bị rollback.
 * Lỗi đẩy chỉ ghi log: trình duyệt vẫn tự đồng bộ khi kết nối lại hoặc khi mở bảng thông báo.
 * Chạy nhiều instance ({@code app.cluster.redis-enabled=true}) thì đi qua {@link RedisPushRelay} để tới được
 * instance đang giữ WebSocket của người nhận; Redis lỗi thì gửi tại chỗ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

    public static final String USER_QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository repository;
    private final ObjectProvider<RedisPushRelay> relay;

    /** Gửi sau commit (hoặc ngay nếu không có transaction). Số chưa đọc được tính lại lúc gửi. */
    public void pushAfterCommit(UUID recipientId, PushMessage message) {
        Runnable task = () -> send(recipientId, message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void send(UUID recipientId, PushMessage message) {
        try {
            long unread = repository.countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(recipientId);
            long action = repository.countPendingActions(recipientId);
            PushMessage withCounts = message.withCounts(unread, action);
            RedisPushRelay cluster = relay.getIfAvailable();
            if (cluster != null) {
                try {
                    cluster.publish(recipientId, withCounts);
                    return;
                } catch (Exception e) {
                    log.warn("Không đẩy được qua Redis ({}), gửi tại instance này", e.getMessage());
                }
            }
            messagingTemplate.convertAndSendToUser(recipientId.toString(), USER_QUEUE, withCounts);
        } catch (Exception e) {
            log.warn("Không đẩy được thông báo {} tới {}: {}", message.kind(), recipientId, e.getMessage());
        }
    }
}
