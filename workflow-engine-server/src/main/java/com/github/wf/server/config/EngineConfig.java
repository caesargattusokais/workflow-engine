package com.github.wf.server.config;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    public WorkflowEngine workflowEngine() {
        InMemoryProcessRepository processRepo = new InMemoryProcessRepository();
        InMemoryInstanceRepository instanceRepo = new InMemoryInstanceRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        return WorkflowEngine.builder()
                .processRepository(processRepo)
                .instanceRepository(instanceRepo)
                .taskRepository(taskRepo)
                .build();
    }
}
