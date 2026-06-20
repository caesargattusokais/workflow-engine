package com.github.wf.server.config;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class EngineConfig {

    @Value("${engine.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── OrgService (LDAP) — only created if ldap.url is configured ──
    @Bean
    @ConditionalOnProperty("ldap.url")
    public OrgService orgService(Environment env) {
        Properties p = new Properties();
        for (String key : new String[]{"url","base","user","password","userFilter","groupFilter","groupMemberAttr","userBase","groupBase"}) {
            String val = env.getProperty("ldap." + key);
            if (val != null) p.setProperty("ldap." + key, val);
        }
        return new LdapOrgService(p);
    }

    // ── Engine ────────────────────────────

    @Bean
    @Profile("!memory")
    public WorkflowEngine workflowEngine(DataSource dataSource,
            @org.springframework.beans.factory.annotation.Autowired(required = false) OrgService orgService) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var builder = WorkflowEngine.builder()
                .processRepository(new JdbcProcessRepository(jdbc))
                .instanceRepository(new JdbcInstanceRepository(jdbc))
                .taskRepository(new JdbcTaskRepository(jdbc))
                .baseUrl(baseUrl);
        if (orgService != null) builder.orgService(orgService);
        WorkflowEngine engine = builder.build();
        engine.recover();
        return engine;
    }

    @Bean
    @Profile("memory")
    public WorkflowEngine workflowEngineMemory(
            @org.springframework.beans.factory.annotation.Autowired(required = false) OrgService orgService) {
        var builder = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .baseUrl(baseUrl);
        if (orgService != null) builder.orgService(orgService);
        return builder.build();
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
