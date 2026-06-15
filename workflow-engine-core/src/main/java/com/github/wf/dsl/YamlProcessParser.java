package com.github.wf.dsl;

import com.github.wf.model.*;
import com.github.wf.model.node.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.Reader;
import java.io.StringReader;
import java.util.*;

public class YamlProcessParser implements ProcessParser {

    @Override
    public ProcessDefinition parse(String yaml) {
        return parse(new StringReader(yaml));
    }

    @Override
    public ProcessDefinition parse(Reader reader) {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(ProcessYaml.class, options));
        ProcessYaml py = yaml.load(reader);
        return convert(py);
    }

    private ProcessDefinition convert(ProcessYaml py) {
        List<Node> nodes = new ArrayList<>();
        Map<String, NodeYaml> nodeYamlMap = new LinkedHashMap<>();
        for (NodeYaml ny : py.nodes) {
            nodeYamlMap.put(ny.id, ny);
            nodes.add(convertNode(ny));
        }
        List<Transition> transitions = convertTransitions(py.transitions, nodeYamlMap);
        return new ProcessDefinition(py.id, py.name, py.version, nodes, transitions);
    }

    private Node convertNode(NodeYaml ny) {
        List<String> listeners = new ArrayList<>();
        if (ny.listeners != null) {
            if (ny.listeners.containsKey("enter")) listeners.add(ny.listeners.get("enter"));
            if (ny.listeners.containsKey("leave")) listeners.add(ny.listeners.get("leave"));
        }
        switch (ny.type) {
            case "startEvent": return new StartEvent(ny.id, ny.name, listeners);
            case "endEvent": return new EndEvent(ny.id, ny.name, listeners);
            case "userTask": return new UserTask(ny.id, ny.name, ny.assignee, ny.candidateGroups, ny.dynamicRouter, listeners);
            case "serviceTask": return new ServiceTask(ny.id, ny.name, ny.handlerClass, listeners);
            case "exclusiveGateway": return new ExclusiveGateway(ny.id, ny.name, listeners);
            case "parallelGateway": return new ParallelGateway(ny.id, ny.name, listeners);
            case "inclusiveGateway": return new InclusiveGateway(ny.id, ny.name, listeners);
            default: throw new IllegalArgumentException("Unknown node type: " + ny.type);
        }
    }

    private List<Transition> convertTransitions(List<TransitionYaml> transitionYamls,
                                                 Map<String, NodeYaml> nodeYamlMap) {
        List<Transition> result = new ArrayList<>();
        if (transitionYamls != null) {
            for (TransitionYaml ty : transitionYamls) {
                if (ty.from == null) continue;
                if ("conditional".equals(ty.type)) {
                    Condition cond;
                    if (ty.conditionClass != null) cond = Condition.javaClass(ty.conditionClass);
                    else if (ty.expr != null) cond = Condition.expression(ty.expr);
                    else continue;
                    result.add(Transition.conditional(ty.from, cond).withTo(ty.to));
                } else if ("default".equals(ty.type)) {
                    result.add(Transition.defaultTransition(ty.from, ty.to));
                } else {
                    result.add(Transition.direct(ty.from, ty.to));
                }
            }
        }
        for (NodeYaml ny : nodeYamlMap.values()) {
            if (ny.conditions != null && !ny.conditions.isEmpty()) {
                for (GatewayConditionYaml gcy : ny.conditions) {
                    if (gcy.isDefault) {
                        result.add(Transition.defaultTransition(ny.id, gcy.to));
                    } else if (gcy.className != null) {
                        result.add(Transition.conditional(ny.id, Condition.javaClass(gcy.className)).withTo(gcy.to));
                    } else if (gcy.expr != null) {
                        result.add(Transition.conditional(ny.id, Condition.expression(gcy.expr)).withTo(gcy.to));
                    }
                }
            }
        }
        return result;
    }
}
