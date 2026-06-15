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

        boolean allArrived = siblings.stream().allMatch(sibling ->
                sibling.getId().equals(exec.getId()) ||
                        (sibling.isCompleted() || sibling.getCurrentNodeId().equals(node.getId())));

        if (allArrived) {
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null) {
                parent.setStatus(ExecutionStatus.ACTIVE);
                List<Transition> parentOutgoing = context.getDefinition()
                        .getOutgoingTransitions(parent.getCurrentNodeId());
                parent.setCurrentNodeId(parentOutgoing.get(0).getTo());
                repo.updateExecution(parent);
            }
            for (Execution s : siblings) {
                if (!s.isCompleted()) {
                    s.setStatus(ExecutionStatus.COMPLETED);
                    repo.updateExecution(s);
                }
            }
            return true;
        } else {
            exec.setStatus(ExecutionStatus.WAITING);
            repo.updateExecution(exec);
            return true;
        }
    }
}
