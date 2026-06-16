package com.github.wf.ext.http;

/**
 * Thrown when an HTTP response has a non-2xx status code.
 * The status code and response body are exposed for SpEL routing:
 * {@code exception.type.contains('HttpStatusException')} or
 * {@code exception.message.contains('503')}.
 */
public class HttpStatusException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public HttpStatusException(int statusCode, String responseBody) {
        super("HTTP " + statusCode + ": " + (responseBody != null ? responseBody : "(no body)"));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }
}
