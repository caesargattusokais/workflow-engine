package com.github.wf.server.ai;

import com.github.wf.dsl.YamlProcessParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural validation for AI-generated workflow YAML. Runs the engine's own
 * {@link YamlProcessParser} (type validity, unknown fields) plus designer-level
 * checks the parser doesn't enforce (unique ids, dangling edges, start/end
 * presence, gateway default branch).
 */
public final class AiFlowValidator {

    private static final Set<String> NODE_TYPES = Set.of(
            "startEvent", "endEvent", "userTask", "serviceTask",
            "exclusiveGateway", "parallelGateway", "inclusiveGateway",
            "timer", "callActivity");

    private AiFlowValidator() {}

    /** @return all problems found; empty list = valid */
    public static List<String> validate(String yaml) {
        List<String> errors = new ArrayList<>();

        Map<String, Object> doc;
        try {
            org.yaml.snakeyaml.Yaml y = new org.yaml.snakeyaml.Yaml();
            Object parsed = y.load(yaml);
            if (!(parsed instanceof Map)) {
                return List.of("YAML did not parse into a mapping");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) parsed;
            doc = m;
        } catch (Exception e) {
            return List.of("YAML syntax error: " + e.getMessage());
        }

        // Engine's own parser — catches unknown node types / malformed blocks
        try {
            new YamlProcessParser().parse(yaml);
        } catch (Exception e) {
            errors.add("Engine parse error: " + e.getMessage());
            return errors;
        }

        Object nodesObj = doc.get("nodes");
        Object transObj = doc.get("transitions");
        if (!(nodesObj instanceof List<?> nodes) || nodes.isEmpty()) {
            errors.add("nodes must be a non-empty list");
            return errors;
        }

        Set<String> ids = new HashSet<>();
        int startCount = 0, endCount = 0;
        for (Object n : nodes) {
            if (!(n instanceof Map<?, ?> nm)) { errors.add("each node must be a mapping"); continue; }
            Object id = nm.get("id");
            Object type = nm.get("type");
            if (id == null || id.toString().isBlank()) { errors.add("a node is missing id"); continue; }
            String idStr = id.toString();
            if (!ids.add(idStr)) errors.add("duplicate node id: " + idStr);
            if (type == null || !NODE_TYPES.contains(type.toString())) {
                errors.add("unknown node type '" + type + "' on node " + idStr
                        + " — allowed: " + String.join(", ", NODE_TYPES));
                continue;
            }
            if ("startEvent".equals(type.toString())) startCount++;
            if ("endEvent".equals(type.toString())) endCount++;
        }

        if (startCount != 1) errors.add("exactly one startEvent is required (found " + startCount + ")");
        if (endCount < 1) errors.add("at least one endEvent is required");

        if (transObj instanceof List<?> transitions) {
            for (Object t : transitions) {
                if (!(t instanceof Map<?, ?> tm)) { errors.add("each transition must be a mapping"); continue; }
                Object from = tm.get("from"), to = tm.get("to");
                if (from == null || to == null) { errors.add("a transition is missing from/to"); continue; }
                if (!ids.contains(from.toString())) errors.add("transition references unknown node: " + from);
                if (!ids.contains(to.toString())) errors.add("transition references unknown node: " + to);
            }
        }

        // Gateways with branches must have a default escape (node-level conditions
        // or transition-level conditional edges — either form needs one)
        Map<String, Map<String, Object>> nodeById = new LinkedHashMap<>();
        for (Object n : nodes) {
            if (n instanceof Map<?, ?> nm && nm.get("id") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) nm;
                nodeById.put(m.get("id").toString(), m);
            }
        }
        Set<String> gatewayWithBranch = new HashSet<>();
        Set<String> hasDefault = new HashSet<>();
        for (Map.Entry<String, Map<String, Object>> e : nodeById.entrySet()) {
            String type = e.getValue().get("type") == null ? "" : e.getValue().get("type").toString();
            if (!type.equals("exclusiveGateway") && !type.equals("inclusiveGateway")) continue;
            if (e.getValue().get("conditions") instanceof List<?> conds && !conds.isEmpty()) {
                gatewayWithBranch.add(e.getKey());
                for (Object c : conds) {
                    if (c instanceof Map<?, ?> cm && Boolean.TRUE.equals(cm.get("default"))) {
                        hasDefault.add(e.getKey());
                    }
                }
            }
        }
        if (transObj instanceof List<?> transitions) {
            for (Object t : transitions) {
                if (!(t instanceof Map<?, ?> tm)) continue;
                Object from = tm.get("from");
                if (from == null) continue;
                if ("conditional".equals(tm.get("type"))) gatewayWithBranch.add(from.toString());
                if (Boolean.TRUE.equals(tm.get("default"))) hasDefault.add(from.toString());
            }
        }
        for (String gw : gatewayWithBranch) {
            if (!hasDefault.contains(gw)) {
                errors.add("gateway '" + gw + "' has conditional branches but no default branch "
                        + "(node-level conditions must include {default: true, to: ...})");
            }
        }

        return errors;
    }

    /** Extract a name for display — best effort, used for draft naming. */
    public static String extractName(String yaml) {
        try {
            Object parsed = new org.yaml.snakeyaml.Yaml().load(yaml);
            if (parsed instanceof Map<?, ?> m && m.get("name") != null) {
                return m.get("name").toString();
            }
        } catch (Exception ignored) { }
        return "AI 生成流程";
    }

    /** Node count — used in the response for a quick summary. */
    public static int nodeCount(String yaml) {
        try {
            Object parsed = new org.yaml.snakeyaml.Yaml().load(yaml);
            if (parsed instanceof Map<?, ?> m && m.get("nodes") instanceof List<?> l) return l.size();
        } catch (Exception ignored) { }
        return 0;
    }
}
