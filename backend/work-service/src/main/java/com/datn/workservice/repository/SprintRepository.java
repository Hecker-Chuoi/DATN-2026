package com.datn.workservice.repository;

import com.datn.workservice.entity.Sprint;
import com.datn.workservice.enums.SprintState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, UUID>, JpaSpecificationExecutor<Sprint> {
    List<Sprint> findByProjectId(UUID projectId);
    List<Sprint> findByProjectIdOrderByStartDateDesc(UUID projectId);
    List<Sprint> findByProjectIdAndState(UUID projectId, SprintState state);
    List<Sprint> findByProjectIdAndStateIn(UUID projectId, Collection<SprintState> states);
    List<Sprint> findByProjectIdAndStateOrderByCompletedDateDesc(UUID projectId, SprintState state);
    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);
    Optional<Sprint> findFirstByProjectIdAndState(UUID projectId, SprintState state);
}
