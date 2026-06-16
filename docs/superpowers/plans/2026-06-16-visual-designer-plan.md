# Visual Designer & Monitor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a React+ReactFlow visual workflow designer and monitor, backed by a Spring Boot REST API wrapping the existing workflow engine.

**Architecture:** Two new Maven modules — `workflow-engine-server` (Spring Boot REST API) and `workflow-engine-web` (React SPA with Vite). Server wraps the existing engine library with 16 REST endpoints. Web provides a Designer tab (drag-drop nodes, connect edges, edit properties, deploy) and a Monitor tab (view instances with active-node highlighting, task actions).

**Tech Stack:** Spring Boot 3.3, Java 17 (server). React 18, TypeScript, ReactFlow 11, Vite, Tailwind CSS (web).

---

## Phase 1: Server Module (Spring Boot)

### Task 1: Scaffold server module

**Files:**
- Create: `D:\workflow-engine\workflow-engine-server\pom.xml`
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\WorkflowServerApp.java`
- Create: `D:\workflow-engine\workflow-engine-server\src\main\resources\application.yml`
- Create: `D:\workflow-engine\workflow-engine-server\src\test\java\com\github\wf\server\SmokeTest.java`
- Modify: `D:\workflow-engine\pom.xml` — add `<module>workflow-engine-server</module>`

- [ ] **Step 1: Write server POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.github.wf</groupId>
        <artifactId>workflow-engine-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>workflow-engine-server</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.github.wf</groupId>
            <artifactId>workflow-engine-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.github.wf</groupId>
            <artifactId>workflow-engine-memory</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>3.3.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <version>3.3.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Write WorkflowServerApp.java**

```java
package com.github.wf.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorkflowServerApp {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowServerApp.class, args);
    }
}
```

- [ ] **Step 3: Write application.yml**

```yaml
server:
  port: 8080

spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
```

- [ ] **Step 4: Add server module to parent POM**

Add `<module>workflow-engine-server</module>` to the `<modules>` section.

- [ ] **Step 5: Smoke test**

```java
package com.github.wf.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SmokeTest {
    @Test
    void contextLoads() {}
}
```

- [ ] **Step 6: Verify**

Run: `mvn test -pl workflow-engine-server -q`
Expected: BUILD SUCCESS, Spring context loads.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: scaffold workflow-engine-server Spring Boot module"
```

---

### Task 2: EngineConfig — Wire WorkflowEngine as Spring Bean

**Files:**
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\config\EngineConfig.java`

- [ ] **Step 1: Write EngineConfig.java**

```java
package com.github.wf.server.config;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    public WorkflowEngine workflowEngine() {
        InMemoryProcessRepository processRepo = new InMemoryProcessRepository();
        InMemoryInstanceRepository instanceRepo = new InMemoryInstanceRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        return WorkflowEngine.builder()
                .processRepository(processRepo)
                .instanceRepository(instanceRepo)
                .taskRepository(taskRepo)
                .build();
    }
}
```

- [ ] **Step 2: Verify**

Run: `mvn compile -pl workflow-engine-server -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: add EngineConfig Spring Bean wiring"
```

---

### Task 3: GraphResponse DTO + DefinitionController

**Files:**
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\dto\GraphResponse.java`
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\dto\DeployRequest.java`
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\controller\DefinitionController.java`
- Create: `D:\workflow-engine\workflow-engine-server\src\test\java\com\github\wf\server\controller\DefinitionControllerTest.java`

- [ ] **Step 1: Write GraphResponse.java**

```java
package com.github.wf.server.dto;

import java.util.List;

public class GraphResponse {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    public GraphResponse() {}
    public GraphResponse(List<GraphNode> nodes, List<GraphEdge> edges) {
        this.nodes = nodes; this.edges = edges;
    }

    public List<GraphNode> getNodes() { return nodes; }
    public void setNodes(List<GraphNode> n) { this.nodes = n; }
    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> e) { this.edges = e; }

    public static class GraphNode {
        private String id, type;
        private double x, y;
        private java.util.Map<String, Object> data = new java.util.HashMap<>();

        public GraphNode() {}
        public GraphNode(String id, String type, double x, double y) {
            this.id = id; this.type = type; this.x = x; this.y = y;
        }
        // getters/setters for id, type, x, y, data
        public String getId() { return id; } public void setId(String i) { id = i; }
        public String getType() { return type; } public void setType(String t) { type = t; }
        public double getX() { return x; } public void setX(double xv) { x = xv; }
        public double getY() { return y; } public void setY(double yv) { y = yv; }
        public java.util.Map<String, Object> getData() { return data; }
        public void setData(java.util.Map<String, Object> d) { data = d; }
    }

    public static class GraphEdge {
        private String id, source, target, type = "smoothstep";
        private String label;
        private java.util.Map<String, Object> data;

        public GraphEdge() {}
        public GraphEdge(String id, String source, String target) {
            this.id = id; this.source = source; this.target = target;
        }
        // getters/setters for all fields
        public String getId() { return id; } public void setId(String i) { id = i; }
        public String getSource() { return source; } public void setSource(String s) { source = s; }
        public String getTarget() { return target; } public void setTarget(String t) { target = t; }
        public String getType() { return type; } public void setType(String t) { type = t; }
        public String getLabel() { return label; } public void setLabel(String l) { label = l; }
        public java.util.Map<String, Object> getData() { return data; }
        public void setData(java.util.Map<String, Object> d) { data = d; }
    }
}
```

- [ ] **Step 2: Write DeployRequest.java**

```java
package com.github.wf.server.dto;

public class DeployRequest {
    private String yaml;
    public String getYaml() { return yaml; }
    public void setYaml(String y) { this.yaml = y; }
}
```

- [ ] **Step 3: Write DefinitionController.java**

```java
package com.github.wf.server.controller;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.*;
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

    public DefinitionController(WorkflowEngine engine) {
        this.engine = engine;
        engine.setProcessParser(new YamlProcessParser());
    }

    @PostMapping
    public ProcessDefinition deploy(@RequestBody DeployRequest req) {
        ProcessDefinition def = engine.deploy(req.getYaml());
        store.put(def.getId(), def);
        return def;
    }

    @GetMapping
    public List<ProcessDefinition> list() {
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ProcessDefinition get(@PathVariable String id) {
        return store.get(id);
    }

    @GetMapping("/{id}/graph")
    public GraphResponse graph(@PathVariable String id) {
        ProcessDefinition def = store.get(id);
        if (def == null) throw new RuntimeException("Not found: " + id);
        return convertToGraph(def);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        store.remove(id);
    }

    // Convert ProcessDefinition → ReactFlow graph
    private GraphResponse convertToGraph(ProcessDefinition def) {
        List<GraphResponse.GraphNode> nodes = new ArrayList<>();
        List<GraphResponse.GraphEdge> edges = new ArrayList<>();
        Map<String, Node> nodeMap = def.getNodes();
        List<String> nodeIds = new ArrayList<>(nodeMap.keySet());

        // Layout nodes vertically with auto-positioning
        for (int i = 0; i < nodeIds.size(); i++) {
            String nid = nodeIds.get(i);
            Node n = nodeMap.get(nid);
            String rft = mapNodeType(n.getType());
            double x = 200, y = 50 + i * 120;
            GraphResponse.GraphNode gn = new GraphResponse.GraphNode(nid, rft, x, y);
            Map<String, Object> data = new HashMap<>();
            data.put("name", n.getName() != null ? n.getName() : nid);
            data.put("listeners", n.getListeners());
            if (n instanceof com.github.wf.model.node.UserTask ut) {
                data.put("assignee", ut.getAssignee());
                data.put("candidateGroups", ut.getCandidateGroups());
                data.put("dynamicRouter", ut.getDynamicRouter());
            } else if (n instanceof com.github.wf.model.node.ServiceTask st) {
                data.put("handlerClass", st.getHandlerClass());
            }
            gn.setData(data);
            nodes.add(gn);
        }

        // Build edges from transitions
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
```

- [ ] **Step 4: Write controller test**

```java
package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.server.dto.GraphResponse;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DefinitionControllerTest {

    @Test
    void deployAndGetGraph() {
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .build();
        DefinitionController ctrl = new DefinitionController(engine);

        ctrl.deploy(new com.github.wf.server.dto.DeployRequest() {{
            setYaml("""
                    id: test
                    version: 1
                    nodes:
                      - id: s
                        type: startEvent
                      - id: e
                        type: endEvent
                    transitions:
                      - from: s
                        to: e
                    """);
        }});

        GraphResponse graph = ctrl.graph("test");
        assertThat(graph.getNodes()).hasSize(2);
        assertThat(graph.getEdges()).hasSize(1);
    }
}
```

- [ ] **Step 5: Run test**

Run: `mvn test -pl workflow-engine-server -Dtest=DefinitionControllerTest -q`
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: add DefinitionController with graph endpoint"
```

---

### Task 4: InstanceController + TaskController

**Files:**
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\dto\StartInstanceRequest.java`
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\dto\CompleteTaskRequest.java`
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\dto\InstanceDetailResponse.java`
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\controller\InstanceController.java`
- Create: `D:\workflow-engine\workflow-engine-server\src\main\java\com\github\wf\server\controller\TaskController.java`
- Create: `D:\workflow-engine\workflow-engine-server\src\test\java\com\github\wf\server\controller\InstanceControllerTest.java`

- [ ] **Step 1: Write DTOs**

```java
// StartInstanceRequest.java
package com.github.wf.server.dto;

import java.util.Map;

public class StartInstanceRequest {
    private String definitionId;
    private Map<String, Object> variables;
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String d) { definitionId = d; }
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> v) { variables = v; }
}

// CompleteTaskRequest.java
package com.github.wf.server.dto;

import java.util.Map;

public class CompleteTaskRequest {
    private Map<String, Object> variables;
    private String comment;
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> v) { variables = v; }
    public String getComment() { return comment; }
    public void setComment(String c) { comment = c; }
}

// InstanceDetailResponse.java
package com.github.wf.server.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public class InstanceDetailResponse {
    private String id, definitionId, status;
    private Map<String, Object> variables;
    private Set<String> activeNodeIds;
    private Instant createdAt, completedAt;

    public InstanceDetailResponse(com.github.wf.model.ProcessInstance inst) {
        this.id = inst.getId();
        this.definitionId = inst.getDefinitionId();
        this.status = inst.getStatus().name();
        this.variables = inst.getVariables();
        this.activeNodeIds = inst.getActiveNodeIds();
        this.createdAt = inst.getCreatedAt();
        this.completedAt = inst.getCompletedAt();
    }

    // getters
    public String getId() { return id; }
    public String getDefinitionId() { return definitionId; }
    public String getStatus() { return status; }
    public Map<String, Object> getVariables() { return variables; }
    public Set<String> getActiveNodeIds() { return activeNodeIds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
```

- [ ] **Step 2: Write InstanceController.java**

```java
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
        return engine.instanceRepository.findByDefinitionId(definitionId != null ? definitionId : "")
                .stream().filter(i -> status == null || i.getStatus().name().equals(status))
                .map(InstanceDetailResponse::new).toList();
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
```

- [ ] **Step 3: Write TaskController.java**

```java
package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.server.dto.CompleteTaskRequest;
import com.github.wf.task.Task;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    private final WorkflowEngine engine;

    public TaskController(WorkflowEngine engine) { this.engine = engine; }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String candidateGroup,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String status) {

        var q = engine.taskQuery();
        if (assignee != null) q.assignee(assignee);
        if (candidateGroup != null) q.candidateGroup(candidateGroup);
        if (instanceId != null) q.instanceId(instanceId);
        if (status != null) q.status(com.github.wf.task.TaskStatus.valueOf(status));

        return engine.queryTasks(q).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("instanceId", t.getInstanceId());
            m.put("nodeId", t.getNodeId());
            m.put("assignee", t.getAssignee());
            m.put("status", t.getStatus().name());
            m.put("candidateGroups", t.getCandidateGroups());
            m.put("variables", t.getVariables());
            m.put("createdAt", t.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping("/{id}/complete")
    public void complete(@PathVariable String id, @RequestBody CompleteTaskRequest req) {
        engine.completeTask(id, req.getVariables(), req.getComment());
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable String id, @RequestBody Map<String, String> body) {
        engine.rejectTask(id, body.getOrDefault("comment", ""));
    }

    @PostMapping("/{id}/delegate")
    public void delegate(@PathVariable String id, @RequestBody Map<String, String> body) {
        engine.delegateTask(id, body.get("newAssignee"));
    }
}
```

- [ ] **Step 4: Write integration test**

```java
// InstanceControllerTest.java
package com.github.wf.server.controller;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.*;
import com.github.wf.server.dto.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;

class InstanceControllerTest {
    private WorkflowEngine engine;
    private DefinitionController defCtrl;
    private InstanceController instCtrl;
    private TaskController taskCtrl;

    @BeforeEach
    void setUp() {
        engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .build();
        defCtrl = new DefinitionController(engine);
        instCtrl = new InstanceController(engine);
        taskCtrl = new TaskController(engine);
    }

    @Test
    void fullFlowStartToListToComplete() {
        defCtrl.deploy(new DeployRequest() {{ setYaml("""
                id: f
                version: 1
                nodes:
                  - id: s
                    type: startEvent
                  - id: t
                    type: userTask
                    name: 审批
                    assignee: "user1"
                  - id: e
                    type: endEvent
                transitions:
                  - from: s
                    to: t
                  - from: t
                    to: e
                """); }});

        var inst = instCtrl.start(new StartInstanceRequest() {{
            setDefinitionId("f"); setVariables(Map.of("x", 1));
        }});
        assertThat(inst.getStatus()).isEqualTo("RUNNING");

        var tasks = taskCtrl.list("user1", null, inst.getId(), null);
        assertThat(tasks).hasSize(1);

        taskCtrl.complete(tasks.get(0).get("id").toString(),
                new CompleteTaskRequest() {{ setComment("ok"); }});

        var updated = instCtrl.get(inst.getId());
        assertThat(updated.getStatus()).isEqualTo("COMPLETED");
    }
}
```

- [ ] **Step 5: Run all server tests**

Run: `mvn test -pl workflow-engine-server -q`
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: add InstanceController, TaskController, and DTOs"
```

---

## Phase 2: Web Module (React + ReactFlow)

### Task 5: Scaffold Vite + React + ReactFlow project

**Files:**
- Create: `D:\workflow-engine\workflow-engine-web\package.json`
- Create: `D:\workflow-engine\workflow-engine-web\vite.config.ts`
- Create: `D:\workflow-engine\workflow-engine-web\tsconfig.json`
- Create: `D:\workflow-engine\workflow-engine-web\tsconfig.node.json`
- Create: `D:\workflow-engine\workflow-engine-web\index.html`
- Create: `D:\workflow-engine\workflow-engine-web\src\main.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\App.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\index.css`
- Create: `D:\workflow-engine\workflow-engine-web\postcss.config.js`
- Create: `D:\workflow-engine\workflow-engine-web\tailwind.config.js`

- [ ] **Step 1: Write package.json**

```json
{
  "name": "workflow-engine-web",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "@xyflow/react": "^12.3.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.1",
    "autoprefixer": "^10.4.19",
    "postcss": "^8.4.38",
    "tailwindcss": "^3.4.4",
    "typescript": "^5.5.3",
    "vite": "^5.3.1"
  }
}
```

- [ ] **Step 2: Write vite.config.ts**

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: { '/api': 'http://localhost:8080' }
  }
});
```

- [ ] **Step 3: Write index.html**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Workflow Designer</title>
</head>
<body class="bg-gray-900 text-white">
  <div id="root"></div>
  <script type="module" src="/src/main.tsx"></script>
</body>
</html>
```

- [ ] **Step 4: Write main.tsx**

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode><App /></React.StrictMode>
);
```

- [ ] **Step 5: Write App.tsx (tab navigation skeleton)**

```tsx
import { useState } from 'react';

export default function App() {
  const [tab, setTab] = useState<'designer' | 'monitor'>('designer');

  return (
    <div className="h-screen flex flex-col">
      <header className="bg-gray-800 border-b border-gray-700 px-4 py-2 flex gap-4">
        <button
          onClick={() => setTab('designer')}
          className={`px-4 py-1 rounded ${tab === 'designer' ? 'bg-blue-600' : 'bg-gray-700'}`}
        >Designer</button>
        <button
          onClick={() => setTab('monitor')}
          className={`px-4 py-1 rounded ${tab === 'monitor' ? 'bg-blue-600' : 'bg-gray-700'}`}
        >Monitor</button>
      </header>
      <main className="flex-1 overflow-hidden">
        {tab === 'designer' ? <p className="p-8 text-gray-400">Designer — coming soon</p> : null}
        {tab === 'monitor' ? <p className="p-8 text-gray-400">Monitor — coming soon</p> : null}
      </main>
    </div>
  );
}
```

- [ ] **Step 6: Write Tailwind config files**

postcss.config.js:
```js
export default { plugins: { tailwindcss: {}, autoprefixer: {} } };
```

tailwind.config.js:
```js
export default { content: ['./index.html', './src/**/*.{ts,tsx}'], theme: { extend: {} }, plugins: [] };
```

index.css:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

- [ ] **Step 7: Install dependencies and verify**

Run: `cd /d/workflow-engine/workflow-engine-web && npm install && npx tsc --noEmit`
Expected: no TypeScript errors.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: scaffold React+Vite+ReactFlow web project"
```

---

### Task 6: 7 custom ReactFlow node components

**Files:**
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\nodes\StartEventNode.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\nodes\EndEventNode.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\nodes\UserTaskNode.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\nodes\ServiceTaskNode.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\nodes\ExclusiveGatewayNode.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\nodes\ParallelGatewayNode.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\nodes\InclusiveGatewayNode.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\nodes\index.ts`

- [ ] **Step 1: StartEventNode.tsx**

```tsx
import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function StartEventNode({ data }: NodeProps) {
  return (
    <div className="relative">
      <Handle type="source" position={Position.Bottom} className="!bg-green-500" />
      <div className="w-9 h-9 rounded-full bg-green-500 border-2 border-green-600
                      flex items-center justify-center text-xs font-bold text-white shadow-lg">
        S
      </div>
      <div className="text-center text-xs text-gray-300 mt-1">{data.name as string}</div>
    </div>
  );
}
```

- [ ] **Step 2: EndEventNode.tsx**

```tsx
import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function EndEventNode({ data }: NodeProps) {
  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-red-400" />
      <div className="w-9 h-9 rounded-full border-3 border-red-500 bg-gray-800
                      flex items-center justify-center text-xs text-red-400 shadow-lg"
           style={{ borderWidth: 3 }}>
        E
      </div>
      <div className="text-center text-xs text-gray-300 mt-1">{data.name as string}</div>
    </div>
  );
}
```

- [ ] **Step 3: UserTaskNode.tsx**

```tsx
import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function UserTaskNode({ data }: NodeProps) {
  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-blue-400" />
      <div className="min-w-[120px] px-4 py-2 rounded-lg bg-blue-600 border border-blue-500
                      flex items-center gap-2 text-sm text-white shadow-lg">
        <span className="text-lg">&#128100;</span>
        <span className="truncate max-w-[100px]">{data.name as string}</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-blue-400" />
    </div>
  );
}
```

- [ ] **Step 4: ServiceTaskNode.tsx**

```tsx
import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function ServiceTaskNode({ data }: NodeProps) {
  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-purple-400" />
      <div className="min-w-[120px] px-4 py-2 rounded-lg bg-purple-700 border border-purple-500
                      flex items-center gap-2 text-sm text-white shadow-lg">
        <span className="text-lg">&#9881;</span>
        <span className="truncate max-w-[100px]">{data.name as string}</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-purple-400" />
    </div>
  );
}
```

- [ ] **Step 5: Gateway nodes (diamond shape)**

All three gateways share the diamond pattern. Key difference: color and symbol.

ExclusiveGatewayNode.tsx:
```tsx
import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function ExclusiveGatewayNode({ data }: NodeProps) {
  return (
    <div className="relative flex flex-col items-center">
      <Handle type="target" position={Position.Top} className="!bg-orange-400" />
      <div className="w-8 h-8 bg-orange-500 rotate-45 border border-orange-400 shadow-lg" />
      <Handle type="source" position={Position.Bottom} className="!bg-orange-400" />
      <div className="text-center text-xs text-gray-300 mt-2">{data.name as string}</div>
    </div>
  );
}
```

ParallelGatewayNode.tsx:
```tsx
import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function ParallelGatewayNode({ data }: NodeProps) {
  return (
    <div className="relative flex flex-col items-center">
      <Handle type="target" position={Position.Top} className="!bg-blue-400" />
      <div className="w-8 h-8 bg-blue-600 rotate-45 border border-blue-400
                      flex items-center justify-center shadow-lg">
        <span className="text-white text-lg font-bold" style={{ transform: 'rotate(-45deg)' }}>+</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-blue-400" />
    </div>
  );
}
```

InclusiveGatewayNode.tsx:
```tsx
import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function InclusiveGatewayNode({ data }: NodeProps) {
  return (
    <div className="relative flex flex-col items-center">
      <Handle type="target" position={Position.Top} className="!bg-purple-400" />
      <div className="w-8 h-8 bg-purple-600 rotate-45 border border-purple-400
                      flex items-center justify-center shadow-lg">
        <span className="text-white text-base font-bold" style={{ transform: 'rotate(-45deg)' }}>~</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-purple-400" />
    </div>
  );
}
```

- [ ] **Step 6: nodes/index.ts — register all custom nodes**

```ts
import StartEventNode from './StartEventNode';
import EndEventNode from './EndEventNode';
import UserTaskNode from './UserTaskNode';
import ServiceTaskNode from './ServiceTaskNode';
import ExclusiveGatewayNode from './ExclusiveGatewayNode';
import ParallelGatewayNode from './ParallelGatewayNode';
import InclusiveGatewayNode from './InclusiveGatewayNode';

export const nodeTypes = {
  startEvent: StartEventNode,
  endEvent: EndEventNode,
  userTask: UserTaskNode,
  serviceTask: ServiceTaskNode,
  exclusiveGateway: ExclusiveGatewayNode,
  parallelGateway: ParallelGatewayNode,
  inclusiveGateway: InclusiveGatewayNode,
};
```

- [ ] **Step 7: Verify TypeScript compiles**

Run: `cd /d/workflow-engine/workflow-engine-web && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: add 7 custom ReactFlow node components"
```

---

### Task 7: Designer page — palette + canvas + property panel

**Files:**
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\DesignerPage.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\NodePalette.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\FlowCanvas.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\PropertyPanel.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\api\client.ts`

- [ ] **Step 1: API client**

```ts
// src/api/client.ts
const BASE = '/api';

export async function deployDefinition(yaml: string) {
  const res = await fetch(`${BASE}/definitions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ yaml })
  });
  return res.json();
}

export async function getDefinitionGraph(id: string) {
  const res = await fetch(`${BASE}/definitions/${id}/graph`);
  return res.json();
}

export async function startInstance(definitionId: string, variables: Record<string, unknown>) {
  const res = await fetch(`${BASE}/instances`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ definitionId, variables })
  });
  return res.json();
}

export async function listInstances(params?: Record<string, string>) {
  const q = new URLSearchParams(params).toString();
  const res = await fetch(`${BASE}/instances${q ? `?${q}` : ''}`);
  return res.json();
}

export async function getInstance(id: string) {
  const res = await fetch(`${BASE}/instances/${id}`);
  return res.json();
}

export async function queryTasks(params?: Record<string, string>) {
  const q = new URLSearchParams(params).toString();
  const res = await fetch(`${BASE}/tasks${q ? `?${q}` : ''}`);
  return res.json();
}

export async function completeTask(id: string, variables?: Record<string, unknown>, comment?: string) {
  await fetch(`${BASE}/tasks/${id}/complete`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ variables, comment })
  });
}

export async function resumeInstance(id: string) {
  await fetch(`${BASE}/instances/${id}/resume`, { method: 'POST' });
}

export async function terminateInstance(id: string, reason?: string) {
  await fetch(`${BASE}/instances/${id}/terminate`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason })
  });
}
```

- [ ] **Step 2: NodePalette.tsx**

```tsx
const PALETTE_ITEMS = [
  { type: 'startEvent', label: 'Start', color: 'bg-green-500', shape: 'rounded-full' },
  { type: 'endEvent', label: 'End', color: 'border-2 border-red-500', shape: 'rounded-full' },
  { type: 'userTask', label: 'User Task', color: 'bg-blue-600', shape: 'rounded-lg' },
  { type: 'serviceTask', label: 'Service Task', color: 'bg-purple-700', shape: 'rounded-lg' },
  { type: 'exclusiveGateway', label: 'Excl. Gate', color: 'bg-orange-500', shape: 'rotate-45' },
  { type: 'parallelGateway', label: 'Par. Gate', color: 'bg-blue-600', shape: 'rotate-45' },
  { type: 'inclusiveGateway', label: 'Incl. Gate', color: 'bg-purple-600', shape: 'rotate-45' },
];

export default function NodePalette() {
  const onDragStart = (e: React.DragEvent, nodeType: string) => {
    e.dataTransfer.setData('application/reactflow', nodeType);
    e.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div className="w-36 bg-gray-800 border-r border-gray-700 p-2 flex flex-col gap-1">
      <div className="text-xs text-gray-500 text-center mb-2">Drag to canvas</div>
      {PALETTE_ITEMS.map(item => (
        <div key={item.type}
          draggable
          onDragStart={(e) => onDragStart(e, item.type)}
          className="bg-gray-700 hover:bg-gray-600 rounded p-2 text-xs cursor-grab
                     flex items-center gap-2 transition-colors"
        >
          <span className={`w-4 h-4 inline-block ${item.color} ${item.shape}`} />
          {item.label}
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 3: FlowCanvas.tsx**

```tsx
import { useCallback, useRef } from 'react';
import {
  ReactFlow, Background, Controls, MiniMap,
  useNodesState, useEdgesState, addEdge, Connection,
  type Node, type Edge
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { nodeTypes } from './nodes';

const initialNodes: Node[] = [];
const initialEdges: Edge[] = [];

export default function FlowCanvas({ onNodeSelect }: { onNodeSelect: (node: Node | null) => void }) {
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  let nodeId = useRef(0);

  const onConnect = useCallback((params: Connection) =>
    setEdges(eds => addEdge(params, eds)), [setEdges]);

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    const type = e.dataTransfer.getData('application/reactflow');
    if (!type || !reactFlowWrapper.current) return;

    const bounds = reactFlowWrapper.current.getBoundingClientRect();
    const position = { x: e.clientX - bounds.left - 60, y: e.clientY - bounds.top - 20 };

    const newNode: Node = {
      id: `node_${++nodeId.current}`,
      type,
      position,
      data: { name: type, assignee: '', candidateGroups: [], handlerClass: '' }
    };
    setNodes(nds => [...nds, newNode]);
  }, [setNodes]);

  const onNodeClick = useCallback((_: unknown, node: Node) => onNodeSelect(node), [onNodeSelect]);

  return (
    <div ref={reactFlowWrapper} className="flex-1 h-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onNodeClick={onNodeClick}
        nodeTypes={nodeTypes}
        fitView
      >
        <Background />
        <Controls />
        <MiniMap nodeColor={n => n.type === 'userTask' ? '#2563eb' :
          n.type === 'startEvent' ? '#22c55e' : '#6b7280'} />
      </ReactFlow>
    </div>
  );
}
```

- [ ] **Step 4: PropertyPanel.tsx**

```tsx
import type { Node } from '@xyflow/react';

export default function PropertyPanel({ node }: { node: Node | null }) {
  if (!node) {
    return (
      <div className="w-56 bg-gray-800 border-l border-gray-700 p-4 text-sm text-gray-500">
        Select a node to edit
      </div>
    );
  }

  return (
    <div className="w-56 bg-gray-800 border-l border-gray-700 p-4 text-sm">
      <h3 className="text-gray-300 font-bold mb-3">{node.type}</h3>
      <label className="block mb-2">
        <span className="text-gray-400 text-xs">Name</span>
        <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
          value={(node.data.name as string) || ''}
          onChange={e => { node.data = { ...node.data, name: e.target.value }; }} />
      </label>

      {node.type === 'userTask' && (
        <>
          <label className="block mb-2">
            <span className="text-gray-400 text-xs">Assignee</span>
            <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
              value={(node.data.assignee as string) || ''} placeholder="e.g. ${applicant}"
              onChange={e => { node.data = { ...node.data, assignee: e.target.value }; }} />
          </label>
          <label className="block mb-2">
            <span className="text-gray-400 text-xs">Candidate Groups</span>
            <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
              value={(node.data.candidateGroups as string[])?.join(', ') || ''}
              placeholder="comma-separated"
              onChange={e => { node.data = { ...node.data, candidateGroups: e.target.value.split(',').map(s => s.trim()) }; }} />
          </label>
        </>
      )}

      {node.type === 'serviceTask' && (
        <label className="block mb-2">
          <span className="text-gray-400 text-xs">Handler Class</span>
          <input className="w-full bg-gray-700 rounded px-2 py-1 text-white text-sm mt-0.5"
            value={(node.data.handlerClass as string) || ''}
            placeholder="com.myapp.Handler"
            onChange={e => { node.data = { ...node.data, handlerClass: e.target.value }; }} />
        </label>
      )}
    </div>
  );
}
```

- [ ] **Step 5: DesignerPage.tsx**

```tsx
import { useState } from 'react';
import type { Node } from '@xyflow/react';
import NodePalette from './NodePalette';
import FlowCanvas from './FlowCanvas';
import PropertyPanel from './PropertyPanel';

export default function DesignerPage() {
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);

  return (
    <div className="flex h-full">
      <NodePalette />
      <FlowCanvas onNodeSelect={setSelectedNode} />
      <PropertyPanel node={selectedNode} />
    </div>
  );
}
```

- [ ] **Step 6: Wire into App.tsx**

Update App.tsx to use DesignerPage:
```tsx
import DesignerPage from './designer/DesignerPage';
// in the main section:
{tab === 'designer' ? <DesignerPage /> : null}
```

- [ ] **Step 7: Verify TypeScript compiles**

Run: `cd /d/workflow-engine/workflow-engine-web && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: add Designer page with palette, canvas, and property panel"
```

---

### Task 8: Monitor page — instance list + flow view + task panel

**Files:**
- Create: `D:\workflow-engine\workflow-engine-web\src\monitor\MonitorPage.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\monitor\InstanceList.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\monitor\InstanceFlow.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\monitor\TaskPanel.tsx`

- [ ] **Step 1: InstanceList.tsx**

```tsx
interface Instance {
  id: string; definitionId: string; status: string;
  variables: Record<string, unknown>;
  activeNodeIds: string[];
}

export default function InstanceList({ onSelect, selectedId, instances }:
    { onSelect: (id: string) => void, selectedId: string | null, instances: Instance[] }) {

  return (
    <div className="w-48 bg-gray-800 border-r border-gray-700 p-2 overflow-y-auto">
      <div className="text-xs text-gray-500 mb-2">Instances</div>
      {instances.length === 0 && (
        <div className="text-gray-600 text-xs">None started</div>
      )}
      {instances.map(inst => (
        <div key={inst.id}
          onClick={() => onSelect(inst.id)}
          className={`p-2 rounded mb-1 cursor-pointer text-xs
            ${selectedId === inst.id ? 'bg-blue-600' : 'bg-gray-700 hover:bg-gray-600'}`}
        >
          <div className="flex items-center gap-1">
            <span className={`w-2 h-2 rounded-full inline-block
              ${inst.status === 'RUNNING' ? 'bg-green-500' :
                inst.status === 'COMPLETED' ? 'bg-blue-500' :
                inst.status === 'SUSPENDED' ? 'bg-yellow-500' : 'bg-red-500'}`} />
            <span className="text-gray-300">{inst.id.substring(0, 5)}</span>
          </div>
          <div className="text-gray-500 text-[10px] mt-0.5">{inst.definitionId}</div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: InstanceFlow.tsx**

```tsx
import { ReactFlow, Background, type Node, type Edge } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { nodeTypes } from '../designer/nodes';

export default function InstanceFlow({ nodes, edges }: { nodes: Node[], edges: Edge[] }) {
  // Color nodes by active status
  const styledNodes = nodes.map(n => ({
    ...n,
    style: n.data.active
      ? { border: '2px solid #3b82f6', boxShadow: '0 0 12px rgba(59,130,246,0.5)' }
      : n.data.status === 'done' ? { opacity: 0.4 } : {}
  }));

  return (
    <div className="flex-1 h-full">
      <ReactFlow nodes={styledNodes} edges={edges} nodeTypes={nodeTypes}
        fitView nodesDraggable={false} nodesConnectable={false} elementsSelectable={false}>
        <Background />
      </ReactFlow>
    </div>
  );
}
```

- [ ] **Step 3: TaskPanel.tsx**

```tsx
interface TaskInfo { id: string; nodeId: string; assignee: string; status: string; }

export default function TaskPanel({ tasks, onComplete, onReject }:
    { tasks: TaskInfo[], onComplete: (id: string) => void, onReject: (id: string) => void }) {

  if (tasks.length === 0) return (
    <div className="bg-gray-800 border-t border-gray-700 p-3 text-xs text-gray-500">
      No pending tasks for this instance
    </div>
  );

  return (
    <div className="bg-gray-800 border-t border-gray-700 p-3">
      <div className="text-xs text-gray-400 mb-2">Tasks</div>
      {tasks.map(t => (
        <div key={t.id} className="flex items-center justify-between py-1 text-sm">
          <span className="text-gray-300">{t.nodeId}</span>
          <span className="text-gray-500">→ {t.assignee}</span>
          <div className="flex gap-2">
            <button onClick={() => onComplete(t.id)}
              className="bg-green-600 hover:bg-green-500 text-white text-xs px-2 py-0.5 rounded">
              Complete
            </button>
            <button onClick={() => onReject(t.id)}
              className="bg-red-600 hover:bg-red-500 text-white text-xs px-2 py-0.5 rounded">
              Reject
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 4: MonitorPage.tsx**

```tsx
import { useState, useEffect, useCallback } from 'react';
import type { Node, Edge } from '@xyflow/react';
import InstanceList from './InstanceList';
import InstanceFlow from './InstanceFlow';
import TaskPanel from './TaskPanel';
import { listInstances, queryTasks, completeTask, getDefinitionGraph, resumeInstance, terminateInstance } from '../api/client';

export default function MonitorPage() {
  const [instances, setInstances] = useState<any[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [tasks, setTasks] = useState<any[]>([]);

  // Poll instances
  useEffect(() => {
    const poll = () => listInstances().then(setInstances);
    poll();
    const interval = setInterval(poll, 3000);
    return () => clearInterval(interval);
  }, []);

  // Load graph + tasks when instance selected
  const loadInstance = useCallback(async (id: string) => {
    setSelectedId(id);
    const inst = instances.find(i => i.id === id);
    if (!inst) return;
    try {
      const graph = await getDefinitionGraph(inst.definitionId);
      // Mark active nodes
      const activeIds: string[] = inst.activeNodeIds || [];
      const styledNodes = (graph.nodes || []).map((n: any) => ({
        ...n,
        data: {
          ...n.data,
          active: activeIds.includes(n.id),
          status: activeIds.includes(n.id) ? 'active' : 'done'
        }
      }));
      setNodes(styledNodes);
      setEdges(graph.edges || []);
    } catch { /* definition may not exist */ }

    const ts = await queryTasks({ instanceId: id });
    setTasks(ts.filter((t: any) => t.status === 'PENDING'));
  }, [instances]);

  const handleComplete = async (taskId: string) => {
    await completeTask(taskId);
    if (selectedId) loadInstance(selectedId);
  };

  const handleReject = async (taskId: string) => {
    // reject via API
    if (selectedId) loadInstance(selectedId);
  };

  const handleResume = async () => {
    if (!selectedId) return;
    await resumeInstance(selectedId);
    listInstances().then(setInstances);
    loadInstance(selectedId);
  };

  const handleTerminate = async () => {
    if (!selectedId) return;
    await terminateInstance(selectedId);
    listInstances().then(setInstances);
    loadInstance(selectedId);
  };

  const selectedInst = instances.find(i => i.id === selectedId);

  return (
    <div className="flex flex-col h-full">
      <div className="flex flex-1 overflow-hidden">
        <InstanceList instances={instances} selectedId={selectedId} onSelect={loadInstance} />
        <InstanceFlow nodes={nodes} edges={edges} />
      </div>
      {selectedInst && selectedInst.status === 'SUSPENDED' && (
        <div className="bg-yellow-900 border-t border-yellow-700 p-2 flex gap-2">
          <button onClick={handleResume}
            className="bg-yellow-600 hover:bg-yellow-500 text-white text-xs px-3 py-1 rounded">
            Resume
          </button>
          <button onClick={handleTerminate}
            className="bg-red-600 hover:bg-red-500 text-white text-xs px-3 py-1 rounded">
            Terminate
          </button>
        </div>
      )}
      <TaskPanel tasks={tasks} onComplete={handleComplete} onReject={handleReject} />
    </div>
  );
}
```

- [ ] **Step 5: Wire MonitorPage into App.tsx**

```tsx
import MonitorPage from './monitor/MonitorPage';
// replace the monitor placeholder:
{tab === 'monitor' ? <MonitorPage /> : null}
```

- [ ] **Step 6: Verify TypeScript**

Run: `cd /d/workflow-engine/workflow-engine-web && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: add Monitor page with instance list, flow view, and task panel"
```

---

### Task 9: Designer — Deploy button + YAML export

**Files:**
- Modify: `D:\workflow-engine\workflow-engine-web\src\designer\DesignerPage.tsx`
- Create: `D:\workflow-engine\workflow-engine-web\src\designer\graphToYaml.ts`

- [ ] **Step 1: graphToYaml.ts — Convert ReactFlow graph back to YAML**

```ts
import type { Node, Edge } from '@xyflow/react';

interface NodeData {
  name?: string; assignee?: string; candidateGroups?: string[];
  handlerClass?: string; dynamicRouter?: string;
}

export function graphToYaml(nodes: Node[], edges: Edge[], name: string = 'workflow'): string {
  const lines: string[] = [];
  lines.push(`id: ${name}`);
  lines.push('version: 1');
  lines.push('nodes:');

  // Find start/end
  const starts = nodes.filter(n => n.type === 'startEvent');
  const ends = nodes.filter(n => n.type === 'endEvent');

  // Write nodes
  for (const node of nodes) {
    const data = node.data as NodeData;
    lines.push(`  - id: ${node.id}`);
    lines.push(`    type: ${node.type}`);
    if (data.name) lines.push(`    name: "${data.name}"`);
    if (data.assignee && node.type === 'userTask') lines.push(`    assignee: "${data.assignee}"`);
    if (data.handlerClass && node.type === 'serviceTask') lines.push(`    handlerClass: "${data.handlerClass}"`);
    if (Array.isArray(data.candidateGroups) && data.candidateGroups.length > 0) {
      lines.push(`    candidateGroups: [${data.candidateGroups.map(g => `"${g}"`).join(', ')}]`);
    }
  }

  // Write transitions (edges)
  lines.push('transitions:');
  for (const edge of edges) {
    const label = edge.data?.label || edge.data?.condition;
    if (label) {
      if (label === 'default') {
        lines.push(`  - from: ${edge.source}`);
        lines.push(`    to: ${edge.target}`);
        lines.push(`    type: default`);
      } else {
        lines.push(`  - from: ${edge.source}`);
        lines.push(`    to: ${edge.target}`);
        lines.push(`    type: conditional`);
        lines.push(`    expr: "${label}"`);
      }
    } else {
      lines.push(`  - from: ${edge.source}`);
      lines.push(`    to: ${edge.target}`);
    }
  }

  return lines.join('\n');
}
```

- [ ] **Step 2: Update DesignerPage with Deploy button**

Add to DesignerPage.tsx:
```tsx
import { useCallback } from 'react';
import { useNodesState, useEdgesState } from '@xyflow/react'; // move from FlowCanvas
import { deployDefinition } from '../api/client';
import { graphToYaml } from './graphToYaml';

// In DesignerPage, add a deploy handler:
const handleDeploy = async () => {
  const yaml = graphToYaml(nodes, edges, 'my-workflow');
  try {
    const result = await deployDefinition(yaml);
    alert(`Deployed: ${result.id} v${result.version}`);
  } catch (e: any) { alert('Deploy failed: ' + e.message); }
};
```

Lift nodes/edges state from FlowCanvas up to DesignerPage so deploy can access them. Pass them as props to FlowCanvas.

- [ ] **Step 3: Verify**

Run: `cd /d/workflow-engine/workflow-engine-web && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: add Deploy button and graph-to-YAML converter"
```

---

## Phase 3: End-to-End Integration

### Task 10: Run full stack and verify

- [ ] **Step 1: Start the server**

```bash
cd /d/workflow-engine && mvn spring-boot:run -pl workflow-engine-server
```

- [ ] **Step 2: Start the frontend**

```bash
cd /d/workflow-engine/workflow-engine-web && npm run dev
```

- [ ] **Step 3: Test flow**

1. Open http://localhost:3000
2. Designer tab: drag Start → UserTask → End onto canvas, connect them
3. Click Deploy → should succeed
4. Switch to Monitor tab → start an instance from that definition
5. See instance appear → click to view flow with active node highlighted
6. Complete the task

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "chore: final integration verification"
```

---

## Spec Coverage Check

| Spec Section | Task(s) |
|---|---|
| Architecture (modules) | 1, 5 |
| REST API — Definitions | 3 |
| REST API — Instances | 4 |
| REST API — Tasks | 4 |
| Graph Format | 3 |
| Node Visual Style (7 types) | 6 |
| Designer Layout (palette/canvas/props) | 7 |
| Monitor Layout (list/flow/tasks) | 8 |
| Deploy + YAML export | 9 |
| End-to-end | 10 |
