package com.github.wf.dsl;

import java.util.List;
import java.util.Map;

public class NodeYaml {
    public String id;
    public String type;
    public String name;
    public String assignee;
    public List<String> candidateGroups;
    public String dynamicRouter;
    public String handlerClass;
    public boolean httpMode;
    public String url;               // HTTP proxy mode
    public String method;             // GET/POST/PUT/DELETE
    public Map<String, String> headers;
    public String body;               // request body template
    public Map<String, String> listeners;
    public List<GatewayConditionYaml> conditions;
    public RetryYaml retry;
    public List<RouteYaml> resultRouting;
    public List<RouteYaml> exceptionRouting;
    public String duration;  // ISO 8601 duration
    public String until;     // ISO 8601 datetime (deadline)

    public static class RetryYaml {
        public int maxAttempts = 1;
        public long delayMs = 1000;
        public double backoffMultiplier = 2.0;
        public List<GatewayConditionYaml> retryOn;
    }

    public static class RouteYaml {
        public String expr;
        public String className;
        public boolean isDefault;
        public String to;
    }
}
