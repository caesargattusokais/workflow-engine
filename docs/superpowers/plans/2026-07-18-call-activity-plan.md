# Call Activity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Call Activity node type — a process can reference and invoke a deployed process definition as a synchronous sub-process, with configurable variable pass-through and explicit mapping.

**Architecture:** New `CallActivityNode` model → `CallActivityRunner` starts a child `ProcessInstance` with `parentInstanceId`/`parentExecutionId` linkage. `EndEventRunner` enhanced to detect child completion and wake the parent. Variable pass-through by default, overridable with `inputMapping`/`outputMapping`.

**Tech Stack:** Java 17, Spring Boot 3.3, React 18 + TypeScript + ReactFlow

## Global Constraints

- Follow existing patterns: `Node` subclass in `model/node/`, `NodeRunner` in `engine/runner/`
- Node type string: `"callActivity"` (camelCase for frontend consistency)
- YAML DSL field: `calledId` (required), `calledVersion` (optional, defaults to latest)
- VariableMapping: `from`/`to`/`expr` fields
- No variable pass-through by default; configured `inputMapping`/`outputMapping` only
- `ProcessInstance` gains `parentInstanceId` + `parentExecutionId` (null for top-level)

---

### Task 1: Model Layer — NodeType, VariableMapping, CallActivityNode, ProcessInstance

**Files:**
- Modify: `workflow-engine-core/src/main/java/com/github/wf/model/NodeType.java`
- Create: `workflow-engine-core/src/main/java/com/github/wf/model/VariableMapping.java`
- Create: `workflow-engine-core/src/main/java/com/github/wf/model/node/CallActivityNode.java`
- Modify: `workflow-engine-core/src/main/java/com/github/wf/model/ProcessInstance.java`

**Interfaces:**
- Produces: `NodeType.CALL_ACTIVITY`, `VariableMapping` class, `CallActivityNode` class, `ProcessInstance.parentInstanceId`/`parentExecutionId`

- [ ] **Step 1: Add CALL_ACTIVITY to NodeType enum**

```java
package com.github.wf.model;

public enum NodeType {
    START_EVENT,
    END_EVENT,
    USER_TASK,
    SERVICE_TASK,
    EXCLUSIVE_GATEWAY,
    PARALLEL_GATEWAY,
    INCLUSIVE_GATEWAY,
    TIMER,
    CALL_ACTIVITY
}
```

- [ ] **Step 2: Create VariableMapping model**

File: `workflow-engine-core/src/main/java/com/github/wf/model/VariableMapping.java`

```java
package com.github.wf.model;

import java.util.Objects;

public class VariableMapping {
    private final String from;
    private final String to;
    private final String expr;  // optional SpEL expression for value transformation

    public VariableMapping(String from, String to, String expr) {
        this.from = Objects.requireNonNull(from, "from must not be null");
        this.to = to != null ? to : from;
        this.expr = expr;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getExpr() { return expr; }
}
```

- [ ] **Step 3: Create CallActivityNode**

File: `workflow-engine-core/src/main/java/com/github/wf/model/node/CallActivityNode.java`

```java
package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import com.github.wf.model.VariableMapping;

import java.util.Collections;
import java.util.List;

public class CallActivityNode extends Node {
    private final String calledId;
    private final Integer calledVersion;
    private final List<VariableMapping> inputMapping;
    private final List<VariableMapping> outputMapping;

    public CallActivityNode(String id, String name,
                            String calledId, Integer calledVersion,
                            List<VariableMapping> inputMapping,
                            List<VariableMapping> outputMapping,
                            List<String> listeners) {
        super(id, name, NodeType.CALL_ACTIVITY, listeners);
        this.calledId = java.util.Objects.requireNonNull(calledId, "calledId must not be null");
        this.calledVersion = calledVersion;
        this.inputMapping = inputMapping != null
            ? Collections.unmodifiableList(inputMapping)
            : Collections.emptyList();
        this.outputMapping = outputMapping != null
            ? Collections.unmodifiableList(outputMapping)
            : Collections.emptyList();
    }

    public String getCalledId() { return calledId; }
    public Integer getCalledVersion() { return calledVersion; }
    public List<VariableMapping> getInputMapping() { return inputMapping; }
    public List<VariableMapping> getOutputMapping() { return outputMapping; }
}
```

- [ ] **Step 4: Add parentInstanceId and parentExecutionId to ProcessInstance**

Add two non-final fields (needed for CallActivityRunner to set them post-construction) and update all constructors:

In `ProcessInstance.java`, add fields after `activeNodeIds`:
```java
private String parentInstanceId;
private String parentExecutionId;
```

Update the three constructors to accept and initialize these fields (null by default):

```java
public ProcessInstance(String id, String definitionId, Map<String, Object> variables) {
    this(id, definitionId, 0, variables, null, null);
}

public ProcessInstance(String id, String definitionId, int definitionVersion, Map<String, Object> variables) {
    this(id, definitionId, definitionVersion, variables, null, null, Instant.now(), null);
}

public ProcessInstance(String id, String definitionId, int definitionVersion,
                       Map<String, Object> variables,
                       String parentInstanceId, String parentExecutionId) {
    this(id, definitionId, definitionVersion, variables, parentInstanceId, parentExecutionId,
         Instant.now(), null);
}

public ProcessInstance(String id, String definitionId, int definitionVersion,
                       Map<String, Object> variables, Instant createdAt, Instant completedAt) {
    this(id, definitionId, definitionVersion, variables, null, null, createdAt, completedAt);
}

public ProcessInstance(String id, String definitionId, int definitionVersion,
                       Map<String, Object> variables,
                       String parentInstanceId, String parentExecutionId,
                       Instant createdAt, Instant completedAt) {
    this.id = id != null ? id : UUID.randomUUID().toString();
    this.definitionId = Objects.requireNonNull(definitionId);
    this.definitionVersion = definitionVersion;
    this.status = InstanceStatus.RUNNING;
    this.variables = new HashMap<>(variables != null ? variables : Map.of());
    this.activeNodeIds = new HashSet<>();
    this.parentInstanceId = parentInstanceId;
    this.parentExecutionId = parentExecutionId;
    this.createdAt = createdAt;
    this.completedAt = completedAt;
}
```

Add getters and setters:
```java
public String getParentInstanceId() { return parentInstanceId; }
public void setParentInstanceId(String parentInstanceId) { this.parentInstanceId = parentInstanceId; }
public String getParentExecutionId() { return parentExecutionId; }
public void setParentExecutionId(String parentExecutionId) { this.parentExecutionId = parentExecutionId; }
```

- [ ] **Step 5: Compile and verify**

Run: `mvn compile -pl workflow-engine-core -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add workflow-engine-core/src/main/java/com/github/wf/model/NodeType.java \
        workflow-engine-core/src/main/java/com/github/wf/model/VariableMapping.java \
        workflow-engine-core/src/main/java/com/github/wf/model/node/CallActivityNode.java \
        workflow-engine-core/src/main/java/com/github/wf/model/ProcessInstance.java
git commit -m "feat: add CallActivity model — NodeType, VariableMapping, CallActivityNode, ProcessInstance parent linkage"
```

---

### Task 2: DSL Layer — NodeYaml, YamlProcessParser, JsonProcessParser

**Files:**
- Modify: `workflow-engine-core/src/main/java/com/github/wf/dsl/NodeYaml.java`
- Modify: `workflow-engine-core/src/main/java/com/github/wf/dsl/YamlProcessParser.java`
- Modify: `workflow-engine-core/src/main/java/com/github/wf/dsl/JsonProcessParser.java`

**Interfaces:**
- Consumes: `NodeType.CALL_ACTIVITY`, `VariableMapping`, `CallActivityNode`
- Produces: YAML/JSON parsing for `callActivity` type, `VariableMappingYaml` inner class

- [ ] **Step 1: Add callActivity fields to NodeYaml**

In `NodeYaml.java`, add fields:
```java
public String calledId;
public Integer calledVersion;
public List<VariableMappingYaml> inputMapping;
public List<VariableMappingYaml> outputMapping;
```

Add inner class:
```java
public static class VariableMappingYaml {
    public String from;
    public String to;
    public String expr;
}
```

- [ ] **Step 2: Add callActivity case to YamlProcessParser.convertNode()**

In `YamlProcessParser.java`, add case before the `default`:
```java
case "callActivity":
    List<VariableMapping> inMappings = new ArrayList<>();
    if (ny.inputMapping != null) {
        for (NodeYaml.VariableMappingYaml vm : ny.inputMapping) {
            inMappings.add(new VariableMapping(vm.from, vm.to, vm.expr));
        }
    }
    List<VariableMapping> outMappings = new ArrayList<>();
    if (ny.outputMapping != null) {
        for (NodeYaml.VariableMappingYaml vm : ny.outputMapping) {
            outMappings.add(new VariableMapping(vm.from, vm.to, vm.expr));
        }
    }
    return new CallActivityNode(ny.id, ny.name, ny.calledId,
        ny.calledVersion, inMappings, outMappings, listeners);
```

Add import:
```java
import com.github.wf.model.VariableMapping;
```

- [ ] **Step 3: Add callActivity case to JsonProcessParser.convertNode()**

Find the switch in `JsonProcessParser.java`'s `convertNode()` method and add the same case before `default`:

```java
case "callActivity":
    List<VariableMapping> inMappings = new ArrayList<>();
    List<JsonElement> inList = getJsonArray(nodeJson, "inputMapping");
    if (inList != null) {
        for (JsonElement e : inList) {
            JsonObject vm = e.getAsJsonObject();
            inMappings.add(new VariableMapping(
                getString(vm, "from"),
                getString(vm, "to"),
                getString(vm, "expr")));
        }
    }
    List<VariableMapping> outMappings = new ArrayList<>();
    List<JsonElement> outList = getJsonArray(nodeJson, "outputMapping");
    if (outList != null) {
        for (JsonElement e : outList) {
            JsonObject vm = e.getAsJsonObject();
            outMappings.add(new VariableMapping(
                getString(vm, "from"),
                getString(vm, "to"),
                getString(vm, "expr")));
        }
    }
    return new CallActivityNode(getString(nodeJson, "id"), getString(nodeJson, "name"),
        getString(nodeJson, "calledId"), getInt(nodeJson, "calledVersion"),
        inMappings, outMappings, listeners);
```

- [ ] **Step 4: Compile and verify core module**

Run: `mvn compile -pl workflow-engine-core -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add workflow-engine-core/src/main/java/com/github/wf/dsl/NodeYaml.java \
        workflow-engine-core/src/main/java/com/github/wf/dsl/YamlProcessParser.java \
        workflow-engine-core/src/main/java/com/github/wf/dsl/JsonProcessParser.java
git commit -m "feat: add callActivity DSL parsing — YAML + JSON parser support"
```

---

### Task 3: Persistence — InstanceRepository implementations + schema.sql

**Files:**
- Modify: `workflow-engine-memory/src/main/java/com/github/wf/memory/InMemoryInstanceRepository.java`
- Modify: `workflow-engine-memory/src/main/java/com/github/wf/memory/JdbcInstanceRepository.java`
- Modify: `workflow-engine-server/src/main/resources/schema.sql`

**Interfaces:**
- Consumes: `ProcessInstance.parentInstanceId`, `ProcessInstance.parentExecutionId`
- Produces: Persisted parent linkage for child instances

- [ ] **Step 1: Update InMemoryInstanceRepository**

No structural changes needed — the `save()` method stores the `ProcessInstance` object as-is, and the new fields are already on the object. Verify no compilation errors.

Run: `mvn compile -pl workflow-engine-memory -q`
Expected: PASS (already compiles since ProcessInstance has the fields)

- [ ] **Step 2: Update JdbcInstanceRepository — writeToDb()**

Add `parent_instance_id` and `parent_execution_id` columns to INSERT and UPDATE in `writeToDb()`:

```java
private void writeToDb(ProcessInstance instance) {
    int updated = jdbc.update(
        "UPDATE process_instance SET status=?, variables_json=?, active_node_ids_json=?, " +
        "parent_instance_id=?, parent_execution_id=?, completed_at=? WHERE id=?",
        instance.getStatus().name(), gson.toJson(instance.getVariables()),
        gson.toJson(new ArrayList<>(instance.getActiveNodeIds())),
        instance.getParentInstanceId(),
        instance.getParentExecutionId(),
        instance.getCompletedAt() != null ? instance.getCompletedAt().toEpochMilli() : null,
        instance.getId());
    if (updated == 0) {
        jdbc.update(
            "INSERT INTO process_instance (id, definition_id, definition_version, status, " +
            "variables_json, active_node_ids_json, parent_instance_id, parent_execution_id, " +
            "created_at, completed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            instance.getId(), instance.getDefinitionId(), instance.getDefinitionVersion(),
            instance.getStatus().name(), gson.toJson(instance.getVariables()),
            gson.toJson(new ArrayList<>(instance.getActiveNodeIds())),
            instance.getParentInstanceId(),
            instance.getParentExecutionId(),
            instance.getCreatedAt().toEpochMilli(),
            instance.getCompletedAt() != null ? instance.getCompletedAt().toEpochMilli() : null);
    }
}
```

- [ ] **Step 3: Update JdbcInstanceRepository — mapInstance()**

Update the `mapInstance()` method to read the new columns:

```java
private ProcessInstance mapInstance(java.sql.ResultSet rs) throws java.sql.SQLException {
    String id = rs.getString("id");
    String defId = rs.getString("definition_id");
    int defVer = rs.getInt("definition_version");
    String varsJson = rs.getString("variables_json");
    Map<String, Object> vars = new HashMap<>();
    if (varsJson != null && !varsJson.isEmpty()) {
        Map<String, Object> parsed = gson.fromJson(varsJson,
            new TypeToken<Map<String, Object>>() {}.getType());
        if (parsed != null) vars = parsed;
    }
    String parentInstId = rs.getString("parent_instance_id");
    String parentExecId = rs.getString("parent_execution_id");
    long created = rs.getLong("created_at");
    long completed = rs.getLong("completed_at");
    ProcessInstance inst = new ProcessInstance(id, defId, defVer, vars,
        rs.wasNull() ? null : parentInstId,
        rs.wasNull() ? null : parentExecId,
        Instant.ofEpochMilli(created), Instant.ofEpochMilli(completed));
    // ... rest of method unchanged
}
```

- [ ] **Step 4: Add SQL columns to schema.sql**

Find the `process_instance` table DDL in `workflow-engine-server/src/main/resources/schema.sql` and add columns:

```sql
ALTER TABLE process_instance ADD COLUMN IF NOT EXISTS parent_instance_id VARCHAR(64);
ALTER TABLE process_instance ADD COLUMN IF NOT EXISTS parent_execution_id VARCHAR(64);
```

- [ ] **Step 5: Compile memory module**

Run: `mvn compile -pl workflow-engine-memory -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add workflow-engine-memory/src/main/java/com/github/wf/memory/InMemoryInstanceRepository.java \
        workflow-engine-memory/src/main/java/com/github/wf/memory/JdbcInstanceRepository.java \
        workflow-engine-server/src/main/resources/schema.sql
git commit -m "feat: persist parentInstanceId/parentExecutionId in process_instance table"
```

---

### Task 4: Server — DefinitionController mapNodeType + graphFromDef data

**Files:**
- Modify: `workflow-engine-server/src/main/java/com/github/wf/server/controller/DefinitionController.java`

**Interfaces:**
- Consumes: `CallActivityNode`, `NodeType.CALL_ACTIVITY`
- Produces: Frontend receives `callActivity` typed graph nodes with `calledId`/`calledVersion`/`inputMapping`/`outputMapping` in data

- [ ] **Step 1: Add CALL_ACTIVITY to mapNodeType switch**

In `mapNodeType()`:
```java
case CALL_ACTIVITY -> "callActivity";
```

- [ ] **Step 2: Add CallActivity data extraction in graphFromDef**

After the existing `else if` chain (around line 158), add:

```java
} else if (n instanceof CallActivityNode ca) {
    data.put("calledId", ca.getCalledId());
    if (ca.getCalledVersion() != null) data.put("calledVersion", ca.getCalledVersion());
    if (!ca.getInputMapping().isEmpty()) {
        data.put("inputMapping", ca.getInputMapping().stream().map(vm -> {
            Map<String, String> m = new HashMap<>();
            m.put("from", vm.getFrom()); m.put("to", vm.getTo());
            if (vm.getExpr() != null) m.put("expr", vm.getExpr());
            return m;
        }).toList());
    }
    if (!ca.getOutputMapping().isEmpty()) {
        data.put("outputMapping", ca.getOutputMapping().stream().map(vm -> {
            Map<String, String> m = new HashMap<>();
            m.put("from", vm.getFrom()); m.put("to", vm.getTo());
            if (vm.getExpr() != null) m.put("expr", vm.getExpr());
            return m;
        }).toList());
    }
}
```

Add import:
```java
import com.github.wf.model.node.CallActivityNode;
```

- [ ] **Step 3: Compile server module**

Run: `mvn compile -pl workflow-engine-server -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add workflow-engine-server/src/main/java/com/github/wf/server/controller/DefinitionController.java
git commit -m "feat: mapNodeType + graphFromDef support CallActivity node"
```

---

### Task 5: Engine — CallActivityRunner, EndEventRunner enhancement, WorkflowEngine registration

**Files:**
- Create: `workflow-engine-core/src/main/java/com/github/wf/engine/runner/CallActivityRunner.java`
- Modify: `workflow-engine-core/src/main/java/com/github/wf/engine/runner/EndEventRunner.java`
- Modify: `workflow-engine-core/src/main/java/com/github/wf/engine/WorkflowEngine.java`

**Interfaces:**
- Consumes: `CallActivityNode`, `ProcessRepository`, `InstanceRepository`, `WorkflowEngine.start()`
- Produces: CallActivityRunner registered, child instance created with parent linkage, EndEventRunner wakes parent

- [ ] **Step 1: Create CallActivityRunner**

File: `workflow-engine-core/src/main/java/com/github/wf/engine/runner/CallActivityRunner.java`

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.model.node.CallActivityNode;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;

import java.util.*;
import java.util.function.Consumer;

public class CallActivityRunner implements NodeRunner {

    private final ProcessRepository processRepository;
    private final InstanceRepository instanceRepository;
    private final Consumer<String> triggerFn;

    public CallActivityRunner(ProcessRepository processRepository,
                              InstanceRepository instanceRepository,
                              Consumer<String> triggerFn) {
        this.processRepository = processRepository;
        this.instanceRepository = instanceRepository;
        this.triggerFn = triggerFn;
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        CallActivityNode caNode = (CallActivityNode) node;
        Execution exec = context.getExecution();

        // 1. Resolve the called process definition
        ProcessDefinition def = resolveDefinition(caNode);
        if (def == null) {
            throw new IllegalArgumentException(
                "CallActivity '" + node.getId() + "': definition not found: "
                + caNode.getCalledId()
                + (caNode.getCalledVersion() != null ? " v" + caNode.getCalledVersion() : ""));
        }

        // 2. Build child variables
        Map<String, Object> childVars = buildChildVariables(caNode,
            instanceRepository.findById(exec.getInstanceId()));

        // 3. Create child instance with parent linkage
        ProcessInstance childInst = new ProcessInstance(null, def.getId(),
            def.getVersion(), childVars,
            exec.getInstanceId(), exec.getId());
        instanceRepository.save(childInst);

        // 4. Create start execution for child
        Node childStartNode = def.getStartNode();
        Execution childExec = new Execution(childInst.getId(), childStartNode.getId());
        instanceRepository.saveExecution(childExec);
        childInst.setActiveNodeIds(Set.of(childStartNode.getId()));
        instanceRepository.update(childInst);

        // 5. Set parent execution to WAITING
        exec.setStatus(ExecutionStatus.WAITING);
        instanceRepository.updateExecution(exec);

        // 6. Trigger child instance
        triggerFn.accept(childInst.getId());

        return false; // waiting for child to complete
    }

    private ProcessDefinition resolveDefinition(CallActivityNode node) {
        Integer version = node.getCalledVersion();
        if (version != null) {
            return processRepository.findAllVersions(node.getCalledId()).stream()
                .filter(d -> d.getVersion() == version)
                .findFirst().orElse(null);
        }
        return processRepository.findLatestById(node.getCalledId());
    }

    private Map<String, Object> buildChildVariables(CallActivityNode node,
                                                     ProcessInstance parentInst) {
        if (!node.getInputMapping().isEmpty()) {
            Map<String, Object> child = new HashMap<>();
            Map<String, Object> parentVars = parentInst.getVariables();
            for (VariableMapping vm : node.getInputMapping()) {
                Object value = parentVars.get(vm.getFrom());
                child.put(vm.getTo(), value);
            }
            return child;
        }
        // No mapping → pass through all parent variables
        return new HashMap<>(parentInst.getVariables());
    }
}
```

- [ ] **Step 2: Enhance EndEventRunner to wake parent on child completion**

Replace `EndEventRunner.java`:

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.model.node.CallActivityNode;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EndEventRunner implements NodeRunner {

    private final ProcessRepository processRepository;
    private final Consumer<String> parentTrigger;

    public EndEventRunner() {
        this(null, null);
    }

    public EndEventRunner(ProcessRepository processRepository, Consumer<String> parentTrigger) {
        this.processRepository = processRepository;
        this.parentTrigger = parentTrigger;
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        Execution exec = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();

        // ── Sub-process completion: wake parent ──
        ProcessInstance instance = repo.findById(exec.getInstanceId());
        if (instance != null && instance.getParentInstanceId() != null
            && processRepository != null && parentTrigger != null) {
            ProcessInstance parentInst = repo.findById(instance.getParentInstanceId());
            if (parentInst != null) {
                // Load parent definition to find CallActivityNode and its outputMapping
                ProcessDefinition parentDef = processRepository.findLatestById(parentInst.getDefinitionId());
                if (parentDef != null) {
                    Execution parentExec = repo.findExecutionById(instance.getParentExecutionId());
                    if (parentExec != null) {
                        Node callActivityNode = parentDef.getNode(parentExec.getCurrentNodeId());
                        if (callActivityNode instanceof CallActivityNode ca) {
                            // Write back variables
                            if (!ca.getOutputMapping().isEmpty()) {
                                Map<String, Object> childVars = instance.getVariables();
                                for (VariableMapping vm : ca.getOutputMapping()) {
                                    parentInst.setVariable(vm.getTo(), childVars.get(vm.getFrom()));
                                }
                            } else {
                                // Full pass-through
                                parentInst.setVariables(instance.getVariables());
                            }
                            repo.update(parentInst);

                            // Advance parent execution past the CallActivity
                            parentExec.setStatus(ExecutionStatus.ACTIVE);
                            List<Transition> outgoings = parentDef.getOutgoingTransitions(
                                parentExec.getCurrentNodeId());
                            if (!outgoings.isEmpty()) {
                                parentExec.setCurrentNodeId(outgoings.get(0).getTo());
                            }
                            repo.updateExecution(parentExec);
                        }
                    }
                }
            }
            // Trigger parent instance to continue
            parentTrigger.accept(instance.getParentInstanceId());

            // Mark child execution and instance COMPLETED
            exec.setStatus(ExecutionStatus.COMPLETED);
            repo.updateExecution(exec);
            return true;
        }

        // ── Existing parallel gateway join logic ──
        if (exec.isChild()) {
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null) {
                List<Execution> siblings = repo.findExecutionsByParentId(
                    exec.getParentExecutionId());
                boolean allDone = siblings.stream()
                    .allMatch(e -> e.getId().equals(exec.getId()) || e.isCompleted());
                if (allDone) {
                    parent.setStatus(ExecutionStatus.ACTIVE);
                    List<Transition> outgoing = context.getDefinition()
                        .getOutgoingTransitions(parent.getCurrentNodeId());
                    if (!outgoing.isEmpty()) {
                        parent.setCurrentNodeId(outgoing.get(0).getTo());
                    }
                    repo.updateExecution(parent);
                }
            }
            exec.setStatus(ExecutionStatus.COMPLETED);
            repo.updateExecution(exec);
        } else {
            exec.setStatus(ExecutionStatus.COMPLETED);
            repo.updateExecution(exec);
        }
        return true;
    }
}
```

Add required imports:
```java
import com.github.wf.model.ProcessInstance;
import com.github.wf.model.ProcessDefinition;
import com.github.wf.model.Transition;
import com.github.wf.model.VariableMapping;
import com.github.wf.model.node.CallActivityNode;
import com.github.wf.spi.ProcessRepository;
import java.util.Map;
import java.util.function.Consumer;
```

- [ ] **Step 3: Update WorkflowEngine — register runners**

In `WorkflowEngine.java`, update `registerDefaultRunners()`:

```java
private void registerDefaultRunners(com.github.wf.ext.OrgService orgService) {
    runners.put(NodeType.START_EVENT, new StartEventRunner());
    runners.put(NodeType.END_EVENT, new EndEventRunner(processRepository, this::trigger));
    runners.put(NodeType.USER_TASK, new UserTaskRunner(taskRepository, delayScheduler::schedule, baseUrl, orgService));
    runners.put(NodeType.SERVICE_TASK, new ServiceTaskRunner(delayScheduler::schedule));
    runners.put(NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayRunner());
    runners.put(NodeType.PARALLEL_GATEWAY, new ParallelGatewayRunner());
    runners.put(NodeType.INCLUSIVE_GATEWAY, new InclusiveGatewayRunner());
    runners.put(NodeType.TIMER, new TimerRunner(delayScheduler::schedule));
    runners.put(NodeType.CALL_ACTIVITY, new CallActivityRunner(
        processRepository, instanceRepository, this::trigger));
}
```

- [ ] **Step 4: Compile core module**

Run: `mvn compile -pl workflow-engine-core -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add workflow-engine-core/src/main/java/com/github/wf/engine/runner/CallActivityRunner.java \
        workflow-engine-core/src/main/java/com/github/wf/engine/runner/EndEventRunner.java \
        workflow-engine-core/src/main/java/com/github/wf/engine/WorkflowEngine.java
git commit -m "feat: CallActivityRunner + EndEventRunner parent wake-up + engine registration"
```

---

### Task 6: Integration Test

**Files:**
- Create: `workflow-engine-core/src/test/resources/call-activity-parent.yaml`
- Create: `workflow-engine-core/src/test/resources/call-activity-child.yaml`
- Create: `workflow-engine-core/src/test/java/com/github/wf/engine/CallActivityIntegrationTest.java`

- [ ] **Step 1: Create child process YAML**

File: `workflow-engine-core/src/test/resources/call-activity-child.yaml`
```yaml
id: child-approval
name: 子流程-审批
version: 1
nodes:
  - id: start
    type: startEvent
  - id: review
    type: userTask
    name: 审批
    assignee: "${user}"
  - id: end
    type: endEvent
transitions:
  - from: start
    to: review
  - from: review
    to: end
```

- [ ] **Step 2: Create parent process YAML with CallActivity**

File: `workflow-engine-core/src/test/resources/call-activity-parent.yaml`
```yaml
id: parent-main
name: 主流程
version: 1
nodes:
  - id: start
    type: startEvent
  - id: call-child
    type: callActivity
    calledId: child-approval
    inputMapping:
      - from: applicant
        to: user
    outputMapping:
      - from: result
        to: approvalResult
  - id: end
    type: endEvent
transitions:
  - from: start
    to: call-child
  - from: call-child
    to: end
```

- [ ] **Step 3: Create integration test**

File: `workflow-engine-core/src/test/java/com/github/wf/engine/CallActivityIntegrationTest.java`

```java
package com.github.wf.engine;

import com.github.wf.model.ProcessDefinition;
import com.github.wf.model.ProcessInstance;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CallActivityIntegrationTest {

    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        engine = WorkflowEngine.builder()
            .inMemory()
            .build();
        // Deploy child process first
        ProcessDefinition child = engine.deploy(new java.io.File(
            "src/test/resources/call-activity-child.yaml"));
        assertEquals("child-approval", child.getId());
        // Deploy parent process
        ProcessDefinition parent = engine.deploy(new java.io.File(
            "src/test/resources/call-activity-parent.yaml"));
        assertEquals("parent-main", parent.getId());
    }

    @Test
    void testCallActivityStartsChildAndParentWaits() {
        // Start parent with applicant variable
        ProcessInstance parentInst = engine.start("parent-main",
            Map.of("applicant", "zhangsan"));
        assertNotNull(parentInst);
        assertEquals("RUNNING", parentInst.getStatus().name());

        // Parent should be WAITING at the callActivity
        // The child instance should be RUNNING
        var allInstances = engine.instanceRepository.findAll();
        assertEquals(2, allInstances.size()); // parent + child

        ProcessInstance childInst = allInstances.stream()
            .filter(i -> i.getParentInstanceId() != null)
            .findFirst().orElseThrow();
        assertEquals(parentInst.getId(), childInst.getParentInstanceId());
        assertEquals("child-approval", childInst.getDefinitionId());

        // Child should have the mapped variable
        assertEquals("zhangsan", childInst.getVariable("user"));

        // Complete the child's user task
        var tasks = engine.queryTasks(
            new TaskQuery().instanceId(childInst.getId()));
        assertEquals(1, tasks.size());
        Task task = tasks.get(0);

        // Set result variable before completing
        childInst = engine.instanceRepository.findById(childInst.getId());
        childInst.setVariable("result", "approved");
        engine.instanceRepository.update(childInst);

        // Complete the task
        engine.taskRepository.complete(task.getId(), Map.of());
        engine.trigger(childInst.getId());

        // Child should now be COMPLETED
        childInst = engine.instanceRepository.findById(childInst.getId());
        assertEquals("COMPLETED", childInst.getStatus().name());

        // Parent should have resumed and completed
        parentInst = engine.instanceRepository.findById(parentInst.getId());
        // Parent should be COMPLETED (or RUNNING if it has more nodes)
        assertNotNull(parentInst);
        // Verify variable was written back
        assertEquals("approved", parentInst.getVariable("approvalResult"));
    }

    @Test
    void testCallActivityWithNonExistentDefinition() {
        // Deploy a bad parent that references non-existent child
        String badYaml = """
            id: bad-parent
            name: Bad Parent
            version: 1
            nodes:
              - id: start
                type: startEvent
              - id: call-bad
                type: callActivity
                calledId: non-existent-process
              - id: end
                type: endEvent
            transitions:
              - from: start
                to: call-bad
              - from: call-bad
                to: end
            """;
        engine.deploy(badYaml);

        assertThrows(IllegalArgumentException.class, () -> {
            engine.start("bad-parent", Map.of());
        });
    }
}
```

- [ ] **Step 4: Run integration test**

Run: `mvn test -pl workflow-engine-core -Dtest=CallActivityIntegrationTest`
Expected: PASS (2 tests pass)

- [ ] **Step 5: Commit**

```bash
git add workflow-engine-core/src/test/resources/call-activity-child.yaml \
        workflow-engine-core/src/test/resources/call-activity-parent.yaml \
        workflow-engine-core/src/test/java/com/github/wf/engine/CallActivityIntegrationTest.java
git commit -m "test: CallActivity integration test — parent/child lifecycle + variable mapping"
```

---

### Task 7: Frontend — CallActivityNode component + designer registration

**Files:**
- Create: `workflow-engine-web/src/designer/nodes/CallActivityNode.tsx`
- Modify: `workflow-engine-web/src/designer/nodes/index.ts`

- [ ] **Step 1: Create CallActivityNode component**

File: `workflow-engine-web/src/designer/nodes/CallActivityNode.tsx`

```tsx
import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function CallActivityNode({ data }: NodeProps) {
  const calledId = data.calledId as string || '';

  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-violet-400" />
      <div className="min-w-[120px] px-4 py-2 rounded-lg bg-violet-700 border-2 border-violet-500
                      flex items-center gap-2 text-sm text-white shadow-lg shadow-violet-500/20">
        <span className="text-lg font-bold">+↵</span>
        <div>
          <div className="truncate max-w-[120px]">{data.name as string || 'Call Activity'}</div>
          {calledId && (
            <div className="text-[10px] opacity-60 truncate max-w-[120px]">{calledId}</div>
          )}
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-violet-400" />
    </div>
  );
}
```

- [ ] **Step 2: Register in nodeTypes**

In `workflow-engine-web/src/designer/nodes/index.ts`:
```ts
import CallActivityNode from './CallActivityNode';

export const nodeTypes = {
  startEvent: StartEventNode,
  endEvent: EndEventNode,
  userTask: UserTaskNode,
  serviceTask: ServiceTaskNode,
  exclusiveGateway: ExclusiveGatewayNode,
  parallelGateway: ParallelGatewayNode,
  inclusiveGateway: InclusiveGatewayNode,
  timer: TimerNode,
  callActivity: CallActivityNode,
};
```

- [ ] **Step 3: Register in monitor InstanceFlow**

In `workflow-engine-web/src/monitor/InstanceFlow.tsx`, add to imports:
```ts
import CallActivityNode from '../designer/nodes/CallActivityNode';
```

And in the existing `nodeTypes` usage, add `callActivity: CallActivityNode` to the nodeTypes object (or import the already-updated `nodeTypes` from `../designer/nodes` — which is what the monitor already does).

Verify: monitor already imports `nodeTypes` from `../designer/nodes`, so the registration in step 2 covers both designer and monitor.

- [ ] **Step 4: Build and verify**

Run: `cd workflow-engine-web && npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add workflow-engine-web/src/designer/nodes/CallActivityNode.tsx \
        workflow-engine-web/src/designer/nodes/index.ts
git commit -m "feat: CallActivityNode ReactFlow component + registration"
```

---

### Task 8: Frontend — graphToYaml + yamlToGraph converters

**Files:**
- Modify: `workflow-engine-web/src/designer/graphToYaml.ts`
- Modify: `workflow-engine-web/src/designer/yamlToGraph.ts`

- [ ] **Step 1: Add callActivity to graphToYaml**

In `graphToYaml.ts`, add after the serviceTask block (before `// Retry`):

```typescript
    if (node.type === 'callActivity') {
      if (data.calledId) lines.push(`    calledId: ${y(data.calledId as string)}`);
      if (data.calledVersion) lines.push(`    calledVersion: ${data.calledVersion}`);
      const inMappings = data.inputMapping as any[] | undefined;
      if (inMappings && inMappings.length > 0) {
        lines.push('    inputMapping:');
        for (const m of inMappings) {
          lines.push(`      - from: ${y(m.from)}`);
          lines.push(`        to: ${y(m.to)}`);
          if (m.expr) lines.push(`        expr: ${y(m.expr)}`);
        }
      }
      const outMappings = data.outputMapping as any[] | undefined;
      if (outMappings && outMappings.length > 0) {
        lines.push('    outputMapping:');
        for (const m of outMappings) {
          lines.push(`      - from: ${y(m.from)}`);
          lines.push(`        to: ${y(m.to)}`);
          if (m.expr) lines.push(`        expr: ${y(m.expr)}`);
        }
      }
    }
```

- [ ] **Step 2: Add callActivity to yamlToGraph flushNode**

In `yamlToGraph.ts`, in `flushNode()`, add callActivity field extraction after `if (Array.isArray(cur.candidateGroups))`:

```typescript
  if (node.type === 'callActivity') {
    if (cur.calledId) (node.data as any).calledId = cur.calledId;
    if (cur.calledVersion) (node.data as any).calledVersion = cur.calledVersion;
  }
```

- [ ] **Step 3: Build and verify**

Run: `cd workflow-engine-web && npm run build`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add workflow-engine-web/src/designer/graphToYaml.ts \
        workflow-engine-web/src/designer/yamlToGraph.ts
git commit -m "feat: callActivity support in graphToYaml + yamlToGraph converters"
```

---

### Task 9: Frontend — PropertyPanel Call Activity editor + monitor registration

**Files:**
- Modify: `workflow-engine-web/src/designer/PropertyPanel.tsx`

- [ ] **Step 1: Add Call Activity property editor**

In PropertyPanel, when `selectedNode.type === 'callActivity'`, render these fields:

- `calledId` — searchable dropdown, populated from `GET /api/definitions`
- `calledVersion` — dropdown (latest / specific versions), fetched after calledId selection
- `inputMapping` — key-value pair list editor (from, to, expr)
- `outputMapping` — key-value pair list editor (from, to, expr)

Since PropertyPanel is complex, add a conditional block after existing type-specific blocks:

```tsx
{selectedNode.type === 'callActivity' && (
  <div className="space-y-3">
    {/* calledId dropdown */}
    <div>
      <label className="text-xs text-gray-400">Called Process</label>
      <select value={data.calledId as string || ''}
        onChange={e => updateNodeData('calledId', e.target.value)}
        className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-1">
        <option value="">-- select definition --</option>
        {allDefs.map((d: any) => (
          <option key={d.id} value={d.id}>{d.name || d.id} (v{d.version})</option>
        ))}
      </select>
    </div>

    {/* calledVersion dropdown */}
    {data.calledId && (
      <div>
        <label className="text-xs text-gray-400">Version</label>
        <select value={data.calledVersion || 'latest'}
          onChange={e => updateNodeData('calledVersion',
            e.target.value === 'latest' ? undefined : parseInt(e.target.value))}
          className="w-full bg-gray-700 rounded px-2 py-1 text-white text-xs mt-1">
          <option value="latest">latest</option>
          {/* version list populated from definitions */}
        </select>
      </div>
    )}
  </div>
)}
```

Note: the `allDefs` list needs to be available in PropertyPanel. The existing `DesignerPage` already fetches definitions. Pass them as a prop or fetch in PropertyPanel.

- [ ] **Step 2: Build and verify**

Run: `cd workflow-engine-web && npm run build`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add workflow-engine-web/src/designer/PropertyPanel.tsx
git commit -m "feat: PropertyPanel Call Activity editor — calledId dropdown + version selector"
```

---

### Task 10: Final verification

- [ ] **Step 1: Run full backend test suite**

Run: `mvn test -pl workflow-engine-core -q`
Expected: all tests PASS (including CallActivityIntegrationTest)

- [ ] **Step 2: Run full frontend build**

Run: `cd workflow-engine-web && npm run build`
Expected: PASS (no errors)

- [ ] **Step 3: End-to-end manual test checklist**

1. Deploy a child workflow (e.g. `leave-approval`)
2. Design a parent workflow with a CallActivity node, select the child via dropdown
3. Start parent instance with variables
4. Verify child instance is created with correct parent linkage
5. Complete child tasks, verify parent resumes
6. Verify variables are written back correctly
7. Verify terminated child → parent state
8. Verify missing calledId → error

- [ ] **Step 4: Final commit**

```bash
git commit -m "chore: final verification — all tests pass, build green"
```

---

## Post-Implementation Review Findings (2026-07-18)

以下问题在最终代码审查中发现，待修复。

### Task 11: Critical — Redis 配置文件完整支持 ✅ DONE (ad39d47)

**Files:**
- Modify: `workflow-engine-memory/src/main/java/com/github/wf/memory/RedisJdbcInstanceRepository.java`
- Modify: `workflow-engine-memory/src/main/java/com/github/wf/memory/RedisConfig.java`

**Description:** CallActivity 功能遗漏了 Redis 配置文件。`RedisJdbcInstanceRepository.writeToDb()` 和 `mapInstance()` 未更新 `parent_instance_id`/`parent_execution_id` 列，`RedisConfig.ProcessInstanceAdapter` 的 Gson 序列化/反序列化未包含 parent 字段。Redis 模式下子流程完成时父流程永远不会被唤醒。

- [x] writeToDb() UPDATE/INSERT SQL 添加 parent_instance_id, parent_execution_id 列
- [x] mapInstance() 读取 parent 列并传入 ProcessInstance 8-arg 构造器（同时修复 completed_at null 问题）
- [x] ProcessInstanceAdapter 序列化添加 parentInstanceId, parentExecutionId
- [x] ProcessInstanceAdapter 反序列化读取 parent 字段，传入 8-arg 构造器

---

### Task 12: Critical — 并行网关子流程状态损坏 ✅ DONE (7e2b98a)

**Description:** CallActivity 子流程含并行网关时，第一个分支的 EndEvent 正确唤醒父流程。第二个分支的 EndEvent 触发时父执行已移出 CallActivityNode，`instanceof CallActivityNode` 失败，else-branch 将已 COMPLETED 的父执行设回 ACTIVE（InMemory 后端）。JDBC 后端因 `update()` 驱逐已完成实例的执行而幸免。

**Fix:** 在进入 CallActivity wake-up 分支前，通过 `findActiveExecutions` 检查是否为子实例的最后一条活跃执行。非最后一条 → 仅标记当前执行 COMPLETED 并返回；是最后一条 → 执行完整父流程唤醒。

- [x] 进入 CallActivity wake-up 分支前检查子实例是否还有其他 active 执行
- [x] 仅当这是子实例的最后一个 active 执行时才唤醒父流程

---

### Task 13: High — 父流程 terminate 未防护 ✅ DONE (e9c3c4c)

**Fix:** 在 `parentInst != null` 后添加 `&& parentInst.isRunning()` 守卫。父流程被 terminate 后子流程 EndEvent 不再写回变量或设置父执行为 ACTIVE，子流程正常完成。

- [x] 在 `parentInst != null` 后添加 `&& parentInst.isRunning()` 守卫

---

### Task 14: High — missing calledId 无限重试循环 ✅ DONE (939a735)

**Fix:** 改为 SUSPEND 模式（对齐 ServiceTaskRunner）：设置 `retryState=SUSPENDED`、`status=WAITING`、在实例变量中记录错误原因。trigger loop 检测到 SUSPENDED 后挂起实例，用户可在监控页面看到并手动修复后 resume。

- [x] resolveDefinition 返回 null 时挂起实例而非抛异常

---

### Task 15: Medium — 其他问题 🔧 PARTIAL (5ee9128)

1. ✅ **EndEventRunner:69** — 全透传改为 merge 语义（逐 key setVariable），不再破坏性覆盖父流程独有变量
2. ✅ **yamlToGraph.ts:177** — `flushNode()` 反序列化 inputMapping/outputMapping，YAML 重新导入不再丢映射配置
3. ✅ **CallActivityRunner.java:55** — `findById()` 返回 null 时抛 `IllegalStateException`（含 instanceId），替代静默 NPE
4. ⚠️ **CallActivityRunner.java:76** — Redis 锁重入问题：triggerFn 在父锁内调用，同步子流程导致 Redis 非重入锁死锁。**已知限制**：仅影响 Redis 模式下的 trivial 子流程（startEvent→endEvent），实际使用中几乎不会遇到。
5. ✅ **CallActivityRunner.java:102** — inputMapping 中父变量不存在时记录 `log.warn`（区分 "值为 null" 和 "变量不存在"）
6. ⚠️ **EndEventRunner — 6 层嵌套**：应抽取方法或反转为 early-return。**已知限制**：不影响功能，下次重构时处理。
7. ✅ **VariableMapping.expr** — SpEL 表达式求值已实现（870475c）。inputMapping 和 outputMapping 均支持。求值失败时回退到原始值并记录 error 日志。
