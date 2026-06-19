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
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-backed ProcessRepository with write-through cache.
 */
public class JdbcProcessRepository implements ProcessRepository {

    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();
    private final Map<String, ProcessDefinition> cache = new ConcurrentHashMap<>();
    private final Map<String, Integer> latestVersion = new ConcurrentHashMap<>();

    public JdbcProcessRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.query("SELECT * FROM process_definition", (rs) -> {
            String id = rs.getString("id");
            int version = rs.getInt("version");
            String name = rs.getString("name");
            ProcessDefinition def = buildDef(id, version, name,
                rs.getString("nodes_json"), rs.getString("transitions_json"));
            String key = id + ":" + version;
            cache.put(key, def);
            latestVersion.merge(id, version, Math::max);
        });
    }

    @Override
    public void save(ProcessDefinition def) {
        String nodesJson = gson.toJson(serializeNodes(def.getNodes()));
        String transitionsJson = gson.toJson(serializeTransitions(def.getTransitions()));
        String key = def.getId() + ":" + def.getVersion();
        cache.put(key, def);
        latestVersion.merge(def.getId(), def.getVersion(), Math::max);
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
        Integer v = latestVersion.get(id);
        return v != null ? cache.get(id + ":" + v) : null;
    }

    @Override
    public List<ProcessDefinition> findAllVersions(String id) {
        return cache.entrySet().stream()
            .filter(e -> e.getKey().startsWith(id + ":"))
            .map(Map.Entry::getValue)
            .sorted(Comparator.comparingInt(ProcessDefinition::getVersion))
            .toList();
    }

    // -- Serialization helpers (same as before) --

    private ProcessDefinition buildDef(String id, int version, String name,
                                        String nodesJson, String transitionsJson) {
        Map<String, Map<String, Object>> nodeMap = gson.fromJson(nodesJson,
            new TypeToken<Map<String, Map<String, Object>>>() {}.getType());
        List<Map<String, Object>> transList = gson.fromJson(transitionsJson,
            new TypeToken<List<Map<String, Object>>>() {}.getType());

        List<Node> nodes = new ArrayList<>();
        if (nodeMap != null) {
            for (var entry : nodeMap.entrySet()) {
                nodes.add(deserializeNode(entry.getKey(), entry.getValue()));
            }
        }

        List<Transition> transitions = new ArrayList<>();
        if (transList != null) {
            for (var t : transList) {
                String from = (String) t.get("from");
                String to = (String) t.get("to");
                String type = (String) t.getOrDefault("type", "DIRECT");
                String expr = (String) t.get("expr");
                String conditionClass = (String) t.get("conditionClass");
                transitions.add(buildTransition(from, to, type, expr, conditionClass));
            }
        }
        return new ProcessDefinition(id, name, version, nodes, transitions);
    }

    private Transition buildTransition(String from, String to, String type, String expr, String className) {
        switch (type) {
            case "CONDITIONAL": {
                var cond = className != null ? com.github.wf.model.Condition.javaClass(className)
                    : com.github.wf.model.Condition.expression(expr);
                return Transition.conditional(from, cond).withTo(to);
            }
            case "DEFAULT": return Transition.defaultTransition(from, to);
            case "RESULT": {
                var cond = className != null ? com.github.wf.model.Condition.javaClass(className)
                    : expr != null ? com.github.wf.model.Condition.expression(expr) : null;
                return Transition.result(from, to, cond);
            }
            case "EXCEPTION": {
                var cond = className != null ? com.github.wf.model.Condition.javaClass(className)
                    : expr != null ? com.github.wf.model.Condition.expression(expr) : null;
                return Transition.exception(from, to, cond);
            }
            case "TIMEOUT": return Transition.timeout(from, to);
            default: return Transition.direct(from, to);
        }
    }

    private Map<String, Map<String, Object>> serializeNodes(Map<String, Node> nodes) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (var entry : nodes.entrySet()) {
            Node n = entry.getValue();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", n.getId()); data.put("name", n.getName());
            data.put("type", n.getType().name()); data.put("listeners", n.getListeners());
            if (n instanceof UserTask ut) {
                data.put("assignee", ut.getAssignee());
                data.put("candidateGroups", ut.getCandidateGroups());
                data.put("dynamicRouter", ut.getDynamicRouter());
                data.put("boundaryTimer", ut.getBoundaryTimer());
                data.put("httpMode", ut.isHttpTask());
                data.put("url", ut.getUrl()); data.put("method", ut.getMethod());
                data.put("headers", ut.getHeaders()); data.put("body", ut.getBody());
            } else if (n instanceof ServiceTask st) {
                data.put("handlerClass", st.getHandlerClass());
                data.put("httpMode", st.isHttpTask());
                data.put("url", st.getUrl()); data.put("method", st.getMethod());
                data.put("headers", st.getHeaders()); data.put("body", st.getBody());
            } else if (n instanceof TimerNode tn) {
                data.put("duration", tn.getDuration());
                data.put("deadline", tn.getDeadline());
            }
            map.put(entry.getKey(), data);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Node deserializeNode(String id, Map<String, Object> data) {
        String type = (String) data.get("type");
        String name = (String) data.get("name");
        List<String> listeners = data.containsKey("listeners") ?
            (List<String>) data.get("listeners") : List.of();
        switch (type) {
            case "START_EVENT": return new StartEvent(id, name, listeners);
            case "END_EVENT": return new EndEvent(id, name, listeners);
            case "USER_TASK": {
                String assignee = (String) data.get("assignee");
                List<String> cg = (List<String>) data.get("candidateGroups");
                String dr = (String) data.get("dynamicRouter");
                String bt = (String) data.get("boundaryTimer");
                boolean hm = Boolean.TRUE.equals(data.get("httpMode"));
                String url = (String) data.get("url");
                String method = (String) data.get("method");
                Map<String, String> headers = (Map<String, String>) data.get("headers");
                String body = (String) data.get("body");
                return new UserTask(id, name, assignee, cg, dr, bt, hm, url, method, headers, body, listeners);
            }
            case "SERVICE_TASK": {
                String hc = (String) data.get("handlerClass");
                boolean hm = Boolean.TRUE.equals(data.get("httpMode"));
                String url = (String) data.get("url");
                String method = (String) data.get("method");
                Map<String, String> headers = (Map<String, String>) data.get("headers");
                String body = (String) data.get("body");
                return new ServiceTask(id, name, hc, hm, url, method, headers, body,
                    null, List.of(), List.of(), listeners);
            }
            case "EXCLUSIVE_GATEWAY": return new ExclusiveGateway(id, name, listeners);
            case "PARALLEL_GATEWAY": return new ParallelGateway(id, name, listeners);
            case "INCLUSIVE_GATEWAY": return new InclusiveGateway(id, name, listeners);
            case "TIMER": {
                String dur = (String) data.get("duration");
                String until = (String) data.get("deadline");
                return new TimerNode(id, name, dur, until, listeners);
            }
            default: throw new IllegalArgumentException("Unknown node type: " + type);
        }
    }

    private List<Map<String, Object>> serializeTransitions(List<Transition> transitions) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Transition t : transitions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", t.getFrom()); m.put("to", t.getTo());
            m.put("type", t.getType().name());
            if (t.getCondition() != null) {
                if (t.getCondition().getExpr() != null) m.put("expr", t.getCondition().getExpr());
                if (t.getCondition().getClassName() != null) m.put("conditionClass", t.getCondition().getClassName());
            }
            list.add(m);
        }
        return list;
    }
}
