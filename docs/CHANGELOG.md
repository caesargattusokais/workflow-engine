# Workflow Engine — Changelog & Status

**Last updated:** 2026-07-19  
**Branch:** 5.1.0

---

## Current Architecture

```
D:\workflow-engine\
├── workflow-engine-core/          # Engine library (Java 17)
├── workflow-engine-memory/        # InMemory + JDBC persistence, Redis distributed lock
├── workflow-engine-mock-ldap/     # Mock LDAP org service (4 users, 4 groups)
├── workflow-engine-server/        # Spring Boot REST API
├── workflow-engine-web/           # React visual designer + monitor dashboard
└── workflow-engine-examples/      # Usage examples
```

**Dependency chain:** `core` ← `memory` ← `server` (server also depends on `mock-ldap`). `web` is standalone, talks to server via REST.

---

## Node Types (8)

| Type | Runner | Description |
|------|--------|-------------|
| startEvent | StartEventRunner | Moves to first outgoing transition |
| endEvent | EndEventRunner | Marks execution COMPLETED; sub-process: wakes parent |
| userTask | UserTaskRunner | Creates Task, HTTP callback, boundary timer, OrgService notification |
| serviceTask | ServiceTaskRunner | Executes handler (code/HTTP), retry with backoff, result/exception routing |
| exclusiveGateway | ExclusiveGatewayRunner | Evaluates conditional first, default last (BPMN spec) |
| parallelGateway | ParallelGatewayRunner | Fork: creates child executions; Join: waits for all siblings |
| inclusiveGateway | InclusiveGatewayRunner | Evaluates conditions, forks matching branches |
| callActivity | CallActivityRunner | Starts child process instance, sync/async modes |

---

## Transition Types (6)

`direct`, `conditional`, `default`, `result`, `exception`, `timeout`

---

## Key Design Decisions (post bug-fix)

### Version-Safe Definition Lookup
Instances pin to the `definitionVersion` they started with. `resolveDefinition()` uses `findByIdAndVersion()` with fallback to `findLatestById()`. Applied in `trigger()`, `completeTask()`, `rejectTask()`, `delegateTask()`, and `EndEventRunner`.

### ExclusiveGateway Default Branch
Default branch is always evaluated **last**, regardless of YAML ordering. This follows BPMN spec — conditional branches must be given priority over the default fallback.

### Boundary Timer Lifecycle
- `timerKey` (e.g. `review_boundaryTimerFired`) is only set when the timer actually fires in `trigger()`, NOT at task creation time.
- On timeout, the PENDING task is cancelled (status → REJECTED), then the execution routes to the timeout edge.

### completeTask Routing
After completing a UserTask, the engine evaluates outgoing transitions in order: direct → conditional (evaluated) → default. This allows UserTasks with conditional edges (e.g. `type: conditional, expr: "approved == true"`) without requiring an explicit gateway.

### Exception Handling in trigger()
When a NodeRunner throws an exception, the engine suspends the execution and instance (status → SUSPENDED), records the suspend reason in instance variables, and notifies state listeners. Previously exceptions were silently swallowed.

### SUSPENDED Status
`InstanceStatus.SUSPENDED` is a non-terminal state. `completedAt` is only set for `COMPLETED` and `TERMINATED`.

### delegateTask
The original task is set to `TaskStatus.DELEGATED` (not left as PENDING), and a new PENDING task is created for the delegate. DELEGATED tasks are excluded from pending task queries.

### group: Assignee
`assignee: "group:managers"` adds "managers" to `candidateGroups` (not to `assignee`). YAML `candidateGroups` are **merged** with any groups added by `group:` assignee, not replaced.

### OrgService Decoupling
`UserTaskRunner` uses the `OrgService` interface for notifications (`sendTaskNotification()`), not the concrete `FeishuOrgService` class. New OrgService implementations only need to override `sendTaskNotification()`.

### Sub-Process (CallActivity) Completion
When a child process reaches EndEvent, `EndEventRunner`:
1. Checks if this is the last active execution in the child
2. Writes back output variables to the parent instance
3. Advances the parent execution past the CallActivity
4. Marks the child ProcessInstance as COMPLETED
5. Triggers the parent instance to continue

### YAML class→className Mapping
YAML `class` key maps to Java `className` field for both `GatewayConditionYaml` and `RouteYaml`, via SnakeYAML `FieldPropertyUtils` aliasing. This allows YAML like `class: "com.test.MyCondition"` to correctly parse into Java.

---

## SPI Layer

### ProcessRepository
```java
void save(ProcessDefinition d);
ProcessDefinition findById(String id);         // id:version key or delegate to findLatestById
ProcessDefinition findLatestById(String id);
ProcessDefinition findByIdAndVersion(String id, int version);  // NEW — version-safe lookup
List<ProcessDefinition> findAllVersions(String id);
```

### InstanceRepository
```java
/** Find all non-completed executions (both ACTIVE and WAITING) for an instance. */
List<Execution> findActiveExecutions(String instanceId);
```

### OrgService
```java
/** Send a task notification (e.g. approval card) to a user.
 *  Returns a message ID if sent, or null if not supported.
 *  Default implementation does nothing — override in implementations that support push notifications. */
default String sendTaskNotification(String assignee, String taskId, String instanceId,
                                     String taskName, String applicant,
                                     String baseUrl, Map<String, Object> variables) {
    return null;
}
```

---

## Persistence Implementations

| Profile | Repos | Lock | Cache |
|---------|-------|------|-------|
| Default (no profile) | JDBC repos + MySQL | LocalInstanceLockManager | Write-through ConcurrentHashMap |
| `memory` | InMemory repos | LocalInstanceLockManager | N/A |
| `redis` | JDBC repos + Redis | RedisInstanceLockManager | Redis L1 cache → MySQL |

**JDBC schema** includes `parent_instance_id` and `parent_execution_id` columns on `process_instance` table for sub-process support.

---

## Frontend (workflow-engine-web)

- **Stack:** Vite + React 18 + @xyflow/react + Tailwind CSS
- **DesignerPage:** Draft list (left), FlowCanvas (center), PropertyPanel (right)
- **MonitorPage:** Instance list + task panel
- **Dashboard:** Stats dashboard
- **i18n:** Chinese/English translations (Zustand-based, persisted to localStorage)
- **graphToYaml.ts / yamlToGraph.ts:** Bidirectional conversion between ReactFlow graph model and YAML DSL
- **8 custom node components** (one per type including CallActivity)
- **handleReject:** Calls the `/api/tasks/{id}/reject` endpoint
- **PropertyPanel:** Uses `setNodes()` to trigger re-render on data change

---

## REST API

All endpoints require `X-User-Id` header. `@CrossOrigin(origins = "*")`.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/definitions` | Deploy YAML |
| GET | `/api/definitions` | List definitions |
| GET | `/api/definitions/{id}` | Get definition |
| GET | `/api/definitions/{id}/graph` | ReactFlow graph JSON |
| DELETE | `/api/definitions/{id}` | Delete definition |
| POST | `/api/instances` | Start instance |
| GET | `/api/instances` | List instances (filter: status, definitionId) |
| GET | `/api/instances/{id}` | Get instance detail |
| GET | `/api/instances/{id}/history` | History list |
| POST | `/api/instances/{id}/resume` | Resume suspended |
| POST | `/api/instances/{id}/terminate` | Terminate |
| GET | `/api/tasks` | Query tasks (default status=PENDING) |
| POST | `/api/tasks/{id}/complete` | Complete task |
| POST | `/api/tasks/{id}/reject` | Reject task |
| POST | `/api/tasks/{id}/delegate` | Delegate task |

---

## Bug Fixes Applied (2026-07-19)

23 bugs fixed across engine, runners, persistence, and frontend:

| # | Bug | Fix |
|---|-----|-----|
| 1 | Credential leak in application.yml | **Skipped** per user request |
| 2 | Version drift — instances use findLatestById | Added `findByIdAndVersion()` + `resolveDefinition()` |
| 3 | ExclusiveGateway default evaluated first | Two-pass: conditional first, default last |
| 4 | Boundary timer timerKey set at task creation | timerKey only set when timer fires in trigger() |
| 5 | Timeout routing didn't cancel PENDING task | Cancel task (→ REJECTED) before routing |
| 6 | findActiveExecutions semantic inconsistency | Added JavaDoc clarifying behavior |
| 7 | completeTask doesn't evaluate conditional transitions | Added conditional + default routing |
| 8 | trigger history/listeners recorded outside runner.run() | Moved inside success block |
| 9 | trigger exceptions swallowed | Suspend execution + instance on exception |
| 10 | CallActivity sync mode returns false | Returns true |
| 11 | EndEventRunner doesn't mark child COMPLETED | Mark child ProcessInstance COMPLETED |
| 12 | delegateTask old task stays PENDING | Set old task to DELEGATED status |
| 13 | InMemoryProcessRepository.findById semantics mismatch | Handle id:version key, else delegate |
| 14 | InstanceController.list() null definitionId | Handle null safely |
| 15 | YAML GatewayConditionYaml class field mapping | Added class→className alias |
| 16 | Frontend handleReject calls complete API | Call reject API instead |
| 17 | PropertyPanel setNodes re-render | Use setNodes() to update state |
| 18 | trigger() runner exception handling | Suspend + record reason |
| 19 | EndEventRunner default constructor sub-process warning | Added warn log |
| 20 | UserTaskRunner coupled to FeishuOrgService | Decoupled via OrgService interface |
| 21 | Log levels — normal flow uses log.warn | Changed to log.info/debug |
| 22 | SUSPENDED sets completedAt | Only COMPLETED/TERMINATED set completedAt |
| 23 | ParallelGateway null outgoing check | Added null check |
| 24 | group: assignee not in candidateGroups | Add group to candidateGroups + merge with YAML groups |

---

## Test Coverage

81 tests total in `workflow-engine-core`:

| Test Class | Count | Coverage |
|------------|-------|----------|
| BugFixIntegrationTest | 10 | Version-safe lookup, gateway default-last, timeout cancel, conditional transitions, delegate, SUSPENDED completedAt, group: assignee |
| YamlProcessParserTest | 6 | YAML parsing, class→className mapping, result/exception routing |
| BoundaryTimerIntegrationTest | 2 | Timer fires + manual complete |
| CallActivityIntegrationTest | 2 | Sub-process start + non-existent definition |
| LeaveApprovalIntegrationTest | 2 | Full approval flow (long + short leave) |
| ServiceTaskRoutingIntegrationTest | 3 | Result routing, exception routing, suspend |
| GatewayRunnerTest | 4 | Exclusive (match + default), Parallel (fork + join) |
| ServiceTaskRunnerTest | 7 | Handler execution, retry, routing |
| SpelExpressionEvaluatorTest | 9 | SpEL expressions |
| NodeTest | 9 | Node model tests |
| ProcessDefinitionTest | 5 | Definition model tests |
| Other | 22 | Execution, TaskQuery, HTTP, builder, etc. |
