package com.github.wf.server.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generation-time settings: the system prompt that shapes the produced YAML
 * and the sampling temperature. Kept separate from {@link AiSettingsService}
 * (provider connection) so changing one does not disturb the other.
 *
 * <p>Storage follows the same precedence as provider settings: page values
 * (DB {@code app_setting}) first, then environment, then built-in defaults.
 * Without a writable datasource (memory profile) overrides stay in process.
 */
public class AiGenerationSettings {

    private static final Logger log = LoggerFactory.getLogger(AiGenerationSettings.class);

    static final String KEY_SYSTEM_PROMPT = "ai.system-prompt";
    static final String KEY_TEMPERATURE = "ai.temperature";
    static final double DEFAULT_TEMPERATURE = 0.2;
    static final double MIN_TEMPERATURE = 0.0;
    static final double MAX_TEMPERATURE = 2.0;

    public enum Source { DATABASE, ENVIRONMENT, DEFAULT }

    /** A prompt of null means "use AiController.DEFAULT_SYSTEM_PROMPT". */
    public record Config(String systemPrompt, double temperature, Source source) {
        public boolean isDefaultPrompt() {
            return systemPrompt == null || systemPrompt.isBlank();
        }
    }

    private final JdbcTemplate jdbc;
    private final String envPrompt;
    private final Double envTemperature;
    private final AtomicReference<Config> memory = new AtomicReference<>();

    public AiGenerationSettings(DataSource dataSource, String envPrompt, String envTemperature) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.envPrompt = trim(envPrompt);
        this.envTemperature = parseTemperature(envTemperature);
    }

    /** Effective settings, never empty: defaults are always available. */
    @SuppressWarnings("unchecked")
    public Config effective() {
        Config m = memory.get();
        if (m != null) return m;
        if (jdbc == null) return fromEnv();
        try {
            List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT config_key, config_value FROM app_setting WHERE config_key IN (?, ?)",
                    KEY_SYSTEM_PROMPT, KEY_TEMPERATURE);
            String prompt = null;
            Double temperature = null;
            for (var row : rows) {
                String k = (String) row.get("config_key");
                String v = trim((String) row.get("config_value"));
                if (v == null) continue;
                if (KEY_SYSTEM_PROMPT.equals(k)) prompt = v;
                if (KEY_TEMPERATURE.equals(k)) temperature = parseTemperature(v);
            }
            if (prompt != null || temperature != null) {
                return new Config(prompt, temperature != null ? temperature : DEFAULT_TEMPERATURE,
                        Source.DATABASE);
            }
        } catch (Exception e) {
            log.warn("AI generation settings table unavailable, falling back: {}", e.getMessage());
        }
        return fromEnv();
    }

    private Config fromEnv() {
        if (envPrompt == null && envTemperature == null) {
            return new Config(null, DEFAULT_TEMPERATURE, Source.DEFAULT);
        }
        return new Config(envPrompt, envTemperature != null ? envTemperature : DEFAULT_TEMPERATURE,
                Source.ENVIRONMENT);
    }

    /**
     * Save page values. A blank prompt means "restore the built-in default"
     * and clears that DB key; this keeps the stock prompt recoverable even if
     * the user pasted a broken one.
     */
    public Config save(String systemPrompt, Double temperature) {
        String prompt = trim(systemPrompt);
        double temp = temperature == null ? DEFAULT_TEMPERATURE : validateTemperature(temperature);
        Config saved = new Config(prompt, temp, Source.DATABASE);
        if (jdbc == null) {
            memory.set(saved);
            return saved;
        }
        try {
            long now = System.currentTimeMillis();
            if (prompt == null) {
                jdbc.update("DELETE FROM app_setting WHERE config_key=?", KEY_SYSTEM_PROMPT);
            } else {
                upsert(KEY_SYSTEM_PROMPT, prompt, now);
            }
            upsert(KEY_TEMPERATURE, Double.toString(temp), now);
            memory.set(null);
            return saved;
        } catch (Exception e) {
            log.warn("AI generation settings could not be persisted, keeping in memory: {}", e.getMessage());
            memory.set(saved);
            return saved;
        }
    }

    /** Remove page overrides — fall back to environment or built-in defaults. */
    public void clear() {
        memory.set(null);
        if (jdbc == null) return;
        try {
            jdbc.update("DELETE FROM app_setting WHERE config_key IN (?, ?)",
                    KEY_SYSTEM_PROMPT, KEY_TEMPERATURE);
        } catch (Exception e) {
            log.warn("AI generation settings clear failed: {}", e.getMessage());
        }
    }

    private void upsert(String key, String value, long now) {
        int updated = jdbc.update(
                "UPDATE app_setting SET config_value=?, updated_at=? WHERE config_key=?", value, now, key);
        if (updated == 0) {
            jdbc.update("INSERT INTO app_setting (config_key, config_value, updated_at) VALUES (?, ?, ?)",
                    key, value, now);
        }
    }

    static double validateTemperature(double value) {
        if (Double.isNaN(value) || value < MIN_TEMPERATURE || value > MAX_TEMPERATURE) {
            throw new IllegalArgumentException("temperature must be between " + MIN_TEMPERATURE
                    + " and " + MAX_TEMPERATURE);
        }
        return value;
    }

    private static Double parseTemperature(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            double v = Double.parseDouble(raw.trim());
            return validateTemperature(v);
        } catch (RuntimeException e) {
            log.warn("Ignoring invalid AI temperature value: {}", raw);
            return null;
        }
    }

    private static String trim(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
