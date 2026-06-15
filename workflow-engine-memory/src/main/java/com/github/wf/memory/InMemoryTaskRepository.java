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
    public void save(Task task) { store.put(task.getId(), task); }

    @Override
    public Task findById(String id) { return store.get(id); }

    @Override
    public void update(Task task) { store.put(task.getId(), task); }

    @Override
    public List<Task> query(TaskQuery query) {
        return store.values().stream()
                .filter(query::matches)
                .collect(Collectors.toList());
    }
}
