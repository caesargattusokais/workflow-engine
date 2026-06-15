package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class EndEvent extends Node {
    public EndEvent(String id, String name, List<String> listeners) {
        super(id, name, NodeType.END_EVENT, listeners);
    }
    public EndEvent(String id, String name) { this(id, name, null); }
    public EndEvent(String id) { this(id, null, null); }
}
