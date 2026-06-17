package com.github.wf.server.controller;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
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
    private final Map<String, ProcessDefinition> store = new LinkedHashMap<>();
    private final Map<String, Map<String, Map<String, Double>>> positionsStore = new LinkedHashMap<>();

    public DefinitionController(WorkflowEngine engine) {
        this.engine = engine;
        engine.setProcessParser(new YamlProcessParser());
    }

    @PostMapping
    public ProcessDefinition deploy(@RequestBody DeployRequest req) {
        ProcessDefinition def = engine.deploy(req.getYaml());
        store.put(def.getId(), def);
        if (req.getPositions() != null) {
            positionsStore.put(def.getId(), req.getPositions());
        }
        return def;
    }

    @GetMapping
    public List<ProcessDefinition> list() {
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ProcessDefinition get(@PathVariable("id") String id) {
        ProcessDefinition def = store.get(id);
        if (def == null) throw new RuntimeException("Not found: " + id);
        return def;
    }

    @GetMapping("/{id}/graph")
    public GraphResponse graph(@PathVariable("id") String id) {
        ProcessDefinition def = store.get(id);
        if (def == null) throw new RuntimeException("Not found: " + id);
        return convertToGraph(def, positionsStore.get(id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") String id) {
        store.remove(id);
        positionsStore.remove(id);
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
            // Use stored positions or auto-layout
            double x, y;
            if (positions != null && positions.containsKey(nid)) {
                var pos = positions.get(nid);
                x = pos.getOrDefault("x", 200.0);
                y = pos.getOrDefault("y", 50.0 + i * 120.0);
            } else {
                x = 200;
                y = 50 + i * 120;
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
            }
            gn.setData(data);
            nodes.add(gn);
        }

        for (Transition t : def.getTransitions()) {
            if (t.getTo() == null) continue;
            GraphResponse.GraphEdge ge = new GraphResponse.GraphEdge(
                    "e-" + t.getId(), t.getFrom(), t.getTo());
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
        };
    }
}
