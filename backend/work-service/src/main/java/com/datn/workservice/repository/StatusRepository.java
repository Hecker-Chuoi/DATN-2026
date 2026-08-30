package com.datn.workservice.repository;

import com.datn.workservice.entity.Status;
import com.datn.workservice.enums.StatusCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatusRepository extends JpaRepository<Status, UUID>, JpaSpecificationExecutor<Status> {
    Optional<Status> findByName(String name);
    List<Status> findByCategory(StatusCategory category);
    boolean existsByName(String name);
}
