package com.datn.aiservice.repository;

import com.datn.aiservice.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, UUID> {
}
