package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import com.github.wf.model.RetryConfig;
import com.github.wf.model.RoutingRule;

import java.util.Collections;
import java.util.List;

public class ServiceTask extends Node {
    private final String handlerClass;
    private final RetryConfig retryConfig;
    private final List<RoutingRule> resultRouting;
    private final List<RoutingRule> exceptionRouting;

    /** Full constructor with all routing/retry fields */
    public ServiceTask(String id, String name, String handlerClass,
                       RetryConfig retryConfig,
                       List<RoutingRule> resultRouting,
                       List<RoutingRule> exceptionRouting,
                       List<String> listeners) {
        super(id, name, NodeType.SERVICE_TASK, listeners);
        this.handlerClass = handlerClass;
        this.retryConfig = retryConfig;
        this.resultRouting = resultRouting != null ? Collections.unmodifiableList(resultRouting) : Collections.emptyList();
        this.exceptionRouting = exceptionRouting != null ? Collections.unmodifiableList(exceptionRouting) : Collections.emptyList();
    }

    /** Backward-compatible constructor (no routing/retry) */
    public ServiceTask(String id, String name, String handlerClass, List<String> listeners) {
        this(id, name, handlerClass, null, null, null, listeners);
    }

    public String getHandlerClass() { return handlerClass; }
    public RetryConfig getRetryConfig() { return retryConfig; }
    public List<RoutingRule> getResultRouting() { return resultRouting; }
    public List<RoutingRule> getExceptionRouting() { return exceptionRouting; }
}
