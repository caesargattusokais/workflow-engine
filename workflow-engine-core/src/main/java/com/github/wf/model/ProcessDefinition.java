package com.github.wf.model;

import java.util.*;

public class ProcessDefinition {
    private final String id;
    private final String name;
    private final int version;
    private final Map<String, Node> nodes;
    private final List<Transition> transitions;
    private final Map<String, List<Transition>> outgoingCache;
    private final Map<String, List<Transition>> incomingCache;

    public ProcessDefinition(String id, String name, int version,
                             List<Node> nodes, List<Transition> transitions) {
        this.id = Objects.requireNonNull(id);
        this.name = name != null ? name : id;
        this.version = version;
        this.nodes = new LinkedHashMap<>();
        for (Node node : nodes) this.nodes.put(node.getId(), node);
        this.transitions = new ArrayList<>(transitions);
        this.outgoingCache = buildOutgoingCache();
        this.incomingCache = buildIncomingCache();
    }

    private Map<String, List<Transition>> buildOutgoingCache() {
        Map<String, List<Transition>> cache = new HashMap<>();
        for (Transition t : transitions)
            cache.computeIfAbsent(t.getFrom(), k -> new ArrayList<>()).add(t);
        return cache;
    }

    private Map<String, List<Transition>> buildIncomingCache() {
        Map<String, List<Transition>> cache = new HashMap<>();
        for (Transition t : transitions)
            if (t.getTo() != null)
                cache.computeIfAbsent(t.getTo(), k -> new ArrayList<>()).add(t);
        return cache;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getVersion() { return version; }
    public Map<String, Node> getNodes() { return Collections.unmodifiableMap(nodes); }
    public List<Transition> getTransitions() { return Collections.unmodifiableList(transitions); }

    public Node getNode(String nodeId) {
        Node node = nodes.get(nodeId);
        if (node == null) throw new IllegalArgumentException("Node not found: " + nodeId);
        return node;
    }

    public List<Transition> getOutgoingTransitions(String nodeId) {
        return outgoingCache.getOrDefault(nodeId, List.of());
    }

    public List<Transition> getIncomingTransitions(String nodeId) {
        return incomingCache.getOrDefault(nodeId, List.of());
    }

    public Node getStartNode() {
        return nodes.values().stream()
                .filter(n -> n.getType() == NodeType.START_EVENT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No start event in process definition"));
    }
}
