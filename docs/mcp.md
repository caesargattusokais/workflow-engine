# MCP Server — 把工作流引擎接入 AI 客户端

`workflow-engine-mcp` 是一个独立的 Spring Boot 应用，把引擎的运维操作暴露为 **MCP (Model Context Protocol) 工具**，让 Claude Code、Cursor 等 AI 客户端可以直接部署流程、启动实例、办理任务、做 AI 诊断——用自然语言。

## 架构

```
┌──────────────┐   MCP (streamable HTTP)   ┌────────────────────┐
│ Claude Code  │ ────────────────────────► │ workflow-engine-mcp │
│ / Cursor     │      POST /mcp            │  (端口 8081)        │
└──────────────┘                           └────────┬───────────┘
                                                    │ REST + X-User-Id
                                                    ▼
                                           ┌────────────────────┐
                                           │ workflow-engine-    │
                                           │ server (端口 8080)  │
                                           └────────────────────┘
```

- **独立进程，不嵌引擎**：MCP 模块通过引擎的 REST API 工作，不共享 JVM/数据库/锁，可以部署在任意能访问引擎的位置。
- **身份**：MCP server 以配置的用户（默认 `mcp-agent`，环境变量 `WF_MCP_USER`）调用引擎，引擎的租户过滤对它和对人类用户完全一致——它只能看到/操作自己名下的数据。

## 启动

```bash
# 前置：引擎已运行（默认假设 localhost:8080）
mvn -pl workflow-engine-mcp package -DskipTests
java -jar workflow-engine-mcp/target/workflow-engine-mcp-1.0.0-SNAPSHOT.jar \
  --wf.engine.base-url=http://localhost:8080/api \
  --wf.engine.user=mcp-agent
# MCP 端点：http://localhost:8081/mcp
```

环境变量：`WF_ENGINE_URL`（引擎 API 地址）、`WF_MCP_USER`（MCP 身份）。

## 接入 AI 客户端

### Claude Code

```bash
claude mcp add --transport http workflow-engine http://localhost:8081/mcp
```

### Cursor / 其他支持 Streamable HTTP 的客户端

在 MCP 配置中加入：

```json
{
  "mcpServers": {
    "workflow-engine": {
      "url": "http://localhost:8081/mcp"
    }
  }
}
```

## 工具清单（10 个）

| 工具 | 说明 | 读/写 |
|------|------|-------|
| `list_workflow_definitions` | 列出已部署的流程定义 | 读 |
| `deploy_workflow` | 从 YAML 部署流程定义 | 写 |
| `start_workflow_instance` | 启动实例（传业务变量） | 写 |
| `list_workflow_instances` | 按状态/定义过滤实例 | 读 |
| `get_workflow_instance` | 实例详情（状态/活动节点/变量） | 读 |
| `get_workflow_history` | 实例执行历史 | 读 |
| `delete_instance` | 删除实例（危险操作） | 写 |
| `list_tasks` | 待办任务（按办理人/实例/状态） | 读 |
| `complete_task` | 完成任务（带表单变量与审批意见） | 写 |
| `diagnose_instance` | AI 副驾：解释实例为什么卡住并给恢复建议 | 读 |

## 对话示例

接入后可以直接说：

- 「帮我部署这个报销流程」（粘贴 YAML）
- 「启动一个报销流程，申请人张三，报销说明：差旅费 2300 元」
- 「现在有哪些待办？把 alice 的 review 任务批掉」
- 「实例 58444759 为什么卡住了？」→ 调用 `diagnose_instance` 返回结构化诊断（summary/cause/suggestions/severity）

## 验证记录（2026-11-20）

JSON-RPC 全链路真机验证通过：`initialize` → `tools/list`（10 工具）→ `deploy_workflow` → `start_workflow_instance` → `list_tasks` → `complete_task` → `get_workflow_instance`（COMPLETED）→ `diagnose_instance`（stub LLM 结构化诊断）→ `delete_instance`。

## 已知边界

- `complete_task` 需要的必填表单字段（userTask `formFields`）由引擎端校验，缺失返回 400，AI 会看到错误信息并重试。
- MCP server 不内置 LLM；`diagnose_instance` 依赖引擎端配置的 AI 服务商（设置页或 `AI_*` 环境变量）。
