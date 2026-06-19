package com.github.wf.server.config;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.memory.JdbcInstanceRepository;
import com.github.wf.memory.JdbcProcessRepository;
import com.github.wf.memory.JdbcTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class EngineConfig {

    @Value("${engine.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    @Profile("!memory")
    public WorkflowEngine workflowEngine(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return WorkflowEngine.builder()
                .processRepository(new JdbcProcessRepository(jdbc))
                .instanceRepository(new JdbcInstanceRepository(jdbc))
                .taskRepository(new JdbcTaskRepository(jdbc))
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    @Profile("memory")
    public WorkflowEngine workflowEngineMemory() {
        return WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .baseUrl(baseUrl)
                .build();
    }
}
