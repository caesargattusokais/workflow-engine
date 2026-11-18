package com.github.wf.server.config;

import com.github.wf.engine.InstanceLockManager;
import com.github.wf.memory.DefinitionRepository;
import com.github.wf.memory.DraftRepository;
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
import com.github.wf.memory.RedisInstanceLockManager;
import com.github.wf.memory.RedisJdbcInstanceRepository;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * All repository beans. The persistence flavor is selected by profile:
 *
 * <ul>
 *   <li>default (no profile): JDBC repositories</li>
 *   <li>{@code redis}: JDBC + Redis L1 cache and distributed lock</li>
 *   <li>{@code memory}: in-memory (no external dependencies)</li>
 * </ul>
 *
 * Engine SPI repositories (Process/Instance/Task) are consumed by the
 * single WorkflowEngine bean in {@link EngineConfig}; Draft/Definition
 * repositories back the designer REST endpoints directly.
 */
@Configuration
public class RepositoryConfig {

    // ── Engine SPI: JDBC (default profile) ──

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

    // ── Engine SPI: Redis L1 cache (redis profile) ──

    @Bean @Profile("redis")
    public ProcessRepository processRepositoryRedis(DataSource dataSource) {
        return new JdbcProcessRepository(new JdbcTemplate(dataSource));
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

    // ── Engine SPI: in-memory (memory profile) ──

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

    // ── Designer support: drafts and definitions ──

    @Bean @Profile("!memory")
    public DraftRepository draftRepository(DataSource dataSource) {
        return new JdbcDraftRepository(new JdbcTemplate(dataSource));
    }

    @Bean @Profile("memory")
    public DraftRepository draftRepositoryMemory() {
        return new InMemoryDraftRepository();
    }

    @Bean @Profile("!memory")
    public DefinitionRepository definitionRepository(DataSource dataSource) {
        return new JdbcDefinitionRepository(new JdbcTemplate(dataSource));
    }

    @Bean @Profile("memory")
    public DefinitionRepository definitionRepositoryMemory() {
        return new InMemoryDefinitionRepository();
    }
}
