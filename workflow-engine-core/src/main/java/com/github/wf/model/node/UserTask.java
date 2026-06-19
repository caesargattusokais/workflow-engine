package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.*;

public class UserTask extends Node {
    private final String assignee;
    private final List<String> candidateGroups;
    private final String dynamicRouter;
    private final String boundaryTimer; // ISO 8601 duration, e.g. "PT30M"
    private final boolean httpMode;
    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final String body;

    /** Backward-compatible constructor (non-HTTP mode) */
    public UserTask(String id, String name, String assignee,
                    List<String> candidateGroups, String dynamicRouter,
                    String boundaryTimer,
                    List<String> listeners) {
        this(id, name, assignee, candidateGroups, dynamicRouter, boundaryTimer,
                false, null, null, null, null, listeners);
    }

    /** Full constructor with HTTP callback support */
    public UserTask(String id, String name, String assignee,
                    List<String> candidateGroups, String dynamicRouter,
                    String boundaryTimer,
                    boolean httpMode, String url, String method,
                    Map<String, String> headers, String body,
                    List<String> listeners) {
        super(id, name, NodeType.USER_TASK, listeners);
        this.assignee = assignee;
        this.candidateGroups = candidateGroups != null ? candidateGroups : List.of();
        this.dynamicRouter = dynamicRouter;
        this.boundaryTimer = boundaryTimer;
        this.httpMode = httpMode;
        this.url = url;
        this.method = method != null ? method : "POST";
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Map.of();
        this.body = body;
    }

    public String getAssignee() { return assignee; }
    public List<String> getCandidateGroups() { return candidateGroups; }
    public String getDynamicRouter() { return dynamicRouter; }
    public String getBoundaryTimer() { return boundaryTimer; }
    public boolean isHttpTask() { return httpMode; }
    public String getUrl() { return url; }
    public String getMethod() { return method; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
}
