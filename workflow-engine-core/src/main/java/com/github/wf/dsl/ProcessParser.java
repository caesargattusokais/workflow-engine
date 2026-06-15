package com.github.wf.dsl;

import com.github.wf.model.ProcessDefinition;
import java.io.Reader;

public interface ProcessParser {
    ProcessDefinition parse(Reader reader);
    ProcessDefinition parse(String yaml);
}
