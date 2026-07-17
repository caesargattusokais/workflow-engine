package com.github.wf.server.config;

import com.github.wf.engine.DelayScheduler;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.ext.OrgService;
import com.github.wf.ext.ldap.LdapOrgService;
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
import com.github.wf.memory.RedisConfig;
import com.github.wf.memory.RedisInstanceLockManager;
import com.github.wf.memory.RedisJdbcInstanceRepository;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import com.google.gson.Gson;
import java.util.Properties;

@Configuration
@Import(RedisConfig.class)
public class EngineConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EngineConfig.class);

    @Value("${engine.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── OrgService (LDAP) — only created if ldap.url is configured ──
    @Bean
    @Profile("mock-ldap")
    public OrgService mockOrgService() {
        return new com.github.wf.mockldap.MockOrgService();
    }

    @Bean
    @ConditionalOnProperty("feishu.app-id")
    public OrgService feishuOrgService(Environment env) {
        Properties p = new Properties();
        for (String key : new String[]{"app-id","app-secret"}) {
            String val = env.getProperty("feishu." + key);
            if (val != null) p.setProperty("feishu." + key, val);
        }
        return new com.github.wf.ext.feishu.FeishuOrgService(p);
    }

    @Bean
    @ConditionalOnProperty("dingtalk.app-key")
    public OrgService dingTalkOrgService(Environment env) {
        Properties p = new Properties();
        for (String key : new String[]{"app-key","app-secret"}) {
            String val = env.getProperty("dingtalk." + key);
            if (val != null) p.setProperty("dingtalk." + key, val);
        }
        return new com.github.wf.ext.dingtalk.DingTalkOrgService(p);
    }

    @Bean
    @ConditionalOnProperty("ldap.url")
    public OrgService orgService(Environment env) {
        log.info("[EngineConfig] Creating LdapOrgService — ldap.url={}", env.getProperty("ldap.url"));
        Properties p = new Properties();
        for (String key : new String[]{"url","base","user","password","userFilter","groupFilter","groupMemberAttr","uidAttr","userObjectClass","userBase","groupBase"}) {
            String val = env.getProperty("ldap." + key);
            if (val != null) p.setProperty("ldap." + key, val);
        }
        return new LdapOrgService(p);
    }

    // ── Redis — force RESP2 to avoid HELLO+AUTH deadlock on Redis 7 ──

    @Bean
    @Profile("redis")
    public org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory lettuceConnectionFactory(
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        var redisConfig = new org.springframework.data.redis.connection.RedisStandaloneConfiguration();
        redisConfig.setHostName(host);
        redisConfig.setPort(port);
        if (password != null && !password.isBlank()) {
            redisConfig.setPassword(org.springframework.data.redis.connection.RedisPassword.of(password));
        }
        var clientOptions = io.lettuce.core.ClientOptions.builder()
                .protocolVersion(io.lettuce.core.protocol.ProtocolVersion.RESP2)
                .build();
        var clientConfig = org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .build();
        return new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(redisConfig, clientConfig);
    }

    @Bean
    @Profile("redis")
    public org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer(
            org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory connectionFactory) {
        var container = new org.springframework.data.redis.listener.RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    // ── WebSocket ────────────────────────

    @Bean
    public com.github.wf.server.ws.InstanceWebSocketHandler instanceWebSocketHandler() {
        return new com.github.wf.server.ws.InstanceWebSocketHandler();
    }

    @Bean
    public com.github.wf.server.ws.MonitorWebSocketHandler monitorWebSocketHandler() {
        return new com.github.wf.server.ws.MonitorWebSocketHandler();
    }

    @Bean
    public com.github.wf.server.ws.InstanceStateDataService instanceStateDataService(
            org.springframework.beans.factory.ObjectProvider<WorkflowEngine> engineProvider) {
        return new com.github.wf.server.ws.InstanceStateDataService(engineProvider);
    }

    // ── Engine ────────────────────────────

    /** Deferred init: load running instances + recover pending timers — after schema.sql has been executed */
    @Bean
    @Profile("!memory")
    org.springframework.boot.ApplicationRunner engineRecovery(WorkflowEngine engine) {
        return args -> {
            // Load running instances from DB/Redis into cache (depends on schema.sql)
            if (engine.instanceRepository instanceof com.github.wf.memory.JdbcInstanceRepository jdbcRepo) {
                jdbcRepo.init();
            } else if (engine.instanceRepository instanceof com.github.wf.memory.RedisJdbcInstanceRepository redisRepo) {
                redisRepo.init();
            }
            engine.recover();
        };
    }

    @Bean(destroyMethod = "shutdown")
    @Profile("!memory & !redis")
    public WorkflowEngine workflowEngine(DataSource dataSource,
            @org.springframework.beans.factory.annotation.Autowired(required = false) OrgService orgService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) DelayScheduler delayScheduler,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.github.wf.engine.InstanceStateListener stateListener) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var builder = WorkflowEngine.builder()
                .processRepository(new JdbcProcessRepository(jdbc))
                .instanceRepository(new JdbcInstanceRepository(jdbc))
                .taskRepository(new JdbcTaskRepository(jdbc))
                .baseUrl(baseUrl);
        if (orgService != null) builder.orgService(orgService);
        if (delayScheduler != null) builder.delayScheduler(delayScheduler);
        var engine = builder.build();
        if (stateListener != null) engine.addStateListener(stateListener);
        return engine;
    }

    @Bean(destroyMethod = "shutdown")
    @Profile("redis")
    public WorkflowEngine workflowEngineRedis(DataSource dataSource,
            StringRedisTemplate redisTemplate,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Gson redisGson,
            @org.springframework.beans.factory.annotation.Autowired(required = false) OrgService orgService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) DelayScheduler delayScheduler,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.github.wf.engine.InstanceStateListener stateListener) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Gson gson = redisGson != null ? redisGson : new Gson();
        var lockMgr = new RedisInstanceLockManager(redisTemplate);
        var builder = WorkflowEngine.builder()
                .processRepository(new JdbcProcessRepository(jdbc))
                .instanceRepository(new RedisJdbcInstanceRepository(jdbc, redisTemplate, gson))
                .taskRepository(new JdbcTaskRepository(jdbc, lockMgr))
                .lockManager(lockMgr)
                .baseUrl(baseUrl);
        if (orgService != null) builder.orgService(orgService);
        if (delayScheduler != null) builder.delayScheduler(delayScheduler);
        var engine = builder.build();
        if (stateListener != null) engine.addStateListener(stateListener);
        return engine;
    }

    @Bean(destroyMethod = "shutdown")
    @Profile("memory")
    public WorkflowEngine workflowEngineMemory(
            @org.springframework.beans.factory.annotation.Autowired(required = false) OrgService orgService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) DelayScheduler delayScheduler,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.github.wf.engine.InstanceStateListener stateListener) {
        var builder = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .baseUrl(baseUrl);
        if (orgService != null) builder.orgService(orgService);
        if (delayScheduler != null) builder.delayScheduler(delayScheduler);
        var engine = builder.build();
        if (stateListener != null) engine.addStateListener(stateListener);
        return engine;
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
