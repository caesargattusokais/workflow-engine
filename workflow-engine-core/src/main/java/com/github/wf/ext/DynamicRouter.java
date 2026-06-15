package com.github.wf.ext;

import java.util.Map;

@FunctionalInterface
public interface DynamicRouter {
    String nextNode(String instanceId, String currentNodeId, Map<String, Object> variables);
}
