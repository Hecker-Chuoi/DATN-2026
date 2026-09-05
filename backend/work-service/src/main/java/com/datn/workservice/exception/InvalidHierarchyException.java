package com.datn.workservice.exception;

import com.datn.common.exception.BadRequestException;

/**
 * InvalidHierarchyException - Thrown when an item violates Jira 3-level hierarchy rules.
 */
public class InvalidHierarchyException extends BadRequestException {

    public InvalidHierarchyException(String message) {
        super(message);
    }
}
