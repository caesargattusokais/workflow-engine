package com.github.wf.server.controller;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.DefinitionRepository;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.server.dto.GraphResponse;
import com.github.wf.server.dto.DeployRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/definitions")
@CrossOrigin(origins = "*")
public class DefinitionController {

    private final WorkflowEngine engine;
    private final DefinitionRepository repo;

    public DefinitionController(WorkflowEngine engine, DefinitionRepository repo) {
        this.engine = engine;
        this.repo = repo;
        engine.setProcessParser(new YamlProcessParser());
    }

    @PostMapping
    public ProcessDefinition deploy(@RequestHeader("X-User-Id") String userId, @RequestBody DeployRequest req) {
        ProcessDefinition def = engine.deploy(req.getYaml());
        repo.save(userId, def, req.getPositions());
        return def;
    }

    @GetMapping
    public Map<String, Object> list(@RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", repo.listLatestByUserPaginated(userId, page, size));
        result.put("page", page);
        result.put("size", size);
        result.put("total", repo.countByUser(userId));
        return result;
    }

    @GetMapping("/{id}")
    public ProcessDefinition get(@RequestHeader("X-User-Id") String userId, @PathVariable("id") String id) {
        ProcessDefinition def = repo.findByUserAndId(userId, id);
        if (def == null) throw new RuntimeException("Not found: " + id);
        return def;
    }

    @GetMapping("/{id}/graph")
    public GraphResponse graph(@RequestHeader("X-User-Id") String userId,
                                @PathVariable("id") String id,
                                @RequestParam(value = "version", required = false) Integer version) {
        ProcessDefinition def;
        if (version != null) {
            def = engine.processRepository.findAllVersions(id).stream()
                .filter(d -> d.getVersion() == version).findFirst().orElse(null);
        } else {
            def = engine.processRepository.findLatestById(id);
        }
        if (def == null) def = engine.processRepository.findLatestById(id);
        if (def == null) throw new RuntimeException("Not found: " + id + (version != null ? " v" + version : ""));
        return graphFromDef(def, repo.findPositions(userId, id, version));
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("X-User-Id") String userId, @PathVariable("id") String id) {
        repo.delete(userId, id);
    }

    public static GraphResponse graphFromDef(ProcessDefinition def, Map<String, Map<String, Double>> positions) {
        List<GraphResponse.GraphNode> nodes = new ArrayList<>();
        List<GraphResponse.GraphEdge> edges = new ArrayList<>();
        Map<String, Node> nodeMap = def.getNodes();
        List<String> nodeIds = new ArrayList<>(nodeMap.keySet());

        // Build adjacency for layered layout when positions are missing
        Map<String, List<String>> outEdges = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String nid : nodeIds) { outEdges.put(nid, new ArrayList<>()); inDegree.put(nid, 0); }
        for (Transition t : def.getTransitions()) {
            if (t.getTo() == null) continue;
            outEdges.computeIfAbsent(t.getFrom(), k -> new ArrayList<>()).add(t.getTo());
            inDegree.merge(t.getTo(), 1, Integer::sum);
        }

        // BFS topological layering for nodes without stored positions
        Map<String, Integer> layers = new LinkedHashMap<>();
        List<String> queue = new ArrayList<>();
        for (String nid : nodeIds) {
            if (inDegree.getOrDefault(nid, 0) == 0) { layers.put(nid, 0); queue.add(nid); }
        }
        if (queue.isEmpty() && !nodeIds.isEmpty()) { layers.put(nodeIds.get(0), 0); queue.add(nodeIds.get(0)); }
        while (!queue.isEmpty()) {
            String cur = queue.remove(0);
            int curLayer = layers.get(cur);
            for (String next : outEdges.getOrDefault(cur, List.of())) {
                int newLayer = curLayer + 1;
                if (!layers.containsKey(next) || newLayer > layers.get(next)) {
                    layers.put(next, newLayer);
                    if (!queue.contains(next)) queue.add(next);
                }
            }
        }
        int maxLayer = layers.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<Integer, List<String>> layerGroups = new LinkedHashMap<>();
        for (String nid : nodeIds) {
            int l = layers.getOrDefault(nid, maxLayer + 1);
            layerGroups.computeIfAbsent(l, k -> new ArrayList<>()).add(nid);
        }

        for (int i = 0; i < nodeIds.size(); i++) {
            String nid = nodeIds.get(i);
            Node n = nodeMap.get(nid);
            double x, y;
            if (positions != null && positions.containsKey(nid)) {
                var pos = positions.get(nid);
                x = pos.getOrDefault("x", 200.0);
                y = pos.getOrDefault("y", 50.0 + i * 120.0);
            } else {
                // Layered layout: layer→X, even spread→Y
                int layer = layers.getOrDefault(nid, 0);
                List<String> siblings = layerGroups.getOrDefault(layer, List.of(nid));
                int idx = siblings.indexOf(nid);
                int totalInLayer = siblings.size();
                x = 50 + layer * 220;
                y = 200 + idx * 100 - (totalInLayer - 1) * 50;
            }
            GraphResponse.GraphNode gn = new GraphResponse.GraphNode(nid, mapNodeType(n.getType()), x, y);
            Map<String, Object> data = new HashMap<>();
            data.put("name", n.getName() != null ? n.getName() : nid);
            data.put("listeners", n.getListeners());
            if (n instanceof UserTask ut) {
                data.put("assignee", ut.getAssignee());
                data.put("candidateGroups", ut.getCandidateGroups());
                data.put("dynamicRouter", ut.getDynamicRouter());
                data.put("boundaryTimer", ut.getBoundaryTimer());
                data.put("httpMode", ut.isHttpTask());
                data.put("url", ut.getUrl());
                data.put("method", ut.getMethod());
            } else if (n instanceof ServiceTask st) {
                data.put("handlerClass", st.getHandlerClass());
                data.put("httpMode", st.isHttpTask());
                data.put("url", st.getUrl());
                data.put("method", st.getMethod());
                if (st.getRetryConfig() != null) {
                    data.put("retryMaxAttempts", st.getRetryConfig().getMaxAttempts());
                    data.put("retryDelayMs", st.getRetryConfig().getDelayMs());
                    data.put("retryBackoff", st.getRetryConfig().getBackoffMultiplier());
                }
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
            // Carry edge type for frontend styling
            Map<String, Object> edgeData = new HashMap<>();
            if (t.isConditional()) {
                edgeData.put("edgeType", "conditional");
                if (t.getCondition() != null && t.getCondition().getExpr() != null)
                    ge.setLabel(t.getCondition().getExpr());
            } else if (t.isDefault()) {
                edgeData.put("edgeType", "default");
                ge.setLabel("default");
            } else if (t.isResult()) {
                edgeData.put("edgeType", "result");
            } else if (t.isException()) {
                edgeData.put("edgeType", "exception");
            } else if (t.isTimeout()) {
                edgeData.put("edgeType", "timeout");
            } else {
                edgeData.put("edgeType", "direct");
            }
            ge.setData(edgeData);
            // Arrow marker for direction
            ge.setMarkerEnd("arrowclosed");
            edges.add(ge);
        }
        return new GraphResponse(nodes, edges);
    }

    private static String mapNodeType(NodeType type) {
        return switch (type) {
            case START_EVENT -> "startEvent"; case END_EVENT -> "endEvent";
            case USER_TASK -> "userTask"; case SERVICE_TASK -> "serviceTask";
            case EXCLUSIVE_GATEWAY -> "exclusiveGateway"; case PARALLEL_GATEWAY -> "parallelGateway";
            case INCLUSIVE_GATEWAY -> "inclusiveGateway"; case TIMER -> "timer";
        };
    }
}
