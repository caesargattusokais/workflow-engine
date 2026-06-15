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
                List.of(new EndEvent("end")), List.of());
        assertThatThrownBy(def::getStartNode).isInstanceOf(IllegalStateException.class);
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
