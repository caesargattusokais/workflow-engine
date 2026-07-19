package com.github.wf.spi;

import com.github.wf.model.ProcessDefinition;
import java.util.List;

public interface ProcessRepository {
    void save(ProcessDefinition definition);
    ProcessDefinition findById(String id);
    ProcessDefinition findLatestById(String id);
    ProcessDefinition findByIdAndVersion(String id, int version);
    List<ProcessDefinition> findAllVersions(String id);
}
