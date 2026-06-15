package com.github.wf.engine;

import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.TaskQuery;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class WorkflowEngine {

    final ProcessRepository processRepository;
    final InstanceRepository instanceRepository;
    final TaskRepository taskRepository;
    final ExpressionEvaluator expressionEvaluator;
    final ConcurrentHashMap<String, ReentrantLock> instanceLocks = new ConcurrentHashMap<>();

    WorkflowEngine(ProcessRepository processRepository,
                   InstanceRepository instanceRepository,
                   TaskRepository taskRepository,
                   ExpressionEvaluator expressionEvaluator) {
        this.processRepository = processRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.expressionEvaluator = expressionEvaluator;
    }

    public static WorkflowEngineBuilder builder() {
        return new WorkflowEngineBuilder();
    }

    public TaskQuery taskQuery() {
        return new TaskQuery();
    }
}
