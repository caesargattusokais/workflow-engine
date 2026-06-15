package com.github.wf.ext;

import java.util.Map;

@FunctionalInterface
public interface ServiceTaskHandler {
    Map<String, Object> execute(Map<String, Object> variables);
}
