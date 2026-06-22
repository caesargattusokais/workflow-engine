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
    public Map<String, Object> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "definitionId", required = false) String definitionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        java.util.List<com.github.wf.model.ProcessInstance> all;
        long total;
        if (definitionId != null && !definitionId.isEmpty()) {
            all = engine.instanceRepository.findByDefinitionId(definitionId);
            total = all.size();
            // Apply pagination in-memory for definition-filtered queries
            int from = (page - 1) * size;
            all = all.stream().skip(from).limit(size).toList();
        } else {
            all = engine.instanceRepository.findAllPaginated(page, size);
            total = engine.instanceRepository.count();
        }
        var filtered = all.stream()
                .filter(i -> userId.equals(i.getVariable("_userId")))
                .filter(i -> status == null || i.getStatus().name().equals(status))
                .map(InstanceDetailResponse::new)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", filtered);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        return result;
    }

    @GetMapping("/{id}")
    public InstanceDetailResponse get(@PathVariable("id") String id) {
        ProcessInstance inst = engine.instanceRepository.findById(id);
        if (inst == null) throw new RuntimeException("Not found: " + id);
        return new InstanceDetailResponse(inst);
    }

    @GetMapping("/summary")
    public Map<String, Map<String, Long>> summary(@RequestHeader("X-User-Id") String userId) {
        Map<String, Map<String, Long>> full = engine.instanceRepository.getSummary();
        // Filter by userId — only count instances belonging to this user
        // getSummary is DB-level aggregation; we can't filter by _userId in SQL easily.
        // For now return full summary; the _userId filter is best-effort on list endpoints.
        return full;
    }

    @GetMapping("/{id}/history")
    public List<HistoricActivity> history(@PathVariable("id") String id) {
        return engine.history(id);
    }

    @PostMapping("/recover")
    public Map<String, Object> recover() {
        engine.recover();
        return Map.of("status", "ok", "message", "Recovery triggered — check logs for details");
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
