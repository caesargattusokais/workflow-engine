package com.github.wf.ext.http;

/**
 * Unchecked wrapper for HTTP timeout errors.
 * SpEL match: {@code exception.type.contains('HttpTimeoutException')}
 */
public class HttpTimeoutException extends RuntimeException {

    public HttpTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
