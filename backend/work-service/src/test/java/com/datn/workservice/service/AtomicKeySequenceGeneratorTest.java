package com.datn.workservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtomicKeySequenceGeneratorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AtomicKeySequenceGenerator sequenceGenerator;

    @BeforeEach
    void setUp() {
        sequenceGenerator = new AtomicKeySequenceGenerator(jdbcTemplate);
    }

    @Test
    @DisplayName("normalizeAndValidateKey - Valid keys should be converted to uppercase")
    void testNormalizeAndValidateKey_Valid() {
        assertEquals("SCRUM", sequenceGenerator.normalizeAndValidateKey("scrum"));
        assertEquals("AUTH_2", sequenceGenerator.normalizeAndValidateKey("auth_2"));
        assertEquals("PROJ", sequenceGenerator.normalizeAndValidateKey("  proj  "));
    }

    @Test
    @DisplayName("normalizeAndValidateKey - Invalid keys should throw IllegalArgumentException")
    void testNormalizeAndValidateKey_Invalid() {
        assertThrows(IllegalArgumentException.class, () -> sequenceGenerator.normalizeAndValidateKey(null));
        assertThrows(IllegalArgumentException.class, () -> sequenceGenerator.normalizeAndValidateKey("   "));
        assertThrows(IllegalArgumentException.class, () -> sequenceGenerator.normalizeAndValidateKey("PROJ-123")); // Contains hyphen
        assertThrows(IllegalArgumentException.class, () -> sequenceGenerator.normalizeAndValidateKey("PROJ@SPECIAL"));
        assertThrows(IllegalArgumentException.class, () -> sequenceGenerator.normalizeAndValidateKey("VERY_LONG_PROJECT_KEY_THAT_EXCEEDS_TWENTY_CHARS"));
    }

    @Test
    @DisplayName("createSequenceForProject - Executes correct DDL in JdbcTemplate")
    void testCreateSequenceForProject() {
        sequenceGenerator.createSequenceForProject("SCRUM");

        verify(jdbcTemplate, times(1)).execute(
                eq("CREATE SEQUENCE IF NOT EXISTS work_item_seq_scrum START WITH 1 INCREMENT BY 1 NO MAXVALUE NO CYCLE")
        );
    }

    @Test
    @DisplayName("dropSequenceForProject - Executes DROP SEQUENCE in JdbcTemplate")
    void testDropSequenceForProject() {
        sequenceGenerator.dropSequenceForProject("SCRUM");

        verify(jdbcTemplate, times(1)).execute(eq("DROP SEQUENCE IF EXISTS work_item_seq_scrum"));
    }

    @Test
    @DisplayName("generateNextCode - Returns formatted code when sequence exists")
    void testGenerateNextCode_Success() {
        when(jdbcTemplate.queryForObject("SELECT nextval('work_item_seq_scrum')", Long.class))
                .thenReturn(105L);

        String code = sequenceGenerator.generateNextCode("SCRUM");

        assertEquals("SCRUM-105", code);
    }

    @Test
    @DisplayName("generateNextCode - Recreates sequence on-the-fly if first attempt fails")
    void testGenerateNextCode_OnTheFlyFallback() {
        when(jdbcTemplate.queryForObject("SELECT nextval('work_item_seq_demo')", Long.class))
                .thenThrow(new DataRetrievalFailureException("relation does not exist"))
                .thenReturn(1L);

        String code = sequenceGenerator.generateNextCode("DEMO");

        assertEquals("DEMO-1", code);
        verify(jdbcTemplate, times(1)).execute(
                eq("CREATE SEQUENCE IF NOT EXISTS work_item_seq_demo START WITH 1 INCREMENT BY 1 NO MAXVALUE NO CYCLE")
        );
    }

    @Test
    @DisplayName("DoD Test: 100 concurrent threads generating codes produces zero duplicates")
    void testConcurrency_100ThreadsNoDuplicate() throws InterruptedException, ExecutionException {
        int threadCount = 100;
        AtomicLong mockSequenceCounter = new AtomicLong(0);

        // Simulate PostgreSQL atomic nextval behavior
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenAnswer(invocation -> mockSequenceCounter.incrementAndGet());

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        Set<String> generatedCodes = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to align
                    String code = sequenceGenerator.generateNextCode("SCRUM");
                    generatedCodes.add(code);
                } catch (Exception e) {
                    fail("Thread encountered exception: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all 100 threads simultaneously
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed, "All 100 threads should complete within 10 seconds");
        assertEquals(threadCount, generatedCodes.size(), "All 100 generated codes must be unique (Zero collisions)");

        for (int i = 1; i <= threadCount; i++) {
            assertTrue(generatedCodes.contains("SCRUM-" + i), "Generated codes must contain SCRUM-" + i);
        }
    }
}
