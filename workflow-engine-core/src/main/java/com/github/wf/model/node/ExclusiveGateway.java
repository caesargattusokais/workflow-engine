package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class ExclusiveGateway extends Node {
    public ExclusiveGateway(String id, String name, List<String> listeners) {
        super(id, name, NodeType.EXCLUSIVE_GATEWAY, listeners);
    }
    public ExclusiveGateway(String id) { this(id, null, null); }
}
