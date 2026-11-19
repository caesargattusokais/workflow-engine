import { test, expect } from './fixtures';

/**
 * The designer → engine contract.
 *
 * Every other AI spec deploys hand-written YAML, so a renamed field between
 * graphToYaml() (front end) and YamlProcessParser (back end) would slip through.
 * This one builds nodes in the exact shape the canvas produces, exports them
 * with the REAL front-end function, then deploys that YAML.
 */
test('designer-generated AI_TASK YAML deploys, runs and routes', async ({ api, page }) => {
  await page.goto('/');

  const yaml = await page.evaluate(async () => {
    // Variable path keeps tsc from trying to resolve a browser-side module;
    // Vite serves the real TS function to the page.
    const modPath = '/src/designer/graphToYaml.ts';
    const { graphToYaml } = await import(/* @vite-ignore */ modPath);
    const node = (id: string, type: string, data: Record<string, unknown>) => ({
      id, type, position: { x: 0, y: 0 }, data,
    });
    const nodes = [
      node('start', 'startEvent', { name: 'start' }),
      node('classify', 'aiTask', {
        name: 'AI 分类',
        mode: 'classify',
        prompt: 'Classify leave request: days=${days}',
        options: ['SHORT', 'LONG'],
        outputVar: 'aiResult',
      }),
      node('manager', 'userTask', { name: 'Manager', assignee: 'manager' }),
      node('director', 'userTask', { name: 'Director', assignee: 'director' }),
      node('end', 'endEvent', { name: 'end' }),
    ];
    const edges = [
      { id: 'e1', source: 'start', target: 'classify', data: { edgeType: 'direct' } },
      { id: 'e2', source: 'classify', target: 'director', data: { edgeType: 'result', expr: "classify_category == 'LONG'" } },
      { id: 'e3', source: 'classify', target: 'manager', data: { edgeType: 'result' } },
      { id: 'e4', source: 'director', target: 'end', data: { edgeType: 'direct' } },
      { id: 'e5', source: 'manager', target: 'end', data: { edgeType: 'direct' } },
    ];
    return graphToYaml(nodes as any, edges as any, 'designer-ai-contract', 'Designer AI Contract', 1);
  });

  // Front-end export must carry every field the back-end parser reads.
  expect(yaml).toContain('type: aiTask');
  expect(yaml).toContain('mode: classify');
  expect(yaml).toContain('outputVar: aiResult');
  expect(yaml).toContain('options:');

  const def = await api.deploy(yaml);

  // Back-end graph view must hand the same fields back to designer/monitor.
  const graph = await api.getDefinitionGraph(def.id);
  const aiNode = graph.nodes.find((n: any) => n.id === 'classify');
  expect(aiNode).toBeTruthy();
  expect(aiNode.type).toBe('aiTask');
  expect(aiNode.data.mode).toBe('classify');
  expect(aiNode.data.outputVar).toBe('aiResult');
  expect(aiNode.data.options).toEqual(['SHORT', 'LONG']);

  // Runtime: the AI result must be visible to the gateway's SpEL condition.
  const inst = await api.startInstance(def.id, { days: 7 });
  const directorTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'director' }, 10000);
  expect(directorTasks.length).toBeGreaterThan(0);
  expect((await api.listTasks({ instanceId: inst.id, assignee: 'manager' })).length).toBe(0);
  await api.completeTask(directorTasks[0].id);
  expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
});
