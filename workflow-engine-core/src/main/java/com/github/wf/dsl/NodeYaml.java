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
    public String boundaryTimer; // ISO 8601 duration for UserTask boundary timer
    public String calledId;
    public Integer calledVersion;
    public boolean async;
    public List<VariableMappingYaml> inputMapping;
    public List<VariableMappingYaml> outputMapping;
    public List<FormFieldYaml> formFields; // userTask completion form

    /** A field in a userTask completion form. */
    public static class FormFieldYaml {
        public String key;
        public String label;
        public String type;       // text | textarea | number | select | date
        public boolean required;
        public List<String> options;
        public String placeholder;
    }

    /** Convert DSL form fields to model objects (null-safe). */
    public static List<com.github.wf.model.node.FormField> buildFormFields(List<FormFieldYaml> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<com.github.wf.model.node.FormField> out = new java.util.ArrayList<>();
        for (FormFieldYaml f : raw) {
            if (f == null || f.key == null || f.key.isBlank()) continue;
            out.add(new com.github.wf.model.node.FormField(
                    f.key, f.label, f.type, f.required, f.options, f.placeholder));
        }
        return out;
    }

    // AI_TASK fields
    public String mode;       // summary | extract | classify
    public String prompt;     // user prompt, ${var} placeholders resolved at runtime
    public String outputVar;  // optional global variable for the whole result
    public List<String> options; // candidate categories for classify mode

    public static class VariableMappingYaml {
        public String from;
        public String to;
        public String expr;
    }

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
