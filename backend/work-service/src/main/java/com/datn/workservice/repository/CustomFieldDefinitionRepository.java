package com.datn.workservice.repository;

import com.datn.workservice.entity.CustomFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, UUID>, JpaSpecificationExecutor<CustomFieldDefinition> {
    List<CustomFieldDefinition> findByProjectId(UUID projectId);
    List<CustomFieldDefinition> findByProjectIdOrProjectIdIsNull(UUID projectId);
    Optional<CustomFieldDefinition> findByProjectIdAndFieldKey(UUID projectId, String fieldKey);
    boolean existsByProjectIdAndFieldKey(UUID projectId, String fieldKey);
}
