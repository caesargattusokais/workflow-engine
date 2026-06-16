# Workflow Engine Visual Designer & Monitor — Design Spec

**Date:** 2026-06-16  
**Frontend:** React 18 + ReactFlow 11  
**Backend:** Spring Boot 3.x (new module)  
**Package:** `com.github.wf`

---

## 1. Architecture

```
D:\workflow-engine\
├── workflow-engine-core/          (existing — engine library)
├── workflow-engine-memory/        (existing — in-memory repos)
├── workflow-engine-server/        NEW — Spring Boot REST API
│   └── src/main/java/com/github/wf/server/
│       ├── WorkflowServerApp.java        # @SpringBootApplication
│       ├── controller/
│       │   ├── DefinitionController.java # /api/definitions
│       │   ├── InstanceController.java   # /api/instances
│       │   └── TaskController.java       # /api/tasks
│       ├── dto/                          # Request/Response DTOs
│       │   ├── GraphResponse.java        # ReactFlow-compatible graph
│       │   └── ...
│       └── config/
│           └── EngineConfig.java         # WorkflowEngine @Bean
├── workflow-engine-web/            NEW — React SPA
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx                      # Tab navigation
│       ├── api/                         # REST client
│       ├── designer/                    # Designer tab
│       │   ├── DesignerPage.tsx         # Palette + Canvas + Props layout
│       │   ├── NodePalette.tsx          # Draggable node types
│       │   ├── FlowCanvas.tsx           # ReactFlow wrapper
│       │   ├── PropertyPanel.tsx        # Selected node editor
│       │   ├── nodes/                   # Custom ReactFlow nodes
│       │   │   ├── StartEventNode.tsx
│       │   │   ├── EndEventNode.tsx
│       │   │   ├── UserTaskNode.tsx
│       │   │   ├── ServiceTaskNode.tsx
│       │   │   ├── ExclusiveGatewayNode.tsx
│       │   │   ├── ParallelGatewayNode.tsx
│       │   │   └── InclusiveGatewayNode.tsx
│       │   └── edges/
│       │       └── LabeledEdge.tsx       # Edge with condition label
│       └── monitor/                     # Monitor tab
│           ├── MonitorPage.tsx           # Instance list + detail
│           ├── InstanceList.tsx
│           ├── InstanceFlow.tsx          # Read-only flow with highlights
│           └── TaskPanel.tsx             # Task actions
└── workflow-engine-examples/        (existing)
```

---

## 2. REST API

### Definitions
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/definitions` | Deploy (YAML/JSON in body) |
| GET | `/api/definitions` | List all definitions |
| GET | `/api/definitions/{id}` | Get definition detail |
| GET | `/api/definitions/{id}/graph` | Get ReactFlow graph JSON (nodes + edges) |
| DELETE | `/api/definitions/{id}` | Delete definition |

### Instances
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/instances` | Start instance (`{definitionId, variables}`) |
| GET | `/api/instances` | List instances (filter: status, definitionId) |
| GET | `/api/instances/{id}` | Get instance + active node IDs |
| GET | `/api/instances/{id}/graph` | Graph with active node highlights |
| POST | `/api/instances/{id}/resume` | Resume suspended |
| POST | `/api/instances/{id}/terminate` | Terminate |

### Tasks
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/tasks` | Query (params: assignee, group, instanceId, status) |
| POST | `/api/tasks/{id}/complete` | Complete task `{variables, comment}` |
| POST | `/api/tasks/{id}/reject` | Reject `{comment}` |
| POST | `/api/tasks/{id}/delegate` | Delegate `{newAssignee}` |

### History
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/instances/{id}/history` | History list |

---

## 3. Graph Format (ReactFlow-compatible)

### GET `/api/definitions/{id}/graph` Response
```json
{
  "nodes": [
    {
      "id": "start",
      "type": "startEvent",
      "position": { "x": 100, "y": 50 },
      "data": { "name": "Start", "listeners": [] }
    },
    {
      "id": "apply",
      "type": "userTask",
      "position": { "x": 100, "y": 150 },
      "data": { "name": "提交申请", "assignee": "${applicant}", "candidateGroups": [] }
    },
    {
      "id": "gateway",
      "type": "exclusiveGateway",
      "position": { "x": 100, "y": 280 },
      "data": { "name": "" }
    }
  ],
  "edges": [
    { "id": "e1", "source": "start", "target": "apply", "type": "smoothstep" },
    { "id": "e2", "source": "apply", "target": "gateway", "type": "smoothstep" },
    { "id": "e3", "source": "gateway", "target": "manager", "type": "smoothstep",
      "data": { "label": "days > 3" }, "style": { "stroke": "#888" } },
    { "id": "e4", "source": "gateway", "target": "dept", "type": "smoothstep",
      "data": { "label": "default" } }
  ]
}
```

### GET `/api/instances/{id}/graph` Response
Same format, plus:
- Completed nodes: `data.active = false` (grayed out)
- Active node: `data.active = true` (highlighted, pulsing border)
- Each node has `data.status`: `"done"`, `"active"`, `"waiting"`, `"pending"`

---

## 4. Node Visual Style

| Node Type | Shape | Color | Icon/Symbol |
|-----------|-------|-------|-------------|
| StartEvent | Circle (r=18) | Green `#4CAF50` | — |
| EndEvent | Circle (r=18) | Red border `#f44336` | — |
| UserTask | Rounded rect (120×40) | Blue `#2196F3` | 👤 person icon |
| ServiceTask | Rounded rect (120×40) | Purple `#9C27B0` | ⚙ gear icon |
| ExclusiveGateway | Diamond (30×30, rotate 45°) | Orange `#FF9800` | — |
| ParallelGateway | Diamond (30×30, rotate 45°) | Blue `#2196F3` | `+` symbol |
| InclusiveGateway | Diamond (30×30, rotate 45°) | Purple `#AB47BC` | `~` symbol |

---

## 5. Designer Page Layout

```
┌──────────────────────────────────────────────────────┐
│  [Designer] [Monitor]                                │
├────────┬────────────────────────────┬────────────────┤
│Palette │       FlowCanvas           │ Property Panel │
│        │                            │                │
│ ○ Start│   ○ ──── □ ──── ◇ ──── ○  │ Name: 审批     │
│        │                            │ Assignee:      │
│ □ Task │                            │   ${applicant} │
│        │                            │ Groups:        │
│ ◇ Gate │                            │   [manager]    │
│        │                            │                │
│ □ Svc  │                            │ Listeners:     │
│        │                            │   [+ Add]      │
│ ○ End  │                            │                │
└────────┴────────────────────────────┴────────────────┘
```

**Palette:** Drag-and-drop node types. Drop on canvas to create.

**Canvas:** ReactFlow with zoom, pan, minimap. Click node → select → Property Panel shows its config. Connect handles to create edges. Click edge → edit condition label.

**Property Panel:** Edits the selected node's properties. Changes update the YAML export. Node-specific fields: UserTask → assignee/groups/router; ServiceTask → handlerClass/retry/routing; Gateway → conditions.

---

## 6. Monitor Page Layout

```
┌──────────────────────────────────────────────────────┐
│  [Designer] [Monitor]                                │
├────────────┬────────────────────────────────────────┤
│ Instance   │         Instance Flow                  │
│ List       │                                        │
│            │   ○ (done)  ──  □ (done)  ──  ◇ ──    │
│ #3a8f ●    │                              │         │
│ leave-app  │                    ◇ (active, pulsing)  │
│            │                    / \                  │
│ #2b7e ●    │              □审批中  □等待中            │
│ order-rev  │                                        │
│            │              [Complete] [Reject]        │
│ #1c6d ◉    │              ─── Task Panel ───        │
│ risk-check │                                        │
└────────────┴────────────────────────────────────────┘
```

**Instance List:** All instances, filterable by status. Select one → Flow View updates.

**Instance Flow:** Same node layout as definition, but nodes colored by status:
- Gray = completed (past)
- Blue pulsing = current active
- White outline = future / not yet reached

**Task Panel:** Below the flow. Shows pending tasks for the selected instance. Complete/Reject/Delegate buttons.

---

## 7. Technologies

| Layer | Technology |
|-------|-----------|
| Frontend framework | React 18, TypeScript |
| Flow library | @xyflow/react (ReactFlow v11) |
| Build tool | Vite |
| Styling | Tailwind CSS |
| HTTP client | fetch / ky |
| Backend | Spring Boot 3.3, Java 17 |
| Engine | workflow-engine-core (existing) |
| Persistence | In-memory (server module starts with InMemory repos) |

---

## 8. MVP Scope

### Includes
- Designer: drag-drop 7 node types, connect edges, edit properties, export YAML
- Designer: deploy to engine via API
- Monitor: instance list, flow diagram with active-node highlights
- Monitor: task actions (complete/reject/delegate)
- Server: full REST API wrapping engine

### Excludes
- Auth / multi-tenant
- Collaborative editing
- Node JSON import (besides initial deploy)
- DB persistence (in-memory only)
- Timer/Signal node visual support
- Responsive mobile layout
