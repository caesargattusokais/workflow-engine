package com.github.wf.memory;

import com.github.wf.model.Node;
import com.github.wf.model.ProcessDefinition;
import com.github.wf.model.Transition;
import com.github.wf.model.node.*;
import com.github.wf.spi.ProcessRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * JDBC-backed ProcessRepository — no cache needed.
 * Definitions are immutable after deploy, direct DB reads are fine.
 */
public class JdbcProcessRepository implements ProcessRepository {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();

    public JdbcProcessRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(ProcessDefinition def) {
        String nodesJson = gson.toJson(serializeNodes(def.getNodes()));
        String transitionsJson = gson.toJson(serializeTransitions(def.getTransitions()));
        jdbc.update(
            "INSERT INTO process_definition (id, version, name, nodes_json, transitions_json) " +
            "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name=VALUES(name), nodes_json=VALUES(nodes_json), transitions_json=VALUES(transitions_json)",
            def.getId(), def.getVersion(), def.getName(), nodesJson, transitionsJson);
    }

    @Override
    public ProcessDefinition findById(String id) {
        return findLatestById(id);
    }

    @Override
    public ProcessDefinition findLatestById(String id) {
        List<ProcessDefinition> list = jdbc.query(
            "SELECT version, name, nodes_json, transitions_json FROM process_definition WHERE id = ? ORDER BY version DESC LIMIT 1",
            (rs, rowNum) -> buildDef(id, rs.getInt("version"), rs.getString("name"),
                rs.getString("nodes_json"), rs.getString("transitions_json")),
            id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<ProcessDefinition> findAllVersions(String id) {
        return jdbc.query(
            "SELECT version, name, nodes_json, transitions_json FROM process_definition WHERE id = ? ORDER BY version ASC",
            (rs, rowNum) -> buildDef(id, rs.getInt("version"), rs.getString("name"),
                rs.getString("nodes_json"), rs.getString("transitions_json")),
            id);
    }

    // -- Build helpers --

    private ProcessDefinition buildDef(String id, int version, String name,
                                        String nodesJson, String transitionsJson) {
        Map<String, Map<String, Object>> nodeMap = gson.fromJson(nodesJson,
            new TypeToken<Map<String, Map<String, Object>>>() {}.getType());
        List<Map<String, Object>> transList = gson.fromJson(transitionsJson,
            new TypeToken<List<Map<String, Object>>>() {}.getType());

        List<Node> nodes = new ArrayList<>();
        if (nodeMap != null) {
            for (var entry : nodeMap.entrySet())
                nodes.add(deserializeNode(entry.getKey(), entry.getValue()));
        }

        List<Transition> transitions = new ArrayList<>();
        if (transList != null) {
            for (var t : transList) {
                String from = (String) t.get("from");
                String to = (String) t.get("to");
                String type = (String) t.getOrDefault("type", "DIRECT");
                String expr = (String) t.get("expr");
                String cc = (String) t.get("conditionClass");
                transitions.add(buildTransition(from, to, type, expr, cc));
            }
        }
        return new ProcessDefinition(id, name, version, nodes, transitions);
    }

    private Transition buildTransition(String from, String to, String type, String expr, String className) {
        switch (type) {
            case "CONDITIONAL": {
                var c = className != null ? com.github.wf.model.Condition.javaClass(className)
                        : com.github.wf.model.Condition.expression(expr);
                return Transition.conditional(from, c).withTo(to);
            }
            case "DEFAULT": return Transition.defaultTransition(from, to);
            case "RESULT": {
                var c = className != null ? com.github.wf.model.Condition.javaClass(className)
                        : expr != null ? com.github.wf.model.Condition.expression(expr) : null;
                return Transition.result(from, to, c);
            }
            case "EXCEPTION": {
                var c = className != null ? com.github.wf.model.Condition.javaClass(className)
                        : expr != null ? com.github.wf.model.Condition.expression(expr) : null;
                return Transition.exception(from, to, c);
            }
            case "TIMEOUT": return Transition.timeout(from, to);
            default: return Transition.direct(from, to);
        }
    }

    @SuppressWarnings("unchecked")
    private Node deserializeNode(String id, Map<String, Object> d) {
        String type = (String) d.get("type");
        String name = (String) d.get("name");
        List<String> listeners = d.containsKey("listeners") ? (List<String>) d.get("listeners") : List.of();
        switch (type) {
            case "START_EVENT": return new StartEvent(id, name, listeners);
            case "END_EVENT": return new EndEvent(id, name, listeners);
            case "USER_TASK": return new UserTask(id, name, (String) d.get("assignee"),
                (List<String>) d.get("candidateGroups"), (String) d.get("dynamicRouter"),
                (String) d.get("boundaryTimer"), Boolean.TRUE.equals(d.get("httpMode")),
                (String) d.get("url"), (String) d.get("method"),
                (Map<String, String>) d.get("headers"), (String) d.get("body"), listeners);
            case "SERVICE_TASK": return new ServiceTask(id, name, (String) d.get("handlerClass"),
                Boolean.TRUE.equals(d.get("httpMode")), (String) d.get("url"), (String) d.get("method"),
                (Map<String, String>) d.get("headers"), (String) d.get("body"),
                null, List.of(), List.of(), listeners);
            case "EXCLUSIVE_GATEWAY": return new ExclusiveGateway(id, name, listeners);
            case "PARALLEL_GATEWAY": return new ParallelGateway(id, name, listeners);
            case "INCLUSIVE_GATEWAY": return new InclusiveGateway(id, name, listeners);
            case "TIMER": return new TimerNode(id, name, (String) d.get("duration"), (String) d.get("deadline"), listeners);
            default: throw new IllegalArgumentException("Unknown: " + type);
        }
    }

    private Map<String, Map<String, Object>> serializeNodes(Map<String, Node> nodes) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        for (var e : nodes.entrySet()) {
            Node n = e.getValue();
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", n.getId()); d.put("name", n.getName());
            d.put("type", n.getType().name()); d.put("listeners", n.getListeners());
            if (n instanceof UserTask ut) { d.put("assignee", ut.getAssignee()); d.put("candidateGroups", ut.getCandidateGroups()); d.put("dynamicRouter", ut.getDynamicRouter()); d.put("boundaryTimer", ut.getBoundaryTimer()); d.put("httpMode", ut.isHttpTask()); d.put("url", ut.getUrl()); d.put("method", ut.getMethod()); d.put("headers", ut.getHeaders()); d.put("body", ut.getBody()); }
            else if (n instanceof ServiceTask st) { d.put("handlerClass", st.getHandlerClass()); d.put("httpMode", st.isHttpTask()); d.put("url", st.getUrl()); d.put("method", st.getMethod()); d.put("headers", st.getHeaders()); d.put("body", st.getBody()); }
            else if (n instanceof TimerNode tn) { d.put("duration", tn.getDuration()); d.put("deadline", tn.getDeadline()); }
            m.put(e.getKey(), d);
        }
        return m;
    }

    private List<Map<String, Object>> serializeTransitions(List<Transition> transitions) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Transition t : transitions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", t.getFrom()); m.put("to", t.getTo()); m.put("type", t.getType().name());
            if (t.getCondition() != null) {
                if (t.getCondition().getExpr() != null) m.put("expr", t.getCondition().getExpr());
                if (t.getCondition().getClassName() != null) m.put("conditionClass", t.getCondition().getClassName());
            }
            list.add(m);
        }
        return list;
    }
}
