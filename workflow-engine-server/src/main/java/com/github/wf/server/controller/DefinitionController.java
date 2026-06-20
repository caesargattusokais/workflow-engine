package com.github.wf.server.controller;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.server.dto.GraphResponse;
import com.github.wf.server.dto.DeployRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/definitions")
@CrossOrigin(origins = "*")
public class DefinitionController {

    private final WorkflowEngine engine;
    private final Map<String, ProcessDefinition> store = new LinkedHashMap<>();
    private final Map<String, Map<String, Map<String, Double>>> positionsStore = new LinkedHashMap<>();

    public DefinitionController(WorkflowEngine engine) {
        this.engine = engine;
        engine.setProcessParser(new YamlProcessParser());
    }

    private String key(String userId, String id) { return userId + ":" + id; }
    private String versionedKey(String userId, String id, int version) { return userId + ":" + id + ":" + version; }

    @PostMapping
    public ProcessDefinition deploy(@RequestHeader("X-User-Id") String userId,
                                    @RequestBody DeployRequest req) {
        ProcessDefinition def = engine.deploy(req.getYaml());
        // Store by version so old instances see their original graph
        store.put(versionedKey(userId, def.getId(), def.getVersion()), def);
        // Also update latest reference
        store.put(key(userId, def.getId()), def);
        if (req.getPositions() != null) {
            positionsStore.put(versionedKey(userId, def.getId(), def.getVersion()), req.getPositions());
            positionsStore.put(key(userId, def.getId()), req.getPositions());
        }
        return def;
    }

    @GetMapping
    public List<ProcessDefinition> list(@RequestHeader("X-User-Id") String userId) {
        // Deduplicate by id, keep latest version
        Map<String, ProcessDefinition> latest = new LinkedHashMap<>();
        String prefix = userId + ":";
        store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix) && !e.getKey().contains(":"))
                .forEach(e -> latest.put(e.getValue().getId(), e.getValue()));
        // Also include versioned entries to find latest
        store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix) && e.getKey().contains(":"))
                .forEach(e -> {
                    ProcessDefinition def = e.getValue();
                    latest.merge(def.getId(), def, (a, b) -> a.getVersion() >= b.getVersion() ? a : b);
                });
        return new ArrayList<>(latest.values());
    }

    @GetMapping("/{id}")
    public ProcessDefinition get(@RequestHeader("X-User-Id") String userId,
                                  @PathVariable("id") String id) {
        ProcessDefinition def = store.get(key(userId, id));
        if (def == null) throw new RuntimeException("Not found: " + id);
        return def;
    }

    @GetMapping("/{id}/graph")
    public GraphResponse graph(@RequestHeader("X-User-Id") String userId,
                                @PathVariable("id") String id,
                                @RequestParam(value = "version", required = false) Integer version) {
        ProcessDefinition def;
        String posKey;
        if (version != null) {
            def = store.get(versionedKey(userId, id, version));
            posKey = versionedKey(userId, id, version);
        } else {
            def = store.get(key(userId, id));
            posKey = key(userId, id);
        }
        if (def == null) throw new RuntimeException("Not found: " + id + (version != null ? " v" + version : ""));
        return convertToGraph(def, positionsStore.get(posKey));
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("X-User-Id") String userId,
                       @PathVariable("id") String id) {
        store.remove(key(userId, id));
        positionsStore.remove(key(userId, id));
    }

    private GraphResponse convertToGraph(ProcessDefinition def, Map<String, Map<String, Double>> positions) {
        List<GraphResponse.GraphNode> nodes = new ArrayList<>();
        List<GraphResponse.GraphEdge> edges = new ArrayList<>();
        Map<String, Node> nodeMap = def.getNodes();
        List<String> nodeIds = new ArrayList<>(nodeMap.keySet());

        for (int i = 0; i < nodeIds.size(); i++) {
            String nid = nodeIds.get(i);
            Node n = nodeMap.get(nid);
            String rft = mapNodeType(n.getType());
            double x, y;
            if (positions != null && positions.containsKey(nid)) {
                var pos = positions.get(nid);
                x = pos.getOrDefault("x", 200.0);
                y = pos.getOrDefault("y", 50.0 + i * 120.0);
            } else {
                x = 200; y = 50 + i * 120;
            }
            GraphResponse.GraphNode gn = new GraphResponse.GraphNode(nid, rft, x, y);
            Map<String, Object> data = new HashMap<>();
            data.put("name", n.getName() != null ? n.getName() : nid);
            data.put("listeners", n.getListeners());
            if (n instanceof UserTask ut) {
                data.put("assignee", ut.getAssignee());
                data.put("candidateGroups", ut.getCandidateGroups());
                data.put("dynamicRouter", ut.getDynamicRouter());
            } else if (n instanceof ServiceTask st) {
                data.put("handlerClass", st.getHandlerClass());
            } else if (n instanceof TimerNode tn) {
                data.put("duration", tn.getDuration());
                data.put("deadline", tn.getDeadline());
            }
            gn.setData(data);
            nodes.add(gn);
        }
        for (Transition t : def.getTransitions()) {
            if (t.getTo() == null) continue;
            GraphResponse.GraphEdge ge = new GraphResponse.GraphEdge("e-" + t.getId(), t.getFrom(), t.getTo());
            if (t.isConditional() && t.getCondition() != null && t.getCondition().getExpr() != null) {
                ge.setLabel(t.getCondition().getExpr());
            } else if (t.isDefault()) {
                ge.setLabel("default");
            }
            edges.add(ge);
        }
        return new GraphResponse(nodes, edges);
    }

    private String mapNodeType(NodeType type) {
        return switch (type) {
            case START_EVENT -> "startEvent";
            case END_EVENT -> "endEvent";
            case USER_TASK -> "userTask";
            case SERVICE_TASK -> "serviceTask";
            case EXCLUSIVE_GATEWAY -> "exclusiveGateway";
            case PARALLEL_GATEWAY -> "parallelGateway";
            case INCLUSIVE_GATEWAY -> "inclusiveGateway";
            case TIMER -> "timer";
        };
    }
}
