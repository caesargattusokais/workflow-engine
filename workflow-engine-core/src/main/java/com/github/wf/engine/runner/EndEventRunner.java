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
            && instance.getParentExecutionId() != null) {
            if (processRepository == null || parentTrigger == null) {
                log.warn("EndEvent: sub-process instance " + instance.getId()
                    + " completed but EndEventRunner was created without ProcessRepository/parentTrigger"
                    + " — parent instance " + instance.getParentInstanceId()
                    + " will NOT be woken. Use the parameterized constructor for sub-process support.");
            } else {
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
            if (parentInst != null && parentInst.isRunning()) {
                Execution parentExec = repo.findExecutionById(instance.getParentExecutionId());
                if (parentExec != null) {
                    ProcessDefinition parentDef = parentInst.getDefinitionVersion() > 0
                        ? processRepository.findByIdAndVersion(parentInst.getDefinitionId(), parentInst.getDefinitionVersion())
                        : processRepository.findLatestById(parentInst.getDefinitionId());
                    if (parentDef == null) {
                        parentDef = processRepository.findLatestById(parentInst.getDefinitionId());
                    }
                    if (parentDef != null) {
                        Node callActivityNode = parentDef.getNode(parentExec.getCurrentNodeId());
                        if (callActivityNode instanceof CallActivityNode ca) {
                            // Write back variables
                            if (!ca.getOutputMapping().isEmpty()) {
                                Map<String, Object> childVars = instance.getVariables();
                                for (VariableMapping vm : ca.getOutputMapping()) {
                                    Object value;
                                    if (vm.getExpr() != null) {
                                        try {
                                            value = context.getExpressionEvaluator()
                                                .evaluate(vm.getExpr(), childVars);
                                        } catch (Exception e) {
                                            log.error("EndEvent outputMapping: expr evaluation failed for '"
                                                + vm.getFrom() + "' → '" + vm.getTo() + "': "
                                                + e.getMessage());
                                            value = childVars.get(vm.getFrom());
                                        }
                                    } else {
                                        value = childVars.get(vm.getFrom());
                                    }
                                    parentInst.setVariable(vm.getTo(), value);
                                }
                            } else {
                                // Merge child variables into parent (non-destructive).
                                // Only overwrite keys that exist in the child, preserving
                                // parent-only variables like _userId or orderId.
                                Map<String, Object> childVars = instance.getVariables();
                                for (Map.Entry<String, Object> e : childVars.entrySet()) {
                                    parentInst.setVariable(e.getKey(), e.getValue());
                                }
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
            // Trigger parent instance to continue (only if we have the callback)
            if (parentTrigger != null) {
                parentTrigger.accept(instance.getParentInstanceId());
            }

            // Mark child execution and instance COMPLETED
            exec.setStatus(ExecutionStatus.COMPLETED);
            repo.updateExecution(exec);
            instance.setStatus(InstanceStatus.COMPLETED);
            instance.setActiveNodeIds(java.util.Set.of());
            repo.update(instance);
            return true;
        }  // end else
        }  // end if (instance.getParentInstanceId() != null)

        // ── Existing parallel gateway join logic ──
        if (exec.isChild()) {
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null) {
                List<Execution> siblings = repo.findExecutionsByParentId(exec.getParentExecutionId());
                boolean allDone = siblings.stream()
                        .allMatch(e -> e.getId().equals(exec.getId()) || e.isCompleted());
                if (allDone) {
                    // All child executions reached their end — complete the parent too
                    parent.setStatus(ExecutionStatus.COMPLETED);
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
