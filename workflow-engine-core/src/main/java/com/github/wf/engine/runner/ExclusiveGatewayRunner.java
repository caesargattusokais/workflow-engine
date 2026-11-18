package com.github.wf.engine.runner;

import com.github.wf.engine.ConditionResolver;
import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.TransitionSelector;
import com.github.wf.model.Node;
import com.github.wf.model.Transition;
import java.util.List;

public class ExclusiveGatewayRunner implements NodeRunner {

    @Override
    public boolean run(Node node, ExecutionContext context) {
        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());

        // Edge-order semantics: first direct or matching conditional wins, default as fallback
        Transition picked = TransitionSelector.select(outgoing,
                c -> ConditionResolver.evaluate(c, context.getVariables(), context.getExpressionEvaluator()),
                false);
        if (picked == null) {
            throw new IllegalStateException("No outgoing transition matched for exclusive gateway: " + node.getId());
        }
        context.getExecution().setCurrentNodeId(picked.getTo());
        return true;
    }
}
