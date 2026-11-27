package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.spi.InstanceRepository;
import java.util.List;

public class ParallelGatewayRunner implements NodeRunner {

    @Override
    public boolean run(Node node, ExecutionContext context) {
        List<Transition> incoming = context.getDefinition().getIncomingTransitions(node.getId());
        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());

        if (incoming.size() <= 1 && outgoing.size() > 1) {
            return handleFork(context, outgoing);
        } else {
            return handleJoin(node, context);
        }
    }

    private boolean handleFork(ExecutionContext context, List<Transition> outgoing) {
        Execution parent = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();

        for (Transition t : outgoing) {
            Execution child = new Execution(null, parent.getInstanceId(), t.getTo(), parent.getId());
            repo.saveExecution(child);
        }

        parent.setStatus(ExecutionStatus.WAITING);
        repo.updateExecution(parent);
        return true;
    }

    private boolean handleJoin(Node node, ExecutionContext context) {
        Execution exec = context.getExecution();
        if (!exec.isChild()) {
            List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
            if (!outgoing.isEmpty()) {
                exec.setCurrentNodeId(outgoing.get(0).getTo());
            }
            return true;
        }

        InstanceRepository repo = context.getInstanceRepository();
        List<Execution> siblings = repo.findExecutionsByParentId(exec.getParentExecutionId());

        // Rejected tokens are terminal for join purposes — otherwise one
        // rejected branch keeps every other branch waiting forever.
        boolean allArrived = siblings.stream().allMatch(sibling ->
                sibling.getId().equals(exec.getId()) ||
                        (sibling.isTerminal() || sibling.getCurrentNodeId().equals(node.getId())));

        if (allArrived) {
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null) {
                parent.setStatus(ExecutionStatus.ACTIVE);
                // Use the JOIN node's outgoing transitions, not the parent's (which is still at fork)
                List<Transition> afterJoin = context.getDefinition().getOutgoingTransitions(node.getId());
                if (!afterJoin.isEmpty()) {
                    parent.setCurrentNodeId(afterJoin.get(0).getTo());
                }
                repo.updateExecution(parent);
            }
            for (Execution s : siblings) {
                if (!s.isCompleted()) {
                    s.setStatus(ExecutionStatus.COMPLETED);
                    repo.updateExecution(s);
                }
            }
            // Keep the in-memory exec object consistent with the sibling write
            // above. Repos like Redis/JDBC return deserialized COPIES, so `exec`
            // is not the same object as its entry in `siblings` — without this
            // it still reads ACTIVE@join and the trigger loop's no-progress
            // guard wrongly suspends it (overwriting the COMPLETED write).
            exec.setStatus(ExecutionStatus.COMPLETED);
            return true;
        } else {
            exec.setStatus(ExecutionStatus.WAITING);
            repo.updateExecution(exec);
            return true;
        }
    }
}
