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
    public List<ProcessDefinition> list(@RequestHeader("X-User-Id") String userId) {
        return repo.listLatestByUser(userId);
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
        return convertToGraph(def, repo.findPositions(userId, id, version));
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("X-User-Id") String userId, @PathVariable("id") String id) {
        repo.delete(userId, id);
    }

    private GraphResponse convertToGraph(ProcessDefinition def, Map<String, Map<String, Double>> positions) {
        List<GraphResponse.GraphNode> nodes = new ArrayList<>();
        List<GraphResponse.GraphEdge> edges = new ArrayList<>();
        Map<String, Node> nodeMap = def.getNodes();
        List<String> nodeIds = new ArrayList<>(nodeMap.keySet());

        for (int i = 0; i < nodeIds.size(); i++) {
            String nid = nodeIds.get(i);
            Node n = nodeMap.get(nid);
            double x, y;
            if (positions != null && positions.containsKey(nid)) {
                var pos = positions.get(nid);
                x = pos.getOrDefault("x", 200.0);
                y = pos.getOrDefault("y", 50.0 + i * 120.0);
            } else { x = 200; y = 50 + i * 120; }
            GraphResponse.GraphNode gn = new GraphResponse.GraphNode(nid, mapNodeType(n.getType()), x, y);
            Map<String, Object> data = new HashMap<>();
            data.put("name", n.getName() != null ? n.getName() : nid);
            data.put("listeners", n.getListeners());
            if (n instanceof UserTask ut) {
                data.put("assignee", ut.getAssignee()); data.put("candidateGroups", ut.getCandidateGroups());
            } else if (n instanceof ServiceTask st) {
                data.put("handlerClass", st.getHandlerClass());
            } else if (n instanceof TimerNode tn) {
                data.put("duration", tn.getDuration()); data.put("deadline", tn.getDeadline());
            }
            gn.setData(data);
            nodes.add(gn);
        }
        for (Transition t : def.getTransitions()) {
            if (t.getTo() == null) continue;
            GraphResponse.GraphEdge ge = new GraphResponse.GraphEdge("e-" + t.getId(), t.getFrom(), t.getTo());
            if (t.isConditional() && t.getCondition() != null && t.getCondition().getExpr() != null)
                ge.setLabel(t.getCondition().getExpr());
            else if (t.isDefault()) ge.setLabel("default");
            edges.add(ge);
        }
        return new GraphResponse(nodes, edges);
    }

    private String mapNodeType(NodeType type) {
        return switch (type) {
            case START_EVENT -> "startEvent"; case END_EVENT -> "endEvent";
            case USER_TASK -> "userTask"; case SERVICE_TASK -> "serviceTask";
            case EXCLUSIVE_GATEWAY -> "exclusiveGateway"; case PARALLEL_GATEWAY -> "parallelGateway";
            case INCLUSIVE_GATEWAY -> "inclusiveGateway"; case TIMER -> "timer";
        };
    }
}
