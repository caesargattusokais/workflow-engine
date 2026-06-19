package com.github.wf.dsl;

import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.google.gson.*;

import java.io.Reader;
import java.io.StringReader;
import java.util.*;

public class JsonProcessParser implements ProcessParser {

    @Override
    public ProcessDefinition parse(String json) {
        return parse(new StringReader(json));
    }

    @Override
    public ProcessDefinition parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        return convert(root);
    }

    private ProcessDefinition convert(JsonObject root) {
        String id = root.get("id").getAsString();
        String name = root.has("name") ? root.get("name").getAsString() : id;
        int version = root.has("version") ? root.get("version").getAsInt() : 1;

        Map<String, NodeYaml> nodeMap = new LinkedHashMap<>();
        List<Node> nodes = new ArrayList<>();

        JsonArray nodesArr = root.getAsJsonArray("nodes");
        for (JsonElement e : nodesArr) {
            JsonObject no = e.getAsJsonObject();
            NodeYaml ny = jsonToNodeYaml(no);
            nodeMap.put(ny.id, ny);
            nodes.add(convertNode(ny));
        }

        List<TransitionYaml> tyList = new ArrayList<>();
        if (root.has("transitions")) {
            JsonArray transArr = root.getAsJsonArray("transitions");
            for (JsonElement e : transArr) {
                JsonObject to = e.getAsJsonObject();
                TransitionYaml ty = new TransitionYaml();
                ty.from = to.has("from") ? to.get("from").getAsString() : null;
                ty.to = to.has("to") ? to.get("to").getAsString() : null;
                ty.type = to.has("type") ? to.get("type").getAsString() : null;
                ty.expr = to.has("expr") ? to.get("expr").getAsString() : null;
                ty.conditionClass = to.has("conditionClass") ? to.get("conditionClass").getAsString() : null;
                tyList.add(ty);
            }
        }

        YamlProcessParser ypp = new YamlProcessParser();
        List<Transition> transitions = ypp.convertTransitionsPublic(tyList, nodeMap);

        return new ProcessDefinition(id, name, version, nodes, transitions);
    }

    private NodeYaml jsonToNodeYaml(JsonObject jo) {
        NodeYaml ny = new NodeYaml();
        ny.id = jo.get("id").getAsString();
        ny.type = jo.get("type").getAsString();
        ny.name = jo.has("name") ? jo.get("name").getAsString() : null;
        ny.assignee = jo.has("assignee") ? jo.get("assignee").getAsString() : null;
        ny.handlerClass = jo.has("handlerClass") ? jo.get("handlerClass").getAsString() : null;
        ny.dynamicRouter = jo.has("dynamicRouter") ? jo.get("dynamicRouter").getAsString() : null;
        ny.duration = jo.has("duration") ? jo.get("duration").getAsString() : null;
        ny.until = jo.has("until") ? jo.get("until").getAsString() : null;
        ny.boundaryTimer = jo.has("boundaryTimer") ? jo.get("boundaryTimer").getAsString() : null;
        ny.httpMode = jo.has("httpMode") && jo.get("httpMode").getAsBoolean();
        ny.url = jo.has("url") ? jo.get("url").getAsString() : null;
        ny.method = jo.has("method") ? jo.get("method").getAsString() : null;
        if (jo.has("headers")) {
            JsonObject hdrs = jo.getAsJsonObject("headers");
            ny.headers = new HashMap<>();
            for (var entry : hdrs.entrySet()) ny.headers.put(entry.getKey(), entry.getValue().getAsString());
        }
        ny.body = jo.has("body") ? jo.get("body").getAsString() : null;

        if (jo.has("candidateGroups")) {
            JsonArray cg = jo.getAsJsonArray("candidateGroups");
            ny.candidateGroups = new ArrayList<>();
            for (JsonElement e : cg) {
                ny.candidateGroups.add(e.getAsString());
            }
        }

        if (jo.has("listeners")) {
            JsonObject l = jo.getAsJsonObject("listeners");
            ny.listeners = new HashMap<>();
            if (l.has("enter")) {
                ny.listeners.put("enter", l.get("enter").getAsString());
            }
            if (l.has("leave")) {
                ny.listeners.put("leave", l.get("leave").getAsString());
            }
        }

        if (jo.has("conditions")) {
            JsonArray conds = jo.getAsJsonArray("conditions");
            ny.conditions = new ArrayList<>();
            for (JsonElement e : conds) {
                JsonObject co = e.getAsJsonObject();
                GatewayConditionYaml gcy = new GatewayConditionYaml();
                gcy.expr = co.has("expr") ? co.get("expr").getAsString() : null;
                gcy.className = co.has("className") ? co.get("className").getAsString() : null;
                gcy.isDefault = co.has("default") && co.get("default").getAsBoolean();
                gcy.to = co.has("to") ? co.get("to").getAsString() : null;
                ny.conditions.add(gcy);
            }
        }

        // Parse retry config
        if (jo.has("retry") && !jo.get("retry").isJsonNull()) {
            JsonObject retryObj = jo.getAsJsonObject("retry");
            ny.retry = new NodeYaml.RetryYaml();
            if (retryObj.has("maxAttempts")) ny.retry.maxAttempts = retryObj.get("maxAttempts").getAsInt();
            if (retryObj.has("delayMs")) ny.retry.delayMs = retryObj.get("delayMs").getAsLong();
            if (retryObj.has("backoffMultiplier")) ny.retry.backoffMultiplier = retryObj.get("backoffMultiplier").getAsDouble();
            if (retryObj.has("retryOn")) {
                JsonArray retryOnArr = retryObj.getAsJsonArray("retryOn");
                ny.retry.retryOn = new ArrayList<>();
                for (JsonElement re : retryOnArr) {
                    JsonObject rco = re.getAsJsonObject();
                    GatewayConditionYaml gcy = new GatewayConditionYaml();
                    gcy.expr = rco.has("expr") ? rco.get("expr").getAsString() : null;
                    gcy.className = rco.has("className") ? rco.get("className").getAsString() : null;
                    ny.retry.retryOn.add(gcy);
                }
            }
        }

        // Parse resultRouting
        if (jo.has("resultRouting")) {
            JsonArray rrArr = jo.getAsJsonArray("resultRouting");
            ny.resultRouting = new ArrayList<>();
            for (JsonElement re : rrArr) {
                JsonObject rro = re.getAsJsonObject();
                NodeYaml.RouteYaml ry = new NodeYaml.RouteYaml();
                ry.to = rro.has("to") ? rro.get("to").getAsString() : null;
                ry.expr = rro.has("expr") ? rro.get("expr").getAsString() : null;
                ry.className = rro.has("className") ? rro.get("className").getAsString() : null;
                ry.isDefault = rro.has("default") && rro.get("default").getAsBoolean();
                ny.resultRouting.add(ry);
            }
        }

        // Parse exceptionRouting
        if (jo.has("exceptionRouting")) {
            JsonArray erArr = jo.getAsJsonArray("exceptionRouting");
            ny.exceptionRouting = new ArrayList<>();
            for (JsonElement re : erArr) {
                JsonObject ero = re.getAsJsonObject();
                NodeYaml.RouteYaml ry = new NodeYaml.RouteYaml();
                ry.to = ero.has("to") ? ero.get("to").getAsString() : null;
                ry.expr = ero.has("expr") ? ero.get("expr").getAsString() : null;
                ry.className = ero.has("className") ? ero.get("className").getAsString() : null;
                ry.isDefault = ero.has("default") && ero.get("default").getAsBoolean();
                ny.exceptionRouting.add(ry);
            }
        }

        return ny;
    }

    private Node convertNode(NodeYaml ny) {
        List<String> listeners = new ArrayList<>();
        if (ny.listeners != null) {
            if (ny.listeners.containsKey("enter")) {
                listeners.add(ny.listeners.get("enter"));
            }
            if (ny.listeners.containsKey("leave")) {
                listeners.add(ny.listeners.get("leave"));
            }
        }
        switch (ny.type) {
            case "startEvent":
                return new StartEvent(ny.id, ny.name, listeners);
            case "endEvent":
                return new EndEvent(ny.id, ny.name, listeners);
            case "userTask":
                return new UserTask(ny.id, ny.name, ny.assignee, ny.candidateGroups, ny.dynamicRouter, ny.boundaryTimer, ny.httpMode, ny.url, ny.method, ny.headers, ny.body, listeners);
            case "serviceTask":
                return new ServiceTask(ny.id, ny.name, ny.handlerClass, listeners);
            case "exclusiveGateway":
                return new ExclusiveGateway(ny.id, ny.name, listeners);
            case "parallelGateway":
                return new ParallelGateway(ny.id, ny.name, listeners);
            case "inclusiveGateway":
                return new InclusiveGateway(ny.id, ny.name, listeners);
            case "timer":
                return new TimerNode(ny.id, ny.name, ny.duration, ny.until, listeners);
            default:
                throw new IllegalArgumentException("Unknown node type: " + ny.type);
        }
    }
}
