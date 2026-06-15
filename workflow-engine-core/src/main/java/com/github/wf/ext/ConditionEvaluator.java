package com.github.wf.ext;

import java.util.Map;

@FunctionalInterface
public interface ConditionEvaluator {
    boolean evaluate(Map<String, Object> variables);
}
