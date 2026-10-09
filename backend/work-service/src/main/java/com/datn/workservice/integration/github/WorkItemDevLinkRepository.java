package com.datn.workservice.integration.github;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkItemDevLinkRepository extends JpaRepository<WorkItemDevLink, UUID> {
    List<WorkItemDevLink> findByWorkItemIdOrderByUpdatedAtDesc(UUID workItemId);
    Optional<WorkItemDevLink> findByWorkItemIdAndKindAndExternalId(UUID workItemId, WorkItemDevLink.Kind kind, String externalId);
}
