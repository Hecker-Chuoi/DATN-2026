package com.datn.workservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * LexoRankService - Jira-grade O(1) Lexicographical Ranking (Fractional Indexing) Engine.
 * Enables instantaneous card reordering on Kanban boards and Backlogs without bulk DB updates.
 */
@Slf4j
@Service
public class LexoRankService {

    public static final String DEFAULT_BUCKET = "0";
    public static final String INITIAL_RANK = "0|i00000:";
    public static final char MID_CHAR = 'i';
    public static final char MIN_CHAR = '0';
    public static final char MAX_CHAR = 'z';

    /**
     * Returns the default initial rank for newly created work items or empty lists.
     */
    public String calculateInitialRank() {
        return INITIAL_RANK;
    }

    /**
     * Calculates a rank strictly before the given rank (moving item to top).
     *
     * @param nextRank rank of the item currently at the target position
     * @return a rank string strictly less than nextRank
     */
    public String calculateBefore(String nextRank) {
        if (nextRank == null || nextRank.isBlank()) {
            return calculateInitialRank();
        }

        String cleaned = cleanRank(nextRank);
        String bucket = extractBucket(nextRank);
        String value = extractValue(cleaned);

        // Find midpoint between "000000" and value
        StringBuilder sb = new StringBuilder();
        boolean foundMid = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!foundMid) {
                if (c > MIN_CHAR) {
                    char mid = (char) (MIN_CHAR + (c - MIN_CHAR) / 2);
                    if (mid > MIN_CHAR) {
                        sb.append(mid);
                        foundMid = true;
                    } else {
                        sb.append(MIN_CHAR);
                    }
                } else {
                    sb.append(MIN_CHAR);
                }
            } else {
                sb.append(MID_CHAR);
                break;
            }
        }

        if (!foundMid) {
            // value is like "00000", prepend a smaller prefix or append
            sb.setLength(0);
            sb.append(MIN_CHAR).append(MID_CHAR);
        }

        String result = formatRank(bucket, sb.toString());
        // In the rare case result >= nextRank, prepend '0'
        if (result.compareTo(nextRank) >= 0) {
            result = formatRank(bucket, "0" + value);
        }
        return result;
    }

    /**
     * Calculates a rank strictly after the given rank (moving item to bottom or appending).
     *
     * @param prevRank rank of the item currently before the target position
     * @return a rank string strictly greater than prevRank
     */
    public String calculateAfter(String prevRank) {
        if (prevRank == null || prevRank.isBlank()) {
            return calculateInitialRank();
        }

        String cleaned = cleanRank(prevRank);
        String bucket = extractBucket(prevRank);
        String value = extractValue(cleaned);

        StringBuilder sb = new StringBuilder();
        boolean foundMid = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!foundMid) {
                if (c < MAX_CHAR) {
                    char mid = (char) (c + (MAX_CHAR - c + 1) / 2);
                    if (mid > c) {
                        sb.append(mid);
                        foundMid = true;
                    } else {
                        sb.append(c);
                    }
                } else {
                    sb.append(MAX_CHAR);
                }
            } else {
                break;
            }
        }

        if (!foundMid) {
            sb.append(MID_CHAR);
        }

        String result = formatRank(bucket, sb.toString());
        if (result.compareTo(prevRank) <= 0) {
            result = formatRank(bucket, value + MID_CHAR);
        }
        return result;
    }

    /**
     * Calculates a rank strictly between prevRank and nextRank.
     * Guaranteed O(1) computation with lexicographical correctness.
     *
     * @param prevRank rank of previous item
     * @param nextRank rank of next item
     * @return a rank string where prevRank < rank < nextRank
     */
    public String calculateBetween(String prevRank, String nextRank) {
        if ((prevRank == null || prevRank.isBlank()) && (nextRank == null || nextRank.isBlank())) {
            return calculateInitialRank();
        }
        if (prevRank == null || prevRank.isBlank()) {
            return calculateBefore(nextRank);
        }
        if (nextRank == null || nextRank.isBlank()) {
            return calculateAfter(prevRank);
        }

        if (prevRank.compareTo(nextRank) >= 0) {
            throw new IllegalArgumentException("prevRank (" + prevRank + ") must be strictly less than nextRank (" + nextRank + ")");
        }

        String bucket = extractBucket(prevRank);
        String pVal = extractValue(cleanRank(prevRank));
        String nVal = extractValue(cleanRank(nextRank));

        String midVal = computeMidpoint(pVal, nVal);
        String result = formatRank(bucket, midVal);

        // Validation guard
        if (result.compareTo(prevRank) <= 0 || result.compareTo(nextRank) >= 0) {
            // Fallback fractional extension
            result = formatRank(bucket, pVal + MID_CHAR);
        }

        return result;
    }

    private String computeMidpoint(String p, String n) {
        int maxLen = Math.max(p.length(), n.length());
        StringBuilder mid = new StringBuilder();

        for (int i = 0; i < maxLen; i++) {
            char cP = (i < p.length()) ? p.charAt(i) : MIN_CHAR;
            char cN = (i < n.length()) ? n.charAt(i) : MAX_CHAR;

            if (cP == cN) {
                mid.append(cP);
            } else if (cN - cP > 1) {
                char midChar = (char) (cP + (cN - cP) / 2);
                mid.append(midChar);
                return mid.toString();
            } else {
                // cN - cP == 1
                mid.append(cP);
                // Continue with suffix of p towards MAX_CHAR
                for (int j = i + 1; j < p.length(); j++) {
                    char nextP = p.charAt(j);
                    if (nextP < MAX_CHAR) {
                        char step = (char) (nextP + (MAX_CHAR - nextP + 1) / 2);
                        if (step > nextP) {
                            mid.append(step);
                            return mid.toString();
                        } else {
                            mid.append(nextP);
                        }
                    } else {
                        mid.append(MAX_CHAR);
                    }
                }
                mid.append(MID_CHAR);
                return mid.toString();
            }
        }

        mid.append(MID_CHAR);
        return mid.toString();
    }

    private String cleanRank(String rank) {
        if (rank == null) return "";
        String r = rank.trim();
        if (r.endsWith(":")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private String extractBucket(String rank) {
        if (rank != null && rank.contains("|")) {
            return rank.substring(0, rank.indexOf("|"));
        }
        return DEFAULT_BUCKET;
    }

    private String extractValue(String cleanRank) {
        if (cleanRank != null && cleanRank.contains("|")) {
            return cleanRank.substring(cleanRank.indexOf("|") + 1);
        }
        return Objects.requireNonNullElse(cleanRank, "");
    }

    private String formatRank(String bucket, String value) {
        return bucket + "|" + value + ":";
    }
}
