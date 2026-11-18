package com.github.wf.server.config;

import com.github.wf.engine.WorkflowEngine;
import com.github.wf.model.ProcessInstance;
import com.github.wf.server.web.NotFoundException;
import com.github.wf.task.Task;

/**
 * Multi-tenant access guard for instance/task endpoints.
 *
 * All checks throw {@link NotFoundException} for BOTH missing
 * and foreign resources, so a caller cannot distinguish "does not exist" from
 * "belongs to someone else" (no existence oracle).
 */
public final class TenantGuard {

    private TenantGuard() {}

    /**
     * Loads the instance and verifies it belongs to {@code userId}
     * (via the {@code _userId} instance variable written at start time).
     */
    public static ProcessInstance requireOwnedInstance(WorkflowEngine engine, String userId, String instanceId) {
        ProcessInstance inst = engine.instanceRepository.findById(instanceId);
        if (inst == null || !userId.equals(inst.getVariable("_userId"))) {
            throw new NotFoundException("Not found: " + instanceId);
        }
        return inst;
    }

    /**
     * Verifies the caller may operate on the task: either the task assignee
     * or the owner of the parent instance.
     */
    public static void requireTaskAccess(WorkflowEngine engine, String userId, String taskId) {
        Task task = engine.taskRepository.findById(taskId);
        if (task == null) {
            throw new NotFoundException("Not found: " + taskId);
        }
        if (userId.equals(task.getAssignee())) {
            return;
        }
        ProcessInstance inst = engine.instanceRepository.findById(task.getInstanceId());
        if (inst == null || !userId.equals(inst.getVariable("_userId"))) {
            throw new NotFoundException("Not found: " + taskId);
        }
    }
}
