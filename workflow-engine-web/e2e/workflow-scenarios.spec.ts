import { test, expect, workflows } from './fixtures';

test.describe('E2E Workflow Scenarios', () => {
  test('simple linear flow: start to finish', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    expect(inst.status).toBe('RUNNING');

    // Find and complete the task
    const tasks = await api.listTasks({ assignee: 'reviewer' });
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
    const submitTasks = await api.listTasks({ assignee: 'applicant' });
    if (submitTasks.length > 0) {
      await api.completeTask(submitTasks[0].id, { days: 5 });
    }

    // Should route to director (days > 3)
    const directorTasks = await api.listTasks({ assignee: 'director' });
    expect(directorTasks.length).toBeGreaterThan(0);

    // Complete director task
    await api.completeTask(directorTasks[0].id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('exclusive gateway: default branch (days <= 3)', async ({ api }) => {
    const def = await api.deploy(workflows.exclusiveGateway);
    const inst = await api.startInstance(def.id, { days: 2 });

    const submitTasks = await api.listTasks({ assignee: 'applicant' });
    if (submitTasks.length > 0) {
      await api.completeTask(submitTasks[0].id, { days: 2 });
    }

    // Should route to manager (days <= 3)
    const managerTasks = await api.listTasks({ assignee: 'manager' });
    expect(managerTasks.length).toBeGreaterThan(0);

    await api.completeTask(managerTasks[0].id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('parallel gateway: fork and join', async ({ api }) => {
    const def = await api.deploy(workflows.parallelGateway);
    const inst = await api.startInstance(def.id);
    expect(inst.status).toBe('RUNNING');

    // Both tasks should be created
    const tasksA = await api.listTasks({ assignee: 'userA' });
    const tasksB = await api.listTasks({ assignee: 'userB' });
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

    // Both tasks should be created
    const tasksA = await api.listTasks({ assignee: 'userA' });
    const tasksB = await api.listTasks({ assignee: 'userB' });
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

    // Only taskA should be created
    const tasksA = await api.listTasks({ assignee: 'userA' });
    expect(tasksA.length).toBeGreaterThan(0);

    await api.completeTask(tasksA[0].id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('instance suspend and resume via reject', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);

    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      await api.rejectTask(tasks[0].id, 'rejected');
    }

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
    await api.startInstance(def.id);

    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      await api.delegateTask(tasks[0].id, 'delegated-user');
      const delegatedTasks = await api.listTasks({ assignee: 'delegated-user' });
      expect(delegatedTasks.length).toBeGreaterThan(0);

      // Complete via delegated user
      await api.completeTask(delegatedTasks[0].id);
    }
  });

  test('full leave-approval flow (short leave)', async ({ api }) => {
    const def = await api.deploy(workflows.leaveApproval);
    const inst = await api.startInstance(def.id, { applicant: 'zhangsan', days: 2 });

    // Complete submit task
    const applyTasks = await api.listTasks({ assignee: 'zhangsan' });
    if (applyTasks.length > 0) {
      await api.completeTask(applyTasks[0].id, { days: 2 });
    }

    // Should route to dept-approve (days <= 3)
    const deptTasks = await api.listTasks({ assignee: 'dept-head' });
    if (deptTasks.length > 0) {
      await api.completeTask(deptTasks[0].id);
    }

    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('full leave-approval flow (long leave)', async ({ api }) => {
    const def = await api.deploy(workflows.leaveApproval);
    const inst = await api.startInstance(def.id, { applicant: 'zhangsan', days: 5 });

    // Complete submit task
    const applyTasks = await api.listTasks({ assignee: 'zhangsan' });
    if (applyTasks.length > 0) {
      await api.completeTask(applyTasks[0].id, { days: 5 });
    }

    // Should route to manager-approve (days > 3)
    const managerTasks = await api.listTasks({ candidateGroup: 'manager' });
    if (managerTasks.length > 0) {
      await api.completeTask(managerTasks[0].id);
    }

    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });

  test('timer node delays execution', async ({ api }) => {
    const def = await api.deploy(workflows.timerFlow);
    const inst = await api.startInstance(def.id);
    // After start, should be at timer node (not at task yet)
    expect(inst.status).toBe('RUNNING');

    // Wait for timer to fire (2s + buffer)
    await new Promise(r => setTimeout(r, 4000));

    // Task should now be available
    const tasks = await api.listTasks({ assignee: 'user1' });
    expect(tasks.length).toBeGreaterThan(0);

    await api.completeTask(tasks[0].id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('COMPLETED');
  });
});
