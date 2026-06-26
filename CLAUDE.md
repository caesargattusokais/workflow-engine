# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Embeddable Java business process workflow engine with a React visual designer. Supports YAML DSL workflow definitions, token-driven execution, JDBC persistence, distributed locking, and LDAP/Feishu/DingTalk org integration.

**Stack:** Java 17, Spring Boot 3.3, Maven multi-module, React 18 + TypeScript + ReactFlow + Tailwind CSS, MySQL/H2.

## Build & Test Commands

```bash
# Full build (skip tests)
mvn clean package -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -pl workflow-engine-core -Dtest=LeaveApprovalIntegrationTest

# Run server (default profile: MySQL on localhost:3306/workflow_engine)
java -jar workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar

# Run server in memory mode (H2, no MySQL needed)
java -jar workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=memory

# Run server with mock LDAP
java -jar workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=mock-ldap

# Frontend dev server (proxies /api → localhost:8080)
cd workflow-engine-web && npm run dev

# Frontend prod build
cd workflow-engine-web && npm run build
```

## Module Architecture

| Module | Role |
|--------|------|
| `workflow-engine-core` | Engine, models, DSL parsers, NodeRunners, SPIs, expression evaluation, extension interfaces |
| `workflow-engine-memory` | InMemory and JDBC persistence implementations, Redis distributed lock |
| `workflow-engine-mock-ldap` | Mock LDAP org service (4 users, 4 groups) for testing |
| `workflow-engine-server` | Spring Boot REST API, EngineConfig bean wiring |
| `workflow-engine-web` | React visual designer + monitor dashboard (Vite + ReactFlow + Tailwind) |
| `workflow-engine-examples` | Usage examples (leave approval, service task routing, SPI) |

**Dependency chain:** `core` ← `memory` ← `server` (server also depends on `mock-ldap`). `web` is standalone, talks to server via REST.

## Engine Architecture

### Token-Driven Execution

The engine uses a token-based execution model. Each active position in the workflow is an `Execution` record (a token). The trigger loop in `WorkflowEngine.trigger()` iterates all active executions, looks up the current node, dispatches to the appropriate `NodeRunner`, and repeats until no executions advance. Parallel gateways fork by creating child executions with `parentExecutionId`; they join when all siblings arrive.

### NodeRunner Pattern

Every node type has a `NodeRunner` implementation (`engine/runner/`). The interface is `boolean run(Node node, ExecutionContext context)` — return `true` if the execution advanced. Runners:

- **StartEventRunner** — moves to first outgoing transition
- **EndEventRunner** — marks execution complete
- **UserTaskRunner** — creates a `Task`, optionally sends HTTP callback, schedules boundary timer via delay queue
- **ServiceTaskRunner** — executes handler (code/HTTP), handles retry with exponential backoff, routes by result/exception edges
- **ExclusiveGatewayRunner** — evaluates conditions, takes first match
- **ParallelGatewayRunner** — fork: creates child executions; join: waits for all siblings, then reactivates parent
- **InclusiveGatewayRunner** — evaluates conditions, forks matching branches in parallel
- **TimerRunner** — waits via delay queue, then advances

### Delay Queue & Recovery

`DelayQueue<DelayedTrigger>` in the engine handles retry backoff and timer delays. A daemon thread blocks on `take()` and calls `trigger()` when a delay expires. On restart, `engine.recover()` (called automatically in EngineConfig) queries for WAITING + TIMER_PENDING/RETRY_PENDING executions and re-triggers them. Recovery is wrapped in a distributed write lock (`__recover__`) to prevent concurrent multi-node scans.

### Edges / Transition Types

6 transition types, defined in `TransitionType`: `direct`, `conditional`, `default`, `result`, `exception`, `timeout`. ServiceTask success routing priority: result edges → node-level result routes → direct edge (fallback). Failure routing: retry → exception edges → node-level exception routes → SUSPEND.

## SPI Layer (Persistence)

Three core SPIs in `workflow-engine-core/spi/`:

- **ProcessRepository** — process definitions (multi-version: `findLatestById` vs `findById`)
- **InstanceRepository** — process instances + executions + historic activities + stats/summary
- **TaskRepository** — user tasks with query support (`TaskQuery`)

All three have `InMemory*` and `Jdbc*` implementations in `workflow-engine-memory/`. JDBC implementations use write-through caches (ConcurrentHashMap) — same object references as InMemory mode, so mutations are instantly visible everywhere while persisting synchronously to MySQL.

Additional repos (not SPI, used by server controllers): `DraftRepository`, `DefinitionRepository` (JDBC-backed, store YAML drafts + definition metadata with canvas positions).

### Distributed Locking

`InstanceLockManager` interface in `core` provides read/write locks. `LocalInstanceLockManager` (in-memory ReentrantReadWriteLock) is the default. `RedisInstanceLockManager` in `memory` uses Redis for multi-node coordination. Activated via `spring.profiles.active=redis` — also enables `RedisJdbcInstanceRepository` (Redis as L1 cache in front of MySQL).

## Server Configuration

`EngineConfig` (`workflow-engine-server`) wires beans based on Spring profiles:

- **Default** (`!memory & !redis`): JDBC repos with MySQL, `LocalInstanceLockManager`
- **`memory`**: InMemory repos, no DB needed
- **`redis`**: JDBC repos + Redis lock manager + Redis cache layer
- **`mock-ldap`**: `MockOrgService` bean (built-in test users/groups)
- **`ldap.url` property set**: `LdapOrgService` bean (conditional on property)
- **`feishu.app-id` / `dingtalk.app-key`**: Feishu/DingTalk org service beans

### Multi-tenancy

All REST endpoints require `X-User-Id` header. Data is filtered by `_userId` variable stored in instance variables. Not enforced at DB level — filtering happens in the controller layer.

## Key Extension Points

- **ServiceTaskHandler** (`@FunctionalInterface`): `Map<String, Object> execute(Map<String, Object> variables)` — register via `engine.registerServiceHandler()` or SPI (`META-INF/services`)
- **OrgService**: LDAP, Feishu, DingTalk, or mock implementations — resolves assignees, managers, org trees
- **ProcessListener**: `onNodeEnter` / `onNodeLeave` callbacks, configured per-node via `listeners: [com.example.MyListener]`
- **ConditionEvaluator**: Java class-based conditions (alternative to SpEL expressions)
- **DynamicRouter**: Programmatic next-node selection for UserTask completion

## REST API Conventions

- `X-User-Id` header on all requests for multi-tenant isolation
- `@CrossOrigin(origins = "*")` on all controllers
- Pagination: `?page=1&size=50` query params; response includes `{items, page, size, total}`
- Status filter: `?status=RUNNING` on list endpoints
- Frontend dev server proxies `/api` to `localhost:8080`

## Frontend Architecture

`workflow-engine-web/` — Vite + React 18 + ReactFlow (`@xyflow/react`) + Tailwind CSS + Zustand (transitive via ReactFlow).

- `App.tsx` — tab switcher (Designer, Monitor, Dashboard)
- `designer/DesignerPage.tsx` — left panel (draft list), center (FlowCanvas), right (PropertyPanel)
- `designer/nodes/` — 8 custom ReactFlow node components (one per type)
- `designer/graphToYaml.ts` / `yamlToGraph.ts` — bidirectional conversion between ReactFlow graph model and YAML DSL
- `monitor/MonitorPage.tsx` — instance list + task panel
- `monitor/Dashboard.tsx` — stats dashboard
- `i18n/` — Chinese/English translations (Zustand-based, persisted to localStorage)
- Custom nodes register `nodeTypes` in FlowCanvas via the `nodes/index.ts` map
- API calls use `fetch()` directly; Vite proxy handles `/api` → backend during dev