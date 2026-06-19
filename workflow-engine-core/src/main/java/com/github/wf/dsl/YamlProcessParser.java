package com.github.wf.dsl;

import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.model.RetryConfig;
import com.github.wf.model.RoutingRule;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.io.Reader;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;

public class YamlProcessParser implements ProcessParser {

    @Override
    public ProcessDefinition parse(String yaml) {
        return parse(new StringReader(yaml));
    }

    @Override
    public ProcessDefinition parse(Reader reader) {
        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(ProcessYaml.class, options);
        constructor.setPropertyUtils(new FieldPropertyUtils());
        Yaml yaml = new Yaml(constructor);
        ProcessYaml py = yaml.load(reader);
        return convert(py);
    }

    private static class FieldPropertyUtils extends PropertyUtils {

        @Override
        public Property getProperty(Class<? extends Object> type, String name) {
            return getProperty(type, name, BeanAccess.FIELD);
        }

        @Override
        protected Map<String, Property> getPropertiesMap(Class<?> type, BeanAccess bAccess) {
            Map<String, Property> map = new LinkedHashMap<>(
                    super.getPropertiesMap(type, BeanAccess.FIELD));
            if (type == GatewayConditionYaml.class) {
                Property isDefaultProp = map.remove("isDefault");
                if (isDefaultProp != null) {
                    try {
                        Field f = GatewayConditionYaml.class.getField("isDefault");
                        map.put("default", new AliasedFieldProperty("default", f));
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
            if (type == NodeYaml.RouteYaml.class) {
                Property isDefaultProp = map.remove("isDefault");
                if (isDefaultProp != null) {
                    try {
                        java.lang.reflect.Field f = NodeYaml.RouteYaml.class.getField("isDefault");
                        map.put("default", new AliasedFieldProperty("default", f));
                    } catch (NoSuchFieldException ignored) {}
                }
            }
            return map;
        }
    }

    private static class AliasedFieldProperty extends Property {
        private final Field field;

        AliasedFieldProperty(String name, Field field) {
            super(name, field.getType());
            this.field = field;
        }

        @Override
        public void set(Object obj, Object value) throws Exception {
            field.set(obj, value);
        }

        @Override
        public Object get(Object obj) {
            try {
                return field.get(obj);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<Annotation> getAnnotations() {
            return List.of();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public Class<?>[] getActualTypeArguments() {
            return new Class<?>[0];
        }
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
            case "userTask": return new UserTask(ny.id, ny.name, ny.assignee, ny.candidateGroups, ny.dynamicRouter, ny.boundaryTimer, ny.httpMode, ny.url, ny.method, ny.headers, ny.body, listeners);
            case "serviceTask":
                RetryConfig retryConfig = buildRetryConfig(ny);
                List<RoutingRule> resultRoutes = buildRoutes(ny.resultRouting);
                List<RoutingRule> exceptionRoutes = buildRoutes(ny.exceptionRouting);
                return new ServiceTask(ny.id, ny.name, ny.handlerClass,
                        ny.httpMode,
                        ny.url, ny.method, ny.headers, ny.body,
                        retryConfig, resultRoutes, exceptionRoutes, listeners);
            case "exclusiveGateway": return new ExclusiveGateway(ny.id, ny.name, listeners);
            case "parallelGateway": return new ParallelGateway(ny.id, ny.name, listeners);
            case "inclusiveGateway": return new InclusiveGateway(ny.id, ny.name, listeners);
            case "timer": return new TimerNode(ny.id, ny.name, ny.duration, ny.until, listeners);
            default: throw new IllegalArgumentException("Unknown node type: " + ny.type);
        }
    }

    public List<Transition> convertTransitionsPublic(List<TransitionYaml> tyList,
                                                      Map<String, NodeYaml> nodeYamlMap) {
        return convertTransitions(tyList, nodeYamlMap);
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
                } else if ("result".equals(ty.type)) {
                    Condition cond = buildEdgeCondition(ty);
                    result.add(Transition.result(ty.from, ty.to, cond));
                } else if ("exception".equals(ty.type)) {
                    Condition cond = buildEdgeCondition(ty);
                    result.add(Transition.exception(ty.from, ty.to, cond));
                } else if ("timeout".equals(ty.type)) {
                    result.add(Transition.timeout(ty.from, ty.to));
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

    private RetryConfig buildRetryConfig(NodeYaml ny) {
        if (ny.retry == null) return null;
        List<Condition> retryOn = new ArrayList<>();
        if (ny.retry.retryOn != null) {
            for (GatewayConditionYaml gcy : ny.retry.retryOn) {
                if (gcy.className != null) retryOn.add(Condition.javaClass(gcy.className));
                else if (gcy.expr != null) retryOn.add(Condition.expression(gcy.expr));
            }
        }
        return new RetryConfig(ny.retry.maxAttempts, ny.retry.delayMs,
                ny.retry.backoffMultiplier, retryOn);
    }

    private List<RoutingRule> buildRoutes(List<NodeYaml.RouteYaml> routeYamls) {
        if (routeYamls == null || routeYamls.isEmpty()) return List.of();
        List<RoutingRule> rules = new ArrayList<>();
        for (NodeYaml.RouteYaml ry : routeYamls) {
            if (ry.isDefault) {
                rules.add(RoutingRule.defaultRule(ry.to));
            } else if (ry.className != null) {
                rules.add(RoutingRule.matched(Condition.javaClass(ry.className), ry.to));
            } else if (ry.expr != null) {
                rules.add(RoutingRule.matched(Condition.expression(ry.expr), ry.to));
            }
        }
        return rules;
    }

    private static Condition buildEdgeCondition(TransitionYaml ty) {
        if (ty.conditionClass != null) return Condition.javaClass(ty.conditionClass);
        if (ty.expr != null && !ty.expr.isBlank()) return Condition.expression(ty.expr);
        return null; // no condition = always match (catch-all)
    }
}
