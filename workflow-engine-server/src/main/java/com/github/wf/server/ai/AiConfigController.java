package com.github.wf.server.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI provider configuration page endpoints: view (secrets masked), save
 * (effective immediately — re-points DynamicAiClient), reset to
 * environment defaults, and connectivity test.
 */
@RestController
@RequestMapping("/api/ai/config")
@CrossOrigin(origins = "*")
@Tag(name = "AI", description = "AI provider configuration")
public class AiConfigController {

    private final AiSettingsService settings;
    private final DynamicAiClient client;

    public AiConfigController(AiSettingsService settings, DynamicAiClient client) {
        this.settings = settings;
        this.client = client;
    }

    @GetMapping
    @Operation(summary = "Current AI configuration", description = "API key is masked; source is database, environment or none.")
    public Map<String, Object> view() {
        Map<String, Object> out = new LinkedHashMap<>();
        var cfg = settings.effective();
        out.put("configured", cfg.isPresent() || client.isConfigured());
        if (cfg.isPresent()) {
            AiSettings s = cfg.get();
            out.put("baseUrl", s.baseUrl());
            out.put("model", s.model());
            out.put("apiKeyMasked", AiSettingsService.mask(s.apiKey()));
            out.put("hasApiKey", s.apiKey() != null && !s.apiKey().isBlank());
            out.put("source", s.source().name().toLowerCase());
        } else {
            out.put("source", "none");
        }
        return out;
    }

    @PutMapping
    @Operation(summary = "Save AI configuration", description = "Takes effect immediately, no restart. Send apiKey empty to keep the existing key.")
    public Map<String, Object> save(@RequestBody Map<String, String> body) {
        AiSettings saved = settings.save(body.get("baseUrl"), body.get("apiKey"), body.get("model"));
        client.setDelegate(saved.newClient());
        return view();
    }

    @DeleteMapping
    @Operation(summary = "Reset AI configuration", description = "Removes page overrides and falls back to the AI_BASE_URL environment defaults.")
    public Map<String, Object> reset() {
        settings.clear();
        client.setDelegate(settings.effective().map(AiSettings::newClient).orElse(null));
        return view();
    }

    @PostMapping("/test")
    @Operation(summary = "Test connectivity", description = "Sends a minimal chat completion. Returns ok=true/false plus a failure reason.")
    public Map<String, Object> test(@RequestBody Map<String, String> body) {
        String baseUrl = trim(body.get("baseUrl"));
        String model = trim(body.get("model"));
        String apiKey = trim(body.get("apiKey"));
        // Blank fields fall back to the effective saved values
        var cfg = settings.effective().orElse(null);
        if (baseUrl == null && cfg != null) baseUrl = cfg.baseUrl();
        if (model == null && cfg != null) model = cfg.model();
        if (apiKey == null && cfg != null) apiKey = cfg.apiKey();

        Map<String, Object> out = new LinkedHashMap<>();
        if (baseUrl == null || model == null) {
            out.put("ok", false);
            out.put("message", "baseUrl and model are required");
            return out;
        }
        long start = System.currentTimeMillis();
        String error = AiSettingsService.testConnection(baseUrl, apiKey, model);
        long elapsed = System.currentTimeMillis() - start;
        out.put("ok", error == null);
        out.put("message", error == null ? "连接成功（" + elapsed + " ms）" : error);
        out.put("latencyMs", elapsed);
        return out;
    }

    private static String trim(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
