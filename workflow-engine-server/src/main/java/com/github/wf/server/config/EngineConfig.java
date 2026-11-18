package com.github.wf.server.config;

import com.github.wf.engine.DelayScheduler;
import com.github.wf.engine.InstanceStateListener;
import com.github.wf.engine.InstanceLockManager;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.ext.OrgService;
import com.github.wf.memory.DbIndexInitializer;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.memory.JdbcInstanceRepository;
import com.github.wf.memory.JdbcProcessRepository;
import com.github.wf.memory.JdbcTaskRepository;
import com.github.wf.memory.RedisConfig;
import com.github.wf.memory.RedisInstanceLockManager;
import com.github.wf.memory.RedisJdbcInstanceRepository;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
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
 * Engine assembly. The persistence flavor is selected by profile on the
 * repository beans below; the engine itself is a single bean that injects
 * whatever repositories the active profile provides.
 *
 * <ul>
 *   <li>default (no profile): JDBC repositories</li>
 *   <li>{@code redis}: JDBC + Redis L1 cache and distributed lock</li>
 *   <li>{@code memory}: in-memory (no external dependencies)</li>
 * </ul>
 *
 * Supporting beans live in focused config classes:
 * OrgServiceConfig (directory backends), RedisInfraConfig (connection
 * factory / listener container), RepositoryConfig (drafts / definitions).
 */
@Configuration
@Import(RedisConfig.class)
public class EngineConfig {

    @Value("${engine.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── Engine repositories (one set per profile) ──

    @Bean @Profile("!memory & !redis")
    public ProcessRepository processRepositoryJdbc(DataSource dataSource) {
        return new JdbcProcessRepository(new JdbcTemplate(dataSource));
    }

    @Bean @Profile("!memory & !redis")
    public InstanceRepository instanceRepositoryJdbc(DataSource dataSource) {
        return new JdbcInstanceRepository(new JdbcTemplate(dataSource));
    }

    @Bean @Profile("!memory & !redis")
    public TaskRepository taskRepositoryJdbc(DataSource dataSource) {
        return new JdbcTaskRepository(new JdbcTemplate(dataSource));
    }

    @Bean @Profile("redis")
    public InstanceLockManager instanceLockManager(StringRedisTemplate redisTemplate) {
        return new RedisInstanceLockManager(redisTemplate);
    }

    @Bean @Profile("redis")
    public InstanceRepository instanceRepositoryRedis(DataSource dataSource,
            StringRedisTemplate redisTemplate, Gson redisGson) {
        return new RedisJdbcInstanceRepository(new JdbcTemplate(dataSource), redisTemplate, redisGson);
    }

    @Bean @Profile("redis")
    public TaskRepository taskRepositoryRedis(DataSource dataSource, InstanceLockManager instanceLockManager) {
        return new JdbcTaskRepository(new JdbcTemplate(dataSource), instanceLockManager);
    }

    @Bean @Profile("memory")
    public ProcessRepository processRepositoryMemory() {
        return new InMemoryProcessRepository();
    }

    @Bean @Profile("memory")
    public InstanceRepository instanceRepositoryMemory() {
        return new InMemoryInstanceRepository();
    }

    @Bean @Profile("memory")
    public TaskRepository taskRepositoryMemory() {
        return new InMemoryTaskRepository();
    }

    // ── Engine (single bean — profile variance lives in the repositories) ──

    @Bean(destroyMethod = "shutdown")
    public WorkflowEngine workflowEngine(ProcessRepository processRepository,
            InstanceRepository instanceRepository,
            TaskRepository taskRepository,
            @Autowired(required = false) InstanceLockManager lockManager,
            @Autowired(required = false) OrgService orgService,
            @Autowired(required = false) DelayScheduler delayScheduler,
            @Autowired(required = false) InstanceStateListener stateListener) {
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
