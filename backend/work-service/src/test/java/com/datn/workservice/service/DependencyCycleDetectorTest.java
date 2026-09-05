package com.datn.workservice.service;

import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemLink;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.exception.CyclicDependencyException;
import com.datn.workservice.repository.WorkItemLinkRepository;
import com.datn.workservice.repository.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyCycleDetectorTest {

    @Mock
    private WorkItemLinkRepository workItemLinkRepository;

    @Mock
    private WorkItemRepository workItemRepository;

    private DependencyCycleDetector cycleDetector;

    @BeforeEach
    void setUp() {
        cycleDetector = new DependencyCycleDetector(workItemLinkRepository, workItemRepository);
    }

    @Test
    @DisplayName("validateNoCycle - Self-blocking throws CyclicDependencyException")
    void testSelfBlockingThrowsException() {
        UUID itemId = UUID.randomUUID();

        CyclicDependencyException ex = assertThrows(CyclicDependencyException.class,
                () -> cycleDetector.validateNoCycle(itemId, itemId, LinkType.BLOCKS));
        assertTrue(ex.getMessage().contains("không thể tự khóa chính nó"));
    }

    @Test
    @DisplayName("validateNoCycle - Informational link types do not trigger cycle detection")
    void testInformationalLinksIgnored() {
        UUID itemA = UUID.randomUUID();
        UUID itemB = UUID.randomUUID();

        assertDoesNotThrow(() -> cycleDetector.validateNoCycle(itemA, itemB, LinkType.RELATES_TO));
        assertDoesNotThrow(() -> cycleDetector.validateNoCycle(itemA, itemB, LinkType.DUPLICATES));
    }

    @Test
    @DisplayName("validateNoCycle - Direct 2-node cycle (A -> B, then B -> A) is detected and throws exception")
    void testDirectTwoNodeCycle() {
        UUID nodeA = UUID.randomUUID();
        UUID nodeB = UUID.randomUUID();

        WorkItem itemA = WorkItem.builder().id(nodeA).code("SCRUM-1").build();
        WorkItem itemB = WorkItem.builder().id(nodeB).code("SCRUM-2").build();

        // Existing link: A blocks B
        WorkItemLink linkAB = WorkItemLink.builder().sourceItemId(nodeA).targetItemId(nodeB).linkType(LinkType.BLOCKS).build();
        when(workItemLinkRepository.findByLinkType(LinkType.BLOCKS)).thenReturn(List.of(linkAB));
        when(workItemRepository.findAllById(List.of(nodeB, nodeA, nodeB))).thenReturn(List.of(itemA, itemB));

        // Attempting to add: B blocks A (would create cycle A -> B -> A)
        CyclicDependencyException ex = assertThrows(CyclicDependencyException.class,
                () -> cycleDetector.validateNoCycle(nodeB, nodeA, LinkType.BLOCKS));

        assertTrue(ex.getMessage().contains("SCRUM-2 -> SCRUM-1 -> SCRUM-2") || ex.getMessage().contains("Deadlock"));
        assertEquals(3, ex.getCycleNodeIds().size());
    }

    @Test
    @DisplayName("validateNoCycle - Multi-node cycle (A -> B -> C -> D, then D -> A) is detected")
    void testMultiNodeCycle() {
        UUID nodeA = UUID.randomUUID();
        UUID nodeB = UUID.randomUUID();
        UUID nodeC = UUID.randomUUID();
        UUID nodeD = UUID.randomUUID();

        WorkItem itemA = WorkItem.builder().id(nodeA).code("SCRUM-1").build();
        WorkItem itemB = WorkItem.builder().id(nodeB).code("SCRUM-2").build();
        WorkItem itemC = WorkItem.builder().id(nodeC).code("SCRUM-3").build();
        WorkItem itemD = WorkItem.builder().id(nodeD).code("SCRUM-4").build();

        // Existing links: A -> B, B -> C, C -> D
        List<WorkItemLink> existingLinks = List.of(
                WorkItemLink.builder().sourceItemId(nodeA).targetItemId(nodeB).linkType(LinkType.BLOCKS).build(),
                WorkItemLink.builder().sourceItemId(nodeB).targetItemId(nodeC).linkType(LinkType.BLOCKS).build(),
                WorkItemLink.builder().sourceItemId(nodeC).targetItemId(nodeD).linkType(LinkType.BLOCKS).build()
        );

        when(workItemLinkRepository.findByLinkType(LinkType.BLOCKS)).thenReturn(existingLinks);
        when(workItemRepository.findAllById(List.of(nodeD, nodeA, nodeB, nodeC, nodeD)))
                .thenReturn(List.of(itemA, itemB, itemC, itemD));

        // Attempting to add: D blocks A (creating A -> B -> C -> D -> A cycle)
        CyclicDependencyException ex = assertThrows(CyclicDependencyException.class,
                () -> cycleDetector.validateNoCycle(nodeD, nodeA, LinkType.BLOCKS));

        assertTrue(ex.getMessage().contains("SCRUM-4 -> SCRUM-1 -> SCRUM-2 -> SCRUM-3 -> SCRUM-4"));
    }

    @Test
    @DisplayName("validateNoCycle - Valid DAG diamond structure (A -> B, A -> C, B -> C) does not throw exception")
    void testValidAcyclicDiamond() {
        UUID nodeA = UUID.randomUUID();
        UUID nodeB = UUID.randomUUID();
        UUID nodeC = UUID.randomUUID();

        // Existing links: A -> B, A -> C
        List<WorkItemLink> existingLinks = List.of(
                WorkItemLink.builder().sourceItemId(nodeA).targetItemId(nodeB).linkType(LinkType.BLOCKS).build(),
                WorkItemLink.builder().sourceItemId(nodeA).targetItemId(nodeC).linkType(LinkType.BLOCKS).build()
        );

        when(workItemLinkRepository.findByLinkType(LinkType.BLOCKS)).thenReturn(existingLinks);

        // Attempting to add: B -> C (DAG remains acyclic: A -> B -> C and A -> C)
        assertDoesNotThrow(() -> cycleDetector.validateNoCycle(nodeB, nodeC, LinkType.BLOCKS));
    }
}
