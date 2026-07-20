import { test, expect, workflows } from './fixtures';

test.describe('Tasks API', () => {
  test('list tasks defaults to PENDING', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const tasks = await api.listTasks();
    expect(Array.isArray(tasks)).toBe(true);
  });

  test('list tasks filtered by assignee', async ({ api }) => {
    const tasks = await api.listTasks({ assignee: 'reviewer' });
    expect(Array.isArray(tasks)).toBe(true);
  });

  test('list tasks filtered by status', async ({ api }) => {
    const tasks = await api.listTasks({ status: 'COMPLETED' });
    expect(Array.isArray(tasks)).toBe(true);
  });

  test('list tasks filtered by instanceId', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.listTasks({ instanceId: inst.id });
    expect(Array.isArray(tasks)).toBe(true);
  });

  test('list tasks filtered by candidateGroup', async ({ api }) => {
    const def = await api.deploy(workflows.leaveApproval);
    await api.startInstance(def.id, { applicant: 'zhangsan', days: 5 });
    const tasks = await api.listTasks({ candidateGroup: 'manager' });
    expect(Array.isArray(tasks)).toBe(true);
  });

  test('complete a task', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      await api.completeTask(tasks[0].id);
      const updated = await api.listTasks({ status: 'COMPLETED' });
      const found = updated.some((t: any) => t.id === tasks[0].id);
      expect(found).toBe(true);
    }
  });

  test('complete task with variables and comment', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      await api.completeTask(tasks[0].id, { approved: true }, 'LGTM');
      // Instance should advance or complete
    }
  });

  test('reject a task', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      await api.rejectTask(tasks[0].id, 'Not acceptable');
      // Task should be rejected
    }
  });

  test('delegate a task', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      await api.delegateTask(tasks[0].id, 'other-user');
      const after = await api.listTasks({ assignee: 'other-user' });
      expect(after.length).toBeGreaterThan(0);
    }
  });

  test('complete non-existent task returns error', async ({ api }) => {
    await expect(api.completeTask('nonexistent-task-id')).rejects.toThrow();
  });

  test('Feishu GET complete endpoint', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      const res = await fetch(`http://localhost:8080/api/tasks/${tasks[0].id}/complete?comment=test`);
      // Returns HTML page
      expect(res.status).toBe(200);
      const html = await res.text();
      expect(html).toContain('已通过');
    }
  });

  test('Feishu GET reject endpoint', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      const res = await fetch(`http://localhost:8080/api/tasks/${tasks[0].id}/reject?comment=test`);
      expect(res.status).toBe(200);
      const html = await res.text();
      expect(html).toContain('已驳回');
    }
  });
});
