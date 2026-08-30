package com.datn.workservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexoRankServiceTest {

    private LexoRankService lexoRankService;

    @BeforeEach
    void setUp() {
        lexoRankService = new LexoRankService();
    }

    @Test
    @DisplayName("calculateInitialRank - Returns default initial rank")
    void testCalculateInitialRank() {
        String initial = lexoRankService.calculateInitialRank();
        assertNotNull(initial);
        assertEquals("0|i00000:", initial);
    }

    @Test
    @DisplayName("calculateBefore - Returns rank strictly less than nextRank")
    void testCalculateBefore() {
        String next = "0|i00000:";
        String before = lexoRankService.calculateBefore(next);

        assertNotNull(before);
        assertTrue(before.compareTo(next) < 0, "Calculated before rank must be strictly less than nextRank");
    }

    @Test
    @DisplayName("calculateAfter - Returns rank strictly greater than prevRank")
    void testCalculateAfter() {
        String prev = "0|i00000:";
        String after = lexoRankService.calculateAfter(prev);

        assertNotNull(after);
        assertTrue(prev.compareTo(after) < 0, "Calculated after rank must be strictly greater than prevRank");
    }

    @Test
    @DisplayName("calculateBetween - Returns rank strictly between prev and next")
    void testCalculateBetween() {
        String prev = "0|i00000:";
        String next = "0|i00010:";
        String mid = lexoRankService.calculateBetween(prev, next);

        assertNotNull(mid);
        assertTrue(prev.compareTo(mid) < 0, "prev must be strictly less than mid");
        assertTrue(mid.compareTo(next) < 0, "mid must be strictly less than next");
    }

    @Test
    @DisplayName("calculateBetween - Throws IllegalArgumentException if prev >= next")
    void testCalculateBetween_InvalidOrder() {
        assertThrows(IllegalArgumentException.class, () ->
                lexoRankService.calculateBetween("0|i00010:", "0|i00000:"));
        assertThrows(IllegalArgumentException.class, () ->
                lexoRankService.calculateBetween("0|i00000:", "0|i00000:"));
    }

    @Test
    @DisplayName("DoD Test: 500 consecutive insertions between adjacent items maintains strict lexicographical order")
    void testConsecutiveInsertionsBetween() {
        String left = "0|i00000:";
        String right = "0|i00001:";

        List<String> ranks = new ArrayList<>();
        ranks.add(left);
        ranks.add(right);

        // Perform 500 consecutive insertions between left and right
        for (int i = 0; i < 500; i++) {
            // Always insert between the last inserted item and the right boundary
            String prev = ranks.get(ranks.size() - 2);
            String next = ranks.get(ranks.size() - 1);
            String mid = lexoRankService.calculateBetween(prev, next);

            assertTrue(prev.compareTo(mid) < 0, "Iteration " + i + ": prev (" + prev + ") must be < mid (" + mid + ")");
            assertTrue(mid.compareTo(next) < 0, "Iteration " + i + ": mid (" + mid + ") must be < next (" + next + ")");

            ranks.add(ranks.size() - 1, mid);
        }

        // Verify the whole list of 502 items is strictly sorted ascending
        assertEquals(502, ranks.size());
        for (int i = 0; i < ranks.size() - 1; i++) {
            String curr = ranks.get(i);
            String next = ranks.get(i + 1);
            assertTrue(curr.compareTo(next) < 0, "Index " + i + ": (" + curr + ") must be strictly < (" + next + ")");
        }
    }

    @Test
    @DisplayName("DoD Test: 500 consecutive prepends (top of list) maintains strict ascending order")
    void testConsecutivePrepends() {
        String currentTop = lexoRankService.calculateInitialRank();
        List<String> topList = new ArrayList<>();
        topList.add(currentTop);

        for (int i = 0; i < 500; i++) {
            String newTop = lexoRankService.calculateBefore(currentTop);
            assertTrue(newTop.compareTo(currentTop) < 0, "New top must be strictly < current top");
            topList.add(0, newTop);
            currentTop = newTop;
        }

        assertEquals(501, topList.size());
        for (int i = 0; i < topList.size() - 1; i++) {
            assertTrue(topList.get(i).compareTo(topList.get(i + 1)) < 0);
        }
    }

    @Test
    @DisplayName("DoD Test: 500 consecutive appends (bottom of list) maintains strict ascending order")
    void testConsecutiveAppends() {
        String currentBottom = lexoRankService.calculateInitialRank();
        List<String> bottomList = new ArrayList<>();
        bottomList.add(currentBottom);

        for (int i = 0; i < 500; i++) {
            String newBottom = lexoRankService.calculateAfter(currentBottom);
            assertTrue(currentBottom.compareTo(newBottom) < 0, "Current bottom must be strictly < new bottom");
            bottomList.add(newBottom);
            currentBottom = newBottom;
        }

        assertEquals(501, bottomList.size());
        for (int i = 0; i < bottomList.size() - 1; i++) {
            assertTrue(bottomList.get(i).compareTo(bottomList.get(i + 1)) < 0);
        }
    }
}
