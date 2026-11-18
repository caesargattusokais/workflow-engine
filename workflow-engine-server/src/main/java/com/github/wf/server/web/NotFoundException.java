package com.github.wf.server.web;

/**
 * Thrown when a resource does not exist OR exists but belongs to another
 * tenant — deliberately indistinguishable so callers get no existence oracle.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
