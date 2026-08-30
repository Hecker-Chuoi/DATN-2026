package com.datn.workservice.exception;

import com.datn.common.exception.BadRequestException;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * CyclicDependencyException - Thrown when a work item link creates a circular dependency / deadlock.
 */
@Getter
public class CyclicDependencyException extends BadRequestException {

    private final List<UUID> cycleNodeIds;
    private final List<String> cycleNodeCodes;

    public CyclicDependencyException(String message) {
        super(message);
        this.cycleNodeIds = Collections.emptyList();
        this.cycleNodeCodes = Collections.emptyList();
    }

    public CyclicDependencyException(String message, List<UUID> cycleNodeIds, List<String> cycleNodeCodes) {
        super(message);
        this.cycleNodeIds = cycleNodeIds != null ? cycleNodeIds : Collections.emptyList();
        this.cycleNodeCodes = cycleNodeCodes != null ? cycleNodeCodes : Collections.emptyList();
    }
}
