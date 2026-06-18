package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import com.github.wf.model.RetryConfig;
import com.github.wf.model.RoutingRule;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ServiceTask extends Node {
    private final String handlerClass;
    private final boolean httpMode;
    private final String url;         // HTTP proxy mode
    private final String method;      // GET/POST/PUT/DELETE
    private final Map<String, String> headers;
    private final String body;        // request body template
    private final RetryConfig retryConfig;
    private final List<RoutingRule> resultRouting;
    private final List<RoutingRule> exceptionRouting;

    /** Full constructor */
    public ServiceTask(String id, String name, String handlerClass,
                       boolean httpMode,
                       String url, String method, Map<String, String> headers, String body,
                       RetryConfig retryConfig,
                       List<RoutingRule> resultRouting,
                       List<RoutingRule> exceptionRouting,
                       List<String> listeners) {
        super(id, name, NodeType.SERVICE_TASK, listeners);
        this.handlerClass = handlerClass;
        this.httpMode = httpMode;
        this.url = url;
        this.method = method != null ? method : "POST";
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        this.body = body;
        this.retryConfig = retryConfig;
        this.resultRouting = resultRouting != null ? Collections.unmodifiableList(resultRouting) : Collections.emptyList();
        this.exceptionRouting = exceptionRouting != null ? Collections.unmodifiableList(exceptionRouting) : Collections.emptyList();
    }

    /** Routing-aware constructor (code mode) */
    public ServiceTask(String id, String name, String handlerClass,
                       RetryConfig retryConfig,
                       List<RoutingRule> resultRouting,
                       List<RoutingRule> exceptionRouting,
                       List<String> listeners) {
        this(id, name, handlerClass, false, null, null, null, null,
                retryConfig, resultRouting, exceptionRouting, listeners);
    }

    /** Backward-compatible constructor (no routing/retry) */
    public ServiceTask(String id, String name, String handlerClass, List<String> listeners) {
        this(id, name, handlerClass, false, null, null, null, null,
                null, null, null, listeners);
    }

    public String getHandlerClass() { return handlerClass; }
    public boolean isHttpTask() { return httpMode; }
    public String getUrl() { return url; }
    public String getMethod() { return method; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public RetryConfig getRetryConfig() { return retryConfig; }
    public List<RoutingRule> getResultRouting() { return resultRouting; }
    public List<RoutingRule> getExceptionRouting() { return exceptionRouting; }
}
