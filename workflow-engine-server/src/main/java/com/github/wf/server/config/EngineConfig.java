package com.github.wf.server.config;

import com.github.wf.engine.DelayScheduler;
import com.github.wf.engine.InstanceStateListener;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.engine.WorkflowEngineBuilder;
import com.github.wf.ext.OrgService;
import com.github.wf.memory.DefinitionRepository;
import com.github.wf.memory.DraftRepository;
import com.github.wf.memory.DbIndexInitializer;
import com.github.wf.memory.InMemoryDefinitionRepository;
import com.github.wf.memory.InMemoryDraftRepository;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.memory.JdbcDefinitionRepository;
import com.github.wf.memory.JdbcDraftRepository;
import com.github.wf.memory.JdbcInstanceRepository;
import com.github.wf.memory.JdbcProcessRepository;
import com.github.wf.memory.JdbcTaskRepository;
import com.github.wf.memory.RedisConfig;
import com.github.wf.memory.RedisInstanceLockManager;
import com.github.wf.memory.RedisJdbcInstanceRepository;
import com.google.gson.Gson;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Engine assembly — one WorkflowEngine bean per persistence profile:
 * default (JDBC), redis (JDBC + Redis L1 cache + distributed lock) and
 * memory (in-memory, no external dependencies).
 *
 * Supporting beans live in focused config classes:
 * OrgServiceConfig (directory backends), RedisInfraConfig (connection
 * factory / listener container), WebSocketConfig (handlers are @Component).
 */
@Configuration
@Import(RedisConfig.class)
public class EngineConfig {

    @Value("${engine.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── Engine ────────────────────────────

    @Bean(destroyMethod = "shutdown")
    @Profile("!memory & !redis")
    public WorkflowEngine workflowEngine(DataSource dataSource,
            @Autowired(required = false) OrgService orgService,
            @Autowired(required = false) DelayScheduler delayScheduler,
            @Autowired(required = false) InstanceStateListener stateListener) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return finishEngine(WorkflowEngine.builder()
                .processRepository(new JdbcProcessRepository(jdbc))
                .instanceRepository(new JdbcInstanceRepository(jdbc))
                .taskRepository(new JdbcTaskRepository(jdbc))
                .baseUrl(baseUrl), orgService, delayScheduler, stateListener);
    }

    @Bean(destroyMethod = "shutdown")
    @Profile("redis")
    public WorkflowEngine workflowEngineRedis(DataSource dataSource,
            StringRedisTemplate redisTemplate,
            @Autowired(required = false) Gson redisGson,
            @Autowired(required = false) OrgService orgService,
            @Autowired(required = false) DelayScheduler delayScheduler,
            @Autowired(required = false) InstanceStateListener stateListener) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Gson gson = redisGson != null ? redisGson : new Gson();
        var lockMgr = new RedisInstanceLockManager(redisTemplate);
        return finishEngine(WorkflowEngine.builder()
                .processRepository(new JdbcProcessRepository(jdbc))
                .instanceRepository(new RedisJdbcInstanceRepository(jdbc, redisTemplate, gson))
                .taskRepository(new JdbcTaskRepository(jdbc, lockMgr))
                .lockManager(lockMgr)
                .baseUrl(baseUrl), orgService, delayScheduler, stateListener);
    }

    @Bean(destroyMethod = "shutdown")
    @Profile("memory")
    public WorkflowEngine workflowEngineMemory(
            @Autowired(required = false) OrgService orgService,
            @Autowired(required = false) DelayScheduler delayScheduler,
            @Autowired(required = false) InstanceStateListener stateListener) {
        return finishEngine(WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .baseUrl(baseUrl), orgService, delayScheduler, stateListener);
    }

    /** Shared wiring of optional collaborators (directory backend, scheduler, listener). */
    private static WorkflowEngine finishEngine(WorkflowEngineBuilder builder,
            OrgService orgService, DelayScheduler delayScheduler, InstanceStateListener stateListener) {
        if (orgService != null) builder.orgService(orgService);
        if (delayScheduler != null) builder.delayScheduler(delayScheduler);
        WorkflowEngine engine = builder.build();
        if (stateListener != null) engine.addStateListener(stateListener);
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

    // ── Draft / Definition repos ──────────

    @Bean @Profile("!memory")
    public DraftRepository draftRepository(DataSource dataSource) {
        return new JdbcDraftRepository(new JdbcTemplate(dataSource));
    }
    @Bean @Profile("memory")
    public DraftRepository draftRepositoryMemory() { return new InMemoryDraftRepository(); }

    @Bean @Profile("!memory")
    public DefinitionRepository definitionRepository(DataSource dataSource) {
        return new JdbcDefinitionRepository(new JdbcTemplate(dataSource));
    }
    @Bean @Profile("memory")
    public DefinitionRepository definitionRepositoryMemory() { return new InMemoryDefinitionRepository(); }
}
