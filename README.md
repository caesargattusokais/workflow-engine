# Workflow Engine — 工作流引擎

可视化业务流程设计、部署、监控平台。支持审批流、条件分支、并行任务、第三方接口调用。

## 快速启动

```bash
# 终端 1：启动后端
cd D:\workflow-engine
mvn install -pl workflow-engine-core,workflow-engine-memory -q -DskipTests
mvn spring-boot:run -pl workflow-engine-server

# 终端 2：启动前端
cd D:\workflow-engine\workflow-engine-web
npm install
npm run dev
```

浏览器打开 http://localhost:3000

## 用户隔离

前端通过 `localStorage.userId` 标识当前用户。所有数据（草稿、定义、实例）按用户隔离存储。

```js
// 浏览器控制台设置用户
localStorage.setItem('userId', 'your-name')
```

## Designer — 流程设计器

### 草稿管理
- **新建草稿**：左侧栏点 `+ New`
- **切换草稿**：左键点击草稿名
- **右键草稿**：View YAML / Rename / Delete
- **自动保存**：编辑后 2 秒自动保存，也可手动点 Save

### 节点类型

| 节点 | 形状 | 说明 |
|------|------|------|
| Start | 绿色圆形 | 流程起点 |
| End | 红色圆形 | 流程终点 |
| UserTask | 蓝色圆角矩形 👤 | 人工审批任务 |
| ServiceTask | 紫色/青色矩形 | 代码逻辑或 HTTP 调用 |
| 判断网关 (XOR) | 橙色菱形 ? | 条件分支，只走第一条命中的路 |
| 并行网关 (AND) | 蓝色菱形 ALL | 所有分支同时走 |
| 条件网关 (OR) | 紫色菱形 ? | 满足条件的分支都走 |

### 操作
- **拖节点**：左侧面板拖到画布
- **连线**：从节点底部圆点拖到目标节点顶部
- **右键节点/连线**：Delete / Duplicate
- **选中节点**：右侧属性面板编辑
- **键盘删除**：Backspace / Delete
- **定位**：左下角"定位"按钮居中所有节点
- **锁定**：左下角"锁定"按钮防止误操作

### 变量系统

1. **Start 节点**定义初始变量（如 `applicant`、`days`）
2. **ServiceTask 返回值**定义输出字段和类型
3. 工具栏 **Variables** 按钮查看所有已知变量

变量在条件中使用 SpEL 表达式：`days > 3`、`result.status == 'PASS'`

### 部署
1. 设计好流程 → 点 **Deploy**
2. 自动部署并启动一个实例
3. 绿色通知条显示结果 + 可展开 YAML 预览
4. 点 **View in Monitor** 查看运行状态

---

## Monitor — 实例监控

### 启动实例
- 顶部面板选择 Definition + 输入初始变量 JSON → **Start Instance**

### 实例列表
- 按 Definition 分组展示
- 点击分组标题展开/收起
- **左键**实例 → 查看流程图 + 详情面板
- **右键**实例 → 操作菜单

| 状态 | 颜色 | 可用操作 |
|------|------|----------|
| RUNNING | 绿色 | Terminate |
| SUSPENDED | 黄色 | Resume / Terminate |
| COMPLETED | 蓝色 | Restart / Delete |
| TERMINATED | 红色 | Restart / Delete |

### 流程图
- 与 Designer 布局一致
- 已完成节点变灰
- 当前活跃节点蓝框高亮

### 详情面板
选中实例后右侧显示：
- 活跃节点名称
- 所有变量及值
- 历史操作时间线

### 任务操作
底部 TaskPanel 显示待办任务：
- **Complete**：完成任务推进流程
- **Reject**：驳回任务

---

## 节点详解

### UserTask — 用户任务
```yaml
- id: approve
  type: userTask
  name: 经理审批
  assignee: "${manager}"          # 审批人 (支持 ${变量})
  candidateGroups: ["manager"]    # 候选组
```
下游通过 `engine.completeTask(taskId, {approved: true})` 推进。

### ServiceTask — 代码逻辑
```yaml
- id: check
  type: serviceTask
  handlerClass: "com.myapp.MyHandler"
```
实现 `com.github.wf.ext.ServiceTaskHandler` 接口：
```java
public class MyHandler implements ServiceTaskHandler {
    @Override
    public Map<String, Object> execute(Map<String, Object> variables) {
        return Map.of("result", "ok");
    }
}
```
Designer 中定义 Input Parameters 和 Return Values。

### ServiceTask — HTTP 调用
Designer 中切换到 **HTTP 调用** 模式，配置：
- **URL**：接口地址，支持 `${var}` 占位符
- **Method**：GET/POST/PUT/DELETE
- **Headers**：键值对
- **Params**：GET 时拼 query string，POST 时组 JSON body
- 自动解析响应 JSON → `result.xxx`

```yaml
- id: risk-check
  type: serviceTask
  url: "https://api.risk.com/check"
  method: POST
  headers:
    Content-Type: "application/json"
  body: '{"amount": ${amount}}'
```

### ServiceTask — 重试与路由
```yaml
  retry:
    maxAttempts: 3
    delayMs: 1000
    backoffMultiplier: 2
    retryOn:
      - expr: "exception.type.contains('TimeoutException')"
  resultRouting:
    - expr: "result.status == 'PASS'"
      to: auto-approve
    - default: true
      to: manual-review
  exceptionRouting:
    - expr: "exception.type.contains('BusinessException')"
      to: error-handler
    - default: true
      to: system-error
```

### 网关 — 条件判断
```yaml
- id: gateway
  type: exclusiveGateway
  conditions:
    - expr: "days > 3"
      to: manager-approve
    - default: true
      to: department-manager
```
- **判断网关**：从上到下依次判断，命中即停止
- **条件网关**：满足条件的全走（多路并行）
- **并行网关**：所有分支同时走，需配合汇合

---

## DSL 参考（YAML）

```yaml
id: leave-approval          # 定义 ID（唯一）
name: 请假审批               # 显示名称
version: 1                  # 版本号
nodes:
  - id: start               # 节点 ID
    type: startEvent        # 节点类型
  - id: apply
    type: userTask
    name: 提交申请
    assignee: "${applicant}"
  - id: gateway
    type: exclusiveGateway
    conditions:
      - expr: "days > 3"    # SpEL 表达式
        to: manager
      - default: true        # 兜底分支
        to: dept-manager
  - id: end
    type: endEvent
transitions:
  - from: start
    to: apply
  - from: apply
    to: gateway
  - from: manager
    to: end
  - from: dept-manager
    to: end
```

---

## REST API

所有接口需带 `X-User-Id` header 做用户隔离。

### Definitions
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/definitions` | 部署流程 `{yaml, positions}` |
| GET | `/api/definitions` | 列出所有定义 |
| GET | `/api/definitions/{id}` | 获取定义详情 |
| GET | `/api/definitions/{id}/graph` | 获取流程图 JSON |
| DELETE | `/api/definitions/{id}` | 删除定义 |

### Instances
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/instances` | 启动实例 `{definitionId, variables}` |
| GET | `/api/instances` | 列出实例 |
| GET | `/api/instances/{id}` | 实例详情 |
| GET | `/api/instances/{id}/history` | 操作历史 |
| POST | `/api/instances/{id}/resume` | 恢复挂起 |
| POST | `/api/instances/{id}/terminate` | 终止 `{reason}` |
| DELETE | `/api/instances/{id}` | 删除（非运行中） |

### Tasks
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks` | 查询任务 `?assignee=&instanceId=` |
| POST | `/api/tasks/{id}/complete` | 完成任务 `{variables, comment}` |
| POST | `/api/tasks/{id}/reject` | 驳回 `{comment}` |

### Drafts
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/drafts` | 列出草稿 |
| POST | `/api/drafts` | 新建草稿 `{name}` |
| PUT | `/api/drafts/{id}` | 更新草稿 `{name, nodes, edges}` |
| DELETE | `/api/drafts/{id}` | 删除草稿 |

---

## Embedding — 嵌入 Java 项目

```java
WorkflowEngine engine = WorkflowEngine.builder()
    .processRepository(new InMemoryProcessRepository())
    .instanceRepository(new InMemoryInstanceRepository())
    .taskRepository(new InMemoryTaskRepository())
    .build();

ProcessDefinition def = engine.deploy(yamlString);
ProcessInstance inst = engine.start(def.getId(), Map.of("applicant", "张三"));
List<Task> tasks = engine.queryTasks(engine.taskQuery().assignee("李四"));
engine.completeTask(tasks.get(0).getId(), Map.of("approved", true), "同意");
```

---

## 技术栈

| 层 | 技术 |
|----|------|
| 引擎核心 | Java 17, Maven |
| 表达式 | SpEL (Spring Expression Language) |
| DSL 解析 | SnakeYAML, Gson |
| 后端服务 | Spring Boot 3.3 |
| 前端 | React 18, TypeScript, Vite, Tailwind CSS |
| 流程图 | @xyflow/react (ReactFlow v12) |

## 项目结构

```
D:\workflow-engine\
├── workflow-engine-core/     核心引擎库
├── workflow-engine-memory/   内存持久化
├── workflow-engine-server/   Spring Boot REST API
├── workflow-engine-web/      React 前端
├── workflow-engine-examples/ 示例项目
└── docs/                     设计文档
```
