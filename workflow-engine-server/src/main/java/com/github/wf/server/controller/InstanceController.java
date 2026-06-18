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
    public InstanceDetailResponse start(@RequestHeader("X-User-Id") String userId,
                                         @RequestBody StartInstanceRequest req) {
        Map<String, Object> vars = req.getVariables() != null
                ? new HashMap<>(req.getVariables()) : new HashMap<>();
        vars.put("_userId", userId);
        ProcessInstance inst = engine.start(req.getDefinitionId(), vars);
        return new InstanceDetailResponse(inst);
    }

    @GetMapping
    public List<InstanceDetailResponse> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String definitionId) {
        java.util.List<com.github.wf.model.ProcessInstance> all;
        if (definitionId != null && !definitionId.isEmpty()) {
            all = engine.instanceRepository.findByDefinitionId(definitionId);
        } else {
            all = engine.instanceRepository.findAll();
        }
        return all.stream()
                .filter(i -> userId.equals(i.getVariable("_userId")))
                .filter(i -> status == null || i.getStatus().name().equals(status))
                .map(InstanceDetailResponse::new)
                .toList();
    }

    @GetMapping("/{id}")
    public InstanceDetailResponse get(@PathVariable("id") String id) {
        ProcessInstance inst = engine.instanceRepository.findById(id);
        if (inst == null) throw new RuntimeException("Not found: " + id);
        return new InstanceDetailResponse(inst);
    }

    @GetMapping("/{id}/history")
    public List<HistoricActivity> history(@PathVariable("id") String id) {
        return engine.history(id);
    }

    @PostMapping("/{id}/resume")
    public InstanceDetailResponse resume(@PathVariable("id") String id) {
        engine.resume(id);
        return new InstanceDetailResponse(engine.instanceRepository.findById(id));
    }

    @PostMapping("/{id}/terminate")
    public void terminate(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        engine.terminate(id, body.getOrDefault("reason", "terminated by user"));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") String id) {
        var inst = engine.instanceRepository.findById(id);
        if (inst == null) throw new RuntimeException("Not found: " + id);
        if (inst.isRunning()) throw new RuntimeException("Cannot delete running instance");
        engine.instanceRepository.deleteById(id);
    }
}
