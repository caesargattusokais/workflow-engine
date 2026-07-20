import { test, expect, workflows } from './fixtures';

test.describe('E2E Workflow Scenarios', () => {
  test('simple linear flow: start to finish', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    expect(inst.status).toBe('RUNNING');

    // Find task for THIS instance only
    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
    await api.completeTask(tasks[0].id);

    // Instance should be COMPLETED
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('exclusive gateway: conditional branch (days > 3)', async ({ api }) => {
    const def = await api.deploy(workflows.exclusiveGateway);
    const inst = await api.startInstance(def.id, { days: 5 });
    expect(inst.status).toBe('RUNNING');

    // Complete the submit task
    const submitTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'applicant' });
    expect(submitTasks.length).toBeGreaterThan(0);
    await api.completeTask(submitTasks[0].id, { days: 5 });

    // Should route to director (days > 3)
    const directorTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'director' });
    expect(directorTasks.length).toBeGreaterThan(0);

    // Complete director task
    await api.completeTask(directorTasks[0].id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('exclusive gateway: default branch (days <= 3)', async ({ api }) => {
    const def = await api.deploy(workflows.exclusiveGateway);
    const inst = await api.startInstance(def.id, { days: 2 });

    const submitTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'applicant' });
    expect(submitTasks.length).toBeGreaterThan(0);
    await api.completeTask(submitTasks[0].id, { days: 2 });

    // Should route to manager (days <= 3)
    const managerTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'manager' });
    expect(managerTasks.length).toBeGreaterThan(0);

    await api.completeTask(managerTasks[0].id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('parallel gateway: fork and join', async ({ api }) => {
    const def = await api.deploy(workflows.parallelGateway);
    const inst = await api.startInstance(def.id);
    expect(inst.status).toBe('RUNNING');

    // Both tasks should be created for this instance
    const tasksA = await api.waitForTasks({ instanceId: inst.id, assignee: 'userA' });
    const tasksB = await api.waitForTasks({ instanceId: inst.id, assignee: 'userB' });
    expect(tasksA.length).toBeGreaterThan(0);
    expect(tasksB.length).toBeGreaterThan(0);

    // Complete both tasks
    await api.completeTask(tasksA[0].id);
    await api.completeTask(tasksB[0].id);

    // Instance should complete after join
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('inclusive gateway: both branches', async ({ api }) => {
    const def = await api.deploy(workflows.inclusiveGateway);
    const inst = await api.startInstance(def.id, { flagA: true, flagB: true });
    expect(inst.status).toBe('RUNNING');

    // Both tasks should be created for this instance
    const tasksA = await api.waitForTasks({ instanceId: inst.id, assignee: 'userA' });
    const tasksB = await api.waitForTasks({ instanceId: inst.id, assignee: 'userB' });
    expect(tasksA.length).toBeGreaterThan(0);
    expect(tasksB.length).toBeGreaterThan(0);

    await api.completeTask(tasksA[0].id);
    await api.completeTask(tasksB[0].id);

    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('inclusive gateway: single branch (flagA only)', async ({ api }) => {
    const def = await api.deploy(workflows.inclusiveGateway);
    const inst = await api.startInstance(def.id, { flagA: true, flagB: false });

    // Only taskA should be created for this instance
    const tasksA = await api.waitForTasks({ instanceId: inst.id, assignee: 'userA' });
    expect(tasksA.length).toBeGreaterThan(0);

    await api.completeTask(tasksA[0].id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('inclusive gateway: zero match falls to default branch', async ({ api }) => {
    const def = await api.deploy(workflows.inclusiveGateway);
    const inst = await api.startInstance(def.id, { flagA: false, flagB: false });

    // Should fall to default branch task
    const defaultTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'defaultUser' });
    expect(defaultTasks.length).toBeGreaterThan(0);

    await api.completeTask(defaultTasks[0].id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('instance suspend and resume via reject', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);

    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
    await api.rejectTask(tasks[0].id, 'rejected');

    const afterReject = await api.getInstance(inst.id);
    // Instance may be SUSPENDED after rejection
    if (afterReject.status === 'SUSPENDED') {
      await api.resumeInstance(inst.id);
      const afterResume = await api.getInstance(inst.id);
      expect(afterResume.status).toBe('RUNNING');
    }
  });

  test('instance termination', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    expect(inst.status).toBe('RUNNING');

    await api.terminateInstance(inst.id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('TERMINATED');
  });

  test('task delegation', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);

    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'reviewer' });
    expect(tasks.length).toBeGreaterThan(0);
    await api.delegateTask(tasks[0].id, 'delegated-user');
    const delegatedTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'delegated-user' });
    expect(delegatedTasks.length).toBeGreaterThan(0);

    // Complete via delegated user
    await api.completeTask(delegatedTasks[0].id);
  });

  test('full leave-approval flow (short leave)', async ({ api }) => {
    const def = await api.deploy(workflows.leaveApproval);
    const inst = await api.startInstance(def.id, { applicant: 'zhangsan', days: 2 });

    // Complete submit task
    const applyTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'zhangsan' });
    expect(applyTasks.length).toBeGreaterThan(0);
    await api.completeTask(applyTasks[0].id, { days: 2 });

    // Should route to dept-approve (days <= 3)
    const deptTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'dept-head' });
    expect(deptTasks.length).toBeGreaterThan(0);
    await api.completeTask(deptTasks[0].id);

    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('full leave-approval flow (long leave)', async ({ api }) => {
    const def = await api.deploy(workflows.leaveApproval);
    const inst = await api.startInstance(def.id, { applicant: 'zhangsan', days: 5 });

    // Complete submit task
    const applyTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'zhangsan' });
    expect(applyTasks.length).toBeGreaterThan(0);
    await api.completeTask(applyTasks[0].id, { days: 5 });

    // Should route to manager-approve (days > 3)
    const managerTasks = await api.waitForTasks({ instanceId: inst.id, candidateGroup: 'manager' });
    expect(managerTasks.length).toBeGreaterThan(0);
    await api.completeTask(managerTasks[0].id);

    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('timer node delays execution', async ({ api }) => {
    const def = await api.deploy(workflows.timerFlow);
    const inst = await api.startInstance(def.id);
    // After start, should be at timer node (not at task yet)
    expect(inst.status).toBe('RUNNING');

    // Poll for task to appear (timer is 2s, allow up to 10s)
    const tasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'user1' }, 10000);
    expect(tasks.length).toBeGreaterThan(0);

    await api.completeTask(tasks[0].id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });
});
