package com.datn.workservice.service;

import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemLink;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.exception.CyclicDependencyException;
import com.datn.workservice.repository.WorkItemLinkRepository;
import com.datn.workservice.repository.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DependencyCycleDetector - Graph DFS algorithm engine to detect and prevent
 * circular dependency cycles (deadlocks) in work item links (e.g. A blocks B -> B blocks C -> C blocks A).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DependencyCycleDetector {

    private final WorkItemLinkRepository workItemLinkRepository;
    private final WorkItemRepository workItemRepository;

    /**
     * Validates that creating a directed dependency link (source -> target) of type BLOCKS
     * does not create a circular dependency / deadlock.
     *
     * @param sourceItemId The item that BLOCKS target item
     * @param targetItemId The item being BLOCKED
     * @param linkType     Type of link (e.g. BLOCKS)
     */
    public void validateNoCycle(UUID sourceItemId, UUID targetItemId, LinkType linkType) {
        if (linkType != LinkType.BLOCKS && linkType != LinkType.CAUSES) {
            // Informational links (RELATES_TO, DUPLICATES, CLONES) do not create execution deadlocks
            return;
        }

        if (sourceItemId.equals(targetItemId)) {
            throw new CyclicDependencyException("Một công việc không thể tự khóa chính nó (Tự phụ thuộc vòng tròn).");
        }

        // Fetch all existing BLOCKS links to construct the directed dependency graph
        List<WorkItemLink> existingLinks = workItemLinkRepository.findByLinkType(linkType);

        Map<UUID, List<UUID>> adj = new HashMap<>();
        for (WorkItemLink link : existingLinks) {
            adj.computeIfAbsent(link.getSourceItemId(), k -> new ArrayList<>()).add(link.getTargetItemId());
        }

        // Adding edge sourceItemId -> targetItemId creates a cycle IF AND ONLY IF
        // there is already a directed path from targetItemId to sourceItemId in the graph!
        List<UUID> cyclePath = findPathDfs(targetItemId, sourceItemId, adj);

        if (!cyclePath.isEmpty()) {
            // Cycle detected! Full path is: sourceItemId -> targetItemId -> ... -> sourceItemId
            List<UUID> fullCycle = new ArrayList<>();
            fullCycle.add(sourceItemId);
            fullCycle.addAll(cyclePath);

            // Fetch work item codes for human-readable error description
            Map<UUID, String> codeMap = workItemRepository.findAllById(fullCycle)
                    .stream()
                    .collect(Collectors.toMap(WorkItem::getId, WorkItem::getCode));

            List<String> codePath = fullCycle.stream()
                    .map(id -> codeMap.getOrDefault(id, id.toString()))
                    .toList();

            String formattedPath = String.join(" -> ", codePath);
            log.warn("Cyclic dependency detected: {}", formattedPath);

            throw new CyclicDependencyException(
                    "Phát hiện chu trình phụ thuộc vòng lặp (Deadlock): " + formattedPath,
                    fullCycle,
                    codePath
            );
        }
    }

    /**
     * Executes DFS to find a directed path from 'start' to 'target'.
     * Returns the path nodes if found (including start and target), or empty list if no path exists.
     */
    public List<UUID> findPathDfs(UUID start, UUID target, Map<UUID, List<UUID>> adj) {
        Set<UUID> visited = new HashSet<>();
        List<UUID> path = new ArrayList<>();

        if (dfs(start, target, adj, visited, path)) {
            return path;
        }
        return Collections.emptyList();
    }

    private boolean dfs(UUID current, UUID target, Map<UUID, List<UUID>> adj, Set<UUID> visited, List<UUID> path) {
        visited.add(current);
        path.add(current);

        if (current.equals(target)) {
            return true;
        }

        List<UUID> neighbors = adj.getOrDefault(current, Collections.emptyList());
        for (UUID neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                if (dfs(neighbor, target, adj, visited, path)) {
                    return true;
                }
            }
        }

        path.remove(path.size() - 1);
        return false;
    }
}
