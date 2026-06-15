package com.github.wf.engine;

import com.github.wf.dsl.ProcessParser;
import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.runner.*;
import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import com.github.wf.task.TaskStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class WorkflowEngine {

    public final ProcessRepository processRepository;
    public final InstanceRepository instanceRepository;
    public final TaskRepository taskRepository;
    final ExpressionEvaluator expressionEvaluator;
    final ConcurrentHashMap<String, ReentrantLock> instanceLocks = new ConcurrentHashMap<>();

    private final Map<NodeType, NodeRunner> runners = new HashMap<>();
    private ProcessParser processParser = new YamlProcessParser();

    WorkflowEngine(ProcessRepository processRepository,
                   InstanceRepository instanceRepository,
                   TaskRepository taskRepository,
                   ExpressionEvaluator expressionEvaluator) {
        this.processRepository = processRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.expressionEvaluator = expressionEvaluator;
        registerDefaultRunners();
    }

    private void registerDefaultRunners() {
        runners.put(NodeType.START_EVENT, new StartEventRunner());
        runners.put(NodeType.END_EVENT, new EndEventRunner());
        runners.put(NodeType.USER_TASK, new UserTaskRunner(taskRepository));
        runners.put(NodeType.SERVICE_TASK, new ServiceTaskRunner());
        runners.put(NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayRunner());
        runners.put(NodeType.PARALLEL_GATEWAY, new ParallelGatewayRunner());
        runners.put(NodeType.INCLUSIVE_GATEWAY, new InclusiveGatewayRunner());
    }

    public static WorkflowEngineBuilder builder() { return new WorkflowEngineBuilder(); }

    public void setProcessParser(ProcessParser processParser) { this.processParser = processParser; }

    public TaskQuery taskQuery() { return new TaskQuery(); }

    public List<Task> queryTasks(TaskQuery query) {
        return taskRepository.query(query);
    }

    // === Public API ===

    public ProcessDefinition deploy(String yaml) {
        ProcessDefinition def = processParser.parse(yaml);
        ProcessDefinition existing = processRepository.findLatestById(def.getId());
        int version = (existing != null) ? existing.getVersion() + 1 : def.getVersion();
        ProcessDefinition versioned = new ProcessDefinition(def.getId(), def.getName(), version,
                new ArrayList<>(def.getNodes().values()), def.getTransitions());
        processRepository.save(versioned);
        return versioned;
    }

    public ProcessDefinition deploy(java.io.File file) {
        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            ProcessDefinition def = processParser.parse(reader);
            ProcessDefinition existing = processRepository.findLatestById(def.getId());
            int version = (existing != null) ? existing.getVersion() + 1 : def.getVersion();
            ProcessDefinition versioned = new ProcessDefinition(def.getId(), def.getName(), version,
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

        ProcessInstance instance = new ProcessInstance(null, definitionId, variables);
        instanceRepository.save(instance);

        Node startNode = def.getStartNode();
        Execution exec = new Execution(instance.getId(), startNode.getId());
        instanceRepository.saveExecution(exec);

        instance.setActiveNodeIds(Set.of(startNode.getId()));
        instanceRepository.update(instance);

        trigger(instance.getId());
        return instanceRepository.findById(instance.getId());
    }

    // === Trigger Loop ===

    public void trigger(String instanceId) {
        ReentrantLock lock = instanceLocks.computeIfAbsent(instanceId, k -> new ReentrantLock());
        lock.lock();
        try {
            ProcessInstance instance = instanceRepository.findById(instanceId);
            if (instance == null || !instance.isRunning()) return;

            ProcessDefinition def = processRepository.findLatestById(instance.getDefinitionId());
            if (def == null) return;

            boolean advanced;
            do {
                advanced = false;
                List<Execution> executions = instanceRepository.findActiveExecutions(instanceId);

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
                        } catch (Exception e) {
                            System.err.println("Error running node " + node.getId() + ": " + e.getMessage());
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
            lock.unlock();
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
                System.err.println("Listener error: " + listenerClass + " - " + e.getMessage());
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
                // Check for dynamic router
                if (node instanceof UserTask) {
                    UserTask ut = (UserTask) node;
                    if (ut.getDynamicRouter() != null) {
                        String nextNodeId = invokeDynamicRouter(ut.getDynamicRouter(),
                                instance.getId(), node.getId(), instance.getVariables());
                        exec.setCurrentNodeId(nextNodeId);
                    } else {
                        List<com.github.wf.model.Transition> outgoing = def.getOutgoingTransitions(node.getId());
                        if (!outgoing.isEmpty()) exec.setCurrentNodeId(outgoing.get(0).getTo());
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
