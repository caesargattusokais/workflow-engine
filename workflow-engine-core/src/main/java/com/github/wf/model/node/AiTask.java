package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import com.github.wf.model.RetryConfig;

import java.util.Collections;
import java.util.List;

/**
 * A node that delegates a unit of cognitive work to an LLM through the
 * {@code AiTaskHandler} SPI: summarise a document, extract fields, or classify
 * an item into one of {@code options}. Results are namespaced by node id so
 * downstream gateways can route on them with SpEL, exactly like service tasks.
 */
public class AiTask extends Node {

    /** summary | extract | classify */
    private final String mode;
    /** User prompt template; ${var} placeholders are resolved at runtime. */
    private final String prompt;
    /** Variable name for the whole result (defaults to node id + "_result"). */
    private final String outputVar;
    /** Candidate categories for classify mode. */
    private final List<String> options;
    private final RetryConfig retryConfig;

    public AiTask(String id, String name, String mode, String prompt,
                  String outputVar, List<String> options, RetryConfig retryConfig,
                  List<String> listeners) {
        super(id, name, NodeType.AI_TASK, listeners);
        this.mode = (mode == null || mode.isBlank()) ? "summary" : mode;
        this.prompt = prompt == null ? "" : prompt;
        this.outputVar = outputVar;
        this.options = options == null ? Collections.emptyList() : Collections.unmodifiableList(options);
        this.retryConfig = retryConfig;
    }

    public String getMode() { return mode; }
    public String getPrompt() { return prompt; }
    public String getOutputVar() { return outputVar; }
    public List<String> getOptions() { return options; }
    public RetryConfig getRetryConfig() { return retryConfig; }
}
