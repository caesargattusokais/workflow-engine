package com.github.wf.engine;

import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.ext.OrgService;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;

import java.util.Objects;

public class WorkflowEngineBuilder {

    private ProcessRepository processRepository;
    private InstanceRepository instanceRepository;
    private TaskRepository taskRepository;
    private ExpressionEvaluator expressionEvaluator;
    private OrgService orgService;
    private String baseUrl;

    WorkflowEngineBuilder() {}

    public WorkflowEngineBuilder processRepository(ProcessRepository processRepository) {
        this.processRepository = processRepository;
        return this;
    }

    public WorkflowEngineBuilder instanceRepository(InstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
        return this;
    }

    public WorkflowEngineBuilder taskRepository(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
        return this;
    }

    public WorkflowEngineBuilder expressionEvaluator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
        return this;
    }

    public WorkflowEngineBuilder orgService(OrgService orgService) {
        this.orgService = orgService;
        return this;
    }

    public WorkflowEngineBuilder baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public WorkflowEngine build() {
        Objects.requireNonNull(processRepository, "processRepository is required");
        Objects.requireNonNull(instanceRepository, "instanceRepository is required");
        Objects.requireNonNull(taskRepository, "taskRepository is required");
        if (expressionEvaluator == null) {
            expressionEvaluator = new SpelExpressionEvaluator();
        }
        return new WorkflowEngine(processRepository, instanceRepository, taskRepository, expressionEvaluator, orgService, baseUrl);
    }
}
