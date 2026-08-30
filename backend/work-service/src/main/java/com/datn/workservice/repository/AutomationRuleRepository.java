package com.datn.workservice.repository;

import com.datn.workservice.entity.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID>, JpaSpecificationExecutor<AutomationRule> {
    List<AutomationRule> findByProjectId(UUID projectId);
    List<AutomationRule> findByProjectIdAndIsActiveTrue(UUID projectId);
    List<AutomationRule> findByIsActiveTrue();
}
