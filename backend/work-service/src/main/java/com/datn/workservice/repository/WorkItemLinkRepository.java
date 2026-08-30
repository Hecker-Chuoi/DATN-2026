package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemLink;
import com.datn.workservice.enums.LinkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkItemLinkRepository extends JpaRepository<WorkItemLink, UUID> {
    List<WorkItemLink> findBySourceItemId(UUID sourceItemId);
    List<WorkItemLink> findBySourceItemIdOrTargetItemId(UUID sourceItemId, UUID targetItemId);
    List<WorkItemLink> findByLinkType(LinkType linkType);
    boolean existsBySourceItemIdAndTargetItemIdAndLinkType(UUID sourceItemId, UUID targetItemId, LinkType linkType);
    void deleteBySourceItemIdAndTargetItemIdAndLinkType(UUID sourceItemId, UUID targetItemId, LinkType linkType);
}
