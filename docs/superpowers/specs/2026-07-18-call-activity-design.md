# Call Activity (子流程调用) 设计文档

**版本:** 1.0  
**日期:** 2026-07-18  
**分支:** 5.1.0

## 概述

新增 Call Activity 节点类型，允许流程引用并调用已部署的流程定义作为子流程。父流程在 Call Activity 处同步等待子流程完成后继续执行。

## 1. YAML DSL

### 完整示例

```yaml
id: main-process
name: 主流程
version: 1
nodes:
  - id: start
    type: startEvent
  - id: call-leave
    type: callActivity
    calledId: leave-approval          # 必填：引用的流程定义 ID
    calledVersion: 2                  # 可选：不填则使用最新版本
    inputMapping:                     # 可选：不配置则全部变量透传
      - from: applicant               # 父变量名
        to: user                      # 子流程中变量名
      - from: days
        to: days
    outputMapping:                    # 可选：不配置则全部回写
      - from: result
        to: approvalResult
  - id: end
    type: endEvent
transitions:
  - from: start
    to: call-leave
  - from: call-leave
    to: end
```

### 字段说明

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `calledId` | 是 | string | 引用的已部署流程定义 ID |
| `calledVersion` | 否 | integer | 指定版本，不填用最新版 |
| `inputMapping` | 否 | VariableMapping[] | 父→子变量映射，不配则全部透传 |
| `outputMapping` | 否 | VariableMapping[] | 子→父变量映射，不配则全部回写 |

### VariableMapping

| 字段 | 必填 | 说明 |
|------|------|------|
| `from` | 是 | 源变量名 |
| `to` | 否 | 目标变量名（缺省同 from）|
| `expr` | 否 | SpEL 表达式，对源值做转换（如 `#from * 2`）|

## 2. 后端模型改动

### 2.1 NodeType 枚举

`model/NodeType.java` — 新增 `CALL_ACTIVITY`

### 2.2 CallActivityNode

`model/node/CallActivityNode.java` — 新建：

```java
public class CallActivityNode extends Node {
    private final String calledId;
    private final Integer calledVersion;        // null = 最新版
    private final List<VariableMapping> inputMapping;
    private final List<VariableMapping> outputMapping;
}
```

### 2.3 VariableMapping

`model/VariableMapping.java` — 新建：

```java
public class VariableMapping {
    private String from;
    private String to;        // 缺省同 from
    private String expr;      // SpEL 表达式，可选
}
```

### 2.4 ProcessInstance 扩展

`model/ProcessInstance.java` — 新增两个字段：

```java
private String parentInstanceId;    // 调用方实例 ID（null = 顶层流程）
private String parentExecutionId;   // 调用方执行点 ID
```

### 2.5 NodeYaml 扩展

`dsl/NodeYaml.java` — 新增字段：

```java
private String calledId;
private Integer calledVersion;
private List<VariableMappingYaml> inputMapping;
private List<VariableMappingYaml> outputMapping;
```

### 2.6 Parser 改动

`dsl/YamlProcessParser.java` + `dsl/JsonProcessParser.java` — `convertNode()` 新增 `"callActivity"` case，映射到 `CallActivityNode`

## 3. 执行模型

### 3.1 CallActivityRunner

`engine/runner/CallActivityRunner.java` — 新建：

```
1. 加载子流程定义：processRepository.findLatestById(node.calledId)
   或按 calledVersion 指定版本加载
2. 解析 inputMapping：
   - 有映射 → 按映射构建子流程变量
   - 无映射 → 父流程变量全部透传
3. 调用 engine.start(calledId, childVariables)
4. 子流程实例记录 parentInstanceId 和 parentExecutionId
5. 当前执行设为 WAITING
6. return false（等待子流程完成）
```

### 3.2 EndEventRunner 增强

`engine/runner/EndEventRunner.java` — 子流程完成时唤醒父流程：

```
1. 检查 instance.parentInstanceId != null
2. 加载父实例，通过 parentExecutionId 找到父执行
3. 解析 outputMapping：
   - 有映射 → 按映射回写变量到父流程
   - 无映射 → 子流程变量全部回写到父流程
4. 父执行 currentNodeId → callActivity 的下一节点（沿过渡走）
5. 父执行 status → ACTIVE
6. engine.trigger(parentInstanceId)
```

### 3.3 WorkflowEngine 注册

`engine/WorkflowEngine.java` — `registerDefaultRunners()` 新增：

```java
runners.put(NodeType.CALL_ACTIVITY, 
    new CallActivityRunner(processRepository, instanceRepository, this::start));
```

## 4. 前端改动

### 4.1 CallActivityNode 组件

`designer/nodes/CallActivityNode.tsx` — 新建，紫色圆角矩形：

```
┌──────────────────┐
│ +↵  子流程调用    │
│     calledId      │
└──────────────────┘
```

### 4.2 PropertyPanel 增强

选中 Call Activity 节点时，属性面板显示：

| 字段 | 组件 | 数据源 |
|------|------|--------|
| `calledId` | 搜索下拉框 | `GET /api/definitions`（已有）|
| `calledVersion` | 版本下拉（latest / vN） | 选中流程后拉版本列表 |
| `inputMapping` | 键值对列表编辑器 | 手动输入 |
| `outputMapping` | 键值对列表编辑器 | 手动输入 |

### 4.3 双向转换

- `designer/graphToYaml.ts` — 新增 `callActivity` case
- `designer/yamlToGraph.ts` — 新增 `callActivity` case

### 4.4 注册

- `designer/nodes/index.ts` — 注册 `callActivity: CallActivityNode`
- `monitor/InstanceFlow.tsx` — 注册 `callActivity: CallActivityNode`

## 5. 改动文件清单

### 后端 (workflow-engine-core)

| 文件 | 操作 |
|------|------|
| `model/NodeType.java` | 修改：加 `CALL_ACTIVITY` 枚举值 |
| `model/node/CallActivityNode.java` | 新建 |
| `model/VariableMapping.java` | 新建 |
| `model/ProcessInstance.java` | 修改：加 `parentInstanceId` / `parentExecutionId` |
| `dsl/NodeYaml.java` | 修改：加 callActivity 字段 |
| `dsl/YamlProcessParser.java` | 修改：convertNode 加 case |
| `dsl/JsonProcessParser.java` | 修改：convertNode 加 case |
| `engine/runner/CallActivityRunner.java` | 新建 |
| `engine/runner/EndEventRunner.java` | 修改：子流程完成唤醒父流程 |
| `engine/WorkflowEngine.java` | 修改：注册 CallActivityRunner |

### 后端 (workflow-engine-memory)

| 文件 | 操作 |
|------|------|
| `InMemoryInstanceRepository.java` | 修改：保存/加载 `parentInstanceId` / `parentExecutionId` |
| `JdbcInstanceRepository.java` | 修改：同上 + 对应 SQL 列 |

### 后端 (workflow-engine-server)

| 文件 | 操作 |
|------|------|
| `controller/DefinitionController.java` | 修改：`graphFromDef` 支持 `callActivity` 节点类型映射 |

### 前端 (workflow-engine-web)

| 文件 | 操作 |
|------|------|
| `designer/nodes/CallActivityNode.tsx` | 新建 |
| `designer/nodes/index.ts` | 修改：注册 callActivity |
| `designer/PropertyPanel.tsx` | 修改：Call Activity 属性编辑器 |
| `designer/graphToYaml.ts` | 修改：加 callActivity case |
| `designer/yamlToGraph.ts` | 修改：加 callActivity case |
| `monitor/InstanceFlow.tsx` | 修改：注册 callActivity 到监控页 |

### 测试

| 文件 | 操作 |
|------|------|
| `core/src/test/.../CallActivityIntegrationTest.java` | 新建：端到端集成测试 |
| `core/src/test/resources/call-activity-parent.yaml` | 新建：父流程测试用例 |
| `core/src/test/resources/call-activity-child.yaml` | 新建：子流程测试用例 |

## 6. 测试策略

1. **单元测试**：YAML 解析 → CallActivityNode（含 VariableMapping）
2. **集成测试**：父流程启动 → 到达 CallActivity → 子流程启动 → 子流程完成 → 父流程唤醒 → 变量回写验证
3. **边界测试**：
   - 引用不存在的 calledId → 报错
   - 子流程执行中被 terminate → 父流程 SUSPENDED
   - 嵌套调用（父→子→孙）→ 变量透传正确
   - 并行网关内嵌 CallActivity → 各自子流程独立
4. **前端测试**：拖入 CallActivity 节点 → 选择 calledId → 正确生成 YAML
