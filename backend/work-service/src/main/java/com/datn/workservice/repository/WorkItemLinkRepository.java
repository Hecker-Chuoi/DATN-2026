package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemLink;
import com.datn.workservice.enums.LinkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkItemLinkRepository extends JpaRepository<WorkItemLink, UUID> {
    List<WorkItemLink> findBySourceItemId(UUID sourceItemId);
    List<WorkItemLink> findBySourceItemIdIn(Collection<UUID> sourceItemIds);
    List<WorkItemLink> findBySourceItemIdOrTargetItemId(UUID sourceItemId, UUID targetItemId);
    List<WorkItemLink> findBySourceItemIdAndLinkType(UUID sourceItemId, LinkType linkType);
    List<WorkItemLink> findByTargetItemIdAndLinkType(UUID targetItemId, LinkType linkType);
    List<WorkItemLink> findByTargetItemIdInAndLinkType(Collection<UUID> targetItemIds, LinkType linkType);
    List<WorkItemLink> findByLinkType(LinkType linkType);
    boolean existsBySourceItemIdAndTargetItemIdAndLinkType(UUID sourceItemId, UUID targetItemId, LinkType linkType);
    void deleteBySourceItemIdAndTargetItemIdAndLinkType(UUID sourceItemId, UUID targetItemId, LinkType linkType);
}
