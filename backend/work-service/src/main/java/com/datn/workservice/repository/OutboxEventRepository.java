package com.datn.workservice.repository;

import com.datn.workservice.entity.OutboxEvent;
import com.datn.workservice.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, Integer maxRetries);
    List<OutboxEvent> findByAggregateId(String aggregateId);
}
