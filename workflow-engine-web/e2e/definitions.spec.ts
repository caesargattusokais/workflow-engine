import { test, expect, workflows } from './fixtures';

test.describe('Definitions API', () => {
  test('deploy a valid YAML definition', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    expect(def).toHaveProperty('id');
    expect(def.version).toBe(1);
  });

  test('deploy with canvas positions', async ({ api }) => {
    const positions = { start: { x: 100, y: 50 }, task1: { x: 100, y: 200 }, end: { x: 100, y: 350 } };
    const def = await api.deploy(workflows.simpleLinear, positions);
    expect(def).toHaveProperty('id');
    const graph = await api.getDefinitionGraph(def.id);
    expect(graph.nodes.length).toBe(3);
  });

  test('deploy same YAML is idempotent (version from YAML)', async ({ api }) => {
    // The engine uses the version from the YAML, not auto-increment
    const { workflowTemplates } = await import('./fixtures');
    const uniqueId = `version-test-${Date.now()}`;
    const yaml = workflowTemplates.simpleLinear.replace(/^id: .+/m, `id: ${uniqueId}`);
    const v1 = await api.deploy(yaml);
    const v2 = await api.deploy(yaml);
    // Both deployments return the same version (from YAML)
    expect(v1.version).toBe(1);
    expect(v2.version).toBe(1);
  });

  test('list definitions returns paginated results', async ({ api }) => {
    const result = await api.listDefinitions();
    expect(result).toHaveProperty('items');
    expect(result).toHaveProperty('total');
    expect(result).toHaveProperty('page');
    expect(result).toHaveProperty('size');
  });

  test('get definition by ID', async ({ api }) => {
    const def = await api.deploy(workflows.exclusiveGateway);
    const fetched = await api.getDefinition(def.id);
    expect(fetched.id).toBe(def.id);
  });

  test('get definition graph', async ({ api }) => {
    const def = await api.deploy(workflows.parallelGateway);
    const graph = await api.getDefinitionGraph(def.id);
    expect(graph).toHaveProperty('nodes');
    expect(graph).toHaveProperty('edges');
    expect(graph.nodes.length).toBeGreaterThan(0);
  });

  test('get definition graph with version', async ({ api }) => {
    const { workflowTemplates } = await import('./fixtures');
    const uniqueId = `graph-version-test-${Date.now()}`;
    const yaml = workflowTemplates.simpleLinear.replace(/^id: .+/m, `id: ${uniqueId}`);
    const v1 = await api.deploy(yaml);
    await api.deploy(yaml); // v2
    const graph = await api.getDefinitionGraph(v1.id, 1);
    expect(graph).toHaveProperty('nodes');
  });

  test('delete definition', async ({ api }) => {
    const def = await api.deploy(workflows.simpleLinear);
    await api.deleteDefinition(def.id);
    // Should not be found in user's list anymore
    const result = await api.listDefinitions();
    const found = (result.items as any[]).some((d: any) => d.id === def.id);
    expect(found).toBe(false);
  });

  test('get non-existent definition returns error', async ({ api }) => {
    await expect(api.getDefinition('nonexistent-id')).rejects.toThrow();
  });
});
