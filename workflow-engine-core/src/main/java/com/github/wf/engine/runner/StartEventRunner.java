package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.model.Node;
import com.github.wf.model.Transition;

import java.util.List;

public class StartEventRunner implements NodeRunner {

    @Override
    public boolean run(Node node, ExecutionContext context) {
        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
        if (outgoing.isEmpty()) {
            throw new IllegalStateException("StartEvent must have an outgoing transition");
        }
        Transition next = outgoing.get(0);
        context.getExecution().setCurrentNodeId(next.getTo());
        return true;
    }
}
