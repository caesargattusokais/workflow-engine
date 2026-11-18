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
import java.util.concurrent.ConcurrentLinkedQueue;

public class WorkflowEngine {

    private static final Log log = LogFactory.getLog(WorkflowEngine.class);
    public final ProcessRepository processRepository;
    public final InstanceRepository instanceRepository;
    public final TaskRepository taskRepository;
    final ExpressionEvaluator expressionEvaluator;
    final DelayScheduler delayScheduler;
    final InstanceLockManager lockManager;

    private final Map<NodeType, NodeRunner> runners = new HashMap<>();
    private ProcessParser processParser = new YamlProcessParser();
    private String baseUrl;
    private final List<InstanceStateListener> stateListeners = new ArrayList<>();
    /**
     * Cross-instance triggers deferred until the current trigger() releases its
     * instance lock. Prevents AB-BA deadlocks between parent→child (CallActivity)
     * and child→parent (EndEvent) lock acquisition orders.
     */
    private final ConcurrentLinkedQueue<String> deferredTriggers = new ConcurrentLinkedQueue<>();

    WorkflowEngine(ProcessRepository processRepository,
                   InstanceRepository instanceRepository,
                   TaskRepository taskRepository,
                   ExpressionEvaluator expressionEvaluator,
                   com.github.wf.ext.OrgService orgService,
                   String baseUrl,
                   InstanceLockManager lockManager,
                   DelayScheduler delayScheduler) {
        this.processRepository = processRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.expressionEvaluator = expressionEvaluator;
        this.baseUrl = baseUrl;
        this.lockManager = lockManager;
        this.delayScheduler = delayScheduler;
        this.delayScheduler.start(this::trigger);
        registerDefaultRunners(orgService);
    }

    private void registerDefaultRunners(com.github.wf.ext.OrgService orgService) {
        runners.put(NodeType.START_EVENT, new StartEventRunner());
        runners.put(NodeType.END_EVENT, new EndEventRunner(processRepository, this::deferTrigger));
        runners.put(NodeType.USER_TASK, new UserTaskRunner(taskRepository, delayScheduler::schedule, baseUrl, orgService));
        runners.put(NodeType.SERVICE_TASK, new ServiceTaskRunner(delayScheduler::schedule));
        runners.put(NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayRunner());
        runners.put(NodeType.PARALLEL_GATEWAY, new ParallelGatewayRunner());
        runners.put(NodeType.INCLUSIVE_GATEWAY, new InclusiveGatewayRunner());
        runners.put(NodeType.TIMER, new TimerRunner(delayScheduler::schedule));
        runners.put(NodeType.CALL_ACTIVITY, new CallActivityRunner(
            processRepository, instanceRepository, this::deferTrigger));
    }

    /**
     * Defer a cross-instance trigger instead of invoking it while the current
     * trigger() still holds its instance lock. The deferred trigger is drained
     * synchronously in trigger()'s finally block after the lock is released,
     * so execution order/semantics are preserved without nested locking.
     */
    void deferTrigger(String instanceId) {
        deferredTriggers.add(instanceId);
    }

    private void drainDeferredTriggers() {
        String next;
        while ((next = deferredTriggers.poll()) != null) {
            try {
                trigger(next);
            } catch (Exception e) {
                log.error("Deferred trigger failed for instance " + next, e);
            }
        }
    }

    public void addStateListener(InstanceStateListener listener) {
        stateListeners.add(listener);
    }

    private void notifyStateListeners(String instanceId) {
        for (InstanceStateListener l : stateListeners) {
            try { l.onStateChanged(instanceId); } catch (Exception e) {
                log.warn("State listener error: " + e.getMessage());
            }
        }
    }

    public static WorkflowEngineBuilder builder() { return new WorkflowEngineBuilder(); }

    /**
     * Recover pending timer/retry executions after server restart.
     * Scans all RUNNING instances for WAITING+TIMER_PENDING or WAITING+RETRY_PENDING
     * executions and re-triggers them so the daemon can pick them up.
     */
    public void recover() {
        // Global recovery lock — prevents multiple nodes from scanning concurrently
        lockManager.writeLock("__recover__");
        try {
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
        } finally {
            lockManager.writeUnlock("__recover__");
        }
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
        delayScheduler.schedule(instanceId, delayMs);
    }

    /** Shut down the delay scheduler (e.g. stop daemon thread). */
    public void shutdown() {
        delayScheduler.stop();
    }

    // === Trigger Loop ===

    /**
     * Resolve the ProcessDefinition for a running instance using its pinned version.
     * Falls back to findLatestById if version is 0 (legacy instances without version).
     */
    private ProcessDefinition resolveDefinition(ProcessInstance instance) {
        if (instance.getDefinitionVersion() > 0) {
            ProcessDefinition def = processRepository.findByIdAndVersion(
                    instance.getDefinitionId(), instance.getDefinitionVersion());
            if (def != null) return def;
        }
        return processRepository.findLatestById(instance.getDefinitionId());
    }

    public void trigger(String instanceId) {
        lockManager.lock(instanceId);
        try {
            ProcessInstance instance = instanceRepository.findById(instanceId);
            if (instance == null || !instance.isRunning()) return;

            ProcessDefinition def = resolveDefinition(instance);
            if (def == null) return;

            // Reactivate pending executions BEFORE the loop (only from daemon wake-ups)
            List<Execution> executions = instanceRepository.findActiveExecutions(instanceId);
            for (Execution exec : executions) {
                if (exec.isWaiting() && (exec.isRetryPending() || exec.isTimerPending())) {
                    // Signal boundary timer expiry before reactivating, so UserTaskRunner can detect it
                    if (exec.isTimerPending()) {
                        String timerKey = InternalVars.boundaryTimerFiredKey(exec.getCurrentNodeId());
                        instance.setVariable(timerKey, true);
                        instanceRepository.update(instance);
                    }
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
                    instance = instanceRepository.findById(instanceId);
                    instance.setStatus(InstanceStatus.COMPLETED);
                    instance.setActiveNodeIds(Set.of());
                    instanceRepository.update(instance);
                    notifyStateListeners(instanceId);
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
                                instanceRepository.updateExecution(exec);
                                instanceRepository.saveHistoricActivity(
                                        HistoricActivity.nodeEnter(instanceId, node.getId(),
                                                node.getName(), node.getType()));
                                invokeListeners(node, instanceId, instance.getVariables(), true);

                                // Check if runner signaled suspension
                                if (exec.isSuspended()) {
                                    if (!exec.isChild()) {
                                        // Main execution — suspend instance immediately
                                        instance = instanceRepository.findById(instanceId);
                                        instance.setStatus(InstanceStatus.SUSPENDED);
                                        instanceRepository.update(instance);
                                        notifyStateListeners(instanceId);
                                        return;
                                    }
                                    // Child execution (parallel fork) — check remaining siblings
                                    List<Execution> siblings = instanceRepository.findExecutionsByParentId(exec.getParentExecutionId());
                                    // Exclude completed siblings — check only the ones still alive
                                    List<Execution> remaining = siblings.stream()
                                            .filter(s -> !s.isCompleted())
                                            .toList();
                                    boolean allSuspended = !remaining.isEmpty() && remaining.stream()
                                            .allMatch(Execution::isSuspended);
                                    if (allSuspended) {
                                        instance = instanceRepository.findById(instanceId);
                                        instance.setStatus(InstanceStatus.SUSPENDED);
                                        instanceRepository.update(instance);
                                        notifyStateListeners(instanceId);
                                        return;
                                    }
                                    // Some remaining siblings still running — don't suspend yet
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error running node " + node.getId() + " — suspending execution", e);
                            // Suspend the failed execution instead of leaving it in an inconsistent state
                            exec.setRetryState(RetryState.SUSPENDED.name());
                            exec.setStatus(ExecutionStatus.WAITING);
                            instanceRepository.updateExecution(exec);
                            // Set suspend reason on the instance
                            instance.setVariable(InternalVars.suspendReasonKey(node.getId()), e.getMessage());
                            instance.setVariable(InternalVars.suspendExceptionKey(node.getId()), e.getClass().getName());
                            instanceRepository.update(instance);
                            // If this is the root execution, suspend the entire instance
                            if (!exec.isChild()) {
                                instance.setStatus(InstanceStatus.SUSPENDED);
                                instanceRepository.update(instance);
                                notifyStateListeners(instanceId);
                                return;
                            }
                        }
                    }
                }
            } while (advanced);

            // Re-read instance to get latest variables (runner may have modified them)
            instance = instanceRepository.findById(instanceId);
            // Update active node snapshot
            List<Execution> remaining = instanceRepository.findActiveExecutions(instanceId);
            Set<String> activeNodes = new HashSet<>();
            for (Execution e : remaining) {
                activeNodes.add(e.getCurrentNodeId());
            }
            instance.setActiveNodeIds(activeNodes);
            instanceRepository.update(instance);
            notifyStateListeners(instanceId);

        } finally {
            lockManager.unlock(instanceId);
            drainDeferredTriggers();
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
        String instanceId = task.getInstanceId();

        // Hold the instance lock for all state mutations — the daemon thread
        // (boundary timer / retry wake-ups) runs trigger() on the same lock and
        // must not interleave with task completion. trigger() itself runs OUTSIDE
        // the lock (Redis locks are non-reentrant).
        lockManager.lock(instanceId);
        try {
        task.setStatus(TaskStatus.COMPLETED);
        task.setVariables(variables);
        taskRepository.update(task);

        ProcessInstance instance = instanceRepository.findById(instanceId);
        ProcessDefinition def = resolveDefinition(instance);
        Node node = def.getNode(task.getNodeId());
        instanceRepository.saveHistoricActivity(
                HistoricActivity.taskCompleted(instance.getId(), node.getId(),
                        node.getName(), node.getType(), task.getAssignee(), comment));

        if (variables != null && !variables.isEmpty()) {
            instance.setVariables(variables);
            instanceRepository.update(instance);
        }

        // Wake up waiting execution
        List<Execution> executions = instanceRepository.findActiveExecutions(instanceId);
        for (Execution exec : executions) {
            if (exec.isWaiting() && exec.getCurrentNodeId().equals(task.getNodeId())) {
                // Clear any pending timer/retry state — user completed manually
                log.info("Clearing retry state for node=" + exec.getCurrentNodeId());
                exec.setRetryState(null);
                instance.removeVariable(InternalVars.boundaryTimerFiredKey(task.getNodeId()));
                instanceRepository.update(instance);

                // Check for dynamic router
                if (node instanceof UserTask) {
                    UserTask ut = (UserTask) node;
                    if (ut.getDynamicRouter() != null) {
                        String nextNodeId = invokeDynamicRouter(ut.getDynamicRouter(),
                                instance.getId(), node.getId(), instance.getVariables());
                        exec.setCurrentNodeId(nextNodeId);
                    } else {
                        // Route via outgoing transitions: direct → conditional (evaluated) → default
                        List<com.github.wf.model.Transition> outgoing = def.getOutgoingTransitions(node.getId());
                        com.github.wf.model.Transition picked = TransitionSelector.select(outgoing,
                                c -> ConditionResolver.evaluate(c, instance.getVariables(), expressionEvaluator),
                                true);
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
        } finally {
            lockManager.unlock(instanceId);
        }

        trigger(instanceId);
    }

    public void rejectTask(String taskId, String comment) {
        Task task = taskRepository.findById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
        String instanceId = task.getInstanceId();

        lockManager.lock(instanceId);
        try {
            task.setStatus(TaskStatus.REJECTED);
            taskRepository.update(task);

            ProcessInstance instance = instanceRepository.findById(instanceId);
            ProcessDefinition def = resolveDefinition(instance);
            Node node = def.getNode(task.getNodeId());
            instanceRepository.saveHistoricActivity(
                    HistoricActivity.taskRejected(instance.getId(), node.getId(),
                            node.getName(), node.getType(), task.getAssignee(), comment));

            notifyStateListeners(instanceId);
        } finally {
            lockManager.unlock(instanceId);
        }
    }

    public void delegateTask(String taskId, String newAssignee) {
        Task task = taskRepository.findById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
        String instanceId = task.getInstanceId();
        String oldAssignee = task.getAssignee();

        lockManager.lock(instanceId);
        try {
            task.setStatus(TaskStatus.DELEGATED);
            taskRepository.update(task);

            Task delegated = new Task(null, task.getInstanceId(), task.getNodeId());
            delegated.setAssignee(newAssignee);
            delegated.setCandidateGroups(task.getCandidateGroups());
            delegated.setVariables(task.getVariables());
            taskRepository.save(delegated);

            ProcessInstance instance = instanceRepository.findById(instanceId);
            ProcessDefinition def = resolveDefinition(instance);
            Node node = def.getNode(task.getNodeId());
            instanceRepository.saveHistoricActivity(
                    HistoricActivity.taskDelegated(instance.getId(), node.getId(),
                            node.getName(), node.getType(), oldAssignee, newAssignee));

            notifyStateListeners(instanceId);
        } finally {
            lockManager.unlock(instanceId);
        }
    }

    public void terminate(String instanceId, String reason) {
        lockManager.lock(instanceId);
        try {
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

            notifyStateListeners(instanceId);
        } finally {
            lockManager.unlock(instanceId);
        }
    }

    /**
     * Resume a suspended process instance. The failed service task will re-execute.
     */
    public void resume(String instanceId) {
        lockManager.lock(instanceId);
        try {
            ProcessInstance instance = instanceRepository.findById(instanceId);
            if (instance == null || instance.getStatus() != InstanceStatus.SUSPENDED) {
                throw new IllegalStateException("Instance not found or not suspended: " + instanceId);
            }

            instance.setStatus(InstanceStatus.RUNNING);
            // Clean up ALL engine-internal suspend keys (per-node and legacy global spellings)
            for (String key : instance.getVariables().keySet().toArray(new String[0])) {
                if (key.endsWith(InternalVars.SUSPEND_REASON_SUFFIX)
                        || key.endsWith(InternalVars.SUSPEND_EXCEPTION_SUFFIX)) {
                    instance.removeVariable(key);
                }
            }
            instanceRepository.update(instance);

            // Find the suspended execution and reactivate it
            List<Execution> executions = instanceRepository.findActiveExecutions(instanceId);
            for (Execution exec : executions) {
                if (exec.isWaiting() && exec.isSuspended()) {
                    exec.setRetryState(null);
                    exec.setRetryAttempt(0);
                    exec.setNextRetryAt(0);
                    exec.setStatus(ExecutionStatus.ACTIVE);
                    instanceRepository.updateExecution(exec);
                }
            }
        } finally {
            lockManager.unlock(instanceId);
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
