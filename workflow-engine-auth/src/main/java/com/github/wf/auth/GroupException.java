package com.github.wf.auth;

/** Domain error carrying the HTTP status the controller should return. */
public class GroupException extends RuntimeException {

    private final int status;

    public GroupException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }

    public static GroupException badRequest(String msg) { return new GroupException(400, msg); }
    public static GroupException forbidden(String msg) { return new GroupException(403, msg); }
    public static GroupException notFound(String msg) { return new GroupException(404, msg); }
    public static GroupException conflict(String msg) { return new GroupException(409, msg); }
}
