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
