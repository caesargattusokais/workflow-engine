# Workflow Engine — 嵌入式 Java 工作流引擎

可嵌入的轻量级业务流程引擎，附带 React 可视化流程设计器。

**技术栈：** Java 17, Spring Boot 3.3, Maven 多模块, React 18 + TypeScript + ReactFlow + Tailwind CSS

```
workflow-engine/
├── workflow-engine-core/       # 核心引擎（模型、解析器、执行器、SPI）
├── workflow-engine-memory/     # 内存存储实现
├── workflow-engine-server/     # Spring Boot REST API 服务
├── workflow-engine-web/        # React 可视化设计器 + 监控面板
└── workflow-engine-examples/   # 使用示例
```

## 快速开始

```bash
# 编译
mvn clean package -DskipTests

# 启动后端 (默认 8080 端口)
java -jar workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar

# 启动前端开发服务器
cd workflow-engine-web && npm run dev
```

浏览器打开 `http://localhost:5173`，左侧草稿列表 → 新建草稿 → 拖拽节点到画布 → 连线 → Deploy。

---

## 节点类型

| 节点 | 说明 |
|------|------|
| **StartEvent** | 流程起点，可配置初始变量 |
| **EndEvent** | 流程终点，到达后流程完成 |
| **UserTask** | 人工任务 — 分配处理人，支持边界定时器超时 |
| **ServiceTask** | 自动任务 — 代码逻辑或 HTTP 调用，支持重试和结果/异常路由 |
| **ExclusiveGateway** | 排他网关 — 只走第一条命中分支 |
| **ParallelGateway** | 并行网关 — 分叉到所有连线，汇合等待全部完成 |
| **InclusiveGateway** | 条件分支 — 满足条件的并行执行 |
| **Timer** | 定时器 — 延迟或截止时间后自动推进 |

---

## 边类型（v2.2.0 边路由）

v2.2.0 将路由配置从节点内部移到画布连线上，所有路径可视化。

| 边类型 | 适用节点 | 颜色 | 说明 |
|--------|---------|------|------|
| **direct** | 所有 | 灰色实线 | 默认连线，无条件直接通过 |
| **conditional** | Gateway | 黄色实线 | 条件分支，SpEL 表达式 `days > 3` |
| **default** | Gateway | 灰色虚线 | 兜底分支，无条件时走这条 |
| **result** | ServiceTask | 绿色实线 | 成功后的结果路由 `result.status == 'PASS'` |
| **exception** | ServiceTask | 红色虚线 | 异常路由 `exception.type.contains('Timeout')` |
| **timeout** | UserTask | 橙色虚线 | 边界定时器超时后走这条 |

右键点击连线 → 选择类型 → 可输入 SpEL 表达式。

---

## YAML DSL 格式

```yaml
id: leave-approval
version: 1
nodes:
  - id: start
    type: startEvent
  - id: approve
    type: userTask
    name: 审批
    assignee: "${applicant}"
    boundaryTimer: "PT30M"          # 30分钟超时 → timeout 边
  - id: check
    type: serviceTask
    handlerClass: com.myapp.CheckHandler
    retry:
      maxAttempts: 3
      delayMs: 1000
      backoffMultiplier: 2
  - id: gw
    type: exclusiveGateway
  - id: end
    type: endEvent
transitions:
  - from: start
    to: approve
  - from: approve
    to: check
    type: direct
  - from: approve
    to: escalation
    type: timeout                  # 超时升级
  - from: check
    to: passed
    type: result
    expr: "result.status == 'PASS'" # SpEL 结果路由
  - from: check
    to: failed
    type: exception                 # 异常路由
  - from: gw
    to: dept
    type: conditional
    expr: "days > 3"
  - from: gw
    to: manager
    type: default                   # 兜底
```

---

## ServiceTask 配置

### 代码逻辑模式
```yaml
- id: calc
  type: serviceTask
  handlerClass: com.myapp.MyHandler   # 实现 ServiceTaskHandler 接口
```

### HTTP 代理模式
```yaml
- id: httpCall
  type: serviceTask
  httpMode: true
  url: https://api.example.com/check
  method: POST
  headers:
    Content-Type: application/json
  body: '{"amount": ${amount}}'
```

### 重试配置
```yaml
retry:
  maxAttempts: 3          # 最大重试次数（默认1=不重试）
  delayMs: 1000           # 基础延迟
  backoffMultiplier: 2    # 指数退避倍数（1s→2s→4s）
  retryOn:                # 仅匹配条件时重试（空=所有异常都重试）
    - expr: "exception.type.contains('TimeoutException')"
```

### 路由优先级
成功路径：① Result 边 → ② 节点 result 路由（兼容旧格式）→ ③ Direct 边（兜底）

异常路径：① 重试（指数退避）→ ② Exception 边 → ③ 节点 exception 路由（兼容）→ ④ SUSPEND

---

## UserTask 边界定时器

设置 `boundaryTimer` 后，如果任务在指定时间内未被处理，自动走 `timeout` 边：

```yaml
- id: review
  type: userTask
  assignee: manager
  boundaryTimer: "PT30M"    # ISO 8601 duration: PT30S / PT10M / PT2H
```

- **手动完成任务** → 走 `direct` 边（不会走 timeout 边）
- **定时器触发** → 走 `timeout` 边（自动升级/催办）

---

## REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/definitions` | 部署流程（Body: YAML + positions） |
| GET | `/api/definitions/{id}` | 获取定义详情 |
| POST | `/api/instances` | 启动实例 |
| GET | `/api/instances` | 实例列表（按 X-User-Id 过滤） |
| GET | `/api/instances/{id}` | 实例详情 + 当前节点 |
| POST | `/api/instances/{id}/terminate` | 终止实例 |
| POST | `/api/instances/{id}/resume` | 恢复挂起的实例 |
| GET | `/api/instances/{id}/history` | 节点历史 |
| GET | `/api/tasks` | 任务列表（按 assignee/candidateGroups 过滤） |
| POST | `/api/tasks/{id}/complete` | 完成任务 |
| POST | `/api/tasks/{id}/reject` | 驳回任务 |
| POST | `/api/tasks/{id}/delegate` | 委派任务 |
| GET | `/api/drafts` | 草稿列表 |
| POST | `/api/drafts` | 创建草稿 |
| GET | `/api/drafts/{id}` | 获取草稿 |
| PUT | `/api/drafts/{id}` | 更新草稿（含版本号时自动升版） |
| DELETE | `/api/drafts/{id}` | 删除草稿 |

**多租户：** 所有请求需带 `X-User-Id` 头，数据按用户隔离。

---

## 引擎 API

```java
WorkflowEngine engine = WorkflowEngine.builder()
    .processRepository(new InMemoryProcessRepository())
    .instanceRepository(new InMemoryInstanceRepository())
    .taskRepository(new InMemoryTaskRepository())
    .build();

// 部署
ProcessDefinition def = engine.deploy(yamlString);

// 启动
ProcessInstance inst = engine.start("workflow-id", Map.of("days", 5));

// 查询任务
List<Task> tasks = engine.queryTasks(
    engine.taskQuery().assignee("张三").status(TaskStatus.PENDING));

// 完成任务
engine.completeTask(tasks.get(0).getId(), Map.of("approved", true), "同意");

// 终止 / 恢复
engine.terminate(inst.getId(), "已撤销");
engine.resume(inst.getId());

// 注册自定义 ServiceTask Handler
engine.registerServiceHandler("com.myapp.Handler", vars -> Map.of("status", "PASS"));
```

---

## 版本历史

| 版本 | 变更 |
|------|------|
| v1.0.0 | 7 种节点类型、Token 驱动执行引擎、YAML DSL、SpEL 条件 |
| v2.0.0 | ServiceTask 重试（指数退避 + DelayQueue）、多版本管理、多租户 |
| v2.1.0 | Timer 节点、UserTask 边界定时器、共享延迟基础设施 |
| v2.2.0 | **边路由** — result/exception/timeout/conditional/default 边类型，路由配置从节点移到画布连线 |
