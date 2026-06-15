package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.model.Node;

public interface NodeRunner {
    /** @return true if the execution advanced, false if it stayed in place */
    boolean run(Node node, ExecutionContext context);
}
