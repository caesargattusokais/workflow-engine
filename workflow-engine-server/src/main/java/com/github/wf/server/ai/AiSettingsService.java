package com.github.wf.server.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;

import javax.sql.DataSource;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI provider settings: the page-configured values (DB table {@code app_setting})
 * take precedence over the environment defaults (AI_BASE_URL / AI_API_KEY /
 * AI_MODEL). When no DataSource exists (memory profile), overrides live in
 * process memory only and vanish on restart.
 */
public class AiSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AiSettingsService.class);

    static final String KEY_BASE_URL = "ai.base-url";
    static final String KEY_API_KEY = "ai.api-key";
    static final String KEY_MODEL = "ai.model";

    private final JdbcTemplate jdbc; // null when no DataSource (memory profile)
    private final String envBaseUrl;
    private final String envApiKey;
    private final String envModel;

    /** In-memory override used only when jdbc is null. */
    private final AtomicReference<AiSettings> memory = new AtomicReference<>();

    public AiSettingsService(DataSource dataSource, String envBaseUrl, String envApiKey, String envModel) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.envBaseUrl = trim(envBaseUrl);
        this.envApiKey = trim(envApiKey);
        this.envModel = trim(envModel);
    }

    /** Effective configuration: in-process override, then page override, then environment. */
    @SuppressWarnings("unchecked")
    public Optional<AiSettings> effective() {
        // An in-process value exists either because there is no datasource at
        // all, or because a save could not be persisted (see save()).
        AiSettings m = memory.get();
        if (m != null) return Optional.of(m);
        if (jdbc == null) return Optional.ofNullable(fromEnv());
        try {
            var rows = jdbc.queryForList(
                    "SELECT config_key, config_value FROM app_setting WHERE config_key IN (?, ?, ?)",
                    KEY_BASE_URL, KEY_API_KEY, KEY_MODEL);
            String baseUrl = null, apiKey = null, model = null;
            for (var row : rows) {
                String k = (String) row.get("config_key");
                String v = trim((String) row.get("config_value"));
                if (v == null) continue;
                switch (k) {
                    case KEY_BASE_URL -> baseUrl = v;
                    case KEY_API_KEY -> apiKey = v;
                    case KEY_MODEL -> model = v;
                }
            }
            if (baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank()) {
                return Optional.of(new AiSettings(baseUrl, apiKey, model, AiSettings.Source.DATABASE));
            }
        } catch (Exception e) {
            log.warn("AI settings table unavailable, falling back to environment: {}", e.getMessage());
        }
        return Optional.ofNullable(fromEnv());
    }

    private AiSettings fromEnv() {
        if (envBaseUrl == null || envBaseUrl.isBlank()) return null;
        return new AiSettings(envBaseUrl, envApiKey, envModel, AiSettings.Source.ENVIRONMENT);
    }

    /**
     * Save page settings. Blank {@code apiKey} means "keep the key that is
     * already effective" (page round-trips masked values that way).
     */
    public AiSettings save(String baseUrl, String apiKey, String model) {
        baseUrl = trim(baseUrl);
        model = trim(model);
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl is required");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
        String key = trim(apiKey);
        if (key == null || key.isBlank()) {
            key = effective().map(AiSettings::apiKey).orElse(null);
        }
        AiSettings saved = new AiSettings(baseUrl, key, model, AiSettings.Source.DATABASE);
        if (jdbc == null) {
            memory.set(saved);
            return saved;
        }
        try {
            long now = System.currentTimeMillis();
            upsert(KEY_BASE_URL, baseUrl, now);
            upsert(KEY_API_KEY, key, now);
            upsert(KEY_MODEL, model, now);
            memory.set(null); // DB is the source of truth again
            return saved;
        } catch (Exception e) {
            // The datasource exists but cannot be written — the memory profile
            // still carries one, and its schema is never initialised. Keep the
            // settings for this process rather than failing the save with a 500.
            log.warn("AI settings could not be persisted, keeping them in memory only: {}", e.getMessage());
            memory.set(saved);
            return saved;
        }
    }

    /** Remove page overrides — falls back to the environment defaults. */
    public void clear() {
        memory.set(null);
        if (jdbc == null) return;
        try {
            jdbc.update("DELETE FROM app_setting WHERE config_key IN (?, ?, ?)",
                    KEY_BASE_URL, KEY_API_KEY, KEY_MODEL);
        } catch (Exception e) {
            log.warn("AI settings clear failed: {}", e.getMessage());
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

    /**
     * Try a minimal chat completion against the given settings.
     * Returns null on success, or a human-readable failure reason.
     */
    public static String testConnection(String baseUrl, String apiKey, String model) {
        String endpoint = OpenAiCompatibleAiClient.normalizeBaseUrl(baseUrl) + OpenAiCompatibleAiClient.CHAT_PATH;
        try {
            OpenAiCompatibleAiClient probe = new OpenAiCompatibleAiClient(baseUrl, apiKey, model);
            String reply = probe.complete("You are a connectivity probe.", "Reply with the single word: ok");
            if (reply == null || reply.isBlank()) return "provider returned empty content";
            return null;
        } catch (OpenAiCompatibleAiClient.ProviderHttpException e) {
            // Already carries the endpoint, the provider's own error message and
            // a version-suffix hint — the root cause message would be poorer.
            return e.getMessage();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (ResourceAccessException e) {
            // Transport failure. Spring's own message ends in ": null" because
            // ConnectException carries no message — say what actually happened.
            return "无法连接 POST " + endpoint + " — " + describeTransport(e.getMostSpecificCause());
        } catch (Exception e) {
            String msg = e.getMessage();
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            if (root != e && root.getMessage() != null) msg = root.getMessage();
            return msg != null ? msg : e.getClass().getSimpleName();
        }
    }

    /**
     * Turn a low-level socket failure into something a user can act on. The
     * JDK HttpClient does not hand back a plain {@code ConnectException} — a
     * refused connection surfaces as {@code ClosedChannelException} a few links
     * down the chain — so the whole chain is inspected, not just the leaf.
     */
    private static String describeTransport(Throwable root) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable t = root; t != null && !chain.contains(t); t = t.getCause()) {
            chain.add(t);
        }
        for (Throwable t : chain) {
            if (t instanceof ConnectException || t instanceof ClosedChannelException) {
                return "连接被拒绝（服务未启动、端口不对或地址不可达）";
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException
                    || t instanceof TimeoutException) {
                return "请求超时（连接 " + OpenAiCompatibleAiClient.CONNECT_TIMEOUT.toSeconds()
                        + "s / 读取 " + OpenAiCompatibleAiClient.READ_TIMEOUT.toSeconds() + "s）";
            }
            if (t instanceof UnknownHostException || t instanceof UnresolvedAddressException) {
                return "域名无法解析（DNS 查不到该主机，检查拼写或网络）";
            }
        }
        for (Throwable t : chain) {
            String m = t.getMessage();
            if (m != null && m.toLowerCase(Locale.ROOT).contains("connection refused")) {
                return "连接被拒绝（服务未启动、端口不对或地址不可达）";
            }
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            String m = chain.get(i).getMessage();
            if (m != null && !m.isBlank()) return m;
        }
        return root.getClass().getSimpleName();
    }

    /** Mask a secret for display: keep only the last 4 characters. */
    public static String mask(String secret) {
        if (secret == null || secret.isBlank()) return null;
        String s = secret.trim();
        return s.length() <= 8 ? "****" : "****" + s.substring(s.length() - 4);
    }

    private static String trim(String s) {
        return s != null ? s.trim() : null;
    }
}
