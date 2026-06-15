package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.ExecutionStatus;
import com.github.wf.model.Node;
import com.github.wf.model.node.UserTask;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;

import java.util.Map;
import java.util.Objects;

public class UserTaskRunner implements NodeRunner {

    private final TaskRepository taskRepository;

    public UserTaskRunner(TaskRepository taskRepository) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        UserTask userTask = (UserTask) node;
        Execution exec = context.getExecution();
        Map<String, Object> variables = context.getVariables();

        // Check if a pending task already exists for this execution+node
        boolean taskExists = taskRepository.query(
                new com.github.wf.task.TaskQuery().instanceId(exec.getInstanceId()))
                .stream()
                .anyMatch(t -> t.getNodeId().equals(node.getId()) && t.isPending());

        if (taskExists) {
            return false; // already waiting, no advance
        }

        // Create task
        Task task = new Task(null, exec.getInstanceId(), node.getId());

        // Evaluate assignee expression or use literal
        if (userTask.getAssignee() != null) {
            String assigneeExpr = userTask.getAssignee();
            if (assigneeExpr.startsWith("${") && assigneeExpr.endsWith("}")) {
                assigneeExpr = assigneeExpr.substring(2, assigneeExpr.length() - 1);
                Object assignee = context.getExpressionEvaluator().evaluate(assigneeExpr, variables);
                task.setAssignee(assignee != null ? assignee.toString() : null);
            } else {
                task.setAssignee(assigneeExpr);
            }
        }

        task.setCandidateGroups(userTask.getCandidateGroups());
        task.setVariables(Map.copyOf(variables));
        taskRepository.save(task);

        exec.setStatus(ExecutionStatus.WAITING);
        return true;
    }
}
