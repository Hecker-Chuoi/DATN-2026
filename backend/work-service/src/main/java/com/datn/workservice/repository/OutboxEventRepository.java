package com.datn.workservice.repository;

import com.datn.workservice.entity.OutboxEvent;
import com.datn.workservice.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, Integer maxRetries);
    List<OutboxEvent> findByAggregateId(String aggregateId);

    /**
     * Khoá một lô sự kiện PENDING cũ nhất. SKIP LOCKED cho phép nhiều instance cùng chạy relay
     * mà không gửi trùng (dòng đang bị instance khác giữ thì bỏ qua).
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(@Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = com.datn.workservice.enums.OutboxStatus.PROCESSED AND e.processedAt < :before")
    int deleteProcessedBefore(@Param("before") LocalDateTime before);
}
