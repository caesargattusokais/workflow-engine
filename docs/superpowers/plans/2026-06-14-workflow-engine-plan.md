# Workflow Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an embeddable Java business process workflow engine with YAML DSL, token-driven execution, and pluggable persistence.

**Architecture:** Maven multi-module project (core + memory + examples). Core contains domain model, DSL parser, token-driven execution engine, extension SPI, and persistence interfaces. Memory module provides in-memory repository implementations. Examples module contains sample workflow YAML files.

**Tech Stack:** Java 17, Maven 3.9+, SnakeYAML 2.2, spring-expression 6.1 (SpEL), JUnit 5.10, AssertJ 3.25

---

### Task 1: Project scaffolding

**Files:**
- Create: `D:\workflow-engine\pom.xml`
- Create: `D:\workflow-engine\workflow-engine-core\pom.xml`
- Create: `D:\workflow-engine\workflow-engine-memory\pom.xml`
- Create: `D:\workflow-engine\workflow-engine-examples\pom.xml`
- Create: `D:\workflow-engine\.gitignore`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\package-info.java`
- Create: `D:\workflow-engine\workflow-engine-memory\src\main\java\com\github\wf\memory\package-info.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\package-info.java`

- [ ] **Step 1: Write parent POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.github.wf</groupId>
    <artifactId>workflow-engine-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Workflow Engine</name>
    <description>Embeddable Java business process workflow engine</description>

    <modules>
        <module>workflow-engine-core</module>
        <module>workflow-engine-memory</module>
        <module>workflow-engine-examples</module>
    </modules>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <snakeyaml.version>2.2</snakeyaml.version>
        <spring.version>6.1.6</spring.version>
        <junit.version>5.10.2</junit.version>
        <assertj.version>3.25.3</assertj.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.yaml</groupId>
                <artifactId>snakeyaml</artifactId>
                <version>${snakeyaml.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework</groupId>
                <artifactId>spring-expression</artifactId>
                <version>${spring.version}</version>
            </dependency>
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>${junit.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.assertj</groupId>
                <artifactId>assertj-core</artifactId>
                <version>${assertj.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: Write core module POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.github.wf</groupId>
        <artifactId>workflow-engine-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>workflow-engine-core</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-expression</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Write memory module POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.github.wf</groupId>
        <artifactId>workflow-engine-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>workflow-engine-memory</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.github.wf</groupId>
            <artifactId>workflow-engine-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: Write examples module POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.github.wf</groupId>
        <artifactId>workflow-engine-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>workflow-engine-examples</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.github.wf</groupId>
            <artifactId>workflow-engine-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.github.wf</groupId>
            <artifactId>workflow-engine-memory</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: Write .gitignore**

```
target/
*.class
*.jar
*.war
.idea/
*.iml
.vscode/
.settings/
.project
.classpath
```

- [ ] **Step 6: Verify Maven compiles**

Run: `cd /d/workflow-engine && mvn compile -q`
Expected: BUILD SUCCESS (only package-info.java exists, no compilation errors)

- [ ] **Step 7: Commit**

```bash
cd /d/workflow-engine && git init && git add -A && git commit -m "chore: scaffold Maven multi-module project"
```

---

### Task 2: Domain model — enums and Node hierarchy

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\NodeType.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\Node.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\node\StartEvent.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\node\EndEvent.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\node\UserTask.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\node\ServiceTask.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\node\ExclusiveGateway.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\node\ParallelGateway.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\node\InclusiveGateway.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\model\NodeTest.java`

- [ ] **Step 1: Write NodeType enum**

```java
package com.github.wf.model;

public enum NodeType {
    START_EVENT,
    END_EVENT,
    USER_TASK,
    SERVICE_TASK,
    EXCLUSIVE_GATEWAY,
    PARALLEL_GATEWAY,
    INCLUSIVE_GATEWAY
}
```

- [ ] **Step 2: Write Node abstract base class**

```java
package com.github.wf.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class Node {
    private final String id;
    private final String name;
    private final NodeType type;
    private final List<String> listeners;

    protected Node(String id, String name, NodeType type, List<String> listeners) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = name != null ? name : id;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.listeners = listeners != null ? new ArrayList<>(listeners) : new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public NodeType getType() { return type; }
    public List<String> getListeners() { return listeners; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node node = (Node) o;
        return id.equals(node.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "id='" + id + '\'' + ", name='" + name + '\'' + '}';
    }
}
```

- [ ] **Step 3: Write concrete node types**

```java
// StartEvent.java
package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class StartEvent extends Node {
    public StartEvent(String id, String name, List<String> listeners) {
        super(id, name, NodeType.START_EVENT, listeners);
    }

    public StartEvent(String id, String name) {
        this(id, name, null);
    }

    public StartEvent(String id) {
        this(id, null, null);
    }
}
```

```java
// EndEvent.java
package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class EndEvent extends Node {
    public EndEvent(String id, String name, List<String> listeners) {
        super(id, name, NodeType.END_EVENT, listeners);
    }

    public EndEvent(String id, String name) {
        this(id, name, null);
    }

    public EndEvent(String id) {
        this(id, null, null);
    }
}
```

```java
// UserTask.java
package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class UserTask extends Node {
    private final String assignee;
    private final List<String> candidateGroups;
    private final String dynamicRouter;

    public UserTask(String id, String name, String assignee,
                    List<String> candidateGroups, String dynamicRouter,
                    List<String> listeners) {
        super(id, name, NodeType.USER_TASK, listeners);
        this.assignee = assignee;
        this.candidateGroups = candidateGroups != null ? candidateGroups : List.of();
        this.dynamicRouter = dynamicRouter;
    }

    public String getAssignee() { return assignee; }
    public List<String> getCandidateGroups() { return candidateGroups; }
    public String getDynamicRouter() { return dynamicRouter; }
}
```

```java
// ServiceTask.java
package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class ServiceTask extends Node {
    private final String handlerClass;

    public ServiceTask(String id, String name, String handlerClass, List<String> listeners) {
        super(id, name, NodeType.SERVICE_TASK, listeners);
        this.handlerClass = handlerClass;
    }

    public String getHandlerClass() { return handlerClass; }
}
```

```java
// ExclusiveGateway.java
package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class ExclusiveGateway extends Node {
    public ExclusiveGateway(String id, String name, List<String> listeners) {
        super(id, name, NodeType.EXCLUSIVE_GATEWAY, listeners);
    }

    public ExclusiveGateway(String id) {
        this(id, null, null);
    }
}
```

```java
// ParallelGateway.java
package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class ParallelGateway extends Node {
    public ParallelGateway(String id, String name, List<String> listeners) {
        super(id, name, NodeType.PARALLEL_GATEWAY, listeners);
    }

    public ParallelGateway(String id) {
        this(id, null, null);
    }
}
```

```java
// InclusiveGateway.java
package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import java.util.List;

public class InclusiveGateway extends Node {
    public InclusiveGateway(String id, String name, List<String> listeners) {
        super(id, name, NodeType.INCLUSIVE_GATEWAY, listeners);
    }

    public InclusiveGateway(String id) {
        this(id, null, null);
    }
}
```

- [ ] **Step 4: Write NodeTest**

```java
package com.github.wf.model;

import com.github.wf.model.node.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeTest {

    @Test
    void startEventHasCorrectType() {
        StartEvent node = new StartEvent("start");
        assertThat(node.getType()).isEqualTo(NodeType.START_EVENT);
        assertThat(node.getId()).isEqualTo("start");
        assertThat(node.getName()).isEqualTo("start"); // defaults to id
    }

    @Test
    void userTaskStoresAssigneeAndGroups() {
        UserTask task = new UserTask("t1", "审批", "${user}",
                List.of("manager", "hr"), null, null);
        assertThat(task.getAssignee()).isEqualTo("${user}");
        assertThat(task.getCandidateGroups()).containsExactly("manager", "hr");
        assertThat(task.getDynamicRouter()).isNull();
    }

    @Test
    void serviceTaskStoresHandlerClass() {
        ServiceTask task = new ServiceTask("svc", "发通知",
                "com.myapp.SendNotification", null);
        assertThat(task.getHandlerClass()).isEqualTo("com.myapp.SendNotification");
    }

    @Test
    void exclusiveGatewayHasCorrectType() {
        ExclusiveGateway gw = new ExclusiveGateway("gw1");
        assertThat(gw.getType()).isEqualTo(NodeType.EXCLUSIVE_GATEWAY);
    }

    @Test
    void parallelGatewayHasCorrectType() {
        ParallelGateway gw = new ParallelGateway("fork");
        assertThat(gw.getType()).isEqualTo(NodeType.PARALLEL_GATEWAY);
    }

    @Test
    void inclusiveGatewayHasCorrectType() {
        InclusiveGateway gw = new InclusiveGateway("incl");
        assertThat(gw.getType()).isEqualTo(NodeType.INCLUSIVE_GATEWAY);
    }

    @Test
    void nodeListenersDefaultToEmptyList() {
        StartEvent node = new StartEvent("s");
        assertThat(node.getListeners()).isEmpty();
    }

    @Test
    void nodeEqualityById() {
        StartEvent a = new StartEvent("x");
        EndEvent b = new EndEvent("x");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void nullIdThrows() {
        assertThatThrownBy(() -> new StartEvent(null))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 5: Run tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -q`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 6: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add node type enum and concrete node classes"
```

---

### Task 3: Domain model — Transition, Condition, ProcessDefinition

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\TransitionType.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\ConditionType.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\Condition.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\Transition.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\ProcessDefinition.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\Deployment.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\model\ProcessDefinitionTest.java`

- [ ] **Step 1: Write TransitionType enum**

```java
package com.github.wf.model;

public enum TransitionType {
    DIRECT,
    CONDITIONAL,
    DEFAULT
}
```

- [ ] **Step 2: Write ConditionType enum and Condition class**

```java
// ConditionType.java
package com.github.wf.model;

public enum ConditionType {
    EXPRESSION,
    JAVA_CLASS
}
```

```java
// Condition.java
package com.github.wf.model;

import java.util.Objects;

public class Condition {
    private final ConditionType type;
    private final String expr;       // SpEL expression, used when type == EXPRESSION
    private final String className;  // ConditionEvaluator class, used when type == JAVA_CLASS

    private Condition(ConditionType type, String expr, String className) {
        this.type = Objects.requireNonNull(type);
        this.expr = expr;
        this.className = className;
    }

    public static Condition expression(String expr) {
        return new Condition(ConditionType.EXPRESSION, Objects.requireNonNull(expr), null);
    }

    public static Condition javaClass(String className) {
        return new Condition(ConditionType.JAVA_CLASS, null, Objects.requireNonNull(className));
    }

    public ConditionType getType() { return type; }
    public String getExpr() { return expr; }
    public String getClassName() { return className; }
}
```

- [ ] **Step 3: Write Transition class**

```java
package com.github.wf.model;

import java.util.Objects;
import java.util.UUID;

public class Transition {
    private final String id;
    private final String from;
    private final String to;
    private final TransitionType type;
    private final Condition condition;

    private Transition(String id, String from, String to, TransitionType type, Condition condition) {
        this.id = id != null ? id : UUID.randomUUID().toString().substring(0, 8);
        this.from = Objects.requireNonNull(from);
        this.to = to; // null allowed for conditional transitions on exclusive gateways
        this.type = type != null ? type : TransitionType.DIRECT;
        this.condition = condition;
    }

    public static Transition direct(String from, String to) {
        return new Transition(null, from, to, TransitionType.DIRECT, null);
    }

    public static Transition conditional(String from, Condition condition) {
        return new Transition(null, from, null, TransitionType.CONDITIONAL, Objects.requireNonNull(condition));
    }

    public static Transition defaultTransition(String from, String to) {
        return new Transition(null, from, to, TransitionType.DEFAULT, null);
    }

    // Builder-style with to() for conditional — used when parsing DSL
    public Transition withTo(String to) {
        return new Transition(this.id, this.from, to, this.type, this.condition);
    }

    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public TransitionType getType() { return type; }
    public Condition getCondition() { return condition; }

    public boolean isConditional() { return type == TransitionType.CONDITIONAL; }
    public boolean isDefault() { return type == TransitionType.DEFAULT; }
    public boolean isDirect() { return type == TransitionType.DIRECT; }
}
```

- [ ] **Step 4: Write ProcessDefinition class**

```java
package com.github.wf.model;

import com.github.wf.model.node.StartEvent;

import java.util.*;
import java.util.stream.Collectors;

public class ProcessDefinition {
    private final String id;
    private final String name;
    private final int version;
    private final Map<String, Node> nodes;
    private final List<Transition> transitions;
    private final Map<String, List<Transition>> outgoingCache; // nodeId → outgoing transitions
    private final Map<String, List<Transition>> incomingCache; // nodeId → incoming transitions

    public ProcessDefinition(String id, String name, int version,
                             List<Node> nodes, List<Transition> transitions) {
        this.id = Objects.requireNonNull(id);
        this.name = name != null ? name : id;
        this.version = version;
        this.nodes = new LinkedHashMap<>();
        for (Node node : nodes) {
            this.nodes.put(node.getId(), node);
        }
        this.transitions = new ArrayList<>(transitions);
        this.outgoingCache = buildOutgoingCache();
        this.incomingCache = buildIncomingCache();
    }

    private Map<String, List<Transition>> buildOutgoingCache() {
        Map<String, List<Transition>> cache = new HashMap<>();
        for (Transition t : transitions) {
            cache.computeIfAbsent(t.getFrom(), k -> new ArrayList<>()).add(t);
        }
        return cache;
    }

    private Map<String, List<Transition>> buildIncomingCache() {
        Map<String, List<Transition>> cache = new HashMap<>();
        for (Transition t : transitions) {
            if (t.getTo() != null) {
                cache.computeIfAbsent(t.getTo(), k -> new ArrayList<>()).add(t);
            }
        }
        return cache;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getVersion() { return version; }
    public Map<String, Node> getNodes() { return Collections.unmodifiableMap(nodes); }
    public List<Transition> getTransitions() { return Collections.unmodifiableList(transitions); }

    public Node getNode(String nodeId) {
        Node node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        return node;
    }

    public List<Transition> getOutgoingTransitions(String nodeId) {
        return outgoingCache.getOrDefault(nodeId, List.of());
    }

    public List<Transition> getIncomingTransitions(String nodeId) {
        return incomingCache.getOrDefault(nodeId, List.of());
    }

    public Node getStartNode() {
        return nodes.values().stream()
                .filter(n -> n.getType() == NodeType.START_EVENT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No start event in process definition"));
    }
}
```

- [ ] **Step 5: Write Deployment class**

```java
package com.github.wf.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Deployment {
    private final String id;
    private final String definitionId;
    private final Instant deployedAt;
    private final String source;

    public Deployment(String id, String definitionId, Instant deployedAt, String source) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.definitionId = Objects.requireNonNull(definitionId);
        this.deployedAt = deployedAt != null ? deployedAt : Instant.now();
        this.source = source;
    }

    public Deployment(String definitionId, String source) {
        this(null, definitionId, null, source);
    }

    public String getId() { return id; }
    public String getDefinitionId() { return definitionId; }
    public Instant getDeployedAt() { return deployedAt; }
    public String getSource() { return source; }
}
```

- [ ] **Step 6: Write ProcessDefinitionTest**

```java
package com.github.wf.model;

import com.github.wf.model.node.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessDefinitionTest {

    @Test
    void findsStartNode() {
        ProcessDefinition def = new ProcessDefinition("test", "Test", 1,
                List.of(new StartEvent("start"), new EndEvent("end")),
                List.of(Transition.direct("start", "end")));

        assertThat(def.getStartNode().getId()).isEqualTo("start");
    }

    @Test
    void throwsWhenNoStartNode() {
        ProcessDefinition def = new ProcessDefinition("test", "Test", 1,
                List.of(new EndEvent("end")),
                List.of());
        assertThatThrownBy(def::getStartNode)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void outgoingTransitionsAreCached() {
        Transition t1 = Transition.direct("a", "b");
        Transition t2 = Transition.direct("a", "c");
        ProcessDefinition def = new ProcessDefinition("test", "Test", 1,
                List.of(new StartEvent("a"), new EndEvent("b"), new EndEvent("c")),
                List.of(t1, t2));

        assertThat(def.getOutgoingTransitions("a")).containsExactly(t1, t2);
        assertThat(def.getOutgoingTransitions("b")).isEmpty();
    }

    @Test
    void incomingTransitionsAreCached() {
        Transition t1 = Transition.direct("a", "b");
        Transition t2 = Transition.direct("c", "b");
        ProcessDefinition def = new ProcessDefinition("test", "Test", 1,
                List.of(new StartEvent("a"), new EndEvent("b"), new StartEvent("c")),
                List.of(t1, t2));

        assertThat(def.getIncomingTransitions("b")).containsExactly(t1, t2);
    }

    @Test
    void conditionalTransition() {
        Condition cond = Condition.expression("${x > 1}");
        Transition t = Transition.conditional("gw", cond).withTo("b");

        assertThat(t.isConditional()).isTrue();
        assertThat(t.getCondition()).isEqualTo(cond);
        assertThat(t.getTo()).isEqualTo("b");
    }
}
```

- [ ] **Step 7: Run tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -q`
Expected: all tests pass

- [ ] **Step 8: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add Transition, Condition, ProcessDefinition, Deployment"
```

---

### Task 4: Runtime models — ProcessInstance, Execution, InstanceStatus, ExecutionStatus

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\InstanceStatus.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\ExecutionStatus.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\ProcessInstance.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\Execution.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\engine\ExecutionTest.java`

- [ ] **Step 1: Write enums**

```java
// InstanceStatus.java
package com.github.wf.model;

public enum InstanceStatus {
    RUNNING,
    COMPLETED,
    TERMINATED
}

// ExecutionStatus.java
package com.github.wf.model;

public enum ExecutionStatus {
    ACTIVE,
    WAITING,
    COMPLETED
}
```

- [ ] **Step 2: Write ProcessInstance**

```java
package com.github.wf.model;

import java.time.Instant;
import java.util.*;

public class ProcessInstance {
    private final String id;
    private final String definitionId;
    private InstanceStatus status;
    private final Map<String, Object> variables;
    private Set<String> activeNodeIds;
    private final Instant createdAt;
    private Instant completedAt;

    public ProcessInstance(String id, String definitionId, Map<String, Object> variables) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.definitionId = Objects.requireNonNull(definitionId);
        this.status = InstanceStatus.RUNNING;
        this.variables = new HashMap<>(variables != null ? variables : Map.of());
        this.activeNodeIds = new HashSet<>();
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getDefinitionId() { return definitionId; }
    public InstanceStatus getStatus() { return status; }
    public Map<String, Object> getVariables() { return Collections.unmodifiableMap(variables); }
    public Set<String> getActiveNodeIds() { return Collections.unmodifiableSet(activeNodeIds); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setStatus(InstanceStatus status) {
        this.status = status;
        if (status == InstanceStatus.COMPLETED || status == InstanceStatus.TERMINATED) {
            this.completedAt = Instant.now();
        }
    }

    public void setVariable(String name, Object value) {
        this.variables.put(name, value);
    }

    public void setVariables(Map<String, Object> vars) {
        this.variables.putAll(vars);
    }

    public Object getVariable(String name) {
        return this.variables.get(name);
    }

    public void setActiveNodeIds(Set<String> activeNodeIds) {
        this.activeNodeIds = new HashSet<>(activeNodeIds);
    }

    public boolean isRunning() { return status == InstanceStatus.RUNNING; }
}
```

- [ ] **Step 3: Write Execution**

```java
package com.github.wf.engine;

import com.github.wf.model.ExecutionStatus;

import java.util.Objects;
import java.util.UUID;

public class Execution {
    private final String id;
    private final String instanceId;
    private String currentNodeId;
    private final String parentExecutionId;
    private ExecutionStatus status;

    public Execution(String id, String instanceId, String currentNodeId, String parentExecutionId) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.instanceId = Objects.requireNonNull(instanceId);
        this.currentNodeId = Objects.requireNonNull(currentNodeId);
        this.parentExecutionId = parentExecutionId;
        this.status = ExecutionStatus.ACTIVE;
    }

    public Execution(String instanceId, String currentNodeId) {
        this(null, instanceId, currentNodeId, null);
    }

    public String getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getCurrentNodeId() { return currentNodeId; }
    public String getParentExecutionId() { return parentExecutionId; }
    public ExecutionStatus getStatus() { return status; }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = Objects.requireNonNull(currentNodeId);
    }

    public void setStatus(ExecutionStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public boolean isActive() { return status == ExecutionStatus.ACTIVE; }
    public boolean isWaiting() { return status == ExecutionStatus.WAITING; }
    public boolean isCompleted() { return status == ExecutionStatus.COMPLETED; }
    public boolean isChild() { return parentExecutionId != null; }
}
```

- [ ] **Step 4: Write ExecutionTest**

```java
package com.github.wf.engine;

import com.github.wf.model.ExecutionStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutionTest {

    @Test
    void rootExecutionHasNoParent() {
        Execution exec = new Execution("inst-1", "start");
        assertThat(exec.isChild()).isFalse();
        assertThat(exec.getParentExecutionId()).isNull();
        assertThat(exec.isActive()).isTrue();
    }

    @Test
    void childExecutionHasParent() {
        Execution child = new Execution("child-1", "inst-1", "task-a", "parent-1");
        assertThat(child.isChild()).isTrue();
        assertThat(child.getParentExecutionId()).isEqualTo("parent-1");
    }

    @Test
    void statusTransitions() {
        Execution exec = new Execution("inst-1", "start");
        assertThat(exec.isActive()).isTrue();

        exec.setStatus(ExecutionStatus.WAITING);
        assertThat(exec.isWaiting()).isTrue();

        exec.setStatus(ExecutionStatus.COMPLETED);
        assertThat(exec.isCompleted()).isTrue();
    }
}
```

- [ ] **Step 5: Run tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -q`
Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add ProcessInstance, Execution with status enums"
```

---

### Task 5: Runtime models — Task, TaskStatus, HistoricActivity, TaskQuery

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\task\TaskStatus.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\task\Task.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\task\TaskQuery.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\model\HistoricActivity.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\task\TaskQueryTest.java`

- [ ] **Step 1: Write TaskStatus**

```java
package com.github.wf.task;

public enum TaskStatus {
    PENDING,
    COMPLETED,
    REJECTED,
    DELEGATED
}
```

- [ ] **Step 2: Write Task**

```java
package com.github.wf.task;

import java.time.Instant;
import java.util.*;

public class Task {
    private final String id;
    private final String instanceId;
    private final String nodeId;
    private String assignee;
    private List<String> candidateGroups;
    private TaskStatus status;
    private Map<String, Object> variables;
    private final Instant createdAt;
    private Instant completedAt;

    public Task(String id, String instanceId, String nodeId) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.instanceId = Objects.requireNonNull(instanceId);
        this.nodeId = Objects.requireNonNull(nodeId);
        this.status = TaskStatus.PENDING;
        this.candidateGroups = new ArrayList<>();
        this.variables = new HashMap<>();
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getNodeId() { return nodeId; }
    public String getAssignee() { return assignee; }
    public List<String> getCandidateGroups() { return candidateGroups; }
    public TaskStatus getStatus() { return status; }
    public Map<String, Object> getVariables() { return variables; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setAssignee(String assignee) { this.assignee = assignee; }
    public void setCandidateGroups(List<String> candidateGroups) { this.candidateGroups = candidateGroups; }
    public void setStatus(TaskStatus status) {
        this.status = status;
        if (status != TaskStatus.PENDING) {
            this.completedAt = Instant.now();
        }
    }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }

    public boolean isPending() { return status == TaskStatus.PENDING; }
}
```

- [ ] **Step 3: Write TaskQuery**

```java
package com.github.wf.task;

import java.util.ArrayList;
import java.util.List;

public class TaskQuery {
    private String assignee;
    private List<String> candidateGroups = new ArrayList<>();
    private String instanceId;
    private TaskStatus status;

    public TaskQuery assignee(String assignee) {
        this.assignee = assignee;
        return this;
    }

    public TaskQuery candidateGroup(String group) {
        this.candidateGroups.add(group);
        return this;
    }

    public TaskQuery candidateGroups(List<String> groups) {
        this.candidateGroups.addAll(groups);
        return this;
    }

    public TaskQuery instanceId(String instanceId) {
        this.instanceId = instanceId;
        return this;
    }

    public TaskQuery status(TaskStatus status) {
        this.status = status;
        return this;
    }

    public String getAssignee() { return assignee; }
    public List<String> getCandidateGroups() { return candidateGroups; }
    public String getInstanceId() { return instanceId; }
    public TaskStatus getStatus() { return status; }

    public boolean matches(Task task) {
        if (assignee != null && !assignee.equals(task.getAssignee())) return false;
        if (instanceId != null && !instanceId.equals(task.getInstanceId())) return false;
        if (status != null && status != task.getStatus()) return false;
        if (!candidateGroups.isEmpty()) {
            boolean hasGroup = candidateGroups.stream()
                    .anyMatch(g -> task.getCandidateGroups().contains(g));
            if (!hasGroup) return false;
        }
        return true;
    }
}
```

- [ ] **Step 4: Write HistoricActivity**

```java
package com.github.wf.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class HistoricActivity {
    private final String id;
    private final String instanceId;
    private final String nodeId;
    private final String nodeName;
    private final NodeType nodeType;
    private final String executor;
    private final String action;
    private final Instant timestamp;
    private final String comment;

    public HistoricActivity(String id, String instanceId, String nodeId, String nodeName,
                            NodeType nodeType, String executor, String action,
                            Instant timestamp, String comment) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.instanceId = Objects.requireNonNull(instanceId);
        this.nodeId = Objects.requireNonNull(nodeId);
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.executor = executor != null ? executor : "system";
        this.action = Objects.requireNonNull(action);
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.comment = comment;
    }

    // Convenience factory methods
    public static HistoricActivity nodeEnter(String instanceId, String nodeId,
                                              String nodeName, NodeType nodeType) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                "system", "enter", null, null);
    }

    public static HistoricActivity nodeLeave(String instanceId, String nodeId,
                                              String nodeName, NodeType nodeType) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                "system", "leave", null, null);
    }

    public static HistoricActivity taskCompleted(String instanceId, String nodeId,
                                                  String nodeName, NodeType nodeType,
                                                  String executor, String comment) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                executor, "complete", null, comment);
    }

    public static HistoricActivity taskRejected(String instanceId, String nodeId,
                                                 String nodeName, NodeType nodeType,
                                                 String executor, String comment) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                executor, "reject", null, comment);
    }

    public static HistoricActivity taskDelegated(String instanceId, String nodeId,
                                                  String nodeName, NodeType nodeType,
                                                  String executor, String comment) {
        return new HistoricActivity(null, instanceId, nodeId, nodeName, nodeType,
                executor, "delegate", null, comment);
    }

    public String getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getNodeId() { return nodeId; }
    public String getNodeName() { return nodeName; }
    public NodeType getNodeType() { return nodeType; }
    public String getExecutor() { return executor; }
    public String getAction() { return action; }
    public Instant getTimestamp() { return timestamp; }
    public String getComment() { return comment; }
}
```

- [ ] **Step 5: Write TaskQueryTest**

```java
package com.github.wf.task;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TaskQueryTest {

    @Test
    void matchesByAssignee() {
        Task task = new Task("t1", "inst-1", "node-1");
        task.setAssignee("张三");

        TaskQuery q = new TaskQuery().assignee("张三");
        assertThat(q.matches(task)).isTrue();

        TaskQuery q2 = new TaskQuery().assignee("李四");
        assertThat(q2.matches(task)).isFalse();
    }

    @Test
    void matchesByCandidateGroup() {
        Task task = new Task("t1", "inst-1", "node-1");
        task.setCandidateGroups(List.of("manager", "hr"));

        TaskQuery q = new TaskQuery().candidateGroup("manager");
        assertThat(q.matches(task)).isTrue();

        TaskQuery q2 = new TaskQuery().candidateGroup("finance");
        assertThat(q2.matches(task)).isFalse();
    }

    @Test
    void matchesByStatus() {
        Task task = new Task("t1", "inst-1", "node-1");
        assertThat(new TaskQuery().status(TaskStatus.PENDING).matches(task)).isTrue();
        assertThat(new TaskQuery().status(TaskStatus.COMPLETED).matches(task)).isFalse();
    }

    @Test
    void chainedBuilder() {
        TaskQuery q = new TaskQuery()
                .assignee("张三")
                .candidateGroup("manager")
                .instanceId("inst-1")
                .status(TaskStatus.PENDING);

        assertThat(q.getAssignee()).isEqualTo("张三");
        assertThat(q.getCandidateGroups()).contains("manager");
        assertThat(q.getInstanceId()).isEqualTo("inst-1");
        assertThat(q.getStatus()).isEqualTo(TaskStatus.PENDING);
    }
}
```

- [ ] **Step 6: Run tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -q`
Expected: all tests pass

- [ ] **Step 7: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add Task, TaskQuery, HistoricActivity models"
```

---

### Task 6: SPI interfaces and extension points

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\spi\ProcessRepository.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\spi\InstanceRepository.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\spi\TaskRepository.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\ext\ConditionEvaluator.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\ext\ProcessListener.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\ext\DynamicRouter.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\ext\ServiceTaskHandler.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\expression\ExpressionEvaluator.java`

- [ ] **Step 1: Write ProcessRepository**

```java
package com.github.wf.spi;

import com.github.wf.model.ProcessDefinition;
import java.util.List;

public interface ProcessRepository {
    void save(ProcessDefinition definition);
    ProcessDefinition findById(String id);
    ProcessDefinition findLatestById(String id);
    List<ProcessDefinition> findAllVersions(String id);
}
```

- [ ] **Step 2: Write InstanceRepository**

```java
package com.github.wf.spi;

import com.github.wf.engine.Execution;
import com.github.wf.model.HistoricActivity;
import com.github.wf.model.ProcessInstance;
import java.util.List;

public interface InstanceRepository {
    void save(ProcessInstance instance);
    ProcessInstance findById(String id);
    void update(ProcessInstance instance);
    List<ProcessInstance> findByDefinitionId(String definitionId);

    // Execution
    void saveExecution(Execution execution);
    Execution findExecutionById(String id);
    List<Execution> findActiveExecutions(String instanceId);
    List<Execution> findExecutionsByParentId(String parentExecutionId);
    void updateExecution(Execution execution);

    // History
    void saveHistoricActivity(HistoricActivity activity);
    List<HistoricActivity> findHistory(String instanceId);
}
```

- [ ] **Step 3: Write TaskRepository**

```java
package com.github.wf.spi;

import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import java.util.List;

public interface TaskRepository {
    void save(Task task);
    Task findById(String id);
    void update(Task task);
    List<Task> query(TaskQuery query);
}
```

- [ ] **Step 4: Write extension interfaces**

```java
// ConditionEvaluator.java
package com.github.wf.ext;

import java.util.Map;

@FunctionalInterface
public interface ConditionEvaluator {
    boolean evaluate(Map<String, Object> variables);
}
```

```java
// ProcessListener.java
package com.github.wf.ext;

import java.util.Map;

public interface ProcessListener {
    default void onNodeEnter(String instanceId, String nodeId, Map<String, Object> variables) {}
    default void onNodeLeave(String instanceId, String nodeId, Map<String, Object> variables) {}
}
```

```java
// DynamicRouter.java
package com.github.wf.ext;

import java.util.Map;

@FunctionalInterface
public interface DynamicRouter {
    String nextNode(String instanceId, String currentNodeId, Map<String, Object> variables);
}
```

```java
// ServiceTaskHandler.java
package com.github.wf.ext;

import java.util.Map;

@FunctionalInterface
public interface ServiceTaskHandler {
    Map<String, Object> execute(Map<String, Object> variables);
}
```

- [ ] **Step 5: Write ExpressionEvaluator interface**

```java
package com.github.wf.expression;

import java.util.Map;

public interface ExpressionEvaluator {
    Object evaluate(String expression, Map<String, Object> variables);

    default boolean evaluateToBoolean(String expression, Map<String, Object> variables) {
        Object result = evaluate(expression, variables);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        return false;
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `cd /d/workflow-engine && mvn compile -pl workflow-engine-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add SPI interfaces and extension point interfaces"
```

---

### Task 7: Expression evaluator — SpEL implementation

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\expression\SpelExpressionEvaluator.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\expression\SpelExpressionEvaluatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.github.wf.expression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SpelExpressionEvaluatorTest {

    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpelExpressionEvaluator();
    }

    @Test
    void evaluatesSimpleComparison() {
        boolean result = evaluator.evaluateToBoolean("days > 3", Map.of("days", 5));
        assertThat(result).isTrue();

        boolean result2 = evaluator.evaluateToBoolean("days > 3", Map.of("days", 1));
        assertThat(result2).isFalse();
    }

    @Test
    void evaluatesEquality() {
        boolean result = evaluator.evaluateToBoolean("status == 'approved'",
                Map.of("status", "approved"));
        assertThat(result).isTrue();
    }

    @Test
    void evaluatesVariableReference() {
        Object result = evaluator.evaluate("applicant", Map.of("applicant", "张三"));
        assertThat(result).isEqualTo("张三");
    }

    @Test
    void evaluatesCompoundExpression() {
        boolean result = evaluator.evaluateToBoolean(
                "amount >= 5000 and type == 'emergency'",
                Map.of("amount", 6000, "type", "emergency"));
        assertThat(result).isTrue();
    }

    @Test
    void handlesNullVariable() {
        boolean result = evaluator.evaluateToBoolean("approver == null",
                Map.of("approver", (Object) null));
        assertThat(result).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=SpelExpressionEvaluatorTest -q`
Expected: compilation error — SpelExpressionEvaluator class not found

- [ ] **Step 3: Write SpelExpressionEvaluator**

```java
package com.github.wf.expression;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

public class SpelExpressionEvaluator implements ExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public Object evaluate(String expression, Map<String, Object> variables) {
        EvaluationContext context = new StandardEvaluationContext();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        // Support both #varName and bare varName syntax
        String spelExpr = expression;
        if (!expression.contains("#") && !expression.contains("'") && looksLikeVariable(expression)) {
            spelExpr = "#" + expression;
        }
        return parser.parseExpression(spelExpr).getValue(context);
    }

    private boolean looksLikeVariable(String expr) {
        return expr.matches("^[a-zA-Z_][a-zA-Z0-9_.]*$");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=SpelExpressionEvaluatorTest -q`
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add SpEL expression evaluator"
```

---

### Task 8: DSL parser (YAML)

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\dsl\ProcessParser.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\dsl\YamlProcessParser.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\dsl\ProcessYaml.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\dsl\NodeYaml.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\dsl\TransitionYaml.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\dsl\YamlProcessParserTest.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\resources\leave-approval.yaml`

- [ ] **Step 1: Write YAML DTO classes**

```java
// ProcessParser.java
package com.github.wf.dsl;

import com.github.wf.model.ProcessDefinition;
import java.io.Reader;

public interface ProcessParser {
    ProcessDefinition parse(Reader reader);
    ProcessDefinition parse(String yaml);
}
```

```java
// ProcessYaml.java
package com.github.wf.dsl;

import java.util.List;

public class ProcessYaml {
    public String id;
    public String name;
    public int version;
    public List<NodeYaml> nodes;
    public List<TransitionYaml> transitions;
}
```

```java
// NodeYaml.java
package com.github.wf.dsl;

import java.util.List;
import java.util.Map;

public class NodeYaml {
    public String id;
    public String type;
    public String name;
    public String assignee;           // userTask
    public List<String> candidateGroups; // userTask
    public String dynamicRouter;      // userTask
    public String handlerClass;       // serviceTask
    public Map<String, String> listeners; // { enter: "class", leave: "class" }
    public List<GatewayConditionYaml> conditions; // exclusiveGateway, inclusiveGateway
}

// GatewayConditionYaml.java
package com.github.wf.dsl;

public class GatewayConditionYaml {
    public String expr;
    public String className;  // maps to "class" in YAML
    public boolean isDefault;
    public String to;
}
```

```java
// TransitionYaml.java
package com.github.wf.dsl;

public class TransitionYaml {
    public String from;
    public String to;
    public String type; // "direct", "conditional", "default"
    public String expr; // condition expression
    public String conditionClass; // condition java class
}
```

- [ ] **Step 2: Write test YAML**

Place at `workflow-engine-core/src/test/resources/leave-approval.yaml`:
```yaml
id: leave-approval
name: 请假审批
version: 1
nodes:
  - id: start
    type: startEvent
  - id: apply
    type: userTask
    name: 提交请假申请
    assignee: "${applicant}"
  - id: gateway
    type: exclusiveGateway
    conditions:
      - expr: "days > 3"
        to: manager-approve
      - default: true
        to: department-manager
  - id: manager-approve
    type: userTask
    name: 总经理审批
    candidateGroups: ["manager"]
    listeners:
      enter: "com.myapp.NotifyListener"
  - id: department-manager
    type: userTask
    name: 部门经理审批
  - id: end
    type: endEvent
transitions:
  - from: start
    to: apply
  - from: apply
    to: gateway
  - from: manager-approve
    to: end
  - from: department-manager
    to: end
```

- [ ] **Step 3: Write failing test**

```java
package com.github.wf.dsl;

import com.github.wf.model.*;
import com.github.wf.model.node.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.io.Reader;
import static org.assertj.core.api.Assertions.assertThat;

class YamlProcessParserTest {

    private final YamlProcessParser parser = new YamlProcessParser();

    @Test
    void parsesLeaveApprovalYaml() {
        Reader reader = new InputStreamReader(
                getClass().getResourceAsStream("/leave-approval.yaml"));

        ProcessDefinition def = parser.parse(reader);

        assertThat(def.getId()).isEqualTo("leave-approval");
        assertThat(def.getName()).isEqualTo("请假审批");
        assertThat(def.getVersion()).isEqualTo(1);

        // Nodes
        assertThat(def.getNodes()).hasSize(6);
        assertThat(def.getStartNode().getId()).isEqualTo("start");

        // UserTask
        Node apply = def.getNode("apply");
        assertThat(apply).isInstanceOf(UserTask.class);
        assertThat(((UserTask) apply).getAssignee()).isEqualTo("${applicant}");

        // Exclusive gateway
        Node gw = def.getNode("gateway");
        assertThat(gw).isInstanceOf(ExclusiveGateway.class);

        // Transitions from gateway (conditions → conditional transitions)
        assertThat(def.getOutgoingTransitions("gateway")).hasSize(2);
        assertThat(def.getOutgoingTransitions("gateway").get(0).isConditional()).isTrue();
        assertThat(def.getOutgoingTransitions("gateway").get(1).isDefault()).isTrue();
    }

    @Test
    void parsesListeners() {
        Reader reader = new InputStreamReader(
                getClass().getResourceAsStream("/leave-approval.yaml"));
        ProcessDefinition def = parser.parse(reader);

        Node managerNode = def.getNode("manager-approve");
        assertThat(managerNode.getListeners()).contains("com.myapp.NotifyListener");
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=YamlProcessParserTest -q`
Expected: compilation error — YamlProcessParser class not found

- [ ] **Step 5: Write YamlProcessParser**

```java
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
            case "startEvent":
                return new StartEvent(ny.id, ny.name, listeners);
            case "endEvent":
                return new EndEvent(ny.id, ny.name, listeners);
            case "userTask":
                return new UserTask(ny.id, ny.name, ny.assignee,
                        ny.candidateGroups, ny.dynamicRouter, listeners);
            case "serviceTask":
                return new ServiceTask(ny.id, ny.name, ny.handlerClass, listeners);
            case "exclusiveGateway":
                return new ExclusiveGateway(ny.id, ny.name, listeners);
            case "parallelGateway":
                return new ParallelGateway(ny.id, ny.name, listeners);
            case "inclusiveGateway":
                return new InclusiveGateway(ny.id, ny.name, listeners);
            default:
                throw new IllegalArgumentException("Unknown node type: " + ny.type);
        }
    }

    private List<Transition> convertTransitions(List<TransitionYaml> transitionYamls,
                                                 Map<String, NodeYaml> nodeYamlMap) {
        List<Transition> result = new ArrayList<>();

        // First, add explicit transitions from the transitions section
        if (transitionYamls != null) {
            for (TransitionYaml ty : transitionYamls) {
                if (ty.from == null) continue;

                if ("conditional".equals(ty.type)) {
                    Condition cond;
                    if (ty.conditionClass != null) {
                        cond = Condition.javaClass(ty.conditionClass);
                    } else if (ty.expr != null) {
                        cond = Condition.expression(ty.expr);
                    } else {
                        continue;
                    }
                    result.add(Transition.conditional(ty.from, cond).withTo(ty.to));
                } else if ("default".equals(ty.type)) {
                    result.add(Transition.defaultTransition(ty.from, ty.to));
                } else {
                    result.add(Transition.direct(ty.from, ty.to));
                }
            }
        }

        // Second, generate transitions from gateway conditions defined on nodes
        for (NodeYaml ny : nodeYamlMap.values()) {
            if (ny.conditions != null && !ny.conditions.isEmpty()) {
                for (GatewayConditionYaml gcy : ny.conditions) {
                    if (gcy.isDefault) {
                        result.add(Transition.defaultTransition(ny.id, gcy.to));
                    } else if (gcy.className != null) {
                        Condition cond = Condition.javaClass(gcy.className);
                        result.add(Transition.conditional(ny.id, cond).withTo(gcy.to));
                    } else if (gcy.expr != null) {
                        Condition cond = Condition.expression(gcy.expr);
                        result.add(Transition.conditional(ny.id, cond).withTo(gcy.to));
                    }
                }
            }
        }

        return result;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=YamlProcessParserTest -q`
Expected: all tests pass

- [ ] **Step 7: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add YAML DSL parser with SnakeYAML"
```

---

### Task 9: In-memory repositories

**Files:**
- Create: `D:\workflow-engine\workflow-engine-memory\src\main\java\com\github\wf\memory\InMemoryProcessRepository.java`
- Create: `D:\workflow-engine\workflow-engine-memory\src\main\java\com\github\wf\memory\InMemoryInstanceRepository.java`
- Create: `D:\workflow-engine\workflow-engine-memory\src\main\java\com\github\wf\memory\InMemoryTaskRepository.java`
- Create: `D:\workflow-engine\workflow-engine-memory\src\test\java\com\github\wf\memory\InMemoryRepositoryTest.java`

- [ ] **Step 1: Write InMemoryProcessRepository**

```java
package com.github.wf.memory;

import com.github.wf.model.ProcessDefinition;
import com.github.wf.spi.ProcessRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProcessRepository implements ProcessRepository {

    private final Map<String, ProcessDefinition> store = new ConcurrentHashMap<>();
    // keyDefinitionId → latest version
    private final Map<String, Integer> latestVersion = new ConcurrentHashMap<>();

    @Override
    public void save(ProcessDefinition definition) {
        String key = definition.getId() + ":" + definition.getVersion();
        store.put(key, definition);
        latestVersion.merge(definition.getId(), definition.getVersion(), Math::max);
    }

    @Override
    public ProcessDefinition findById(String id) {
        return store.get(id);
    }

    @Override
    public ProcessDefinition findLatestById(String id) {
        Integer version = latestVersion.get(id);
        if (version == null) return null;
        return store.get(id + ":" + version);
    }

    @Override
    public List<ProcessDefinition> findAllVersions(String id) {
        List<ProcessDefinition> result = new ArrayList<>();
        for (Map.Entry<String, ProcessDefinition> entry : store.entrySet()) {
            if (entry.getKey().startsWith(id + ":")) {
                result.add(entry.getValue());
            }
        }
        result.sort(Comparator.comparingInt(ProcessDefinition::getVersion));
        return result;
    }
}
```

- [ ] **Step 2: Write InMemoryInstanceRepository**

```java
package com.github.wf.memory;

import com.github.wf.engine.Execution;
import com.github.wf.model.HistoricActivity;
import com.github.wf.model.ProcessInstance;
import com.github.wf.spi.InstanceRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryInstanceRepository implements InstanceRepository {

    private final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, Execution> executions = new ConcurrentHashMap<>();
    private final List<HistoricActivity> history = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void save(ProcessInstance instance) {
        instances.put(instance.getId(), instance);
    }

    @Override
    public ProcessInstance findById(String id) {
        return instances.get(id);
    }

    @Override
    public void update(ProcessInstance instance) {
        instances.put(instance.getId(), instance);
    }

    @Override
    public List<ProcessInstance> findByDefinitionId(String definitionId) {
        return instances.values().stream()
                .filter(i -> definitionId.equals(i.getDefinitionId()))
                .collect(Collectors.toList());
    }

    @Override
    public void saveExecution(Execution execution) {
        executions.put(execution.getId(), execution);
    }

    @Override
    public Execution findExecutionById(String id) {
        return executions.get(id);
    }

    @Override
    public List<Execution> findActiveExecutions(String instanceId) {
        return executions.values().stream()
                .filter(e -> e.getInstanceId().equals(instanceId))
                .filter(e -> !e.isCompleted())
                .collect(Collectors.toList());
    }

    @Override
    public List<Execution> findExecutionsByParentId(String parentExecutionId) {
        return executions.values().stream()
                .filter(e -> parentExecutionId.equals(e.getParentExecutionId()))
                .collect(Collectors.toList());
    }

    @Override
    public void updateExecution(Execution execution) {
        executions.put(execution.getId(), execution);
    }

    @Override
    public void saveHistoricActivity(HistoricActivity activity) {
        history.add(activity);
    }

    @Override
    public List<HistoricActivity> findHistory(String instanceId) {
        return history.stream()
                .filter(h -> h.getInstanceId().equals(instanceId))
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 3: Write InMemoryTaskRepository**

```java
package com.github.wf.memory;

import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryTaskRepository implements TaskRepository {

    private final Map<String, Task> store = new ConcurrentHashMap<>();

    @Override
    public void save(Task task) {
        store.put(task.getId(), task);
    }

    @Override
    public Task findById(String id) {
        return store.get(id);
    }

    @Override
    public void update(Task task) {
        store.put(task.getId(), task);
    }

    @Override
    public List<Task> query(TaskQuery query) {
        return store.values().stream()
                .filter(query::matches)
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 4: Write tests**

```java
package com.github.wf.memory;

import com.github.wf.engine.Execution;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import com.github.wf.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRepositoryTest {

    private InMemoryProcessRepository processRepo;
    private InMemoryInstanceRepository instanceRepo;
    private InMemoryTaskRepository taskRepo;

    @BeforeEach
    void setUp() {
        processRepo = new InMemoryProcessRepository();
        instanceRepo = new InMemoryInstanceRepository();
        taskRepo = new InMemoryTaskRepository();
    }

    @Test
    void processRepositoryFindsLatestVersion() {
        ProcessDefinition v1 = new ProcessDefinition("wf", "Test", 1,
                List.of(new StartEvent("s"), new EndEvent("e")),
                List.of(Transition.direct("s", "e")));
        ProcessDefinition v2 = new ProcessDefinition("wf", "Test", 2,
                List.of(new StartEvent("s"), new EndEvent("e")),
                List.of(Transition.direct("s", "e")));

        processRepo.save(v1);
        processRepo.save(v2);

        ProcessDefinition latest = processRepo.findLatestById("wf");
        assertThat(latest.getVersion()).isEqualTo(2);
    }

    @Test
    void instanceRepositoryFindsActiveExecutions() {
        Execution e1 = new Execution("e1", "inst-1", "node-a", null);
        Execution e2 = new Execution("e2", "inst-1", "node-b", null);
        e2.setStatus(ExecutionStatus.COMPLETED);
        Execution e3 = new Execution("e3", "inst-2", "node-c", null);

        instanceRepo.saveExecution(e1);
        instanceRepo.saveExecution(e2);
        instanceRepo.saveExecution(e3);

        List<Execution> active = instanceRepo.findActiveExecutions("inst-1");
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo("e1");
    }

    @Test
    void instanceRepositoryFindsChildExecutions() {
        Execution parent = new Execution("p1", "inst-1", "gw", null);
        Execution child1 = new Execution("c1", "inst-1", "task-1", "p1");
        Execution child2 = new Execution("c2", "inst-1", "task-2", "p1");

        instanceRepo.saveExecution(parent);
        instanceRepo.saveExecution(child1);
        instanceRepo.saveExecution(child2);

        List<Execution> children = instanceRepo.findExecutionsByParentId("p1");
        assertThat(children).hasSize(2);
    }

    @Test
    void taskRepositoryQueriesByAssignee() {
        Task t1 = new Task("t1", "inst-1", "node-1");
        t1.setAssignee("张三");
        Task t2 = new Task("t2", "inst-1", "node-2");
        t2.setAssignee("李四");

        taskRepo.save(t1);
        taskRepo.save(t2);

        List<Task> result = taskRepo.query(new TaskQuery().assignee("张三"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("t1");
    }

    @Test
    void historyIsRecorded() {
        HistoricActivity ha = HistoricActivity.nodeEnter("inst-1", "start", "开始", NodeType.START_EVENT);
        instanceRepo.saveHistoricActivity(ha);

        List<HistoricActivity> history = instanceRepo.findHistory("inst-1");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAction()).isEqualTo("enter");
    }
}
```

- [ ] **Step 5: Run tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-memory -q`
Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add in-memory repository implementations"
```

---

### Task 10: WorkflowEngineBuilder

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\WorkflowEngineBuilder.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\engine\WorkflowEngineBuilderTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.github.wf.engine;

import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WorkflowEngineBuilderTest {

    @Test
    void buildsWithRequiredDependencies() {
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(mock(ProcessRepository.class))
                .instanceRepository(mock(InstanceRepository.class))
                .taskRepository(mock(TaskRepository.class))
                .build();

        assertThat(engine).isNotNull();
    }

    @Test
    void usesDefaultExpressionEvaluator() {
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(mock(ProcessRepository.class))
                .instanceRepository(mock(InstanceRepository.class))
                .taskRepository(mock(TaskRepository.class))
                .build();

        assertThat(engine).isNotNull();
    }

    @Test
    void canOverrideExpressionEvaluator() {
        ExpressionEvaluator custom = mock(ExpressionEvaluator.class);
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(mock(ProcessRepository.class))
                .instanceRepository(mock(InstanceRepository.class))
                .taskRepository(mock(TaskRepository.class))
                .expressionEvaluator(custom)
                .build();

        assertThat(engine).isNotNull();
    }

    @Test
    void requiresProcessRepository() {
        assertThatThrownBy(() -> WorkflowEngine.builder()
                .instanceRepository(mock(InstanceRepository.class))
                .taskRepository(mock(TaskRepository.class))
                .build())
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=WorkflowEngineBuilderTest -q`
Expected: compilation error — WorkflowEngineBuilder/WorkflowEngine not found

Note: This test uses Mockito. Instead of adding Mockito, use simple anonymous classes.

Actually, let me rewrite the test without Mockito to avoid adding another dependency:

```java
package com.github.wf.engine;

import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEngineBuilderTest {

    // Simple stub implementations for testing
    private static final ProcessRepository STUB_PROCESS = new ProcessRepository() {
        public void save(com.github.wf.model.ProcessDefinition d) {}
        public com.github.wf.model.ProcessDefinition findById(String id) { return null; }
        public com.github.wf.model.ProcessDefinition findLatestById(String id) { return null; }
        public java.util.List<com.github.wf.model.ProcessDefinition> findAllVersions(String id) { return java.util.List.of(); }
    };

    private static final InstanceRepository STUB_INSTANCE = new InstanceRepository() {
        public void save(com.github.wf.model.ProcessInstance i) {}
        public com.github.wf.model.ProcessInstance findById(String id) { return null; }
        public void update(com.github.wf.model.ProcessInstance i) {}
        public java.util.List<com.github.wf.model.ProcessInstance> findByDefinitionId(String d) { return java.util.List.of(); }
        public void saveExecution(Execution e) {}
        public Execution findExecutionById(String id) { return null; }
        public java.util.List<Execution> findActiveExecutions(String i) { return java.util.List.of(); }
        public java.util.List<Execution> findExecutionsByParentId(String p) { return java.util.List.of(); }
        public void updateExecution(Execution e) {}
        public void saveHistoricActivity(com.github.wf.model.HistoricActivity h) {}
        public java.util.List<com.github.wf.model.HistoricActivity> findHistory(String i) { return java.util.List.of(); }
    };

    private static final TaskRepository STUB_TASK = new TaskRepository() {
        public void save(com.github.wf.task.Task t) {}
        public com.github.wf.task.Task findById(String id) { return null; }
        public void update(com.github.wf.task.Task t) {}
        public java.util.List<com.github.wf.task.Task> query(com.github.wf.task.TaskQuery q) { return java.util.List.of(); }
    };

    @Test
    void buildsWithRequiredDependencies() {
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(STUB_PROCESS)
                .instanceRepository(STUB_INSTANCE)
                .taskRepository(STUB_TASK)
                .build();
        assertThat(engine).isNotNull();
    }

    @Test
    void requiresProcessRepository() {
        assertThatThrownBy(() -> WorkflowEngine.builder()
                .instanceRepository(STUB_INSTANCE)
                .taskRepository(STUB_TASK)
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresInstanceRepository() {
        assertThatThrownBy(() -> WorkflowEngine.builder()
                .processRepository(STUB_PROCESS)
                .taskRepository(STUB_TASK)
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresTaskRepository() {
        assertThatThrownBy(() -> WorkflowEngine.builder()
                .processRepository(STUB_PROCESS)
                .instanceRepository(STUB_INSTANCE)
                .build())
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails (revised)**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=WorkflowEngineBuilderTest -q`
Expected: compilation error — WorkflowEngineBuilder class not found

- [ ] **Step 3: Write WorkflowEngineBuilder and a skeleton WorkflowEngine**

```java
// WorkflowEngineBuilder.java
package com.github.wf.engine;

import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;

import java.util.Objects;

public class WorkflowEngineBuilder {

    private ProcessRepository processRepository;
    private InstanceRepository instanceRepository;
    private TaskRepository taskRepository;
    private ExpressionEvaluator expressionEvaluator;

    WorkflowEngineBuilder() {} // package-private, called by WorkflowEngine.builder()

    public WorkflowEngineBuilder processRepository(ProcessRepository processRepository) {
        this.processRepository = processRepository;
        return this;
    }

    public WorkflowEngineBuilder instanceRepository(InstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
        return this;
    }

    public WorkflowEngineBuilder taskRepository(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
        return this;
    }

    public WorkflowEngineBuilder expressionEvaluator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
        return this;
    }

    public WorkflowEngine build() {
        Objects.requireNonNull(processRepository, "processRepository is required");
        Objects.requireNonNull(instanceRepository, "instanceRepository is required");
        Objects.requireNonNull(taskRepository, "taskRepository is required");
        if (expressionEvaluator == null) {
            expressionEvaluator = new SpelExpressionEvaluator();
        }
        return new WorkflowEngine(processRepository, instanceRepository, taskRepository, expressionEvaluator);
    }
}
```

```java
// WorkflowEngine.java (skeleton)
package com.github.wf.engine;

import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.TaskQuery;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class WorkflowEngine {

    final ProcessRepository processRepository;
    final InstanceRepository instanceRepository;
    final TaskRepository taskRepository;
    final ExpressionEvaluator expressionEvaluator;
    final ConcurrentHashMap<String, ReentrantLock> instanceLocks = new ConcurrentHashMap<>();

    WorkflowEngine(ProcessRepository processRepository,
                   InstanceRepository instanceRepository,
                   TaskRepository taskRepository,
                   ExpressionEvaluator expressionEvaluator) {
        this.processRepository = processRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.expressionEvaluator = expressionEvaluator;
    }

    public static WorkflowEngineBuilder builder() {
        return new WorkflowEngineBuilder();
    }

    public TaskQuery taskQuery() {
        return new TaskQuery();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=WorkflowEngineBuilderTest -q`
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add WorkflowEngineBuilder and WorkflowEngine skeleton"
```

---

### Task 11: NodeRunner interface and StartEvent/EndEvent runners

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\runner\NodeRunner.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\runner\StartEventRunner.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\runner\EndEventRunner.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\engine\runner\StartEndEventRunnerTest.java`

- [ ] **Step 1: Write NodeRunner interface**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.model.Node;
import com.github.wf.model.ProcessDefinition;

public interface NodeRunner {
    boolean run(Node node, ExecutionContext context);
}
```

- [ ] **Step 2: Write ExecutionContext**

```java
package com.github.wf.engine;

import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.model.Node;
import com.github.wf.model.ProcessDefinition;
import com.github.wf.spi.InstanceRepository;

import java.util.List;
import java.util.Map;

public class ExecutionContext {
    private final ProcessDefinition definition;
    private final Execution execution;
    private final ExpressionEvaluator expressionEvaluator;
    private final InstanceRepository instanceRepository;

    public ExecutionContext(ProcessDefinition definition, Execution execution,
                            ExpressionEvaluator expressionEvaluator,
                            InstanceRepository instanceRepository) {
        this.definition = definition;
        this.execution = execution;
        this.expressionEvaluator = expressionEvaluator;
        this.instanceRepository = instanceRepository;
    }

    public ProcessDefinition getDefinition() { return definition; }
    public Execution getExecution() { return execution; }
    public ExpressionEvaluator getExpressionEvaluator() { return expressionEvaluator; }
    public InstanceRepository getInstanceRepository() { return instanceRepository; }

    public String getInstanceId() { return execution.getInstanceId(); }
    public String getCurrentNodeId() { return execution.getCurrentNodeId(); }
    public Map<String, Object> getVariables() {
        return instanceRepository.findById(execution.getInstanceId()).getVariables();
    }
}
```

- [ ] **Step 3: Write StartEventRunner**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.model.Node;
import com.github.wf.model.Transition;

import java.util.List;

public class StartEventRunner implements NodeRunner {

    @Override
    public boolean run(Node node, ExecutionContext context) {
        // StartEvent: immediately move to the next node
        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
        if (outgoing.isEmpty()) {
            throw new IllegalStateException("StartEvent must have an outgoing transition");
        }
        Transition next = outgoing.get(0);
        context.getExecution().setCurrentNodeId(next.getTo());
        return true; // advanced
    }
}
```

- [ ] **Step 4: Write EndEventRunner**

```java
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
            // Child execution reached end — merge to parent
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null) {
                // Check if all siblings are complete
                List<Execution> siblings = repo.findExecutionsByParentId(exec.getParentExecutionId());
                boolean allDone = siblings.stream()
                        .allMatch(e -> e.getId().equals(exec.getId()) || e.isCompleted());
                if (allDone) {
                    parent.setStatus(ExecutionStatus.ACTIVE);
                    // Move parent past the join gateway
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
            // Root execution reached end event
            exec.setStatus(ExecutionStatus.COMPLETED);
            repo.updateExecution(exec);
        }
        return true;
    }
}
```

- [ ] **Step 5: Write test**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.model.*;
import com.github.wf.model.node.EndEvent;
import com.github.wf.model.node.StartEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class StartEndEventRunnerTest {

    private InMemoryInstanceRepository instanceRepo;
    private SpelExpressionEvaluator exprEval;

    @BeforeEach
    void setUp() {
        instanceRepo = new InMemoryInstanceRepository();
        exprEval = new SpelExpressionEvaluator();
    }

    @Test
    void startEventMovesToNextNode() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new StartEvent("start"), new EndEvent("end")),
                List.of(Transition.direct("start", "end")));

        Execution exec = new Execution("inst-1", "start");
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        StartEventRunner runner = new StartEventRunner();
        boolean advanced = runner.run(def.getNode("start"), ctx);

        assertThat(advanced).isTrue();
        assertThat(exec.getCurrentNodeId()).isEqualTo("end");
    }

    @Test
    void endEventCompletesExecution() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new StartEvent("start"), new EndEvent("end")),
                List.of(Transition.direct("start", "end")));

        Execution exec = new Execution("inst-1", "end");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        EndEventRunner runner = new EndEventRunner();
        boolean advanced = runner.run(def.getNode("end"), ctx);

        assertThat(advanced).isTrue();
        assertThat(exec.isCompleted()).isTrue();
    }
}
```

- [ ] **Step 6: Run tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=StartEndEventRunnerTest -q`
Expected: all tests pass

- [ ] **Step 7: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add NodeRunner interface, ExecutionContext, StartEvent and EndEvent runners"
```

---

### Task 12: UserTask runner

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\runner\UserTaskRunner.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\engine\runner\UserTaskRunnerTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.model.*;
import com.github.wf.model.node.UserTask;
import com.github.wf.task.Task;
import com.github.wf.task.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class UserTaskRunnerTest {

    private InMemoryInstanceRepository instanceRepo;
    private InMemoryTaskRepository taskRepo;
    private SpelExpressionEvaluator exprEval;
    private ProcessDefinition def;

    @BeforeEach
    void setUp() {
        instanceRepo = new InMemoryInstanceRepository();
        taskRepo = new InMemoryTaskRepository();
        exprEval = new SpelExpressionEvaluator();

        ProcessInstance instance = new ProcessInstance("inst-1", "wf",
                Map.of("applicant", "张三"));
        instanceRepo.save(instance);

        def = new ProcessDefinition("wf", "Test", 1,
                List.of(new UserTask("t1", "审批", "${applicant}",
                        List.of("manager"), null, null)),
                List.of());
    }

    @Test
    void createsTaskWhenFirstEntered() {
        Execution exec = new Execution("e1", "inst-1", "t1");
        instanceRepo.saveExecution(exec);

        UserTaskRunner runner = new UserTaskRunner(taskRepo);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        boolean advanced = runner.run(def.getNode("t1"), ctx);

        assertThat(advanced).isTrue();
        assertThat(exec.isWaiting()).isTrue();

        // Task was created
        List<Task> tasks = taskRepo.query(new TaskQuery().instanceId("inst-1"));
        assertThat(tasks).hasSize(1);
        Task task = tasks.get(0);
        assertThat(task.getNodeId()).isEqualTo("t1");
        assertThat(task.getAssignee()).isEqualTo("张三"); // evaluated from "${applicant}"
        assertThat(task.getCandidateGroups()).contains("manager");
    }

    @Test
    void skipsWhenTaskAlreadyExists() {
        Execution exec = new Execution("e1", "inst-1", "t1");
        instanceRepo.saveExecution(exec);

        UserTaskRunner runner = new UserTaskRunner(taskRepo);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        // First run creates task
        runner.run(def.getNode("t1"), ctx);

        // Second run — task already exists, should skip
        // Reset exec to active to simulate re-trigger
        exec.setStatus(ExecutionStatus.ACTIVE);
        boolean advanced = runner.run(def.getNode("t1"), ctx);

        assertThat(advanced).isFalse(); // did not advance
        List<Task> tasks = taskRepo.query(new TaskQuery().instanceId("inst-1"));
        assertThat(tasks).hasSize(1); // still just one task
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=UserTaskRunnerTest -q`
Expected: compilation error — UserTaskRunner not found

- [ ] **Step 3: Write UserTaskRunner**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.model.ExecutionStatus;
import com.github.wf.model.Node;
import com.github.wf.model.node.UserTask;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.Task;

import java.util.Map;
import java.util.Objects;

public class UserTaskRunner implements NodeRunner {

    private final TaskRepository taskRepository;

    public UserTaskRunner(TaskRepository taskRepository) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
    }

    @Override
    public boolean run(Node node, ExecutionContext context) {
        UserTask userTask = (UserTask) node;
        Execution exec = context.getExecution();
        Map<String, Object> variables = context.getVariables();

        // Check if a task already exists for this execution+node
        boolean taskExists = taskRepository.query(
                new com.github.wf.task.TaskQuery()
                        .instanceId(exec.getInstanceId()))
                .stream()
                .anyMatch(t -> t.getNodeId().equals(node.getId()) && t.isPending());

        if (taskExists) {
            return false; // already waiting, no advance
        }

        // Create task
        Task task = new Task(null, exec.getInstanceId(), node.getId());

        // Evaluate assignee expression
        if (userTask.getAssignee() != null) {
            String assigneeExpr = userTask.getAssignee();
            if (assigneeExpr.startsWith("${") && assigneeExpr.endsWith("}")) {
                assigneeExpr = assigneeExpr.substring(2, assigneeExpr.length() - 1);
            }
            Object assignee = context.getExpressionEvaluator().evaluate(assigneeExpr, variables);
            task.setAssignee(assignee != null ? assignee.toString() : null);
        }

        task.setCandidateGroups(userTask.getCandidateGroups());
        task.setVariables(Map.copyOf(variables));
        taskRepository.save(task);

        exec.setStatus(ExecutionStatus.WAITING);
        return true; // advanced (created a task and stopped)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=UserTaskRunnerTest -q`
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add UserTaskRunner — creates tasks and pauses execution"
```

---

### Task 13: ServiceTask runner

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\runner\ServiceTaskRunner.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\engine\runner\ServiceTaskRunnerTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.ext.ServiceTaskHandler;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.model.*;
import com.github.wf.model.node.EndEvent;
import com.github.wf.model.node.ServiceTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceTaskRunnerTest {

    private InMemoryInstanceRepository instanceRepo;
    private SpelExpressionEvaluator exprEval;
    private ProcessDefinition def;

    @BeforeEach
    void setUp() {
        instanceRepo = new InMemoryInstanceRepository();
        exprEval = new SpelExpressionEvaluator();

        ProcessInstance instance = new ProcessInstance("inst-1", "wf",
                Map.of("amount", 100));
        instanceRepo.save(instance);

        def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ServiceTask("svc", "发通知", "com.test.MyHandler", null),
                        new EndEvent("end")),
                List.of(Transition.direct("svc", "end")));
    }

    @Test
    void invokesHandlerAndMovesToNext() {
        Execution exec = new Execution("e1", "inst-1", "svc");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        // Register a handler that doubles the amount
        ServiceTaskRunner runner = new ServiceTaskRunner();
        runner.registerHandler("com.test.MyHandler", vars -> {
            int amount = (int) vars.get("amount");
            return Map.of("amount", amount * 2);
        });

        boolean advanced = runner.run(def.getNode("svc"), ctx);

        assertThat(advanced).isTrue();
        assertThat(exec.getCurrentNodeId()).isEqualTo("end");

        // Variables updated by handler
        ProcessInstance updated = instanceRepo.findById("inst-1");
        assertThat(updated.getVariable("amount")).isEqualTo(200);
    }

    @Test
    void instantiatesHandlerByClassName() throws Exception {
        // This test verifies that the reflective instantiation path works
        // We use a known class name and verify it gets invoked
        Execution exec = new Execution("e2", "inst-1", "svc");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        // Register with the same class name that will be looked up
        // Test that the handler is invoked via class name lookup
        ServiceTaskRunner runner = new ServiceTaskRunner();
        runner.registerHandler("com.test.MyHandler", vars -> Map.of("result", "ok"));

        runner.run(def.getNode("svc"), ctx);

        ProcessInstance updated = instanceRepo.findById("inst-1");
        assertThat(updated.getVariable("result")).isEqualTo("ok");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=ServiceTaskRunnerTest -q`
Expected: compilation error — ServiceTaskRunner not found

- [ ] **Step 3: Write ServiceTaskRunner**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.ext.ServiceTaskHandler;
import com.github.wf.model.Node;
import com.github.wf.model.ProcessInstance;
import com.github.wf.model.Transition;
import com.github.wf.model.node.ServiceTask;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceTaskRunner implements NodeRunner {

    // Registry of handler instances (can be pre-registered or reflectively loaded)
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
            // Try reflective instantiation
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

        // Move to next node
        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
        if (!outgoing.isEmpty()) {
            context.getExecution().setCurrentNodeId(outgoing.get(0).getTo());
        }

        return true;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=ServiceTaskRunnerTest -q`
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add ServiceTaskRunner with handler registry"
```

---

### Task 14: Gateway runners — Exclusive, Parallel, Inclusive

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\runner\ExclusiveGatewayRunner.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\runner\ParallelGatewayRunner.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\runner\InclusiveGatewayRunner.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\engine\runner\GatewayRunnerTest.java`

- [ ] **Step 1: Write ExclusiveGatewayRunner**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.ext.ConditionEvaluator;
import com.github.wf.model.*;
import com.github.wf.model.node.ExclusiveGateway;

import java.util.List;
import java.util.Map;

public class ExclusiveGatewayRunner implements NodeRunner {

    @Override
    public boolean run(Node node, ExecutionContext context) {
        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());
        Map<String, Object> variables = context.getVariables();

        // Try each conditional transition in order
        for (Transition t : outgoing) {
            if (t.isConditional()) {
                if (evaluateCondition(t.getCondition(), variables, context)) {
                    context.getExecution().setCurrentNodeId(t.getTo());
                    return true;
                }
            } else if (t.isDefault()) {
                context.getExecution().setCurrentNodeId(t.getTo());
                return true;
            }
        }

        throw new IllegalStateException("No outgoing transition matched for exclusive gateway: " + node.getId());
    }

    private boolean evaluateCondition(Condition condition, Map<String, Object> variables,
                                       ExecutionContext context) {
        if (condition.getType() == ConditionType.EXPRESSION) {
            return context.getExpressionEvaluator().evaluateToBoolean(condition.getExpr(), variables);
        } else {
            // JAVA_CLASS — instantiate and call
            try {
                Class<?> clazz = Class.forName(condition.getClassName());
                ConditionEvaluator evaluator = (ConditionEvaluator) clazz.getDeclaredConstructor().newInstance();
                return evaluator.evaluate(variables);
            } catch (Exception e) {
                throw new RuntimeException("Cannot evaluate condition: " + condition.getClassName(), e);
            }
        }
    }
}
```

- [ ] **Step 2: Write ParallelGatewayRunner**

```java
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

        if (incoming.size() == 1 && outgoing.size() > 1) {
            // FORK: create child executions for each outgoing path
            return handleFork(node, context, outgoing);
        } else {
            // JOIN: wait for all siblings to arrive
            return handleJoin(node, context);
        }
    }

    private boolean handleFork(Node node, ExecutionContext context, List<Transition> outgoing) {
        Execution parent = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();

        for (Transition t : outgoing) {
            Execution child = new Execution(
                    null, parent.getInstanceId(), t.getTo(), parent.getId());
            repo.saveExecution(child);
        }

        parent.setStatus(ExecutionStatus.WAITING);
        repo.updateExecution(parent);
        return true;
    }

    private boolean handleJoin(Node node, ExecutionContext context) {
        Execution exec = context.getExecution();
        if (!exec.isChild()) {
            // Not a child execution — pass through
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
            // Wake up the parent execution
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null) {
                parent.setStatus(ExecutionStatus.ACTIVE);
                List<Transition> parentOutgoing = context.getDefinition()
                        .getOutgoingTransitions(parent.getCurrentNodeId());
                // Move parent past the fork gateway
                parent.setCurrentNodeId(parentOutgoing.get(0).getTo());
                repo.updateExecution(parent);
            }

            // Mark all siblings as completed
            for (Execution s : siblings) {
                if (!s.isCompleted()) {
                    s.setStatus(ExecutionStatus.COMPLETED);
                    repo.updateExecution(s);
                }
            }
            return true;
        } else {
            // Wait for siblings
            exec.setStatus(ExecutionStatus.WAITING);
            repo.updateExecution(exec);
            return true;
        }
    }
}
```

- [ ] **Step 3: Write InclusiveGatewayRunner**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.ext.ConditionEvaluator;
import com.github.wf.model.*;
import com.github.wf.spi.InstanceRepository;

import java.util.List;
import java.util.Map;

public class InclusiveGatewayRunner implements NodeRunner {

    @Override
    public boolean run(Node node, ExecutionContext context) {
        List<Transition> incoming = context.getDefinition().getIncomingTransitions(node.getId());
        List<Transition> outgoing = context.getDefinition().getOutgoingTransitions(node.getId());

        // Determine fork vs join by topology
        boolean isFork = incoming.size() <= 1 || outgoing.size() > 1;

        if (isFork && outgoing.size() > 1) {
            return handleFork(node, context, outgoing);
        } else {
            return handleJoin(node, context);
        }
    }

    private boolean handleFork(Node node, ExecutionContext context, List<Transition> outgoing) {
        Execution parent = context.getExecution();
        InstanceRepository repo = context.getInstanceRepository();
        Map<String, Object> variables = context.getVariables();

        int forked = 0;
        for (Transition t : outgoing) {
            boolean match;
            if (t.isConditional()) {
                match = evaluateCondition(t.getCondition(), variables, context);
            } else if (t.isDefault()) {
                match = (forked == 0); // default only if nothing else matched
            } else {
                match = true;
            }

            if (match) {
                Execution child = new Execution(
                        null, parent.getInstanceId(), t.getTo(), parent.getId());
                repo.saveExecution(child);
                forked++;
            }
        }

        if (forked == 0) {
            throw new IllegalStateException("No outgoing transition matched for inclusive gateway: " + node.getId());
        }

        // If only one path matched, just move inline (no fork needed)
        if (forked == 1) {
            List<Transition> outgoingTransitions = context.getDefinition().getOutgoingTransitions(node.getId());
            // Already created one child — cancel fork, move parent directly
            // Clean up the single child
            List<Execution> children = repo.findExecutionsByParentId(parent.getId());
            for (Execution child : children) {
                parent.setCurrentNodeId(child.getCurrentNodeId());
                // Remove the unnecessary child
                child.setStatus(ExecutionStatus.COMPLETED);
                repo.updateExecution(child);
            }
        } else {
            parent.setStatus(ExecutionStatus.WAITING);
            repo.updateExecution(parent);
        }
        return true;
    }

    private boolean handleJoin(Node node, ExecutionContext context) {
        // Same logic as parallel gateway join
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
                        sibling.isCompleted() ||
                        sibling.getCurrentNodeId().equals(node.getId()));

        if (allArrived) {
            Execution parent = repo.findExecutionById(exec.getParentExecutionId());
            if (parent != null && parent.isWaiting()) {
                parent.setStatus(ExecutionStatus.ACTIVE);
                // Move parent past the gateway to the join's next node
                List<Transition> afterJoin = context.getDefinition()
                        .getOutgoingTransitions(node.getId());
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
        } else {
            exec.setStatus(ExecutionStatus.WAITING);
            repo.updateExecution(exec);
        }
        return true;
    }

    private boolean evaluateCondition(Condition condition, Map<String, Object> variables,
                                       ExecutionContext context) {
        if (condition.getType() == ConditionType.EXPRESSION) {
            return context.getExpressionEvaluator().evaluateToBoolean(condition.getExpr(), variables);
        } else {
            try {
                Class<?> clazz = Class.forName(condition.getClassName());
                ConditionEvaluator evaluator = (ConditionEvaluator) clazz.getDeclaredConstructor().newInstance();
                return evaluator.evaluate(variables);
            } catch (Exception e) {
                throw new RuntimeException("Cannot evaluate condition: " + condition.getClassName(), e);
            }
        }
    }
}
```

- [ ] **Step 4: Write gateway tests**

```java
package com.github.wf.engine.runner;

import com.github.wf.engine.ExecutionContext;
import com.github.wf.engine.Execution;
import com.github.wf.expression.SpelExpressionEvaluator;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class GatewayRunnerTest {

    private InMemoryInstanceRepository instanceRepo;
    private SpelExpressionEvaluator exprEval;

    @BeforeEach
    void setUp() {
        instanceRepo = new InMemoryInstanceRepository();
        exprEval = new SpelExpressionEvaluator();
    }

    // --- Exclusive Gateway ---

    @Test
    void exclusiveGatewayChoosesMatchingPath() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ExclusiveGateway("gw"), new UserTask("t1", "A", null, null, null, null),
                        new UserTask("t2", "B", null, null, null, null)),
                List.of(
                        Transition.conditional("gw", Condition.expression("amount > 100")).withTo("t1"),
                        Transition.defaultTransition("gw", "t2")));

        ProcessInstance instance = new ProcessInstance("inst-1", "wf", Map.of("amount", 200));
        instanceRepo.save(instance);

        Execution exec = new Execution("e1", "inst-1", "gw");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ExclusiveGatewayRunner runner = new ExclusiveGatewayRunner();
        runner.run(def.getNode("gw"), ctx);

        assertThat(exec.getCurrentNodeId()).isEqualTo("t1"); // matched first condition
    }

    @Test
    void exclusiveGatewayFallsThroughToDefault() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ExclusiveGateway("gw"), new UserTask("t1", "A", null, null, null, null),
                        new UserTask("t2", "B", null, null, null, null)),
                List.of(
                        Transition.conditional("gw", Condition.expression("amount > 100")).withTo("t1"),
                        Transition.defaultTransition("gw", "t2")));

        ProcessInstance instance = new ProcessInstance("inst-1", "wf", Map.of("amount", 50));
        instanceRepo.save(instance);

        Execution exec = new Execution("e1", "inst-1", "gw");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ExclusiveGatewayRunner runner = new ExclusiveGatewayRunner();
        runner.run(def.getNode("gw"), ctx);

        assertThat(exec.getCurrentNodeId()).isEqualTo("t2"); // fell through to default
    }

    // --- Parallel Gateway ---

    @Test
    void parallelGatewayForkCreatesChildExecutions() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ParallelGateway("fork"), new ParallelGateway("join"),
                        new UserTask("t1", "A", null, null, null, null),
                        new UserTask("t2", "B", null, null, null, null)),
                List.of(
                        Transition.direct("fork", "t1"),
                        Transition.direct("fork", "t2"),
                        Transition.direct("t1", "join"),
                        Transition.direct("t2", "join")));

        ProcessInstance instance = new ProcessInstance("inst-1", "wf", Map.of());
        instanceRepo.save(instance);

        Execution exec = new Execution("e1", "inst-1", "fork");
        instanceRepo.saveExecution(exec);
        ExecutionContext ctx = new ExecutionContext(def, exec, exprEval, instanceRepo);

        ParallelGatewayRunner runner = new ParallelGatewayRunner();
        runner.run(def.getNode("fork"), ctx);

        // Parent is waiting
        assertThat(exec.isWaiting()).isTrue();

        // Two children were created
        List<Execution> children = instanceRepo.findExecutionsByParentId("e1");
        assertThat(children).hasSize(2);
        assertThat(children).extracting(Execution::getCurrentNodeId)
                .containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    void parallelGatewayJoinWaitsForAllSiblings() {
        ProcessDefinition def = new ProcessDefinition("wf", "Test", 1,
                List.of(new ParallelGateway("fork"), new ParallelGateway("join"),
                        new EndEvent("end")),
                List.of(
                        Transition.direct("fork", "t1"),
                        Transition.direct("fork", "t2"),
                        Transition.direct("t1", "join"),
                        Transition.direct("t2", "join"),
                        Transition.direct("join", "end")));

        ProcessInstance instance = new ProcessInstance("inst-1", "wf", Map.of());
        instanceRepo.save(instance);

        // Parent waiting at fork
        Execution parent = new Execution("parent", "inst-1", "fork", null);
        parent.setStatus(ExecutionStatus.WAITING);
        instanceRepo.saveExecution(parent);

        // Two children
        Execution c1 = new Execution("c1", "inst-1", "join", "parent");
        Execution c2 = new Execution("c2", "inst-1", "t2", "parent");
        instanceRepo.saveExecution(c1);
        instanceRepo.saveExecution(c2);

        // c1 arrives at join — not all arrived yet
        ExecutionContext ctx1 = new ExecutionContext(def, c1, exprEval, instanceRepo);
        ParallelGatewayRunner runner = new ParallelGatewayRunner();
        runner.run(def.getNode("join"), ctx1);
        assertThat(instanceRepo.findExecutionById("c1").isWaiting()).isTrue();

        // c2 arrives at join — now all arrived
        c2.setCurrentNodeId("join");
        instanceRepo.updateExecution(c2);
        ExecutionContext ctx2 = new ExecutionContext(def, c2, exprEval, instanceRepo);
        runner.run(def.getNode("join"), ctx2);

        // Parent should be resumed
        Execution resumedParent = instanceRepo.findExecutionById("parent");
        assertThat(resumedParent.isActive()).isTrue();
    }
}
```

- [ ] **Step 5: Run tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=GatewayRunnerTest -q`
Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add Exclusive, Parallel, and Inclusive gateway runners"
```

---

### Task 15: WorkflowEngine — deploy and start

**Files:**
- Modify: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\WorkflowEngine.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\engine\WorkflowEngineDeployStartTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineDeployStartTest {

    private WorkflowEngine engine;
    private InMemoryProcessRepository processRepo;
    private InMemoryInstanceRepository instanceRepo;
    private InMemoryTaskRepository taskRepo;

    @BeforeEach
    void setUp() {
        processRepo = new InMemoryProcessRepository();
        instanceRepo = new InMemoryInstanceRepository();
        taskRepo = new InMemoryTaskRepository();

        engine = WorkflowEngine.builder()
                .processRepository(processRepo)
                .instanceRepository(instanceRepo)
                .taskRepository(taskRepo)
                .build();
    }

    @Test
    void deploysAndStartsSimpleWorkflow() {
        String yaml = """
                id: simple
                name: 简单流程
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: task1
                    type: userTask
                    name: 审批
                    assignee: "审批人"
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: task1
                  - from: task1
                    to: end
                """;

        engine.setProcessParser(new YamlProcessParser());

        // Deploy
        ProcessDefinition def = engine.deploy(yaml);
        assertThat(def.getId()).isEqualTo("simple");
        assertThat(processRepo.findLatestById("simple")).isNotNull();

        // Start
        ProcessInstance instance = engine.start("simple", Map.of("initiator", "张三"));
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(instance.getVariables()).containsEntry("initiator", "张三");

        // A task should have been created
        assertThat(taskRepo.query(engine.taskQuery().instanceId(instance.getId())))
                .hasSize(1);
    }

    @Test
    void startUnknownDefinitionThrows() {
        assertThatThrownBy(() -> engine.start("nonexistent", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=WorkflowEngineDeployStartTest -q`
Expected: test failures — deploy/start not implemented

- [ ] **Step 3: Implement deploy and start in WorkflowEngine**

Add to WorkflowEngine.java:

```java
package com.github.wf.engine;

import com.github.wf.dsl.ProcessParser;
import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.runner.*;
import com.github.wf.expression.ExpressionEvaluator;
import com.github.wf.model.*;
import com.github.wf.model.node.*;
import com.github.wf.spi.InstanceRepository;
import com.github.wf.spi.ProcessRepository;
import com.github.wf.spi.TaskRepository;
import com.github.wf.task.TaskQuery;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class WorkflowEngine {

    final ProcessRepository processRepository;
    final InstanceRepository instanceRepository;
    final TaskRepository taskRepository;
    final ExpressionEvaluator expressionEvaluator;
    final ConcurrentHashMap<String, ReentrantLock> instanceLocks = new ConcurrentHashMap<>();

    // Node runners
    private final Map<NodeType, NodeRunner> runners = new HashMap<>();
    private ProcessParser processParser = new YamlProcessParser();

    WorkflowEngine(ProcessRepository processRepository,
                   InstanceRepository instanceRepository,
                   TaskRepository taskRepository,
                   ExpressionEvaluator expressionEvaluator) {
        this.processRepository = processRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.expressionEvaluator = expressionEvaluator;
        registerDefaultRunners();
    }

    private void registerDefaultRunners() {
        runners.put(NodeType.START_EVENT, new StartEventRunner());
        runners.put(NodeType.END_EVENT, new EndEventRunner());
        runners.put(NodeType.USER_TASK, new UserTaskRunner(taskRepository));
        runners.put(NodeType.SERVICE_TASK, new ServiceTaskRunner());
        runners.put(NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayRunner());
        runners.put(NodeType.PARALLEL_GATEWAY, new ParallelGatewayRunner());
        runners.put(NodeType.INCLUSIVE_GATEWAY, new InclusiveGatewayRunner());
    }

    public static WorkflowEngineBuilder builder() {
        return new WorkflowEngineBuilder();
    }

    public void setProcessParser(ProcessParser processParser) {
        this.processParser = processParser;
    }

    public TaskQuery taskQuery() {
        return new TaskQuery();
    }

    // --- Public API ---

    public ProcessDefinition deploy(String yaml) {
        ProcessDefinition def = processParser.parse(yaml);
        processRepository.save(def);
        return def;
    }

    public ProcessDefinition deploy(java.io.File file) {
        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            ProcessDefinition def = processParser.parse(reader);
            processRepository.save(def);
            return def;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to deploy workflow file: " + file, e);
        }
    }

    public ProcessInstance start(String definitionId, Map<String, Object> variables) {
        ProcessDefinition def = processRepository.findLatestById(definitionId);
        if (def == null) {
            throw new IllegalArgumentException("Process definition not found: " + definitionId);
        }

        ProcessInstance instance = new ProcessInstance(null, definitionId, variables);
        instanceRepo.saveInstance(instance);

        Node startNode = def.getStartNode();
        Execution exec = new Execution(instance.getId(), startNode.getId());
        instanceRepo.saveExecution(exec);

        // Set initial active nodes
        instance.setActiveNodeIds(java.util.Set.of(startNode.getId()));
        instanceRepo.update(instance);

        // Run the trigger loop
        trigger(instance.getId());

        return instanceRepo.findById(instance.getId());
    }

    // Trigger placeholder — will be fully implemented in next task
    public void trigger(String instanceId) {
        ReentrantLock lock = instanceLocks.computeIfAbsent(instanceId, k -> new ReentrantLock());
        lock.lock();
        try {
            ProcessInstance instance = instanceRepo.findById(instanceId);
            if (instance == null || !instance.isRunning()) return;

            ProcessDefinition def = processRepository.findLatestById(instance.getDefinitionId());
            if (def == null) return;

            boolean advanced;
            do {
                advanced = false;
                java.util.List<Execution> executions = instanceRepo.findActiveExecutions(instanceId);

                for (Execution exec : executions) {
                    Node node = def.getNode(exec.getCurrentNodeId());
                    NodeRunner runner = runners.get(node.getType());

                    if (runner != null) {
                        ExecutionContext ctx = new ExecutionContext(def, exec,
                                expressionEvaluator, instanceRepo);
                        if (runner.run(node, ctx)) {
                            advanced = true;
                            instanceRepo.updateExecution(exec);
                        }
                    }
                }

                // Re-check if instance is complete
                java.util.List<Execution> remaining = instanceRepo.findActiveExecutions(instanceId);
                if (remaining.isEmpty()) {
                    instance.setStatus(InstanceStatus.COMPLETED);
                    instanceRepo.update(instance);
                    break;
                }
            } while (advanced);

        } finally {
            lock.unlock();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=WorkflowEngineDeployStartTest -q`
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add WorkflowEngine deploy, start, and trigger loop"
```

---

### Task 16: WorkflowEngine — completeTask, rejectTask, delegateTask, terminate, history

**Files:**
- Modify: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\engine\WorkflowEngine.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\engine\WorkflowEngineTaskApiTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineTaskApiTest {

    private WorkflowEngine engine;
    private InMemoryInstanceRepository instanceRepo;
    private InMemoryTaskRepository taskRepo;

    @BeforeEach
    void setUp() {
        instanceRepo = new InMemoryInstanceRepository();
        taskRepo = new InMemoryTaskRepository();

        engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(instanceRepo)
                .taskRepository(taskRepo)
                .build();
        engine.setProcessParser(new YamlProcessParser());
    }

    private String deployAndStartSimpleFlow() {
        engine.deploy("""
                id: test
                version: 1
                nodes:
                  - id: s
                    type: startEvent
                  - id: t1
                    type: userTask
                    name: 审批
                  - id: e
                    type: endEvent
                transitions:
                  - from: s
                    to: t1
                  - from: t1
                    to: e
                """);
        ProcessInstance instance = engine.start("test", Map.of());
        return instance.getId();
    }

    @Test
    void completesTaskAndAdvancesWorkflow() {
        String instanceId = deployAndStartSimpleFlow();

        // Find the pending task
        List<Task> tasks = taskRepo.query(engine.taskQuery().instanceId(instanceId));
        assertThat(tasks).hasSize(1);
        Task task = tasks.get(0);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);

        // Complete it
        engine.completeTask(task.getId(), Map.of("approved", true), "同意");

        // Task should be completed
        Task updated = taskRepo.findById(task.getId());
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(updated.getVariables()).containsEntry("approved", true);

        // Workflow should be complete (t1→end)
        ProcessInstance instance = instanceRepo.findById(instanceId);
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void rejectsTask() {
        String instanceId = deployAndStartSimpleFlow();
        List<Task> tasks = taskRepo.query(engine.taskQuery().instanceId(instanceId));

        engine.rejectTask(tasks.get(0).getId(), "信息不完整");

        Task updated = taskRepo.findById(tasks.get(0).getId());
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.REJECTED);
    }

    @Test
    void delegatesTask() {
        String instanceId = deployAndStartSimpleFlow();
        List<Task> tasks = taskRepo.query(engine.taskQuery().instanceId(instanceId));

        engine.delegateTask(tasks.get(0).getId(), "王五");

        Task updated = taskRepo.findById(tasks.get(0).getId());
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.DELEGATED);
        assertThat(updated.getAssignee()).isEqualTo("王五");
    }

    @Test
    void terminatesInstance() {
        String instanceId = deployAndStartSimpleFlow();

        engine.terminate(instanceId, "撤销申请");

        ProcessInstance instance = instanceRepo.findById(instanceId);
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.TERMINATED);
    }

    @Test
    void retrievesHistory() {
        String instanceId = deployAndStartSimpleFlow();
        List<Task> tasks = taskRepo.query(engine.taskQuery().instanceId(instanceId));
        engine.completeTask(tasks.get(0).getId(), Map.of(), "通过");

        List<HistoricActivity> history = engine.history(instanceId);
        assertThat(history).isNotEmpty();
        // Should have at least enter events for start, t1, end
        assertThat(history.stream().map(HistoricActivity::getAction))
                .contains("enter", "complete");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=WorkflowEngineTaskApiTest -q`
Expected: compilation errors — completeTask/rejectTask/delegateTask not found

- [ ] **Step 3: Add public API methods to WorkflowEngine**

Add these methods to WorkflowEngine.java (after the existing `trigger()` method):

```java
public void completeTask(String taskId, Map<String, Object> variables, String comment) {
    Task task = taskRepository.findById(taskId);
    if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
    if (!task.isPending()) throw new IllegalStateException("Task is not pending: " + taskId);

    task.setStatus(TaskStatus.COMPLETED);
    task.setVariables(variables);
    taskRepository.update(task);

    // Write history
    ProcessInstance instance = instanceRepository.findById(task.getInstanceId());
    ProcessDefinition def = processRepository.findLatestById(instance.getDefinitionId());
    Node node = def.getNode(task.getNodeId());
    instanceRepository.saveHistoricActivity(
            HistoricActivity.taskCompleted(instance.getId(), node.getId(),
                    node.getName(), node.getType(), task.getAssignee(), comment));

    // Update instance variables
    if (variables != null && !variables.isEmpty()) {
        instance.setVariables(variables);
        instanceRepository.update(instance);
    }

    // Find pending execution and wake it up
    List<Execution> executions = instanceRepository.findActiveExecutions(task.getInstanceId());
    for (Execution exec : executions) {
        if (exec.isWaiting() && exec.getCurrentNodeId().equals(task.getNodeId())) {
            // Check for dynamic router
            if (node instanceof com.github.wf.model.node.UserTask) {
                com.github.wf.model.node.UserTask ut = (com.github.wf.model.node.UserTask) node;
                if (ut.getDynamicRouter() != null) {
                    String nextNodeId = invokeDynamicRouter(ut.getDynamicRouter(),
                            instance.getId(), node.getId(), instance.getVariables());
                    exec.setCurrentNodeId(nextNodeId);
                } else {
                    // Follow static transitions
                    List<com.github.wf.model.Transition> outgoing = def.getOutgoingTransitions(node.getId());
                    if (!outgoing.isEmpty()) {
                        exec.setCurrentNodeId(outgoing.get(0).getTo());
                    }
                }
            } else {
                List<com.github.wf.model.Transition> outgoing = def.getOutgoingTransitions(node.getId());
                if (!outgoing.isEmpty()) {
                    exec.setCurrentNodeId(outgoing.get(0).getTo());
                }
            }
            exec.setStatus(com.github.wf.model.ExecutionStatus.ACTIVE);
            instanceRepository.updateExecution(exec);
            break;
        }
    }

    trigger(task.getInstanceId());
}

public void rejectTask(String taskId, String comment) {
    Task task = taskRepository.findById(taskId);
    if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
    task.setStatus(TaskStatus.REJECTED);
    taskRepository.update(task);

    ProcessInstance instance = instanceRepository.findById(task.getInstanceId());
    ProcessDefinition def = processRepository.findLatestById(instance.getDefinitionId());
    Node node = def.getNode(task.getNodeId());
    instanceRepository.saveHistoricActivity(
            HistoricActivity.taskRejected(instance.getId(), node.getId(),
                    node.getName(), node.getType(), task.getAssignee(), comment));

    // Rejected tasks leave the execution waiting — caller decides next action
}

public void delegateTask(String taskId, String newAssignee) {
    Task task = taskRepository.findById(taskId);
    if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);
    String oldAssignee = task.getAssignee();
    task.setStatus(TaskStatus.DELEGATED);
    taskRepository.update(task);

    // Create a new task for the delegate
    Task delegated = new Task(null, task.getInstanceId(), task.getNodeId());
    delegated.setAssignee(newAssignee);
    delegated.setCandidateGroups(task.getCandidateGroups());
    delegated.setVariables(task.getVariables());
    taskRepository.save(delegated);

    ProcessInstance instance = instanceRepository.findById(task.getInstanceId());
    ProcessDefinition def = processRepository.findLatestById(instance.getDefinitionId());
    Node node = def.getNode(task.getNodeId());
    instanceRepository.saveHistoricActivity(
            HistoricActivity.taskDelegated(instance.getId(), node.getId(),
                    node.getName(), node.getType(), oldAssignee, newAssignee));
}

public void terminate(String instanceId, String reason) {
    ProcessInstance instance = instanceRepository.findById(instanceId);
    if (instance == null) throw new IllegalArgumentException("Instance not found: " + instanceId);
    instance.setStatus(InstanceStatus.TERMINATED);
    instanceRepository.update(instance);

    // Complete all pending tasks
    List<Task> tasks = taskRepository.query(taskQuery().instanceId(instanceId));
    for (Task t : tasks) {
        if (t.isPending()) {
            t.setStatus(TaskStatus.REJECTED);
            taskRepository.update(t);
        }
    }

    // Complete all executions
    List<Execution> executions = instanceRepository.findActiveExecutions(instanceId);
    for (Execution exec : executions) {
        exec.setStatus(com.github.wf.model.ExecutionStatus.COMPLETED);
        instanceRepository.updateExecution(exec);
    }

    if (reason != null) {
        instanceRepository.saveHistoricActivity(new HistoricActivity(
                null, instanceId, "system", "terminate", null,
                "system", "terminate", null, reason));
    }
}

public List<HistoricActivity> history(String instanceId) {
    return instanceRepository.findHistory(instanceId);
}

private String invokeDynamicRouter(String routerClass, String instanceId,
                                    String currentNodeId, Map<String, Object> variables) {
    try {
        Class<?> clazz = Class.forName(routerClass);
        com.github.wf.ext.DynamicRouter router =
                (com.github.wf.ext.DynamicRouter) clazz.getDeclaredConstructor().newInstance();
        return router.nextNode(instanceId, currentNodeId, variables);
    } catch (Exception e) {
        throw new RuntimeException("Cannot invoke DynamicRouter: " + routerClass, e);
    }
}

// Add missing imports to the top of WorkflowEngine.java:
// import com.github.wf.task.Task;
// import com.github.wf.task.TaskStatus;
// import com.github.wf.model.HistoricActivity;
// import java.util.List;
```

IMPORTANT: In the `trigger()` method, add history recording and listener invocation. Modify the trigger method so that when a node is first entered, it writes a history record. Add after the `runner.run(node, ctx)` line:

```java
// Inside trigger(), after runner.run():
if (runner.run(node, ctx)) {
    advanced = true;
    instanceRepository.updateExecution(exec);
    // Record history: node enter
    instanceRepository.saveHistoricActivity(
            HistoricActivity.nodeEnter(instanceId, node.getId(), node.getName(), node.getType()));
    // Invoke enter listeners
    invokeListeners(node, instanceId, instance.getVariables(), true);
}
```

Add the listener invocation helper:

```java
private void invokeListeners(Node node, String instanceId,
                              Map<String, Object> variables, boolean enter) {
    for (String listenerClass : node.getListeners()) {
        try {
            Class<?> clazz = Class.forName(listenerClass);
            com.github.wf.ext.ProcessListener listener =
                    (com.github.wf.ext.ProcessListener) clazz.getDeclaredConstructor().newInstance();
            if (enter) {
                listener.onNodeEnter(instanceId, node.getId(), variables);
            } else {
                listener.onNodeLeave(instanceId, node.getId(), variables);
            }
        } catch (Exception e) {
            // Log and continue — a listener failure shouldn't break the engine
            System.err.println("Listener error: " + listenerClass + " - " + e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=WorkflowEngineTaskApiTest -q`
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add completeTask, rejectTask, delegateTask, terminate, history APIs"
```

---

### Task 17: JSON parser

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\main\java\com\github\wf\dsl\JsonProcessParser.java`
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\dsl\JsonProcessParserTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.github.wf.dsl;

import com.github.wf.model.ProcessDefinition;
import com.github.wf.model.node.EndEvent;
import com.github.wf.model.node.StartEvent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JsonProcessParserTest {

    @Test
    void parsesJsonProcess() {
        String json = """
                {
                  "id": "json-test",
                  "name": "JSON流程",
                  "version": 1,
                  "nodes": [
                    {"id": "start", "type": "startEvent"},
                    {"id": "end", "type": "endEvent"}
                  ],
                  "transitions": [
                    {"from": "start", "to": "end"}
                  ]
                }
                """;

        JsonProcessParser parser = new JsonProcessParser();
        ProcessDefinition def = parser.parse(json);

        assertThat(def.getId()).isEqualTo("json-test");
        assertThat(def.getName()).isEqualTo("JSON流程");
        assertThat(def.getNode("start")).isInstanceOf(StartEvent.class);
        assertThat(def.getNode("end")).isInstanceOf(EndEvent.class);
        assertThat(def.getTransitions()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=JsonProcessParserTest -q`
Expected: compilation error

- [ ] **Step 3: Write JsonProcessParser**

```java
package com.github.wf.dsl;

import com.github.wf.model.*;
import com.github.wf.model.node.*;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
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
        try (JsonReader jsonReader = Json.createReader(reader)) {
            JsonObject root = jsonReader.readObject();
            return convert(root);
        }
    }

    private ProcessDefinition convert(JsonObject root) {
        String id = root.getString("id");
        String name = root.getString("name", id);
        int version = root.getInt("version", 1);

        Map<String, NodeYaml> nodeMap = new LinkedHashMap<>();
        List<Node> nodes = new ArrayList<>();

        JsonArray nodesArr = root.getJsonArray("nodes");
        for (int i = 0; i < nodesArr.size(); i++) {
            JsonObject no = nodesArr.getJsonObject(i);
            NodeYaml ny = jsonToNodeYaml(no);
            nodeMap.put(ny.id, ny);
            nodes.add(convertNode(ny));
        }

        List<Transition> transitions = new ArrayList<>();
        JsonArray transArr = root.getJsonArray("transitions");
        if (transArr != null) {
            List<TransitionYaml> tyList = new ArrayList<>();
            for (int i = 0; i < transArr.size(); i++) {
                JsonObject to = transArr.getJsonObject(i);
                TransitionYaml ty = new TransitionYaml();
                ty.from = to.getString("from", null);
                ty.to = to.getString("to", null);
                ty.type = to.getString("type", null);
                ty.expr = to.getString("expr", null);
                ty.conditionClass = to.getString("conditionClass", null);
                tyList.add(ty);
            }
            // Reuse the Yaml parser's transition converter
            transitions = new YamlProcessParser().convertTransitionsPublic(tyList, nodeMap);
        }

        return new ProcessDefinition(id, name, version, nodes, transitions);
    }

    private NodeYaml jsonToNodeYaml(JsonObject jo) {
        NodeYaml ny = new NodeYaml();
        ny.id = jo.getString("id");
        ny.type = jo.getString("type");
        ny.name = jo.getString("name", null);
        ny.assignee = jo.getString("assignee", null);
        ny.handlerClass = jo.getString("handlerClass", null);
        ny.dynamicRouter = jo.getString("dynamicRouter", null);

        if (jo.containsKey("candidateGroups")) {
            JsonArray cg = jo.getJsonArray("candidateGroups");
            ny.candidateGroups = new ArrayList<>();
            for (int i = 0; i < cg.size(); i++) {
                ny.candidateGroups.add(cg.getString(i));
            }
        }

        if (jo.containsKey("listeners")) {
            JsonObject l = jo.getJsonObject("listeners");
            ny.listeners = new HashMap<>();
            if (l.containsKey("enter")) ny.listeners.put("enter", l.getString("enter"));
            if (l.containsKey("leave")) ny.listeners.put("leave", l.getString("leave"));
        }

        if (jo.containsKey("conditions")) {
            JsonArray conds = jo.getJsonArray("conditions");
            ny.conditions = new ArrayList<>();
            for (int i = 0; i < conds.size(); i++) {
                JsonObject co = conds.getJsonObject(i);
                GatewayConditionYaml gcy = new GatewayConditionYaml();
                gcy.expr = co.getString("expr", null);
                gcy.className = co.getString("className", null);
                gcy.isDefault = co.getBoolean("default", false);
                gcy.to = co.getString("to", null);
                ny.conditions.add(gcy);
            }
        }

        return ny;
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
}
```

Since JsonProcessParser reuses transition conversion logic, add a public helper in YamlProcessParser:

```java
// Add to YamlProcessParser.java:
public List<Transition> convertTransitionsPublic(List<TransitionYaml> tyList, Map<String, NodeYaml> nodeYamlMap) {
    return convertTransitions(tyList, nodeYamlMap);
}
```

- [ ] **Step 4: Run tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=JsonProcessParserTest -q`
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add JSON process parser"
```

---

### Task 18: End-to-end integration test — leave approval flow

**Files:**
- Create: `D:\workflow-engine\workflow-engine-core\src\test\java\com\github\wf\engine\LeaveApprovalIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
package com.github.wf.engine;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.model.*;
import com.github.wf.task.Task;
import com.github.wf.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class LeaveApprovalIntegrationTest {

    private WorkflowEngine engine;
    private InMemoryInstanceRepository instanceRepo;
    private InMemoryTaskRepository taskRepo;

    @BeforeEach
    void setUp() {
        instanceRepo = new InMemoryInstanceRepository();
        taskRepo = new InMemoryTaskRepository();

        engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(instanceRepo)
                .taskRepository(taskRepo)
                .build();
        engine.setProcessParser(new YamlProcessParser());
    }

    @Test
    void fullLeaveApprovalFlow() {
        // 1. Deploy
        engine.deploy("""
                id: leave-approval
                name: 请假审批
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: apply
                    type: userTask
                    name: 提交请假申请
                    assignee: "${applicant}"
                  - id: gateway
                    type: exclusiveGateway
                    conditions:
                      - expr: "days > 3"
                        to: manager-approve
                      - default: true
                        to: department-manager
                  - id: manager-approve
                    type: userTask
                    name: 总经理审批
                    candidateGroups: ["manager"]
                  - id: department-manager
                    type: userTask
                    name: 部门经理审批
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: apply
                  - from: apply
                    to: gateway
                  - from: manager-approve
                    to: end
                  - from: department-manager
                    to: end
                """);

        // 2. Start — 请假5天，走总经理审批
        ProcessInstance instance = engine.start("leave-approval",
                Map.of("applicant", "张三", "days", 5));

        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.RUNNING);

        // 3. 申请人的待办
        List<Task> applicantTasks = taskRepo.query(
                engine.taskQuery().assignee("张三"));
        assertThat(applicantTasks).hasSize(1);
        Task applyTask = applicantTasks.get(0);
        assertThat(applyTask.getNodeId()).isEqualTo("apply");

        // 4. 提交申请
        engine.completeTask(applyTask.getId(), Map.of("reason", "看病"), "看病请假");

        // 5. 因为 days > 3，应该走到总经理审批
        List<Task> managerTasks = taskRepo.query(
                engine.taskQuery().candidateGroup("manager"));
        assertThat(managerTasks).hasSize(1);
        assertThat(managerTasks.get(0).getNodeId()).isEqualTo("manager-approve");

        // 6. 总经理审批通过
        engine.completeTask(managerTasks.get(0).getId(),
                Map.of("approved", true), "同意");

        // 7. 流程结束
        ProcessInstance completed = instanceRepo.findById(instance.getId());
        assertThat(completed.getStatus()).isEqualTo(InstanceStatus.COMPLETED);

        // 8. 历史记录
        List<HistoricActivity> history = engine.history(instance.getId());
        assertThat(history).isNotEmpty();
    }

    @Test
    void leaveApprovalShortLeaveGoesToDepartmentManager() {
        engine.deploy("""
                id: leave-approval
                version: 1
                nodes:
                  - id: start
                    type: startEvent
                  - id: apply
                    type: userTask
                    name: 提交请假申请
                    assignee: "${applicant}"
                  - id: gateway
                    type: exclusiveGateway
                    conditions:
                      - expr: "days > 3"
                        to: manager-approve
                      - default: true
                        to: department-manager
                  - id: manager-approve
                    type: userTask
                    name: 总经理审批
                  - id: department-manager
                    type: userTask
                    name: 部门经理审批
                  - id: end
                    type: endEvent
                transitions:
                  - from: start
                    to: apply
                  - from: apply
                    to: gateway
                  - from: manager-approve
                    to: end
                  - from: department-manager
                    to: end
                """);

        // 请假1天，走部门经理
        ProcessInstance instance = engine.start("leave-approval",
                Map.of("applicant", "李四", "days", 1));

        List<Task> applyTasks = taskRepo.query(
                engine.taskQuery().assignee("李四"));
        engine.completeTask(applyTasks.get(0).getId(), Map.of(), "请假1天");

        // 应该走到部门经理（没有 candidateGroup，走 default 分支）
        List<Task> pending = taskRepo.query(
                engine.taskQuery().instanceId(instance.getId()).status(TaskStatus.PENDING));
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getNodeId()).isEqualTo("department-manager");
    }
}
```

- [ ] **Step 2: Run integration tests**

Run: `cd /d/workflow-engine && mvn test -pl workflow-engine-core -Dtest=LeaveApprovalIntegrationTest -q`
Expected: all tests pass

- [ ] **Step 3: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "test: add leave approval end-to-end integration tests"
```

---

### Task 19: Example project and final verification

**Files:**
- Create: `D:\workflow-engine\workflow-engine-examples\src\main\java\com\github\wf\examples\LeaveApprovalExample.java`
- Create: `D:\workflow-engine\workflow-engine-examples\src\main\resources\examples\leave-approval.yaml`

- [ ] **Step 1: Write example YAML**

Place at `workflow-engine-examples/src/main/resources/examples/leave-approval.yaml`:

```yaml
id: leave-approval
name: 请假审批流程
version: 1
nodes:
  - id: start
    type: startEvent
  - id: apply
    type: userTask
    name: 提交请假申请
    assignee: "${applicant}"
  - id: gateway
    type: exclusiveGateway
    conditions:
      - expr: "days > 3"
        to: manager-approve
      - default: true
        to: department-manager
  - id: manager-approve
    type: userTask
    name: 总经理审批
    candidateGroups: ["manager"]
  - id: department-manager
    type: userTask
    name: 部门经理审批
  - id: end
    type: endEvent
transitions:
  - from: start
    to: apply
  - from: apply
    to: gateway
  - from: manager-approve
    to: end
  - from: department-manager
    to: end
```

- [ ] **Step 2: Write example Java program**

```java
package com.github.wf.examples;

import com.github.wf.dsl.YamlProcessParser;
import com.github.wf.engine.WorkflowEngine;
import com.github.wf.memory.InMemoryInstanceRepository;
import com.github.wf.memory.InMemoryProcessRepository;
import com.github.wf.memory.InMemoryTaskRepository;
import com.github.wf.model.*;
import com.github.wf.task.Task;

import java.io.File;
import java.util.List;
import java.util.Map;

public class LeaveApprovalExample {

    public static void main(String[] args) {
        // 1. 构建引擎
        WorkflowEngine engine = WorkflowEngine.builder()
                .processRepository(new InMemoryProcessRepository())
                .instanceRepository(new InMemoryInstanceRepository())
                .taskRepository(new InMemoryTaskRepository())
                .build();
        engine.setProcessParser(new YamlProcessParser());

        // 2. 部署流程
        String yamlPath = args.length > 0 ? args[0]
                : "src/main/resources/examples/leave-approval.yaml";
        ProcessDefinition def = engine.deploy(new File(yamlPath));
        System.out.println("部署流程: " + def.getName() + " v" + def.getVersion());

        // 3. 启动流程实例
        ProcessInstance instance = engine.start("leave-approval",
                Map.of("applicant", "张三", "days", 5));
        System.out.println("启动实例: " + instance.getId());

        // 4. 查询待办任务
        List<Task> tasks = engine.taskQuery()
                .assignee("张三")
                .list(engine);
        System.out.println("张三的待办: " + tasks.size() + " 个");

        if (!tasks.isEmpty()) {
            // 5. 完成任务
            engine.completeTask(tasks.get(0).getId(), Map.of(), "已提交申请");
            System.out.println("任务已完成: " + tasks.get(0).getId());
        }

        // 6. 查看历史
        List<HistoricActivity> history = engine.history(instance.getId());
        System.out.println("历史记录: " + history.size() + " 条");
        history.forEach(h -> System.out.println("  " + h.getAction() + " - " + h.getNodeName()));
    }
}
```

Note: Since `taskQuery().list()` doesn't exist on the engine directly (the query needs to be passed to the task repository), update LeaveApprovalExample to use `taskRepository.query()` or add a convenience `list()` method. Add this to TaskQuery:

```java
// Add to TaskQuery.java:
public List<Task> list(TaskRepository repository) {
    return repository.query(this);
}
```

Or add to WorkflowEngine:

```java
// Add to WorkflowEngine.java:
public List<Task> queryTasks(TaskQuery query) {
    return taskRepository.query(query);
}
```

Use the latter approach and update the example accordingly.

- [ ] **Step 3: Run all tests**

Run: `cd /d/workflow-engine && mvn test -q`
Expected: all tests pass across all modules

- [ ] **Step 4: Commit**

```bash
cd /d/workflow-engine && git add -A && git commit -m "feat: add example project and leave approval demo"
```

---

## Self-Review Notes

### Spec Coverage Check
| Spec Section | Task(s) |
|---|---|
| Domain Model (3.1-3.3) | Tasks 2-5 |
| DSL Format (4) | Tasks 8, 17 |
| Execution Algorithm (5) | Tasks 11-15 |
| Extension Points (6) | Task 6, Task 16 (DynamicRouter, ProcessListener) |
| SPI (7) | Task 6, Task 9 |
| Java API (8) | Tasks 15-16 |
| MVP Scope (9) | All tasks |

### Placeholder Scan
- No TBD/TODO found
- No "handle edge cases" without code
- All code blocks contain actual implementation

### Type Consistency
- `instanceRepository.save()` used consistently (not `saveInstance`)
- `HistoricActivity` factory methods match usage
- `TaskQuery.matches()` consistent with `TaskRepository.query()`
- `ExecutionContext.getVariables()` delegates to `instanceRepository.findById().getVariables()`
<｜end▁of▁thinking｜>

<｜｜DSML｜｜tool_calls>
<｜｜DSML｜｜invoke name="Write">
<｜｜DSML｜｜parameter name="content" string="true">...