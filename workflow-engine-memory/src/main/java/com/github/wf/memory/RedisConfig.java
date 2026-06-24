package com.github.wf.memory;

import com.github.wf.engine.Execution;

import com.github.wf.model.ExecutionStatus;
import com.github.wf.model.InstanceStatus;
import com.github.wf.model.ProcessInstance;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;

@Configuration
@Profile("redis")
public class RedisConfig {

    @Bean
    public Gson redisGson() {
        return new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .registerTypeAdapter(ProcessInstance.class, new ProcessInstanceAdapter())
            .registerTypeAdapter(Execution.class, new ExecutionAdapter())
            .create();
    }

    // ── Adapters ──

    static class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override public JsonElement serialize(Instant src, Type type, JsonSerializationContext ctx) {
            return new JsonPrimitive(src.toEpochMilli());
        }
        @Override public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            return Instant.ofEpochMilli(json.getAsLong());
        }
    }

    static class ProcessInstanceAdapter implements JsonSerializer<ProcessInstance>, JsonDeserializer<ProcessInstance> {
        @Override
        public JsonElement serialize(ProcessInstance src, Type type, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            o.addProperty("id", src.getId());
            o.addProperty("definitionId", src.getDefinitionId());
            o.addProperty("definitionVersion", src.getDefinitionVersion());
            o.addProperty("status", src.getStatus().name());
            o.add("variables", ctx.serialize(src.getVariables()));
            o.add("activeNodeIds", ctx.serialize(new ArrayList<>(src.getActiveNodeIds())));
            o.add("createdAt", ctx.serialize(src.getCreatedAt()));
            o.add("completedAt", ctx.serialize(src.getCompletedAt()));
            return o;
        }
        @Override
        public ProcessInstance deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            JsonObject o = json.getAsJsonObject();
            Map<String, Object> vars = ctx.deserialize(o.get("variables"),
                new TypeToken<Map<String, Object>>() {}.getType());
            Instant created = ctx.deserialize(o.get("createdAt"), Instant.class);
            Instant completed = o.has("completedAt") && !o.get("completedAt").isJsonNull()
                ? ctx.deserialize(o.get("completedAt"), Instant.class) : null;
            ProcessInstance inst = new ProcessInstance(
                o.get("id").getAsString(),
                o.get("definitionId").getAsString(),
                o.get("definitionVersion").getAsInt(),
                vars, created, completed);
            inst.setStatus(InstanceStatus.valueOf(o.get("status").getAsString()));
            List<String> activeIds = ctx.deserialize(o.get("activeNodeIds"),
                new TypeToken<List<String>>() {}.getType());
            if (activeIds != null) inst.setActiveNodeIds(new HashSet<>(activeIds));
            return inst;
        }
    }

    static class ExecutionAdapter implements JsonSerializer<Execution>, JsonDeserializer<Execution> {
        @Override
        public JsonElement serialize(Execution src, Type type, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            o.addProperty("id", src.getId());
            o.addProperty("instanceId", src.getInstanceId());
            o.addProperty("currentNodeId", src.getCurrentNodeId());
            o.addProperty("parentExecutionId", src.getParentExecutionId());
            o.addProperty("status", src.getStatus().name());
            o.addProperty("retryAttempt", src.getRetryAttempt());
            o.addProperty("nextRetryAt", src.getNextRetryAt());
            o.addProperty("retryState", src.getRetryState());
            return o;
        }
        @Override
        public Execution deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            JsonObject o = json.getAsJsonObject();
            String parentId = o.has("parentExecutionId") && !o.get("parentExecutionId").isJsonNull()
                ? o.get("parentExecutionId").getAsString() : null;
            Execution exec = new Execution(
                o.get("id").getAsString(),
                o.get("instanceId").getAsString(),
                o.get("currentNodeId").getAsString(),
                parentId);
            exec.setStatus(ExecutionStatus.valueOf(o.get("status").getAsString()));
            exec.setRetryAttempt(o.get("retryAttempt").getAsInt());
            exec.setNextRetryAt(o.get("nextRetryAt").getAsLong());
            exec.setRetryState(o.has("retryState") && !o.get("retryState").isJsonNull()
                ? o.get("retryState").getAsString() : null);
            return exec;
        }
    }
}
