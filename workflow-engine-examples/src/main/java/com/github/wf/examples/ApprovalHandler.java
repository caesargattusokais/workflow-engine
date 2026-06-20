package com.github.wf.examples;

import com.github.wf.ext.ServiceTaskHandler;

import java.util.Map;

/**
 * SPI-discovered handler — automatically found via ServiceLoader.
 * No need to call engine.registerServiceHandler().
 *
 * Usage in YAML:
 *   handlerClass: ApprovalHandler   (matches getSimpleName)
 * or
 *   handlerClass: com.github.wf.examples.ApprovalHandler
 */
public class ApprovalHandler implements ServiceTaskHandler {

    @Override
    public Map<String, Object> execute(Map<String, Object> variables) {
        int amount = variables.get("amount") instanceof Number n
                ? n.intValue() : 100;
        String status = amount <= 5000 ? "approved" : "pending_review";
        System.out.println("  [ApprovalHandler] amount=" + amount + " → " + status);
        return Map.of("status", status, "amount", amount);
    }
}
