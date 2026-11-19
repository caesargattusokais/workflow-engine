package com.github.wf.ext;

import java.util.Map;

/**
 * SPI that bridges an AI_TASK node to whatever LLM client the host
 * application configures. The engine core stays free of any LLM dependency;
 * the server module supplies the implementation via ServiceLoader (mirrors
 * {@link ServiceTaskHandler}).
 *
 * <p>Implementations should return a map of named values. {@code text} is
 * treated as the raw model answer and is always exposed to downstream
 * expressions; any other key becomes a namespaced variable.</p>
 */
@FunctionalInterface
public interface AiTaskHandler {
    /**
     * @param systemPrompt instructions describing the task (mode-specific)
     * @param userPrompt   the request with placeholders already resolved
     * @param variables    current process variables (read-only intent)
     * @return structured result map; never null (return an empty map instead)
     */
    Map<String, Object> execute(String systemPrompt, String userPrompt,
                                Map<String, Object> variables);
}
