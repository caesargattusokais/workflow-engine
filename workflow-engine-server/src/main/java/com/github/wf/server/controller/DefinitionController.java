package com.github.wf.server.controller;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.server.dto.GraphResponse;
import com.github.wf.server.dto.DeployRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/definitions")
@CrossOrigin(origins = "*")
public class DefinitionController {

    private final WorkflowEngine engine;
    private final JdbcTemplate jdbc;
    private static final Gson gson = new Gson();

    public DefinitionController(WorkflowEngine engine, JdbcTemplate jdbc) {
        this.engine = engine;
        this.jdbc = jdbc;
        engine.setProcessParser(new YamlProcessParser());
    }

    @PostMapping
    public ProcessDefinition deploy(@RequestHeader("X-User-Id") String userId,
                                    @RequestBody DeployRequest req) {
        ProcessDefinition def = engine.deploy(req.getYaml());
        String positionsJson = req.getPositions() != null ? gson.toJson(req.getPositions()) : null;
        jdbc.update(
            "INSERT INTO definition (id, version, user_id, name, positions_json) VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE name = VALUES(name), positions_json = VALUES(positions_json)",
            def.getId(), def.getVersion(), userId, def.getName(), positionsJson);
        return def;
    }

    @GetMapping
    public List<ProcessDefinition> list(@RequestHeader("X-User-Id") String userId) {
        Map<String, ProcessDefinition> latest = new LinkedHashMap<>();
        jdbc.query(
            "SELECT id, version, name FROM definition WHERE user_id = ? ORDER BY id, version DESC",
            (rs) -> {
                String id = rs.getString("id");
                latest.putIfAbsent(id, new ProcessDefinition(id, rs.getString("name"), rs.getInt("version"), List.of(), List.of()));
            }, userId);
        return new ArrayList<>(latest.values());
    }

    @GetMapping("/{id}")
    public ProcessDefinition get(@RequestHeader("X-User-Id") String userId,
                                  @PathVariable("id") String id) {
        List<ProcessDefinition> list = jdbc.query(
            "SELECT id, version, name FROM definition WHERE user_id = ? AND id = ? ORDER BY version DESC LIMIT 1",
            (rs, rowNum) -> new ProcessDefinition(rs.getString("id"), rs.getString("name"),
                rs.getInt("version"), List.of(), List.of()),
            userId, id);
        if (list.isEmpty()) throw new RuntimeException("Not found: " + id);
        return list.get(0);
    }

    @GetMapping("/{id}/graph")
    public GraphResponse graph(@RequestHeader("X-User-Id") String userId,
                                @PathVariable("id") String id,
                                @RequestParam(value = "version", required = false) Integer version) {
        ProcessDefinition def;
        Map<String, Map<String, Double>> positions = null;
        if (version != null) {
            def = engine.processRepository.findLatestById(id); // fallback
        } else {
            def = engine.processRepository.findLatestById(id);
        }
        if (def == null) throw new RuntimeException("Not found: " + id + (version != null ? " v" + version : ""));
        // Load positions from DB
        String sql = version != null
            ? "SELECT positions_json FROM definition WHERE user_id = ? AND id = ? AND version = ?"
            : "SELECT positions_json FROM definition WHERE user_id = ? AND id = ? ORDER BY version DESC LIMIT 1";
        List<Object> params = new ArrayList<>(List.of(userId, id));
        if (version != null) params.add(version);
        List<String> posList = jdbc.query(sql, (rs, rowNum) -> rs.getString("positions_json"), params.toArray());
        String posJson = posList.isEmpty() ? null : posList.get(0);
        if (posJson != null) {
            positions = gson.fromJson(posJson, new TypeToken<Map<String, Map<String, Double>>>() {}.getType());
        }
        return convertToGraph(def, positions);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("X-User-Id") String userId,
                       @PathVariable("id") String id) {
        jdbc.update("DELETE FROM definition WHERE user_id = ? AND id = ?", userId, id);
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
