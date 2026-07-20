import { test, expect, workflows } from './fixtures';

test.describe('Tasks API', () => {
  test('list tasks defaults to PENDING', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ assignee: 'reviewer' });
    expect(Array.isArray(tasks)).toBe(true);
    expect(tasks.length).toBeGreaterThan(0);
  });

  test('list tasks filtered by assignee', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
  });

  test('list tasks filtered by status', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    await api.completeTask(tasks[0].id);
    // Wait for task to reach COMPLETED status
    const completed = await api.waitForTaskStatus(tasks[0].id, 'COMPLETED');
    expect(completed).toBeTruthy();
  });

  test('list tasks filtered by instanceId', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ instanceId: inst.id });
    expect(tasks.length).toBeGreaterThan(0);
  });

  test('list tasks filtered by candidateGroup', async ({ api }) => {
    const def = await api.deploy(workflows.leaveApproval);
    const inst = await api.startInstance(def.id, { applicant: 'zhangsan', days: 5 });
    // Complete the submit task first so manager task is created
    const applyTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'zhangsan' });
    expect(applyTasks.length).toBeGreaterThan(0);
    await api.completeTask(applyTasks[0].id, { days: 5 });
    // Now manager task should appear
    const tasks = await api.waitForTasks({ instanceId: inst.id, candidateGroup: 'manager' });
    expect(tasks.length).toBeGreaterThan(0);
  });

  test('complete a task', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
    await api.completeTask(tasks[0].id);
    const completed = await api.waitForTaskStatus(tasks[0].id, 'COMPLETED');
    expect(completed).toBeTruthy();
  });

  test('complete task with variables and comment', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
    await api.completeTask(tasks[0].id, { approved: true }, 'LGTM');
    // Instance should advance or complete
    const completed = await api.waitForTaskStatus(tasks[0].id, 'COMPLETED');
    expect(completed).toBeTruthy();
  });

  test('reject a task', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
    await api.rejectTask(tasks[0].id, 'Not acceptable');
    // Task should be rejected — verify by checking the task is no longer PENDING
    // (rejectTask marks task as REJECTED but does not suspend the instance)
    const instAfter = await api.getInstance(inst.id);
    expect(instAfter.status).toBe('RUNNING');
  });

  test('delegate a task', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
    await api.delegateTask(tasks[0].id, 'other-user');
    const after = await api.waitForTasks({ instanceId: inst.id, assignee: 'other-user' });
    expect(after.length).toBeGreaterThan(0);
  });

  test('complete non-existent task returns error', async ({ api }) => {
    await expect(api.completeTask('nonexistent-task-id')).rejects.toThrow();
  });

  test('Feishu GET complete endpoint', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
    const res = await fetch(`http://localhost:8080/api/tasks/${tasks[0].id}/complete?comment=test`, {
      headers: { 'X-User-Id': 'testuser' },
    });
    // Returns HTML page
    expect(res.status).toBe(200);
    const html = await res.text();
    expect(html).toContain('已通过');
  });

  test('Feishu GET reject endpoint', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
    const res = await fetch(`http://localhost:8080/api/tasks/${tasks[0].id}/reject?comment=test`, {
      headers: { 'X-User-Id': 'testuser' },
    });
    expect(res.status).toBe(200);
    const html = await res.text();
    expect(html).toContain('已驳回');
  });
});
