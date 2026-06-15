package com.github.wf.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Deployment {
    private final String id;
    private final String definitionId;
    private final Instant deployedAt;
    private final String source;

    public Deployment(String id, String definitionId, Instant deployedAt, String source) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.definitionId = Objects.requireNonNull(definitionId);
        this.deployedAt = deployedAt != null ? deployedAt : Instant.now();
        this.source = source;
    }

    public Deployment(String definitionId, String source) {
        this(null, definitionId, null, source);
    }

    public String getId() { return id; }
    public String getDefinitionId() { return definitionId; }
    public Instant getDeployedAt() { return deployedAt; }
    public String getSource() { return source; }
}
