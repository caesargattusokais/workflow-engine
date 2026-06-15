package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class InclusiveGateway extends Node {
    public InclusiveGateway(String id, String name, List<String> listeners) {
        super(id, name, NodeType.INCLUSIVE_GATEWAY, listeners);
    }
    public InclusiveGateway(String id) { this(id, null, null); }
}
