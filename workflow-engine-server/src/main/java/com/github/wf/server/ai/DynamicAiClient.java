package com.github.wf.server.ai;

import java.util.concurrent.atomic.AtomicReference;

/**
 * AiClient indirection that can be re-pointed at runtime when the user
 * saves new AI settings from the configuration page. Always registered —
 * when no delegate is set, complete() fails with the standard
 * "AI is not configured" message so callers degrade gracefully.
 */
public class DynamicAiClient implements AiClient {

    private final AtomicReference<AiClient> delegate = new AtomicReference<>();

    public void setDelegate(AiClient client) {
        delegate.set(client);
    }

    public boolean isConfigured() {
        return delegate.get() != null;
    }

    /** Currently effective settings label for diagnostics, or null. */
    public String describe() {
        AiClient d = delegate.get();
        return d != null ? d.describe() : null;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        AiClient d = delegate.get();
        if (d == null) {
            throw new IllegalStateException(
                    "AI is not configured — set AI_BASE_URL / AI_API_KEY / AI_MODEL or use /api/ai/config");
        }
        return d.complete(systemPrompt, userPrompt);
    }
}
