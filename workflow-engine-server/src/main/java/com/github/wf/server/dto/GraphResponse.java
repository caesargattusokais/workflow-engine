package com.github.wf.server.dto;

import java.util.*;

public class GraphResponse {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    public GraphResponse() {}
    public GraphResponse(List<GraphNode> nodes, List<GraphEdge> edges) {
        this.nodes = nodes; this.edges = edges;
    }

    public List<GraphNode> getNodes() { return nodes; }
    public void setNodes(List<GraphNode> n) { this.nodes = n; }
    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> e) { this.edges = e; }

    public static class GraphNode {
        private String id, type;
        private double x, y;
        private Map<String, Object> data = new HashMap<>();

        public GraphNode() {}
        public GraphNode(String id, String type, double x, double y) {
            this.id = id; this.type = type; this.x = x; this.y = y;
        }
        public String getId() { return id; }
        public void setId(String i) { id = i; }
        public String getType() { return type; }
        public void setType(String t) { type = t; }
        public double getX() { return x; }
        public void setX(double xv) { x = xv; }
        public double getY() { return y; }
        public void setY(double yv) { y = yv; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> d) { data = d; }
    }

    public static class GraphEdge {
        private String id, source, target, type = "smoothstep";
        private String label;
        private Map<String, Object> data;

        public GraphEdge() {}
        public GraphEdge(String id, String source, String target) {
            this.id = id; this.source = source; this.target = target;
        }
        public String getId() { return id; }
        public void setId(String i) { id = i; }
        public String getSource() { return source; }
        public void setSource(String s) { source = s; }
        public String getTarget() { return target; }
        public void setTarget(String t) { target = t; }
        public String getType() { return type; }
        public void setType(String t) { type = t; }
        public String getLabel() { return label; }
        public void setLabel(String l) { label = l; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> d) { data = d; }
    }
}
