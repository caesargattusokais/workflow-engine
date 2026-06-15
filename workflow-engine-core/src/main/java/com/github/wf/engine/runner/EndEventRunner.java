package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.ExecutionStatus;
import com.github.wf.model.Node;
import com.github.wf.spi.InstanceRepository;

import java.util.List;

public class EndEventRunner implements NodeRunner {

    @Override
    public boolean run(Node node, ExecutionContext context) {
        Execution exec = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();

        if (exec.isChild()) {
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null) {
                List<Execution> siblings = repo.findExecutionsByParentId(exec.getParentExecutionId());
                boolean allDone = siblings.stream()
                        .allMatch(e -> e.getId().equals(exec.getId()) || e.isCompleted());
                if (allDone) {
                    parent.setStatus(ExecutionStatus.ACTIVE);
                    List<com.github.wf.model.Transition> outgoing =
                            context.getDefinition().getOutgoingTransitions(parent.getCurrentNodeId());
                    if (!outgoing.isEmpty()) {
                        parent.setCurrentNodeId(outgoing.get(0).getTo());
                    }
                    repo.updateExecution(parent);
                }
            }
            exec.setStatus(ExecutionStatus.COMPLETED);
            repo.updateExecution(exec);
        } else {
            exec.setStatus(ExecutionStatus.COMPLETED);
            repo.updateExecution(exec);
        }
        return true;
    }
}
