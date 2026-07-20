# Workflow Engine — Changelog & Status

**Last updated:** 2026-07-20  
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

## Node Types (9)

| Type | Runner | Description |
|------|--------|-------------|
| startEvent | StartEventRunner | Moves to first outgoing transition |
| endEvent | EndEventRunner | Marks execution COMPLETED; sub-process: wakes parent |
| userTask | UserTaskRunner | Creates Task, HTTP callback, boundary timer, OrgService notification |
| serviceTask | ServiceTaskRunner | Executes handler (code/HTTP), retry with backoff, result/exception routing |
| exclusiveGateway | ExclusiveGatewayRunner | Evaluates conditional first, default last (BPMN spec) |
| parallelGateway | ParallelGatewayRunner | Fork: creates child executions; Join: waits for all siblings |
| inclusiveGateway | InclusiveGatewayRunner | Evaluates conditions, forks matching branches; default branch when zero match |
| timer | TimerRunner | Waits via delay queue, then advances |
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
- **E2E tests:** 117 Playwright tests covering all user flows (see Test Coverage below)

---

## E2E Test Infrastructure

### Running E2E Tests

```bash
# 1. Build backend
mvn clean package -DskipTests

# 2. Start backend in memory + mock-ldap mode (no MySQL needed)
java -jar workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=memory,mock-ldap \
  --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration

# 3. Run E2E tests
cd workflow-engine-web && npx playwright test
```

### Key Design Patterns

- **Polling helpers** (`waitForTasks`, `waitForTaskStatus`) handle async engine behavior — no fixed `setTimeout`
- **Data isolation** via Proxy-generated unique YAML IDs per test access, `instanceId`-scoped task queries, timestamped draft names
- **`workflowTemplates`** (raw YAML) vs **`workflows`** (Proxy with auto-unique IDs) — use `workflowTemplates` + `uniqueYamlId()` when you need stable IDs for assertions

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
| DELETE | `/api/instances/{id}` | Delete instance |
| GET | `/api/tasks` | Query tasks (default status=PENDING) |
| POST | `/api/tasks/{id}/complete` | Complete task |
| POST | `/api/tasks/{id}/reject` | Reject task |
| POST | `/api/tasks/{id}/delegate` | Delegate task |
| GET | `/api/dashboard/stats` | Dashboard stats (optional ?definitionId=) |
| GET | `/api/dashboard/timeline/{instanceId}` | Instance timeline |
| GET | `/api/instances/summary` | Instance summary stats |
| POST | `/api/instances/recover` | Recover pending timers/retries |

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

## Bug Fixes Applied (2026-07-20) — E2E Testing Round

3 additional engine bugs discovered and fixed during E2E test development:

| # | Bug | Root Cause | Fix |
|---|-----|-----------|-----|
| 25 | ParallelGateway join routes parent to wrong branch | Join handler used `parent.getCurrentNodeId()` (fork node) to find outgoing transitions, routing parent to fork's first branch instead of join's outgoing | Use `node.getId()` (join node) for outgoing transitions lookup |
| 26 | EndEventRunner child execution join routes parent incorrectly | When all child executions reach EndEvent without explicit join node, parent was set ACTIVE and routed through fork's outgoing instead of being completed | Set parent status to COMPLETED directly |
| 27 | mock-ldap + feishu OrgService bean conflict | `application.yml` has `feishu.app-id` configured, causing both `mockOrgService` and `feishuOrgService` beans when `mock-ldap` profile is active | Add `@Primary` to `mockOrgService()` bean definition |

### E2E Test Infrastructure

- **Playwright** E2E test suite with Chromium against Spring Boot backend (memory + mock-ldap profile)
- **117 E2E tests** covering: app shell, designer drafts, designer canvas, designer deploy, monitor instances, monitor list, dashboard, API endpoints, workflow scenarios, error handling, auth, definitions, tasks, drafts
- **Polling helpers** (`waitForTasks`, `waitForTaskStatus`) for handling async engine behavior
- **Data isolation** via unique YAML IDs (Proxy pattern), instanceId-scoped task queries, timestamped draft names
- **Global setup** starts backend in memory+mock-ldap mode with DataSource auto-config excluded

---

## Test Coverage

### Java Unit/Integration Tests (84 total)

`workflow-engine-core` (84 tests):

| Test Class | Count | Coverage |
|------------|-------|----------|
| BugFixIntegrationTest | 10 | Version-safe lookup, gateway default-last, timeout cancel, conditional transitions, delegate, SUSPENDED completedAt, group: assignee |
| YamlProcessParserTest | 6 | YAML parsing, class→className mapping, result/exception routing |
| BoundaryTimerIntegrationTest | 2 | Timer fires + manual complete |
| CallActivityIntegrationTest | 2 | Sub-process start + non-existent definition |
| LeaveApprovalIntegrationTest | 2 | Full approval flow (long + short leave) |
| ServiceTaskRoutingIntegrationTest | 3 | Result routing, exception routing, suspend |
| InclusiveGatewayIntegrationTest | 1 | Inclusive gateway with default branch |
| GatewayRunnerTest | 4 | Exclusive (match + default), Parallel (fork + join) |
| ServiceTaskRunnerTest | 7 | Handler execution, retry, routing |
| SpelExpressionEvaluatorTest | 9 | SpEL expressions |
| NodeTest | 9 | Node model tests |
| ProcessDefinitionTest | 5 | Definition model tests |
| Other | 24 | Execution, TaskQuery, HTTP, builder, etc. |

`workflow-engine-memory` (51 JDBC tests):

| Test Class | Count | Coverage |
|------------|-------|----------|
| JdbcProcessRepositoryTest | ~17 | CRUD, versioning, findLatest, findAllVersions |
| JdbcInstanceRepositoryTest | ~17 | Instance lifecycle, executions, history, stats |
| JdbcTaskRepositoryTest | ~17 | Task CRUD, queries, status transitions |

### E2E Tests (117 total)

`workflow-engine-web/e2e/` — Playwright + Chromium:

| Spec File | Count | Coverage |
|-----------|-------|----------|
| app-shell.spec.ts | 3 | Tab switching, language toggle, navigation |
| designer-drafts.spec.ts | 7 | Draft CRUD, copy, import, auto-save |
| designer-canvas.spec.ts | 6 | Drag nodes, connect edges, select, delete, edit properties |
| designer-deploy.spec.ts | 4 | Deploy empty/valid, view/download YAML |
| monitor-instances.spec.ts | 6 | Start, view, complete, reject, terminate, resume |
| monitor-list.spec.ts | 4 | Status filter, refresh, delete, restart |
| dashboard-api.spec.ts | 4 | Stats, per-draft stats, timeline, empty state |
| workflow-scenarios.spec.ts | 7 | Simple linear, exclusive/parallel/inclusive gateway, timer, zero-match default |
| definitions.spec.ts | 8 | Deploy, list, get, graph, version, delete, error |
| tasks.spec.ts | 8 | List, complete, reject, delegate, candidate groups, feishu |
| drafts.spec.ts | 8 | CRUD, copy, import, update, delete |
| error-handling.spec.ts | 8 | 404, 401, toast, long name, special chars, concurrent, Swagger, OpenAPI |
| auth.spec.ts | 4 | Multi-tenant isolation, X-User-Id enforcement |
| dashboard.spec.ts | 4 | Stats display, per-draft, timeline, empty |
