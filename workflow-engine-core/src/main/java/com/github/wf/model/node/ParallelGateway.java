package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class ParallelGateway extends Node {
    public ParallelGateway(String id, String name, List<String> listeners) {
        super(id, name, NodeType.PARALLEL_GATEWAY, listeners);
    }
    public ParallelGateway(String id) { this(id, null, null); }
}
