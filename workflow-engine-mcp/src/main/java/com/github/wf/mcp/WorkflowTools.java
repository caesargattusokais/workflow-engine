package com.github.wf.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * MCP tools exposing the workflow engine to AI clients. Every tool maps 1:1
 * onto an engine REST endpoint and is read- or write-scoped accordingly:
 *
 * <ul>
 *   <li>list_workflow_definitions / list_workflow_instances / get_workflow_instance /
 *       get_workflow_history / list_tasks — read-only inspection</li>
 *   <li>deploy_workflow / start_workflow_instance / complete_task / delete_instance —
 *       mutations an operator would normally do from the UI</li>
 *   <li>diagnose_instance — AI copilot: explains why an instance is stuck</li>
 * </ul>
 *
 * <p>Variables are passed as free-form key/value maps and forwarded verbatim,
 * so AI clients can fill business fields (e.g. {@code applicant}, {@code amount})
 * without this layer knowing them.</p>
 */
@Service
public class WorkflowTools {

    private final EngineClient engine;

    public WorkflowTools(EngineClient engine) {
        this.engine = engine;
    }

    // ── Definitions ──────────────────────────────────────────────

    @Tool(name = "list_workflow_definitions",
            description = "List deployed workflow definitions (id, name, version). Use this first to discover what flows exist before starting one.")
    public Object listDefinitions(
            @ToolParam(description = "Page number, starting at 1 (default 1)", required = false) Integer page,
            @ToolParam(description = "Page size (default 20)", required = false) Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : size;
        return engine.get("/definitions?page=" + p + "&size=" + s);
    }

    @Tool(name = "deploy_workflow",
            description = "Deploy a workflow definition from YAML. Returns the created definition with its id, which is needed to start instances. The YAML must follow the engine DSL: nodes (startEvent, userTask, aiTask, exclusiveGateway, parallelGateway, timer, endEvent...) and transitions.")
    public Object deployWorkflow(
            @ToolParam(description = "Workflow definition YAML") String yaml) {
        return engine.post("/definitions", Map.of("yaml", yaml));
    }

    // ── Instances ────────────────────────────────────────────────

    @Tool(name = "start_workflow_instance",
            description = "Start a new instance of a deployed workflow definition. Business variables (e.g. applicant, days, amount) are passed verbatim to the engine and drive assignee expressions and gateway conditions.")
    public Object startInstance(
            @ToolParam(description = "Definition id returned by deploy_workflow or list_workflow_definitions") String definitionId,
            @ToolParam(description = "Business variables as key/value pairs, e.g. {\"applicant\":\"zhangsan\",\"days\":5}", required = false) Map<String, Object> variables) {
        return engine.post("/instances", Map.of(
                "definitionId", definitionId,
                "variables", variables == null ? Map.of() : variables));
    }

    @Tool(name = "list_workflow_instances",
            description = "List workflow instances, optionally filtered by definition id or status (RUNNING, COMPLETED, TERMINATED, SUSPENDED).")
    public Object listInstances(
            @ToolParam(description = "Filter by definition id", required = false) String definitionId,
            @ToolParam(description = "Filter by status: RUNNING / COMPLETED / TERMINATED / SUSPENDED", required = false) String status,
            @ToolParam(description = "Page number (default 1)", required = false) Integer page,
            @ToolParam(description = "Page size (default 20)", required = false) Integer size) {
        StringBuilder url = new StringBuilder("/instances?page=")
                .append(page == null || page < 1 ? 1 : page)
                .append("&size=").append(size == null || size < 1 ? 20 : size);
        if (definitionId != null && !definitionId.isBlank()) url.append("&definitionId=").append(urlEncode(definitionId));
        if (status != null && !status.isBlank()) url.append("&status=").append(urlEncode(status));
        return engine.get(url.toString());
    }

    @Tool(name = "get_workflow_instance",
            description = "Get one workflow instance: status, active nodes and all variables (business fields plus engine-internal keys prefixed with _).")
    public Object getInstance(
            @ToolParam(description = "Instance id") String instanceId) {
        return engine.get("/instances/" + urlEncode(instanceId));
    }

    @Tool(name = "get_workflow_history",
            description = "Get the step-by-step history of a workflow instance (node, action, executor, time, comment). Useful to reconstruct what already happened before deciding next steps.")
    public Object getInstanceHistory(
            @ToolParam(description = "Instance id") String instanceId) {
        return engine.get("/instances/" + urlEncode(instanceId) + "/history");
    }

    @Tool(name = "delete_instance",
            description = "Permanently delete a workflow instance and its history. Destructive — prefer terminate via the UI unless the caller explicitly wants deletion.")
    public Object deleteInstance(
            @ToolParam(description = "Instance id") String instanceId) {
        return engine.delete("/instances/" + urlEncode(instanceId));
    }

    // ── Tasks ────────────────────────────────────────────────────

    @Tool(name = "list_tasks",
            description = "List human tasks, optionally filtered by assignee, instance id or status (PENDING, COMPLETED). Use assignee to find the current user's todo list.")
    public Object listTasks(
            @ToolParam(description = "Filter by assignee user id", required = false) String assignee,
            @ToolParam(description = "Filter by instance id", required = false) String instanceId,
            @ToolParam(description = "Filter by status, e.g. PENDING", required = false) String status) {
        StringBuilder url = new StringBuilder("/tasks?");
        boolean first = true;
        for (String[] kv : new String[][]{
                {"assignee", assignee}, {"instanceId", instanceId}, {"status", status}}) {
            if (kv[1] != null && !kv[1].isBlank()) {
                url.append(first ? "" : "&").append(kv[0]).append("=").append(urlEncode(kv[1]));
                first = false;
            }
        }
        return engine.get(url.toString());
    }

    @Tool(name = "complete_task",
            description = "Complete a pending human task, optionally supplying its form variables (use list_tasks to see each task's required formFields — missing required fields are rejected with 400) and an approval comment.")
    public Object completeTask(
            @ToolParam(description = "Task id from list_tasks") String taskId,
            @ToolParam(description = "Task variables as key/value pairs, e.g. {\"amountText\":\"差旅费 2300 元\"}", required = false) Map<String, Object> variables,
            @ToolParam(description = "Approval comment", required = false) String comment) {
        return engine.post("/tasks/" + urlEncode(taskId) + "/complete", Map.of(
                "variables", variables == null ? Map.of() : variables,
                "comment", comment == null ? "" : comment));
    }

    // ── AI copilot ───────────────────────────────────────────────

    @Tool(name = "diagnose_instance",
            description = "AI copilot: ask the configured LLM why a workflow instance is stuck/suspended and what to do next. Returns structured diagnosis (summary, cause, suggestions, severity). Read-only.")
    public Object diagnoseInstance(
            @ToolParam(description = "Instance id") String instanceId) {
        return engine.post("/ai/diagnose", Map.of("instanceId", instanceId));
    }

    @SuppressWarnings("deprecation")
    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s);
    }
}
