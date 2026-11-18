package com.github.wf.server.ai;

/**
 * Minimal LLM client abstraction — the engine core stays AI-free; this lives
 * in the server module and is wired only when {@code ai.base-url} is present.
 * Implementation targets any OpenAI-compatible chat/completions endpoint
 * (OpenAI, DeepSeek, Qwen, Ollama, vLLM, …).
 */
public interface AiClient {

    /** Send a chat completion and return the assistant message content. */
    String complete(String systemPrompt, String userPrompt);

    /** Human-readable provider label for logs / error messages. */
    default String describe() { return "AiClient"; }
}
