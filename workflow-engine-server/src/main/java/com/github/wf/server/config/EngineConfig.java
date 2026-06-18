package com.github.wf.server.config;

import com.github.wf.engine.Execution;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableScheduling
public class EngineConfig {

    private WorkflowEngine engine;

    @Bean
    public WorkflowEngine workflowEngine() {
        InMemoryProcessRepository processRepo = new InMemoryProcessRepository();
        InMemoryInstanceRepository instanceRepo = new InMemoryInstanceRepository();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();

        engine = WorkflowEngine.builder()
                .processRepository(processRepo)
                .instanceRepository(instanceRepo)
                .taskRepository(taskRepo)
                .build();
        return engine;
    }

    /** Wake up instances whose retry timer has expired — checks every second */
    @Scheduled(fixedDelay = 1000)
    public void retryScheduler() {
        if (engine == null) return;
        try {
            Set<String> instanceIds = new HashSet<>();
            List<com.github.wf.model.ProcessInstance> all = engine.instanceRepository.findAll();
            for (var inst : all) {
                if (!inst.isRunning()) continue;
                List<Execution> executions = engine.instanceRepository.findActiveExecutions(inst.getId());
                for (Execution exec : executions) {
                    if (exec.isWaiting() && exec.getNextRetryAt() > 0
                            && System.currentTimeMillis() >= exec.getNextRetryAt()) {
                        instanceIds.add(inst.getId());
                    }
                }
            }
            for (String id : instanceIds) {
                engine.trigger(id);
            }
        } catch (Exception e) {
            // ignore — don't crash the scheduler
        }
    }
}
