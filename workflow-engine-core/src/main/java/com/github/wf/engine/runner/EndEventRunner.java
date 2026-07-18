package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.model.node.CallActivityNode;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EndEventRunner implements NodeRunner {

    private static final Log log = LogFactory.getLog(EndEventRunner.class);

    private final ProcessRepository processRepository;
    private final Consumer<String> parentTrigger;

    public EndEventRunner() {
        this(null, null);
    }

    public EndEventRunner(ProcessRepository processRepository, Consumer<String> parentTrigger) {
        this.processRepository = processRepository;
        this.parentTrigger = parentTrigger;
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        Execution exec = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();

        // ── Sub-process completion: wake parent ──
        ProcessInstance instance = repo.findById(exec.getInstanceId());
        if (instance != null && instance.getParentInstanceId() != null
            && processRepository != null && parentTrigger != null) {
            // Only wake parent when this is the LAST active execution in the child.
            // A child with a parallel gateway has multiple concurrent branches —
            // each ending at an EndEvent. We must wait for all of them.
            List<Execution> siblings = repo.findActiveExecutions(exec.getInstanceId());
            boolean isLast = siblings.stream()
                .allMatch(e -> e.getId().equals(exec.getId()) || e.isCompleted());
            if (!isLast) {
                exec.setStatus(ExecutionStatus.COMPLETED);
                repo.updateExecution(exec);
                return true;
            }

            ProcessInstance parentInst = repo.findById(instance.getParentInstanceId());
            if (parentInst != null) {
                Execution parentExec = repo.findExecutionById(instance.getParentExecutionId());
                if (parentExec != null) {
                    ProcessDefinition parentDef = processRepository.findLatestById(parentInst.getDefinitionId());
                    if (parentDef != null) {
                        Node callActivityNode = parentDef.getNode(parentExec.getCurrentNodeId());
                        if (callActivityNode instanceof CallActivityNode ca) {
                            // Write back variables
                            if (!ca.getOutputMapping().isEmpty()) {
                                Map<String, Object> childVars = instance.getVariables();
                                for (VariableMapping vm : ca.getOutputMapping()) {
                                    parentInst.setVariable(vm.getTo(), childVars.get(vm.getFrom()));
                                }
                            } else {
                                // Full pass-through
                                parentInst.setVariables(instance.getVariables());
                            }
                            repo.update(parentInst);

                            // Advance parent execution past the CallActivity
                            parentExec.setStatus(ExecutionStatus.ACTIVE);
                            List<Transition> outgoings = parentDef.getOutgoingTransitions(
                                parentExec.getCurrentNodeId());
                            if (!outgoings.isEmpty()) {
                                parentExec.setCurrentNodeId(outgoings.get(0).getTo());
                            }
                            repo.updateExecution(parentExec);
                        } else {
                            // Parent execution is not at a CallActivityNode — data integrity issue.
                            // Unstick the parent by setting it back to ACTIVE.
                            String nodeDesc = callActivityNode != null
                                ? callActivityNode.getId() + " (" + callActivityNode.getClass().getSimpleName() + ")"
                                : "null";
                            log.warn("EndEvent: parent execution " + parentExec.getId()
                                + " is at node " + nodeDesc
                                + ", expected CallActivityNode; unsticking to ACTIVE");
                            parentExec.setStatus(ExecutionStatus.ACTIVE);
                            repo.updateExecution(parentExec);
                        }
                    }
                }
            }
            // Trigger parent instance to continue
            parentTrigger.accept(instance.getParentInstanceId());

            // Mark child execution and instance COMPLETED
            exec.setStatus(ExecutionStatus.COMPLETED);
            repo.updateExecution(exec);
            return true;
        }

        // ── Existing parallel gateway join logic ──
        if (exec.isChild()) {
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null) {
                List<Execution> siblings = repo.findExecutionsByParentId(exec.getParentExecutionId());
                boolean allDone = siblings.stream()
                        .allMatch(e -> e.getId().equals(exec.getId()) || e.isCompleted());
                if (allDone) {
                    parent.setStatus(ExecutionStatus.ACTIVE);
                    List<Transition> outgoing = context.getDefinition()
                            .getOutgoingTransitions(parent.getCurrentNodeId());
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
