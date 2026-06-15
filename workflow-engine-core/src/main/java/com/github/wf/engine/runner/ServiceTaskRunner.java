package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.ext.ServiceTaskHandler;
import com.github.wf.model.Node;
import com.github.wf.model.ProcessInstance;
import com.github.wf.model.Transition;
import com.github.wf.model.node.ServiceTask;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceTaskRunner implements NodeRunner {

    private final Map<String, ServiceTaskHandler> handlerRegistry = new ConcurrentHashMap<>();

    public void registerHandler(String className, ServiceTaskHandler handler) {
        handlerRegistry.put(className, handler);
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        ServiceTask serviceTask = (ServiceTask) node;
        String handlerClass = serviceTask.getHandlerClass();

        ServiceTaskHandler handler = handlerRegistry.get(handlerClass);
        if (handler == null) {
            try {
                Class<?> clazz = Class.forName(handlerClass);
                handler = (ServiceTaskHandler) clazz.getDeclaredConstructor().newInstance();
                handlerRegistry.put(handlerClass, handler);
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate ServiceTaskHandler: " + handlerClass, e);
            }
        }

        ProcessInstance instance = context.getInstanceRepository()
                .findById(context.getInstanceId());
        Map<String, Object> variables = instance.getVariables();

        Map<String, Object> result = handler.execute(variables);
        if (result != null) {
            instance.setVariables(result);
            context.getInstanceRepository().update(instance);
        }

        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
        if (!outgoing.isEmpty()) {
            context.getExecution().setCurrentNodeId(outgoing.get(0).getTo());
        }

        return true;
    }
}
