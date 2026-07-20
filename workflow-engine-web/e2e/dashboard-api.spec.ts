import { test, expect, workflows } from './fixtures';

test.describe('Dashboard API', () => {
  test('get global stats', async ({ api }) => {
    const stats = await api.getDashboardStats();
    expect(stats).toHaveProperty('total');
    expect(stats).toHaveProperty('running');
    expect(stats).toHaveProperty('completed');
    expect(stats).toHaveProperty('suspended');
    expect(stats).toHaveProperty('pendingTasks');
  });

  test('get stats filtered by definitionId', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const stats = await api.getDashboardStats(def.id);
    expect(stats).toHaveProperty('total');
    expect(stats.total).toBeGreaterThanOrEqual(1);
  });

  test('stats include pending task count', async ({ api }) => {
    const stats = await api.getDashboardStats();
    expect(stats).toHaveProperty('pendingTasks');
    expect(typeof stats.pendingTasks).toBe('number');
  });

  test('get instance timeline', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    // Complete the task to generate history
    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      await api.completeTask(tasks[0].id);
    }
    const timeline = await api.getTimeline(inst.id);
    expect(Array.isArray(timeline)).toBe(true);
    if (timeline.length > 0) {
      expect(timeline[0]).toHaveProperty('nodeId');
      expect(timeline[0]).toHaveProperty('action');
      expect(timeline[0]).toHaveProperty('time');
    }
  });

  test('timeline duration calculation', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const tasks = await api.listTasks({ assignee: 'reviewer' });
    if (tasks.length > 0) {
      await api.completeTask(tasks[0].id);
    }
    const timeline = await api.getTimeline(inst.id);
    if (timeline.length >= 2) {
      // At least one step should have durationMs
      const withDuration = timeline.filter((s: any) => s.durationMs != null);
      expect(withDuration.length).toBeGreaterThan(0);
    }
  });
});
