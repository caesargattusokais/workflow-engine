package com.github.wf.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class Node {
    private final String id;
    private final String name;
    private final NodeType type;
    private final List<String> listeners;

    protected Node(String id, String name, NodeType type, List<String> listeners) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = name != null ? name : id;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.listeners = listeners != null ? new ArrayList<>(listeners) : new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public NodeType getType() { return type; }
    public List<String> getListeners() { return listeners; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node node = (Node) o;
        return id.equals(node.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "id='" + id + '\'' + ", name='" + name + '\'' + '}';
    }
}
