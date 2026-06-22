package com.github.wf.model;

import java.util.*;

/** Aggregated instance statistics — populated via SQL or in-memory streams. */
public class InstanceStats {
    private long total, running, completed, suspended, terminated;
    private double avgDurationMs;
    private Map<String, Map<String, Long>> byDefinition = new LinkedHashMap<>();

    // Getters
    public long getTotal() { return total; } public void setTotal(long v) { total = v; }
    public long getRunning() { return running; } public void setRunning(long v) { running = v; }
    public long getCompleted() { return completed; } public void setCompleted(long v) { completed = v; }
    public long getSuspended() { return suspended; } public void setSuspended(long v) { suspended = v; }
    public long getTerminated() { return terminated; } public void setTerminated(long v) { terminated = v; }
    public double getAvgDurationMs() { return avgDurationMs; } public void setAvgDurationMs(double v) { avgDurationMs = v; }
    public Map<String, Map<String, Long>> getByDefinition() { return byDefinition; }
    public void setByDefinition(Map<String, Map<String, Long>> v) { byDefinition = v; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total); m.put("running", running);
        m.put("completed", completed); m.put("suspended", suspended);
        m.put("terminated", terminated); m.put("avgDurationMs", Math.round(avgDurationMs));
        m.put("byDefinition", byDefinition);
        return m;
    }
}
