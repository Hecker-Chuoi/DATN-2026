package com.datn.aiservice.repository;

import com.datn.aiservice.entity.AiUserMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiUserMemoryRepository extends JpaRepository<AiUserMemory, UUID> {
    List<AiUserMemory> findTop50ByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String userId);
    Optional<AiUserMemory> findByIdAndUserIdAndDeletedAtIsNull(UUID id, String userId);
}
