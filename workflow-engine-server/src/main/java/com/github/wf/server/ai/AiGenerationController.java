package com.github.wf.server.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generation settings page endpoints: the system prompt and temperature that
 * shape AI-produced YAML. Independent of provider connection settings.
 */
@RestController
@RequestMapping("/api/ai/generation")
@CrossOrigin(origins = "*")
@Tag(name = "AI", description = "AI generation settings")
public class AiGenerationController {

    private final AiGenerationSettings settings;
    private final AiClientWiring wiring;

    public AiGenerationController(AiGenerationSettings settings, AiClientWiring wiring) {
        this.settings = settings;
        this.wiring = wiring;
    }

    @GetMapping
    @Operation(summary = "Current generation settings", description = "Returns the effective system prompt and temperature.")
    public Map<String, Object> view() {
        return render(settings.effective());
    }

    @PutMapping
    @Operation(summary = "Save generation settings", description = "Takes effect immediately, no restart. Blank prompt restores the built-in default.")
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        String prompt = body.get("systemPrompt") instanceof String s ? s : null;
        Double temperature = parseTemperature(body.get("temperature"));
        AiGenerationSettings.Config saved = settings.save(prompt, temperature);
        wiring.repoint(); // apply the new temperature to the live delegate
        return render(saved);
    }

    @DeleteMapping
    @Operation(summary = "Reset generation settings", description = "Removes page overrides, falling back to env or the built-in default prompt/temperature.")
    public Map<String, Object> reset() {
        settings.clear();
        wiring.repoint();
        return render(settings.effective());
    }

    /** Return the prompt the user should see, resolving null to the built-in default. */
    private static Map<String, Object> render(AiGenerationSettings.Config cfg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("systemPrompt", cfg.isDefaultPrompt() ? AiController.DEFAULT_SYSTEM_PROMPT : cfg.systemPrompt());
        out.put("defaultPrompt", AiController.DEFAULT_SYSTEM_PROMPT);
        out.put("temperature", cfg.temperature());
        out.put("source", cfg.source().name().toLowerCase());
        out.put("isDefaultPrompt", cfg.isDefaultPrompt());
        return out;
    }

    private static Double parseTemperature(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof String s && !s.isBlank()) return Double.parseDouble(s.trim());
        return null;
    }
}
