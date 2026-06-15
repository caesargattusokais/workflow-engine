package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class ServiceTask extends Node {
    private final String handlerClass;

    public ServiceTask(String id, String name, String handlerClass, List<String> listeners) {
        super(id, name, NodeType.SERVICE_TASK, listeners);
        this.handlerClass = handlerClass;
    }

    public String getHandlerClass() { return handlerClass; }
}
