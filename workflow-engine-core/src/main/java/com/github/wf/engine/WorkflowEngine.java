package com.github.wf.engine;

import com.github.wf.dsl.ProcessParser;
import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.runner.*;
import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.ext.ServiceTaskHandler;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import com.github.wf.task.TaskStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.concurrent.*;

public class WorkflowEngine {

    private static final Log log = LogFactory.getLog(WorkflowEngine.class);
    public final ProcessRepository processRepository;
    public final InstanceRepository instanceRepository;
    public final TaskRepository taskRepository;
    final ExpressionEvaluator expressionEvaluator;
    final DelayQueue<DelayedTrigger> delayQueue = new DelayQueue<>();
    final InstanceLockManager lockManager;

    private final Map<NodeType, NodeRunner> runners = new HashMap<>();
    private ProcessParser processParser = new YamlProcessParser();
    private String baseUrl;

    WorkflowEngine(ProcessRepository processRepository,
                   InstanceRepository instanceRepository,
                   TaskRepository taskRepository,
                   ExpressionEvaluator expressionEvaluator,
                   com.github.wf.ext.OrgService orgService,
                   String baseUrl,
                   InstanceLockManager lockManager) {
        this.processRepository = processRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.expressionEvaluator = expressionEvaluator;
        this.baseUrl = baseUrl;
        this.lockManager = lockManager;
        registerDefaultRunners(orgService);
        // Delay daemon: picks up delayed triggers (retry/timer), wakes instances
        Thread delayDaemon = new Thread(() -> {
            while (true) {
                try {
                    DelayedTrigger dt = delayQueue.take();
                    log.warn("Delayed trigger: " + dt.instanceId);
                    trigger(dt.instanceId);
                } catch (InterruptedException e) { break; }
            }
        }, "wf-delay-daemon");
        delayDaemon.setDaemon(true);
        delayDaemon.start();
    }

    private void registerDefaultRunners(com.github.wf.ext.OrgService orgService) {
        runners.put(NodeType.START_EVENT, new StartEventRunner());
        runners.put(NodeType.END_EVENT, new EndEventRunner());
        runners.put(NodeType.USER_TASK, new UserTaskRunner(taskRepository, this::scheduleDelay, baseUrl, orgService));
        runners.put(NodeType.SERVICE_TASK, new ServiceTaskRunner(this::scheduleDelay));
        runners.put(NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayRunner());
        runners.put(NodeType.PARALLEL_GATEWAY, new ParallelGatewayRunner());
        runners.put(NodeType.INCLUSIVE_GATEWAY, new InclusiveGatewayRunner());
        runners.put(NodeType.TIMER, new TimerRunner(this::scheduleDelay));
    }

    public static WorkflowEngineBuilder builder() { return new WorkflowEngineBuilder(); }

    /**
     * Recover pending timer/retry executions after server restart.
     * Scans all RUNNING instances for WAITING+TIMER_PENDING or WAITING+RETRY_PENDING
     * executions and re-triggers them so the daemon can pick them up.
     */
    public void recover() {
        log.warn("Starting recovery scan...");
        int count = 0;
        List<Execution> pending = instanceRepository.findPendingTimerRetry();
        for (Execution exec : pending) {
            log.warn("Recovering pending execution: instance=" + exec.getInstanceId()
                    + " node=" + exec.getCurrentNodeId() + " state=" + exec.getRetryState());
            exec.setStatus(ExecutionStatus.ACTIVE);
            exec.setRetryState(null);
            instanceRepository.updateExecution(exec);
            trigger(exec.getInstanceId());
            count++;
        }
        log.warn("Recovery complete: " + count + " executions re-triggered");
    }

    public void setProcessParser(ProcessParser processParser) { this.processParser = processParser; }

    /**
     * Register a service task handler for the given class name.
     * Handlers registered this way take precedence over classpath instantiation.
     */
    public void registerServiceHandler(String className, ServiceTaskHandler handler) {
        ServiceTaskRunner runner = (ServiceTaskRunner) runners.get(NodeType.SERVICE_TASK);
        runner.registerHandler(className, handler);
    }

    public TaskQuery taskQuery() { return new TaskQuery(); }

    public List<Task> queryTasks(TaskQuery query) {
        return taskRepository.query(query);
    }

    // === Public API ===

    public ProcessDefinition deploy(String yaml) {
        ProcessDefinition def = processParser.parse(yaml);
        // Use the version from the YAML; if that exact version exists, return it (idempotent)
        int version = def.getVersion();
        ProcessDefinition versioned = new ProcessDefinition(def.getId(), def.getName(), version,
                new ArrayList<>(def.getNodes().values()), def.getTransitions());
        processRepository.save(versioned);
        return versioned;
    }

    public ProcessDefinition deploy(java.io.File file) {
        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            ProcessDefinition def = processParser.parse(reader);
            ProcessDefinition versioned = new ProcessDefinition(def.getId(), def.getName(), def.getVersion(),
                    new ArrayList<>(def.getNodes().values()), def.getTransitions());
            processRepository.save(versioned);
            return versioned;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to deploy workflow file: " + file, e);
        }
    }

    public ProcessInstance start(String definitionId, Map<String, Object> variables) {
        ProcessDefinition def = processRepository.findLatestById(definitionId);
        if (def == null) throw new IllegalArgumentException("Process definition not found: " + definitionId);

        ProcessInstance instance = new ProcessInstance(null, definitionId, def.getVersion(), variables);
        instanceRepository.save(instance);

        Node startNode = def.getStartNode();
        Execution exec = new Execution(instance.getId(), startNode.getId());
        instanceRepository.saveExecution(exec);

        instance.setActiveNodeIds(Set.of(startNode.getId()));
        instanceRepository.update(instance);

        trigger(instance.getId());
        return instanceRepository.findById(instance.getId());
    }

    /** Generic delayed trigger — used by both retry and timer nodes */
    public void scheduleDelay(String instanceId, long delayMs) {
        delayQueue.put(new DelayedTrigger(instanceId, delayMs));
    }

    static class DelayedTrigger implements Delayed {
        final String instanceId;
        final long deadline; // System.nanoTime() — monotonic
        DelayedTrigger(String iid, long delayMs) {
            this.instanceId = iid;
            this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        }
        public long getDelay(TimeUnit unit) {
            return unit.convert(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
        }
        public int compareTo(Delayed o) {
            return Long.compare(deadline, ((DelayedTrigger)o).deadline);
        }
    }

    // === Trigger Loop ===

    public void trigger(String instanceId) {
        lockManager.lock(instanceId);
        try {
            ProcessInstance instance = instanceRepository.findById(instanceId);
            if (instance == null || !instance.isRunning()) return;

            ProcessDefinition def = processRepository.findLatestById(instance.getDefinitionId());
            if (def == null) return;

            // Reactivate pending executions BEFORE the loop (only from daemon wake-ups)
            List<Execution> executions = instanceRepository.findActiveExecutions(instanceId);
            for (Execution exec : executions) {
                if (exec.isWaiting() && ("RETRY_PENDING".equals(exec.getRetryState()) || "TIMER_PENDING".equals(exec.getRetryState()))) {
                    exec.setStatus(ExecutionStatus.ACTIVE);
                    exec.setRetryState(null);
                    instanceRepository.updateExecution(exec);
                }
            }

            boolean advanced;
            do {
                advanced = false;
                executions = instanceRepository.findActiveExecutions(instanceId);

                if (executions.isEmpty()) {
                    instance.setStatus(InstanceStatus.COMPLETED);
                    instanceRepository.update(instance);
                    return;
                }

                for (Execution exec : executions) {
                    if (!exec.isActive()) continue;
                    Node node = def.getNode(exec.getCurrentNodeId());
                    NodeRunner runner = runners.get(node.getType());

                    if (runner != null) {
                        ExecutionContext ctx = new ExecutionContext(def, exec,
                                expressionEvaluator, instanceRepository);
                        try {
                            if (runner.run(node, ctx)) {
                                advanced = true;
                            }
                            instanceRepository.updateExecution(exec);
                            instanceRepository.saveHistoricActivity(
                                    HistoricActivity.nodeEnter(instanceId, node.getId(),
                                            node.getName(), node.getType()));
                            invokeListeners(node, instanceId, instance.getVariables(), true);

                            // Check if runner signaled suspension
                            if ("SUSPENDED".equals(exec.getRetryState())) {
                                if (!exec.isChild()) {
                                    // Main execution — suspend instance immediately
                                    instance.setStatus(InstanceStatus.SUSPENDED);
                                    instanceRepository.update(instance);
                                    return;
                                }
                                // Child execution (parallel fork) — check remaining siblings
                                List<Execution> siblings = instanceRepository.findExecutionsByParentId(exec.getParentExecutionId());
                                // Exclude completed siblings — check only the ones still alive
                                List<Execution> remaining = siblings.stream()
                                        .filter(s -> !s.isCompleted())
                                        .toList();
                                boolean allSuspended = !remaining.isEmpty() && remaining.stream()
                                        .allMatch(s -> "SUSPENDED".equals(s.getRetryState()));
                                if (allSuspended) {
                                    instance.setStatus(InstanceStatus.SUSPENDED);
                                    instanceRepository.update(instance);
                                    return;
                                }
                                // Some remaining siblings still running — don't suspend yet
                            }
                        } catch (Exception e) {
                            log.error("Error running node " + node.getId(), e);
                        }
                    }
                }
            } while (advanced);

            // Update active node snapshot
            List<Execution> remaining = instanceRepository.findActiveExecutions(instanceId);
            Set<String> activeNodes = new HashSet<>();
            for (Execution e : remaining) {
                activeNodes.add(e.getCurrentNodeId());
            }
            instance.setActiveNodeIds(activeNodes);
            instanceRepository.update(instance);

        } finally {
            lockManager.unlock(instanceId);
        }
    }

    private void invokeListeners(Node node, String instanceId,
                                  Map<String, Object> variables, boolean enter) {
        for (String listenerClass : node.getListeners()) {
            try {
                Class<?> clazz = Class.forName(listenerClass);
                com.github.wf.ext.ProcessListener listener =
                        (com.github.wf.ext.ProcessListener) clazz.getDeclaredConstructor().newInstance();
                if (enter) {
                    listener.onNodeEnter(instanceId, node.getId(), variables);
                } else {
                    listener.onNodeLeave(instanceId, node.getId(), variables);
                }
            } catch (Exception e) {
                log.error("Listener error: " + listenerClass, e);
            }
        }
    }

    // === Task operations (placeholders for Task 16) ===

    public void completeTask(String taskId, Map<String, Object> variables, String comment) {
        Task task = taskRepository.findById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
        if (!task.isPending()) throw new IllegalStateException("Task is not pending: " + taskId);

        task.setStatus(TaskStatus.COMPLETED);
        task.setVariables(variables);
        taskRepository.update(task);

        ProcessInstance instance = instanceRepository.findById(task.getInstanceId());
        ProcessDefinition def = processRepository.findLatestById(instance.getDefinitionId());
        Node node = def.getNode(task.getNodeId());
        instanceRepository.saveHistoricActivity(
                HistoricActivity.taskCompleted(instance.getId(), node.getId(),
                        node.getName(), node.getType(), task.getAssignee(), comment));

        if (variables != null && !variables.isEmpty()) {
            instance.setVariables(variables);
            instanceRepository.update(instance);
        }

        // Wake up waiting execution
        List<Execution> executions = instanceRepository.findActiveExecutions(task.getInstanceId());
        for (Execution exec : executions) {
            if (exec.isWaiting() && exec.getCurrentNodeId().equals(task.getNodeId())) {
                // Clear any pending timer/retry state — user completed manually
                log.info("Clearing retry state for node=" + exec.getCurrentNodeId());
                exec.setRetryState(null);
                instance.removeVariable(task.getNodeId() + "_boundaryTimerFired");
                instanceRepository.update(instance);

                // Check for dynamic router
                if (node instanceof UserTask) {
                    UserTask ut = (UserTask) node;
                    if (ut.getDynamicRouter() != null) {
                        String nextNodeId = invokeDynamicRouter(ut.getDynamicRouter(),
                                instance.getId(), node.getId(), instance.getVariables());
                        exec.setCurrentNodeId(nextNodeId);
                    } else {
                        // Pick first non-routing edge (direct/conditional/default), skip timeout/result/exception
                        List<com.github.wf.model.Transition> outgoing = def.getOutgoingTransitions(node.getId());
                        com.github.wf.model.Transition picked = null;
                        for (com.github.wf.model.Transition t : outgoing) {
                            if (t.isDirect()) { picked = t; break; }
                        }
                        if (picked == null) {
                            for (com.github.wf.model.Transition t : outgoing) {
                                if (t.isConditional() || t.isDefault()) { picked = t; break; }
                            }
                        }
                        if (picked != null) exec.setCurrentNodeId(picked.getTo());
                    }
                } else {
                    List<com.github.wf.model.Transition> outgoing = def.getOutgoingTransitions(node.getId());
                    if (!outgoing.isEmpty()) exec.setCurrentNodeId(outgoing.get(0).getTo());
                }
                exec.setStatus(com.github.wf.model.ExecutionStatus.ACTIVE);
                instanceRepository.updateExecution(exec);
                break;
            }
        }

        trigger(task.getInstanceId());
    }

    public void rejectTask(String taskId, String comment) {
        Task task = taskRepository.findById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
        task.setStatus(TaskStatus.REJECTED);
        taskRepository.update(task);

        ProcessInstance instance = instanceRepository.findById(task.getInstanceId());
        ProcessDefinition def = processRepository.findLatestById(instance.getDefinitionId());
        Node node = def.getNode(task.getNodeId());
        instanceRepository.saveHistoricActivity(
                HistoricActivity.taskRejected(instance.getId(), node.getId(),
                        node.getName(), node.getType(), task.getAssignee(), comment));
    }

    public void delegateTask(String taskId, String newAssignee) {
        Task task = taskRepository.findById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
        String oldAssignee = task.getAssignee();
        task.setStatus(TaskStatus.DELEGATED);
        taskRepository.update(task);

        Task delegated = new Task(null, task.getInstanceId(), task.getNodeId());
        delegated.setAssignee(newAssignee);
        delegated.setCandidateGroups(task.getCandidateGroups());
        delegated.setVariables(task.getVariables());
        taskRepository.save(delegated);

        ProcessInstance instance = instanceRepository.findById(task.getInstanceId());
        ProcessDefinition def = processRepository.findLatestById(instance.getDefinitionId());
        Node node = def.getNode(task.getNodeId());
        instanceRepository.saveHistoricActivity(
                HistoricActivity.taskDelegated(instance.getId(), node.getId(),
                        node.getName(), node.getType(), oldAssignee, newAssignee));
    }

    public void terminate(String instanceId, String reason) {
        ProcessInstance instance = instanceRepository.findById(instanceId);
        if (instance == null) throw new IllegalArgumentException("Instance not found: " + instanceId);
        instance.setStatus(InstanceStatus.TERMINATED);
        instanceRepository.update(instance);

        List<Task> tasks = taskRepository.query(taskQuery().instanceId(instanceId));
        for (Task t : tasks) {
            if (t.isPending()) {
                t.setStatus(TaskStatus.REJECTED);
                taskRepository.update(t);
            }
        }

        List<Execution> executions = instanceRepository.findActiveExecutions(instanceId);
        for (Execution exec : executions) {
            exec.setStatus(com.github.wf.model.ExecutionStatus.COMPLETED);
            instanceRepository.updateExecution(exec);
        }

        if (reason != null) {
            instanceRepository.saveHistoricActivity(new HistoricActivity(null, instanceId,
                    "system", "terminate", null, "system", "terminate", null, reason));
        }
    }

    /**
     * Resume a suspended process instance. The failed service task will re-execute.
     */
    public void resume(String instanceId) {
        ProcessInstance instance = instanceRepository.findById(instanceId);
        if (instance == null || instance.getStatus() != InstanceStatus.SUSPENDED) {
            throw new IllegalStateException("Instance not found or not suspended: " + instanceId);
        }

        instance.setStatus(InstanceStatus.RUNNING);
        instance.setVariable("_suspendReason", null);
        instance.setVariable("_suspendException", null);
        instanceRepository.update(instance);

        // Find the suspended execution and reactivate it
        List<Execution> executions = instanceRepository.findActiveExecutions(instanceId);
        for (Execution exec : executions) {
            if (exec.isWaiting() && "SUSPENDED".equals(exec.getRetryState())) {
                exec.setRetryState(null);
                exec.setRetryAttempt(0);
                exec.setNextRetryAt(0);
                exec.setStatus(ExecutionStatus.ACTIVE);
                instanceRepository.updateExecution(exec);
            }
        }

        trigger(instanceId);
    }

    public List<HistoricActivity> history(String instanceId) {
        return instanceRepository.findHistory(instanceId);
    }

    private String invokeDynamicRouter(String routerClass, String instanceId,
                                        String currentNodeId, Map<String, Object> variables) {
        try {
            Class<?> clazz = Class.forName(routerClass);
            com.github.wf.ext.DynamicRouter router =
                    (com.github.wf.ext.DynamicRouter) clazz.getDeclaredConstructor().newInstance();
            return router.nextNode(instanceId, currentNodeId, variables);
        } catch (Exception e) {
            throw new RuntimeException("Cannot invoke DynamicRouter: " + routerClass, e);
        }
    }
}
