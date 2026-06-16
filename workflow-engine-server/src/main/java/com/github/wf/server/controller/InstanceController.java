package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.*;
import com.github.wf.server.dto.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/instances")
@CrossOrigin(origins = "*")
public class InstanceController {

    private final WorkflowEngine engine;

    public InstanceController(WorkflowEngine engine) { this.engine = engine; }

    @PostMapping
    public InstanceDetailResponse start(@RequestBody StartInstanceRequest req) {
        ProcessInstance inst = engine.start(req.getDefinitionId(),
                req.getVariables() != null ? req.getVariables() : Map.of());
        return new InstanceDetailResponse(inst);
    }

    @GetMapping
    public List<InstanceDetailResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String definitionId) {
        // Find all instances; filter by definitionId if provided
        return engine.instanceRepository.findByDefinitionId(
                definitionId != null ? definitionId : "")
                .stream()
                .filter(i -> status == null || i.getStatus().name().equals(status))
                .map(InstanceDetailResponse::new)
                .toList();
    }

    @GetMapping("/{id}")
    public InstanceDetailResponse get(@PathVariable String id) {
        ProcessInstance inst = engine.instanceRepository.findById(id);
        if (inst == null) throw new RuntimeException("Not found: " + id);
        return new InstanceDetailResponse(inst);
    }

    @GetMapping("/{id}/history")
    public List<HistoricActivity> history(@PathVariable String id) {
        return engine.history(id);
    }

    @PostMapping("/{id}/resume")
    public InstanceDetailResponse resume(@PathVariable String id) {
        engine.resume(id);
        return new InstanceDetailResponse(engine.instanceRepository.findById(id));
    }

    @PostMapping("/{id}/terminate")
    public void terminate(@PathVariable String id, @RequestBody Map<String, String> body) {
        engine.terminate(id, body.getOrDefault("reason", "terminated by user"));
    }
}
