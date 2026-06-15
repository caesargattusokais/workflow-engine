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
