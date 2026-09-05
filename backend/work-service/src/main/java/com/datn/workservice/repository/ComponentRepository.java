package com.datn.workservice.repository;

import com.datn.workservice.entity.Component;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComponentRepository extends JpaRepository<Component, UUID>, JpaSpecificationExecutor<Component> {
    List<Component> findByProjectId(UUID projectId);
    Optional<Component> findByProjectIdAndName(UUID projectId, String name);
    boolean existsByProjectIdAndName(UUID projectId, String name);
}
