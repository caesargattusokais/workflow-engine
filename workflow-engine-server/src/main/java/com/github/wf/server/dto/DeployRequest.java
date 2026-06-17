package com.github.wf.server.dto;

import java.util.Map;

public class DeployRequest {
    private String yaml;
    private Map<String, Map<String, Double>> positions; // nodeId → {x, y}

    public String getYaml() { return yaml; }
    public void setYaml(String y) { this.yaml = y; }
    public Map<String, Map<String, Double>> getPositions() { return positions; }
    public void setPositions(Map<String, Map<String, Double>> p) { this.positions = p; }
}
