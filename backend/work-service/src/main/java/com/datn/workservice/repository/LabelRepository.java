package com.datn.workservice.repository;

import com.datn.workservice.entity.Label;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabelRepository extends JpaRepository<Label, UUID>, JpaSpecificationExecutor<Label> {
    List<Label> findByProjectId(UUID projectId);
    Optional<Label> findByProjectIdAndName(UUID projectId, String name);
    boolean existsByProjectIdAndName(UUID projectId, String name);
}
