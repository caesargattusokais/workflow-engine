package com.github.wf.server.config;

import com.github.wf.memory.DefinitionRepository;
import com.github.wf.memory.DraftRepository;
import com.github.wf.memory.InMemoryDefinitionRepository;
import com.github.wf.memory.InMemoryDraftRepository;
import com.github.wf.memory.JdbcDefinitionRepository;
import com.github.wf.memory.JdbcDraftRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Designer support repositories — drafts and process definitions.
 * These back the REST designer endpoints, not the engine SPI itself;
 * engine repositories live in {@link EngineConfig}.
 */
@Configuration
public class RepositoryConfig {

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
