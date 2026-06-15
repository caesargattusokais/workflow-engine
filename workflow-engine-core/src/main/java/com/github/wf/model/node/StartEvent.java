package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class StartEvent extends Node {
    public StartEvent(String id, String name, List<String> listeners) {
        super(id, name, NodeType.START_EVENT, listeners);
    }
    public StartEvent(String id, String name) { this(id, name, null); }
    public StartEvent(String id) { this(id, null, null); }
}
