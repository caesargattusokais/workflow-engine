package com.github.wf.server.config;

import com.github.wf.engine.DelayScheduler;
import com.github.wf.engine.InstanceStateListener;
import com.github.wf.engine.InstanceLockManager;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.ext.OrgService;
import com.github.wf.memory.DbIndexInitializer;
import com.github.wf.memory.JdbcInstanceRepository;
import com.github.wf.memory.RedisConfig;
import com.github.wf.memory.RedisJdbcInstanceRepository;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Engine assembly — a single WorkflowEngine bean that injects whatever
 * repositories the active profile provides (all repository beans live in
 * {@link RepositoryConfig}).
 *
 * Other supporting beans live in focused config classes:
 * OrgServiceConfig (directory backends), RedisInfraConfig (connection
 * factory / listener container), RepositoryConfig (all repositories).
 */
@Configuration
@Import(RedisConfig.class)
public class EngineConfig {

    @Value("${engine.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── Engine ────────────────────────────

    @Bean(destroyMethod = "shutdown")
    public WorkflowEngine workflowEngine(ProcessRepository processRepository,
            InstanceRepository instanceRepository,
            TaskRepository taskRepository,
            @Autowired(required = false) InstanceLockManager lockManager,
            @Autowired(required = false) OrgService orgService,
            @Autowired(required = false) DelayScheduler delayScheduler,
            @Autowired(required = false) InstanceStateListener stateListener,
            @Autowired(required = false) com.github.wf.server.ai.DynamicAiClient aiClient) {
        var builder = WorkflowEngine.builder()
                .processRepository(processRepository)
                .instanceRepository(instanceRepository)
                .taskRepository(taskRepository)
                .baseUrl(baseUrl);
        if (lockManager != null) builder.lockManager(lockManager);
        if (orgService != null) builder.orgService(orgService);
        if (delayScheduler != null) builder.delayScheduler(delayScheduler);
        WorkflowEngine engine = builder.build();
        if (stateListener != null) engine.addStateListener(stateListener);
        // Bridge AI_TASK nodes to the dynamically configured client. The facade
        // delegates on every call, so settings changes apply to running flows.
        if (aiClient != null) {
            engine.registerAiHandler(new com.github.wf.server.ai.ServerAiTaskHandler(aiClient));
        }
        return engine;
    }

    // ── Startup recovery ─────────────────

    /** Deferred init: create indexes + load running instances + recover pending timers — after schema.sql has been executed */
    @Bean
    @Profile("!memory")
    ApplicationRunner engineRecovery(WorkflowEngine engine, DataSource dataSource) {
        return args -> {
            // Secondary indexes (idempotent — safe for MySQL and H2)
            DbIndexInitializer.createIndexes(new JdbcTemplate(dataSource));
            // Load running instances from DB/Redis into cache (depends on schema.sql)
            if (engine.instanceRepository instanceof JdbcInstanceRepository jdbcRepo) {
                jdbcRepo.init();
            } else if (engine.instanceRepository instanceof RedisJdbcInstanceRepository redisRepo) {
                redisRepo.init();
            }
            engine.recover();
        };
    }

    // ── MQ Delay Scheduler ─────────────

    @Bean
    @ConditionalOnProperty("rocketmq.name-server")
    public DelayScheduler rocketMQDelayScheduler(RocketMQTemplate rocketMQTemplate) {
        return new com.github.wf.memory.RocketMQDelayScheduler(rocketMQTemplate);
    }
}
