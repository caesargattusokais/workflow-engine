package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.ExecutionStatus;
import com.github.wf.model.Node;
import com.github.wf.model.ProcessInstance;
import com.github.wf.model.Transition;
import com.github.wf.model.node.UserTask;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class UserTaskRunner implements NodeRunner {

    private final TaskRepository taskRepository;
    private final BiConsumer<String, Long> scheduler;

    public UserTaskRunner(TaskRepository taskRepository) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.scheduler = null;
    }

    public UserTaskRunner(TaskRepository taskRepository, BiConsumer<String, Long> scheduler) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.scheduler = scheduler;
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        UserTask userTask = (UserTask) node;
        Execution exec = context.getExecution();
        Map<String, Object> variables = context.getVariables();

        // Check if re-entering after boundary timer timeout
        String timerKey = node.getId() + "_boundaryTimerFired";
        ProcessInstance instance = context.getInstanceRepository().findById(context.getInstanceId());
        if (Boolean.TRUE.equals(instance.getVariable(timerKey))) {
            instance.setVariable(timerKey, null);
            context.getInstanceRepository().update(instance);
            // Route via timeout edge
            for (Transition t : context.getDefinition().getOutgoingTransitions(node.getId())) {
                if (t.isTimeout()) {
                    exec.setCurrentNodeId(t.getTo());
                    exec.setStatus(ExecutionStatus.ACTIVE);
                    return true;
                }
            }
            // If no timeout edge, fall through and create task normally
        }

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

        // Schedule boundary timer if configured
        if (userTask.getBoundaryTimer() != null && !userTask.getBoundaryTimer().isBlank() && scheduler != null) {
            try {
                long delayMs = Duration.parse(userTask.getBoundaryTimer()).toMillis();
                if (delayMs > 0) {
                    instance.setVariable(timerKey, true);
                    context.getInstanceRepository().update(instance);
                    scheduler.accept(exec.getInstanceId(), delayMs);
                    exec.setRetryState("TIMER_PENDING");
                }
            } catch (Exception e) {
                // Invalid duration format — skip boundary timer
            }
        }

        return true;
    }
}
