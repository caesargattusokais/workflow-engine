package com.github.wf.model.node;

import com.github.wf.model.Node;
import com.github.wf.model.NodeType;
import com.github.wf.model.VariableMapping;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CallActivityNode extends Node {
    private final String calledId;
    private final Integer calledVersion;
    private final List<VariableMapping> inputMapping;
    private final List<VariableMapping> outputMapping;

    public CallActivityNode(String id, String name,
                            String calledId, Integer calledVersion,
                            List<VariableMapping> inputMapping,
                            List<VariableMapping> outputMapping,
                            List<String> listeners) {
        super(id, name, NodeType.CALL_ACTIVITY, listeners);
        this.calledId = Objects.requireNonNull(calledId, "calledId must not be null");
        this.calledVersion = calledVersion;
        this.inputMapping = inputMapping != null
            ? Collections.unmodifiableList(inputMapping)
            : Collections.emptyList();
        this.outputMapping = outputMapping != null
            ? Collections.unmodifiableList(outputMapping)
            : Collections.emptyList();
    }

    public String getCalledId() { return calledId; }
    public Integer getCalledVersion() { return calledVersion; }
    public List<VariableMapping> getInputMapping() { return inputMapping; }
    public List<VariableMapping> getOutputMapping() { return outputMapping; }
}
