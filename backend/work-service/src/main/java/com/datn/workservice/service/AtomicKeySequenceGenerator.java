package com.datn.workservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * AtomicKeySequenceGenerator - High-concurrency Atomic Key Sequence Generator for Projects.
 * Utilizes PostgreSQL native sequences (nextval) for O(1) non-blocking, zero-collision issue codes (e.g. "SCRUM-1", "SCRUM-2").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtomicKeySequenceGenerator {

    private static final String SEQUENCE_PREFIX = "work_item_seq_";
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,20}$");

    private final JdbcTemplate jdbcTemplate;

    /**
     * Normalizes and validates project key to avoid SQL injection in sequence names.
     *
     * @param projectKey raw project key (e.g. "scrum", "AUTH")
     * @return normalized uppercase key
     */
    public String normalizeAndValidateKey(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalArgumentException("Project key must not be null or blank");
        }
        String normalized = projectKey.trim().toUpperCase();
        if (!VALID_KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid project key format: '" + projectKey + "'. Only alphanumeric and underscore allowed (max 20 chars).");
        }
        return normalized;
    }

    /**
     * Gets the full sequence name for a project key in PostgreSQL.
     */
    public String getSequenceName(String projectKey) {
        String normalized = normalizeAndValidateKey(projectKey);
        return SEQUENCE_PREFIX + normalized.toLowerCase();
    }

    /**
     * Initializes a PostgreSQL sequence for the given project key if it doesn't already exist.
     *
     * @param projectKey project key (e.g. "SCRUM")
     */
    public void createSequenceForProject(String projectKey) {
        String seqName = getSequenceName(projectKey);
        String sql = "CREATE SEQUENCE IF NOT EXISTS " + seqName + " START WITH 1 INCREMENT BY 1 NO MAXVALUE NO CYCLE";
        try {
            jdbcTemplate.execute(sql);
            log.info("Successfully ensured PostgreSQL sequence '{}' for project '{}'", seqName, projectKey);
        } catch (DataAccessException ex) {
            log.error("Failed to create sequence '{}' for project '{}': {}", seqName, projectKey, ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Drops the PostgreSQL sequence for a project key (e.g. during project hard deletion).
     *
     * @param projectKey project key (e.g. "SCRUM")
     */
    public void dropSequenceForProject(String projectKey) {
        String seqName = getSequenceName(projectKey);
        String sql = "DROP SEQUENCE IF EXISTS " + seqName;
        try {
            jdbcTemplate.execute(sql);
            log.info("Successfully dropped PostgreSQL sequence '{}' for project '{}'", seqName, projectKey);
        } catch (DataAccessException ex) {
            log.warn("Failed to drop sequence '{}' for project '{}': {}", seqName, projectKey, ex.getMessage());
        }
    }

    /**
     * Generates the next atomic work item code for a project (e.g. "SCRUM-1", "SCRUM-2").
     * Safe against race conditions and concurrent requests.
     *
     * @param projectKey project key (e.g. "SCRUM")
     * @return Generated code (e.g. "SCRUM-105")
     */
    public String generateNextCode(String projectKey) {
        String normalizedKey = normalizeAndValidateKey(projectKey);
        String seqName = getSequenceName(normalizedKey);
        String nextValSql = "SELECT nextval('" + seqName + "')";

        try {
            Long nextVal = jdbcTemplate.queryForObject(nextValSql, Long.class);
            if (nextVal == null) {
                throw new IllegalStateException("nextval returned null for sequence: " + seqName);
            }
            return normalizedKey + "-" + nextVal;
        } catch (DataAccessException ex) {
            log.warn("Sequence '{}' query failed. Attempting to create sequence on-the-fly...", seqName);
            createSequenceForProject(normalizedKey);
            Long nextVal = jdbcTemplate.queryForObject(nextValSql, Long.class);
            if (nextVal == null) {
                throw new IllegalStateException("nextval returned null after recreation for sequence: " + seqName);
            }
            return normalizedKey + "-" + nextVal;
        }
    }
}
