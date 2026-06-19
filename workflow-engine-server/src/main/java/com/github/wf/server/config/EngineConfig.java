package com.github.wf.server.config;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Value("${engine.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public WorkflowEngine workflowEngine() {
        InMemoryProcessRepository processRepo = new InMemoryProcessRepository();
        InMemoryInstanceRepository instanceRepo = new InMemoryInstanceRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        return WorkflowEngine.builder()
                .processRepository(processRepo)
                .instanceRepository(instanceRepo)
                .taskRepository(taskRepo)
                .baseUrl(baseUrl)
                .build();
    }
}
