package com.datn.notification.repository;

import com.datn.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    boolean existsByDedupKey(String dedupKey);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    Optional<Notification> findByIdAndRecipientIdAndDeletedAtIsNull(UUID id, UUID recipientId);

    /** Thông báo chưa đọc cùng nhóm, còn trong cửa sổ gộp (R4). */
    Optional<Notification> findFirstByRecipientIdAndGroupKeyAndReadAtIsNullAndDeletedAtIsNullAndUpdatedAtAfterOrderByUpdatedAtDesc(
            UUID recipientId, String groupKey, Instant after);

    long countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(UUID recipientId);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.recipientId = :recipientId AND n.deletedAt IS NULL
              AND n.actionRequired = TRUE AND n.actionResolvedAt IS NULL
            """)
    long countPendingActions(@Param("recipientId") UUID recipientId);

    /** Các thông báo "Cần xử lý" đang mở của một đối tượng (R9). */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.entityId = :entityId AND n.type IN :types
              AND n.actionRequired = TRUE AND n.actionResolvedAt IS NULL
            """)
    List<Notification> findOpenActions(@Param("entityId") UUID entityId, @Param("types") Collection<String> types);

    /** Như trên nhưng theo dự án (dùng cho loại tổng hợp theo dự án, ví dụ C4). */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.projectId = :projectId AND n.type IN :types
              AND n.actionRequired = TRUE AND n.actionResolvedAt IS NULL
            """)
    List<Notification> findOpenActionsByProject(@Param("projectId") UUID projectId, @Param("types") Collection<String> types);

    @Query("SELECT n.id FROM Notification n WHERE n.recipientId = :recipientId AND n.readAt IS NULL AND n.deletedAt IS NULL")
    List<UUID> findUnreadIds(@Param("recipientId") UUID recipientId);

    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :now
            WHERE n.recipientId = :recipientId AND n.readAt IS NULL AND n.deletedAt IS NULL AND n.id IN :ids
            """)
    int markReadByIds(@Param("recipientId") UUID recipientId, @Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE Notification n SET n.deletedAt = :now
            WHERE n.recipientId = :recipientId AND n.readAt IS NOT NULL AND n.deletedAt IS NULL
            """)
    int softDeleteAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);

    @Query("SELECT n.id FROM Notification n WHERE n.recipientId = :recipientId AND n.readAt IS NOT NULL AND n.deletedAt IS NULL")
    List<UUID> findReadIds(@Param("recipientId") UUID recipientId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.deletedAt IS NOT NULL AND n.deletedAt < :before")
    int purgeDeletedBefore(@Param("before") Instant before);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.readAt < :before")
    int purgeReadBefore(@Param("before") Instant before);
}
