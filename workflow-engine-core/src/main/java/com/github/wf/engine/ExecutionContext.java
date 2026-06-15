package com.github.wf.engine;

import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.model.ProcessDefinition;
import com.github.wf.spi.InstanceRepository;

import java.util.Map;

public class ExecutionContext {
    private final ProcessDefinition definition;
    private final Execution execution;
    private final ExpressionEvaluator expressionEvaluator;
    private final InstanceRepository instanceRepository;

    public ExecutionContext(ProcessDefinition definition, Execution execution,
                            ExpressionEvaluator expressionEvaluator,
                            InstanceRepository instanceRepository) {
        this.definition = definition;
        this.execution = execution;
        this.expressionEvaluator = expressionEvaluator;
        this.instanceRepository = instanceRepository;
    }

    public ProcessDefinition getDefinition() { return definition; }
    public Execution getExecution() { return execution; }
    public ExpressionEvaluator getExpressionEvaluator() { return expressionEvaluator; }
    public InstanceRepository getInstanceRepository() { return instanceRepository; }
    public String getInstanceId() { return execution.getInstanceId(); }
    public String getCurrentNodeId() { return execution.getCurrentNodeId(); }
    public Map<String, Object> getVariables() {
        return instanceRepository.findById(execution.getInstanceId()).getVariables();
    }
}
