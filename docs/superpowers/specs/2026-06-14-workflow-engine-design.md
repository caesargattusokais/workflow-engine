# Workflow Engine Design Spec

**Date:** 2026-06-14  
**Language:** Java  
**Type:** Business Process Engine (审批流)  
**Package:** `com.github.wf`

---

## 1. Requirements Summary

| Dimension | Choice |
|-----------|--------|
| Language | Java (no external framework dependency in core) |
| Scenario | Business process / approval workflow |
| Process definition | Custom DSL (YAML primary, JSON secondary) |
| Node types | All: StartEvent, EndEvent, UserTask, ExclusiveGateway, ParallelGateway, InclusiveGateway, ServiceTask |
| Persistence | Pluggable SPI — memory implementation first, DB later |
| Deployment | Embedded SDK (JAR) |
| Execution model | Token-driven (Camunda/Flowable classic pattern) |
| Expression engine | SpEL (Spring Expression Language) |
| YAML parsing | SnakeYAML |

---

## 2. Module Architecture

```
D:\workflow-engine\
├── pom.xml                                  # Parent POM
├── workflow-engine-core/                    # Zero external deps except SnakeYAML
│   └── src/main/java/com/github/wf/
│       ├── model/                           # Graph model (POJOs)
│       │   ├── ProcessDefinition.java
│       │   ├── Node.java                    # Abstract base
│       │   ├── Transition.java
│       │   ├── Condition.java
│       │   ├── Deployment.java
│       │   └── node/
│       │       ├── StartEvent.java
│       │       ├── EndEvent.java
│       │       ├── UserTask.java
│       │       ├── ExclusiveGateway.java
│       │       ├── ParallelGateway.java
│       │       ├── InclusiveGateway.java
│       │       └── ServiceTask.java
│       ├── dsl/                             # DSL parsing
│       │   ├── ProcessParser.java           # Interface
│       │   ├── YamlProcessParser.java
│       │   └── JsonProcessParser.java
│       ├── engine/                          # Execution engine
│       │   ├── WorkflowEngine.java          # Facade
│       │   ├── WorkflowEngineBuilder.java
│       │   ├── Execution.java               # Token
│       │   ├── ExecutionContext.java
│       │   └── runner/
│       │       ├── NodeRunner.java          # Interface
│       │       ├── UserTaskRunner.java
│       │       ├── GatewayRunner.java
│       │       └── ServiceTaskRunner.java
│       ├── ext/                             # Extension points
│       │   ├── ConditionEvaluator.java      # Custom condition
│       │   ├── ProcessListener.java         # Lifecycle listener
│       │   └── DynamicRouter.java           # Dynamic routing
│       ├── task/                            # Task service
│       │   ├── Task.java
│       │   ├── TaskService.java
│       │   └── TaskQuery.java
│       ├── spi/                             # Persistence interfaces
│       │   ├── ProcessRepository.java
│       │   ├── InstanceRepository.java
│       │   └── TaskRepository.java
│       └── expression/
│           └── ExpressionEvaluator.java     # SpEL adapter
├── workflow-engine-memory/                  # In-memory persistence
│   └── src/main/java/com/github/wf/memory/
│       ├── InMemoryProcessRepository.java
│       ├── InMemoryInstanceRepository.java
│       └── InMemoryTaskRepository.java
└── workflow-engine-examples/                # Example workflows
    └── src/main/resources/examples/
        └── leave-approval.yaml
```

---

## 3. Core Domain Model

### 3.1 Process Definition (blueprint)

```
ProcessDefinition
  id: String
  name: String
  version: int
  nodes: Map<String, Node>
  transitions: List<Transition>

Node (abstract)
  id: String
  type: NodeType (START_EVENT | END_EVENT | USER_TASK |
                  SERVICE_TASK | EXCLUSIVE_GATEWAY |
                  PARALLEL_GATEWAY | INCLUSIVE_GATEWAY)
  name: String
  listeners: List<String>         # ProcessListener fully-qualified class names

Transition
  id: String
  from: String                    # Source node id
  to: String                      # Target node id (null for conditional transitions)
  type: TransitionType (DIRECT | CONDITIONAL | DEFAULT)
  condition: Condition            # Only for CONDITIONAL type

Condition
  type: EXPRESSION | JAVA_CLASS
  expr: String                    # SpEL expression, e.g. "${amount > 5000}"
  className: String               # ConditionEvaluator implementation class

Deployment
  id: String
  definitionId: String
  deployedAt: Instant
  source: String                  # Original YAML/JSON content
```

### 3.2 Runtime (instance & token)

```
ProcessInstance
  id: String
  definitionId: String
  status: RUNNING | COMPLETED | TERMINATED
  variables: Map<String, Object>
  activeNodeIds: Set<String>      # Snapshot for fast query

Execution (Token)
  id: String
  instanceId: String
  currentNodeId: String
  parentExecutionId: String       # null for root; set for parallel forks
  status: ACTIVE | WAITING | COMPLETED
```

#### Execution fork/join model

```
              root:exec-1
                  │
      ┌───────────┼───────────┐
    exec-2       exec-3       exec-4
  (finance)     (HR)         (legal)
      │            │            │
      └───────────┼───────────┘
              join (waits for all children)
              → root resumes single-threaded
```

- Fork: parallel/inclusive gateway creates child Executions
- Join: all siblings must arrive; parent resumes when all complete
- Exclusive gateway does NOT fork — picks one path inline

### 3.3 Task & History

```
Task
  id: String
  instanceId: String
  nodeId: String
  assignee: String
  candidateGroups: List<String>
  status: PENDING | COMPLETED | REJECTED | DELEGATED
  variables: Map<String, Object>
  createdAt: Instant
  completedAt: Instant

HistoricActivity
  id: String
  instanceId: String
  nodeId: String
  nodeName: String
  nodeType: NodeType
  executor: String               # Who acted (assignee for user tasks, "system" for auto)
  action: String                  # "enter" | "leave" | "complete" | "reject" | "delegate"
  timestamp: Instant
  comment: String

ProcessVariable
  name: String
  type: STRING | NUMBER | BOOLEAN | JSON
  value: Object
  scope: INSTANCE | NODE          # Instance-level or node-local
  writable: boolean               # Some vars can be read-only in sub-scopes
```

---

## 4. DSL Format (YAML)

### 4.1 Minimal example

```yaml
id: leave-approval
name: 请假审批
version: 1
nodes:
  - id: start
    type: startEvent
  - id: apply
    type: userTask
    name: 提交请假申请
    assignee: "${applicant}"
  - id: gateway
    type: exclusiveGateway
    conditions:
      - expr: "${days > 3}"
        to: manager-approve
      - class: "com.myapp.IsEmergency"
        to: general-manager
      - default: true
        to: department-manager
  - id: manager-approve
    type: userTask
    name: 总经理审批
    candidateGroups: ["manager"]
    listeners:
      enter: "com.myapp.NotifyManagerListener"
      leave: "com.myapp.AuditLogger"
  - id: department-manager
    type: userTask
    name: 部门经理审批
  - id: general-manager
    type: userTask
    name: 总经理审批
  - id: end
    type: endEvent
transitions:
  - from: start
    to: apply
  - from: apply
    to: gateway
  - from: gateway
    to: manager-approve        # conditional — matched by conditions above
    type: conditional
  - from: gateway
    to: department-manager
    type: conditional
  - from: gateway
    to: general-manager
    type: conditional
  - from: manager-approve
    to: end
  - from: department-manager
    to: end
  - from: general-manager
    to: end
```

### 4.2 Parallel gateway example

```yaml
nodes:
  - id: fork
    type: parallelGateway
  - id: finance-approve
    type: userTask
    name: 财务审批
  - id: hr-approve
    type: userTask
    name: HR审批
  - id: join
    type: parallelGateway

transitions:
  - from: fork
    to: finance-approve
  - from: fork
    to: hr-approve
  - from: finance-approve
    to: join
  - from: hr-approve
    to: join
  - from: join
    to: next-step
```

---

## 5. Engine Execution Algorithm

### 5.1 `WorkflowEngine.trigger(instanceId)`

Called after every external event (start process, complete task). Engine advances tokens until all executions are waiting on user tasks.

```
trigger(instanceId):
  lock instance
  while true:
    executions = active executions for this instance
    if executions is empty:
      instance.status = COMPLETED; return

    advanced = false
    for each exec in executions:
      node = definition.getNode(exec.currentNodeId)

      switch node.type:
        START_EVENT:
          moveToNext(exec); advanced = true

        END_EVENT:
          if exec is child: merge to parent; mark child COMPLETED
          else: instance completed
          advanced = true

        USER_TASK:
          task = findTask(instanceId, node.id)
          if task is null: create Task, exec WAITING; advanced = true
          // else: already waiting — skip

        SERVICE_TASK:
          invoke handler class; write result → variables
          moveToNext(exec); advanced = true

        EXCLUSIVE_GATEWAY:
          for each outgoing condition in order:
            if condition matches: moveToNext(exec, chosenTransition); break
          if none matched: moveToNext(exec, defaultTransition)
          advanced = true

        PARALLEL_GATEWAY:
          incoming = count of transitions INTO this node
          if incoming == 1:  // FORK
            for each outgoing transition:
              fork child execution
            mark exec WAITING (wait for children)
          else:  // JOIN
            siblings = findSiblingExecutions(exec)
            if all siblings arrived at this gateway:
              merge → parent execution, moveToNext(parent)
            else:
              mark exec WAITING
          advanced = true

        INCLUSIVE_GATEWAY:
          same as parallel, but only fork for conditions that match (0..N branches)

    if not advanced: break  // all executions waiting on user tasks

  unlock instance
```

### 5.2 Key design decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| trigger() sync/async | Synchronous | Embedded SDK — caller decides threading |
| Expression engine | SpEL | Spring built-in, sufficient power, no extra dep |
| YAML parsing | SnakeYAML | Lightweight, YAML-focused |
| Concurrency | Per-instance ReentrantLock | Same instance serialized; different instances parallel |

---

## 6. Extension Points

### 6.1 ConditionEvaluator

```java
package com.github.wf.ext;

public interface ConditionEvaluator {
    boolean evaluate(Map<String, Object> variables);
}
```

Referenced in DSL via `class: "com.myapp.MyCondition"`.

### 6.2 ProcessListener

```java
package com.github.wf.ext;

public interface ProcessListener {
    void onNodeEnter(String instanceId, String nodeId, Map<String, Object> variables);
    void onNodeLeave(String instanceId, String nodeId, Map<String, Object> variables);
}
```

Referenced in DSL via `listeners: { enter: "com.myapp.MyListener", leave: "..." }`.

### 6.3 DynamicRouter

```java
package com.github.wf.ext;

public interface DynamicRouter {
    String nextNode(String instanceId, String currentNodeId, Map<String, Object> variables);
}
```

When a user task has `dynamicRouter: "com.myapp.Router"`, after task completion the engine calls the router to determine the next node instead of following static transitions.

---

## 7. SPI (Persistence Interfaces)

```java
public interface ProcessRepository {
    void save(ProcessDefinition def);
    ProcessDefinition findById(String id);
    ProcessDefinition findLatestById(String id);
    List<ProcessDefinition> findAllVersions(String id);
}

public interface InstanceRepository {
    void save(ProcessInstance instance);
    ProcessInstance findById(String id);
    void update(ProcessInstance instance);
    List<ProcessInstance> findByDefinitionId(String defId);
    void saveHistoricActivity(HistoricActivity activity);
    List<HistoricActivity> findHistory(String instanceId);
}

public interface TaskRepository {
    void save(Task task);
    Task findById(String id);
    void update(Task task);
    List<Task> query(TaskQuery query);
}
```

---

## 8. Java API (embedded usage)

```java
// 1. Build engine
WorkflowEngine engine = WorkflowEngine.builder()
    .processRepository(new InMemoryProcessRepository())
    .instanceRepository(new InMemoryInstanceRepository())
    .taskRepository(new InMemoryTaskRepository())
    .build();

// 2. Deploy process definition
ProcessDefinition def = engine.deploy(new File("leave-approval.yaml"));

// 3. Start instance
ProcessInstance instance = engine.start(def.getId(), Map.of("applicant", "张三", "days", 5));

// 4. Query pending tasks
List<Task> tasks = engine.taskQuery()
    .assignee("张三")
    .candidateGroup("manager")
    .list();

// 5. Complete task
engine.completeTask(tasks.get(0).getId(), Map.of("approved", true), "同意请假");

// 6. Reject / delegate
engine.rejectTask(taskId, "信息不完整，请补充");
engine.delegateTask(taskId, "王五");

// 7. Query history
List<HistoricActivity> history = engine.history(instance.getId());

// 8. Terminate
engine.terminate(instance.getId(), "申请人撤销");
```

---

## 9. MVP Scope Boundaries

### MVP includes
- All 7 node types (StartEvent, EndEvent, UserTask, ServiceTask, ExclusiveGateway, ParallelGateway, InclusiveGateway)
- YAML + JSON DSL parsing
- Token-driven execution engine with fork/join
- Task CRUD: query, complete, reject, delegate
- All 3 extension points: ConditionEvaluator, ProcessListener, DynamicRouter
- HistoricActivity audit log
- Variable scoping (instance/node)
- In-memory persistence
- Per-instance concurrency safety

### MVP excludes
- Timer / scheduled events
- Signal / message events
- Sub-process / call activity
- Compensation / saga rollback
- Batch / async job executor
- Identity service (LDAP/org integration)
- RetryPolicy for service tasks
- DB persistence (SPI is ready, just no impl yet)
- BPMN XML support
- REST API layer

---

## 10. Test Strategy

- Unit tests for each NodeRunner in isolation
- Unit tests for DSL parser (valid YAML → ProcessDefinition)
- Unit tests for gateway logic (exclusive conditions, parallel fork/join, inclusive)
- Integration tests: full workflow scenarios (happy path + reject + delegate + terminate)
- Concurrency test: rapid-fire trigger() on same instance
- All tests use InMemory repositories
