package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.model.node.CallActivityNode;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.function.Consumer;

public class CallActivityRunner implements NodeRunner {

    private static final Log log = LogFactory.getLog(CallActivityRunner.class);

    private final ProcessRepository processRepository;
    private final InstanceRepository instanceRepository;
    private final Consumer<String> triggerFn;

    public CallActivityRunner(ProcessRepository processRepository,
                              InstanceRepository instanceRepository,
                              Consumer<String> triggerFn) {
        this.processRepository = processRepository;
        this.instanceRepository = instanceRepository;
        this.triggerFn = triggerFn;
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        CallActivityNode caNode = (CallActivityNode) node;
        Execution exec = context.getExecution();

        // 1. Resolve the called process definition
        ProcessDefinition def = resolveDefinition(caNode);
        if (def == null) {
            // Suspend the instance — same pattern as ServiceTaskRunner on unroutable failure.
            // Avoids infinite retry loop: every trigger() would re-throw and keep the
            // execution ACTIVE, never reaching a terminal state.
            ProcessInstance inst = instanceRepository.findById(exec.getInstanceId());
            if (inst != null) {
                inst.setVariable("_suspendReason",
                    "CallActivity '" + node.getId() + "': definition not found: "
                    + caNode.getCalledId()
                    + (caNode.getCalledVersion() != null ? " v" + caNode.getCalledVersion() : ""));
                inst.setVariable("_suspendException", "IllegalStateException");
                instanceRepository.update(inst);
            }
            exec.setRetryState("SUSPENDED");
            exec.setStatus(ExecutionStatus.WAITING);
            exec.setRetryAttempt(0);
            instanceRepository.updateExecution(exec);
            return true;
        }

        // 2. Build child variables
        ProcessInstance parentInst = instanceRepository.findById(exec.getInstanceId());
        if (parentInst == null) {
            throw new IllegalStateException(
                "CallActivity '" + node.getId() + "': parent instance not found: "
                + exec.getInstanceId());
        }
        Map<String, Object> childVars = buildChildVariables(caNode, parentInst);

        // 3. Create child instance with parent linkage
        ProcessInstance childInst = new ProcessInstance(null, def.getId(),
            def.getVersion(), childVars,
            exec.getInstanceId(), exec.getId());
        instanceRepository.save(childInst);

        // 4. Create start execution for child
        Node childStartNode = def.getStartNode();
        Execution childExec = new Execution(childInst.getId(), childStartNode.getId());
        instanceRepository.saveExecution(childExec);
        childInst.setActiveNodeIds(Set.of(childStartNode.getId()));
        instanceRepository.update(childInst);

        // 5. Set parent execution to WAITING
        exec.setStatus(ExecutionStatus.WAITING);
        instanceRepository.updateExecution(exec);

        // 6. Trigger child instance
        triggerFn.accept(childInst.getId());

        return false; // waiting for child to complete
    }

    private ProcessDefinition resolveDefinition(CallActivityNode node) {
        Integer version = node.getCalledVersion();
        if (version != null) {
            return processRepository.findAllVersions(node.getCalledId()).stream()
                .filter(d -> d.getVersion() == version)
                .findFirst().orElse(null);
        }
        return processRepository.findLatestById(node.getCalledId());
    }

    private Map<String, Object> buildChildVariables(CallActivityNode node,
                                                     ProcessInstance parentInst) {
        if (!node.getInputMapping().isEmpty()) {
            Map<String, Object> child = new HashMap<>();
            Map<String, Object> parentVars = parentInst.getVariables();
            for (VariableMapping vm : node.getInputMapping()) {
                Object value = parentVars.get(vm.getFrom());
                if (value == null && !parentVars.containsKey(vm.getFrom())) {
                    log.warn("CallActivity inputMapping: variable '" + vm.getFrom()
                        + "' not found in parent instance, mapping to null");
                }
                child.put(vm.getTo(), value);
            }
            return child;
        }
        // No mapping → pass through all parent variables
        return new HashMap<>(parentInst.getVariables());
    }
}
