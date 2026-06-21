# Workflow Engine — 嵌入式 Java 工作流引擎

可嵌入的轻量级业务流程引擎，附带 React 可视化流程设计器。

**技术栈：** Java 17, Spring Boot 3.3, Maven 多模块, React 18 + TypeScript + ReactFlow + Tailwind CSS

```
workflow-engine/
├── workflow-engine-core/         # 核心引擎（模型、解析器、执行器、SPI）
├── workflow-engine-memory/       # 内存 + JDBC 持久化实现
├── workflow-engine-mock-ldap/    # Mock LDAP 组织架构（测试用）
├── workflow-engine-server/       # Spring Boot REST API 服务
├── workflow-engine-web/          # React 可视化设计器 + 监控面板
├── workflow-engine-examples/     # 使用示例
└── docker/                       # Docker Compose（MySQL + LDAP）
```

## 快速开始

```bash
# 编译
mvn clean package -DskipTests

# 启动后端 (默认 8080 端口，需 MySQL)
java -jar workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar

# 内存模式（无需 MySQL）
java -jar workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=memory

# Mock LDAP 模式（内置测试组织架构）
java -jar workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=mock-ldap

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
| **UserTask** | 人工任务 — 分配处理人，支持边界定时器超时、HTTP 回调、LDAP 组织选择 |
| **ServiceTask** | 自动任务 — 代码逻辑/HTTP 调用/SPI 发现，支持重试和结果/异常路由 |
| **ExclusiveGateway** | 排他网关 — 只走第一条命中分支 |
| **ParallelGateway** | 并行网关 — 分叉到所有连线，汇合等待全部完成 |
| **InclusiveGateway** | 条件分支 — 满足条件的并行执行 |
| **Timer** | 定时器 — 延迟或截止时间后自动推进 |

---

## 边类型（v2.2.0 边路由）

| 边类型 | 适用节点 | 颜色 | 说明 |
|--------|---------|------|------|
| **direct** | 所有 | 灰色实线 | 默认连线，无条件直接通过 |
| **conditional** | Gateway | 黄色实线 | 条件分支，SpEL 表达式 `days > 3` |
| **default** | Gateway | 灰色虚线 | 兜底分支 |
| **result** | ServiceTask | 绿色实线 | 结果路由 `result.status == 'PASS'` |
| **exception** | ServiceTask | 红色虚线 | 异常路由 |
| **timeout** | UserTask | 橙色虚线 | 边界定时器超时后走这条 |

右键点击连线 → 选择类型 → 可输入 SpEL 表达式。

---

## YAML DSL 格式

```yaml
id: leave-approval
name: '请假审批'
version: 1
nodes:
  - id: start
    type: startEvent
  - id: approve
    type: userTask
    name: 审批
    assignee: "${applicant}.manager"   # 申请人的上级
    boundaryTimer: "PT30M"             # 30分钟超时 → timeout 边
  - id: check
    type: serviceTask
    handlerClass: ApprovalHandler      # SPI 自动发现
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
    type: timeout                   # 超时升级
  - from: check
    to: passed
    type: result
    expr: "result.status == 'PASS'"
  - from: check
    to: failed
    type: exception
  - from: gw
    to: dept
    type: conditional
    expr: "days > 3"
  - from: gw
    to: manager
    type: default
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

### SPI 自动发现（v3.3.0）
Handler 可通过 `META-INF/services/com.github.wf.ext.ServiceTaskHandler` 注册，引擎自动发现，无需 `registerServiceHandler()`。支持简单类名：

```yaml
handlerClass: ApprovalHandler   # ServiceLoader 自动找到 com.myapp.ApprovalHandler
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
成功：① Result 边 → ② 节点 result 路由 → ③ Direct 边（兜底）

异常：① 重试 → ② Exception 边 → ③ 节点 exception 路由 → ④ SUSPEND

---

## UserTask 配置

### 处理人分配

| 写法 | 含义 |
|------|------|
| `zhangsan` | 直接指定 uid |
| `${applicant}` | 从流程变量取值 |
| `${applicant}.manager` | 申请人的直属上级（需配置 OrgService） |
| `${applicant}.manager.manager` | 申请人的上两级 |

### HTTP 回调模式（v2.4.0）
```yaml
- id: approve
  type: userTask
  httpMode: true
  url: https://oa.company.com/api/approval
  method: POST
  headers:
    Content-Type: application/json
  body: '{"taskId":"${taskId}","completeUrl":"${completeUrl}","rejectUrl":"${rejectUrl}"}'
```
任务创建时向第三方推送，body 自动注入 `${taskId}`、`${completeUrl}`、`${rejectUrl}`。第三方审批后回调完成。

### 边界定时器
```yaml
boundaryTimer: "PT30M"    # ISO 8601: PT30S / PT10M / PT2H
```
- **手动完成** → 走 `direct` 边
- **定时器触发** → 走 `timeout` 边（自动升级）

### 组织架构选择（v3.4.0）
配置 LDAP 或 Mock LDAP 后，PropertyPanel 显示组织树，可直接点选处理人和候选人，无需手动输入 uid。

---

## 组织架构集成（v3.4.0）

### OrgService SPI
```java
public interface OrgService {
    OrgUser getUser(String uid);
    List<String> getGroups(String uid);
    List<String> getGroupMembers(String group);
    String getManager(String uid);                    // 直属上级
    default String getManager(String uid, int levels); // N级上级
    default List<OrgTree> getOrgTree();                // 组织树
    default List<OrgUser> searchUsers(String query);   // 用户搜索
    default List<String> listGroups();                 // 组列表
}
```

### 配置方式

**LDAP/AD：** 配置 `application.yml`
```yaml
ldap:
  url: ldap://dc.company.com:389
  base: dc=company,dc=com
  user: cn=admin,dc=company,dc=com
  password: secret
  uidAttr: sAMAccountName        # AD
  # uidAttr: uid                 # OpenLDAP
  userObjectClass: user           # AD
  # userObjectClass: inetOrgPerson # OpenLDAP
```

**Mock LDAP（测试用）：** `--spring.profiles.active=mock-ldap`

内置 4 个用户（zhangsan/lisi/wangwu/zhaoliu）+ 4 个组，含完整上下级关系。

**Docker LDAP：** `docker-compose up -d` 启动本地 OpenLDAP + phpLDAPadmin（管理界面 `http://localhost:8080`）。

---

## 数据库持久化（v3.0.0）

Spring JDBC (JdbcTemplate) 持久化，支持 MySQL 和 H2。

### 表结构
- `process_definition` — 流程定义（id + version 复合主键）
- `process_instance` — 流程实例
- `execution` — 执行记录（parent_execution_id 支持并行网关）
- `task` — 任务
- `historic_activity` — 历史审计
- `draft` — 草稿（v3.2.0）
- `definition` — 定义元数据 + 位置信息（v3.2.0）

### Profile
- 默认：MySQL（`application.yml` 配置 datasource）
- `memory`：H2 内存数据库，重启数据丢失
- `mock-ldap`：Mock LDAP + MySQL

### 启动恢复
服务重启时自动扫描 `WAITING + TIMER_PENDING/RETRY_PENDING` 的执行记录并重新触发。手动恢复：`POST /api/instances/recover`。

---

## 流程模板（v3.3.0）

Designer 侧边栏 `T` 按钮 → 5 个预置模板：请假审批、报销审批、采购审批、新员工入职、合同审批。点击"使用此模板"一键导入为草稿。

---

## 前端国际化（v2.5.0）

右上角 `中 | EN` 切换，默认读浏览器语言，持久化到 localStorage。所有 UI 文字支持中英文实时切换。

---

## YAML 导入导出（v2.3.0）

- 侧边栏 `Import` 按钮 → 导入 `.yaml` 文件为草稿，自动拓扑分层布局
- 右键草稿 → `Download YAML` → 导出为 `.yaml` 文件
- 右键草稿 → `Copy` → 复制草稿

---

## REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/definitions` | 部署流程 |
| GET | `/api/definitions` | 定义列表 |
| GET | `/api/definitions/{id}/graph` | 流程图画布数据（支持 ?version=N） |
| POST | `/api/instances` | 启动实例 |
| GET | `/api/instances` | 实例列表（按 X-User-Id 过滤） |
| GET | `/api/instances/{id}` | 实例详情 |
| POST | `/api/instances/{id}/terminate` | 终止实例 |
| POST | `/api/instances/{id}/resume` | 恢复挂起实例 |
| POST | `/api/instances/recover` | 恢复待处理定时器/重试 |
| GET | `/api/instances/{id}/history` | 节点历史 |
| GET | `/api/tasks` | 任务列表 |
| POST | `/api/tasks/{id}/complete` | 完成任务 |
| POST | `/api/tasks/{id}/reject` | 驳回任务 |
| GET | `/api/drafts` | 草稿列表 |
| POST | `/api/drafts` | 创建草稿 |
| PUT | `/api/drafts/{id}` | 更新草稿 |
| POST | `/api/drafts/{id}/copy` | 复制草稿 |
| POST | `/api/drafts/import` | 导入 YAML 为草稿 |
| DELETE | `/api/drafts/{id}` | 删除草稿 |
| GET | `/api/org/tree` | 组织树（需 LDAP） |
| GET | `/api/org/users?q=` | 用户搜索（需 LDAP） |
| GET | `/api/org/groups` | 组列表（需 LDAP） |

**多租户：** 所有请求需带 `X-User-Id` 头，数据按用户隔离。

---

## 引擎 API

```java
WorkflowEngine engine = WorkflowEngine.builder()
    .processRepository(new InMemoryProcessRepository())
    .instanceRepository(new InMemoryInstanceRepository())
    .taskRepository(new InMemoryTaskRepository())
    .orgService(new MockOrgService())          // LDAP 集成（可选）
    .baseUrl("http://localhost:8080")          // 回调 URL（可选）
    .build();

// 部署 & 启动
ProcessDefinition def = engine.deploy(yamlString);
ProcessInstance inst = engine.start("workflow-id", Map.of("days", 5));

// 查询任务
List<Task> tasks = engine.queryTasks(
    engine.taskQuery().assignee("张三").status(TaskStatus.PENDING));

// 完成任务
engine.completeTask(tasks.get(0).getId(), Map.of("approved", true), "同意");

// 终止 / 恢复 / 重试恢复
engine.terminate(inst.getId(), "已撤销");
engine.resume(inst.getId());
engine.recover();  // 扫描并重新触发待处理定时器/重试

// 注册 ServiceTask Handler
engine.registerServiceHandler("com.myapp.Handler", vars -> Map.of("status", "PASS"));
```

---

## 版本历史

| 版本 | 变更 |
|------|------|
| v1.0.0 | 7 种节点类型、Token 驱动执行引擎、YAML DSL、SpEL 条件 |
| v2.0.0 | ServiceTask 重试（指数退避 + DelayQueue）、多版本管理、多租户 |
| v2.1.0 | Timer 节点、UserTask 边界定时器 |
| v2.2.0 | 边路由 — 6 种边类型，路由配置从节点移到画布连线 |
| v2.3.0 | 草稿复制、YAML 导入导出、拓扑分层布局 |
| v2.4.0 | UserTask HTTP 回调模式、HttpClientUtil、回调 URL 注入 |
| v2.5.0 | 前端国际化（中/英文切换） |
| v3.0.0 | MySQL 数据库持久化、写穿透缓存、启动恢复 |
| v3.1.0 | 版本化流程图、草稿名校验、实例列表显示名称 |
| v3.2.0 | 草稿 + 定义 JDBC 持久化 |
| v3.3.0 | 流程模板市场（5 个预置模板）、ServiceTask SPI 自动发现 |
| v3.4.0 | LDAP 组织架构集成、组织树选择器、Mock LDAP 模块 |
