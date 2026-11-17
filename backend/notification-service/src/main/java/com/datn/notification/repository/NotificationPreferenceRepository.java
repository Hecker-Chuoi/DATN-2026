package com.datn.notification.repository;

import com.datn.notification.entity.NotificationPreference;
import com.datn.notification.enums.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, NotificationPreference.Key> {

    List<NotificationPreference> findByUserId(UUID userId);

    List<NotificationPreference> findByUserIdInAndCategory(Collection<UUID> userIds, NotificationCategory category);
}
