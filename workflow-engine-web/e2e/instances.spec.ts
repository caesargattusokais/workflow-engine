import { test, expect, workflows } from './fixtures';

test.describe('Instances API', () => {
  test('start an instance from definition', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    expect(inst).toHaveProperty('id');
    expect(inst.status).toBe('RUNNING');
  });

  test('start instance with variables', async ({ api }) => {
    const def = await api.deploy(workflows.exclusiveGateway);
    const inst = await api.startInstance(def.id, { days: 5, applicant: 'zhangsan' });
    expect(inst.status).toBe('RUNNING');
    expect(inst.variables).toHaveProperty('days');
    expect(inst.variables).toHaveProperty('_userId');
  });

  test('list instances', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const result = await api.listInstances();
    expect(result).toHaveProperty('items');
    expect(result.items.length).toBeGreaterThan(0);
  });

  test('list instances filtered by status', async ({ api }) => {
    const result = await api.listInstances(1, 10, undefined, 'RUNNING');
    expect(result).toHaveProperty('items');
    for (const inst of result.items as any[]) {
      expect(inst.status).toBe('RUNNING');
    }
  });

  test('list instances filtered by definitionId', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    const result = await api.listInstances(1, 10, def.id);
    expect(result).toHaveProperty('items');
  });

  test('list instances with pagination', async ({ api }) => {
    const result = await api.listInstances(1, 2);
    expect(result).toHaveProperty('page', 1);
    expect(result).toHaveProperty('size', 2);
  });

  test('get instance detail', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const detail = await api.getInstance(inst.id);
    expect(detail.id).toBe(inst.id);
    expect(detail).toHaveProperty('activeNodeIds');
  });

  test('terminate a running instance', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    await api.terminateInstance(inst.id);
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('TERMINATED');
  });

  test('terminate with reason', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    await api.terminateInstance(inst.id, 'cancelled by test');
    const updated = await api.getInstance(inst.id);
    expect(updated.status).toBe('TERMINATED');
  });

  test('delete a completed instance', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    await api.terminateInstance(inst.id);
    await api.deleteInstance(inst.id);
    await expect(api.getInstance(inst.id)).rejects.toThrow();
  });

  test('delete a running instance fails', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    await expect(api.deleteInstance(inst.id)).rejects.toThrow();
  });

  test('get instance history', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);
    const history = await api.getInstanceHistory(inst.id);
    expect(Array.isArray(history)).toBe(true);
    expect(history.length).toBeGreaterThan(0);
  });

  test('trigger manual recovery', async ({ api }) => {
    const result = await api.recover();
    expect(result.status).toBe('ok');
  });

  test('instance summary endpoint', async ({ api }) => {
    const summary = await api.getInstanceSummary();
    expect(typeof summary).toBe('object');
  });
});
