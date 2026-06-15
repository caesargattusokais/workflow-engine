package com.github.wf.ext;

import java.util.Map;

public interface ProcessListener {
    default void onNodeEnter(String instanceId, String nodeId, Map<String, Object> variables) {}
    default void onNodeLeave(String instanceId, String nodeId, Map<String, Object> variables) {}
}
