package com.github.wf.server.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wf.ext.AiTaskHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges the engine's AiTaskHandler SPI to the (dynamically configured) AI
 * client. The handler is a thin facade over DynamicAiClient, so changing the
 * provider/model/temperature on the settings page takes effect for running
 * workflows without a restart — no re-registration needed.
 */
public class ServerAiTaskHandler implements AiTaskHandler {

    private static final Log log = LogFactory.getLog(ServerAiTaskHandler.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AiClient client;

    public ServerAiTaskHandler(AiClient client) {
        this.client = client;
    }

    @Override
    public Map<String, Object> execute(String systemPrompt, String userPrompt,
                                       Map<String, Object> variables) {
        String answer = client.complete(systemPrompt, userPrompt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", answer == null ? "" : answer.trim());

        // extract mode asks the model for a JSON object — parse it when possible
        // so each field also becomes its own namespaced process variable.
        if (systemPrompt.contains("extract structured data")) {
            String json = stripFences(result.get("text").toString());
            try {
                Map<String, Object> parsed = JSON.readValue(json, new TypeReference<>() {});
                result.putAll(parsed);
            } catch (Exception e) {
                log.warn("AI_TASK extract returned non-JSON; keeping raw text: " + e.getMessage());
            }
        }
        return result;
    }

    private static String stripFences(String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            int fence = s.lastIndexOf("```");
            if (fence > 0) s = s.substring(0, fence);
        }
        return s.trim();
    }
}
