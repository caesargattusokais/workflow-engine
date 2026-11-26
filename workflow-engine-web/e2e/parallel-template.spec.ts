import { test, expect } from './fixtures';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { uniqueYamlId } from './fixtures';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * The bundled "三方并行会签" template must demonstrate the parallel join
 * semantics end to end:
 *  - front end imports it with two parallelGateway nodes (fork + join)
 *  - fork spawns three concurrent review tasks
 *  - completing only two branches does NOT unlock the downstream director task
 *  - the last branch to arrive wakes the join and unlocks the director task
 */
const TEMPLATE = fs.readFileSync(
  path.join(__dirname, '../public/templates/parallel-review.yaml'), 'utf-8');

test.describe('Parallel review template', () => {
  test('imports into designer graph with fork and join gateways', async ({ page }) => {
    await page.goto('/');
    const graph = await page.evaluate(async (yaml) => {
      const { yamlToGraph } = await import(/* @vite-ignore */ '/src/designer/yamlToGraph.ts');
      return yamlToGraph(yaml);
    }, TEMPLATE);

    const gateways = graph.nodes.filter((n: any) => n.type === 'parallelGateway');
    expect(gateways).toHaveLength(2);
    expect(graph.nodes.filter((n: any) => n.type === 'userTask')).toHaveLength(5);

    const join = graph.nodes.find((n: any) => n.id === 'join');
    expect(graph.edges.filter((e: any) => e.target === 'join')).toHaveLength(3);
  });

  test('join waits for all three reviews before unlocking the director', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(TEMPLATE));
    const inst = await api.startInstance(def.id, { applicant: 'zhangsan' });

    // Submit step carries the required form fields.
    const submitTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'zhangsan' }, 10000);
    expect(submitTasks.length).toBeGreaterThan(0);
    await api.completeTask(submitTasks[0].id, { reason: '研发部采购测试服务器', amount: 120000 });

    // fork spawns three concurrent review tasks.
    const finance = await api.waitForTasks({ instanceId: inst.id, assignee: 'finance' }, 10000);
    const legal = await api.waitForTasks({ instanceId: inst.id, assignee: 'legal' }, 10000);
    const tech = await api.waitForTasks({ instanceId: inst.id, assignee: 'tech' }, 10000);
    expect(finance).toHaveLength(1);
    expect(legal).toHaveLength(1);
    expect(tech).toHaveLength(1);

    // Director task must not exist yet — the join is still waiting.
    const before = await api.listTasks({ instanceId: inst.id, assignee: 'director' });
    expect(before).toHaveLength(0);
    expect((await api.getInstance(inst.id)).status).toBe('RUNNING');

    // Complete two of three branches. Still no director task.
    await api.completeTask(finance[0].id, { financeOpinion: '预算通过' });
    await api.completeTask(legal[0].id, { legalOpinion: '合同通过' });
    const midway = await api.listTasks({ instanceId: inst.id, assignee: 'director' });
    expect(midway, 'director must stay blocked until all three reviews complete').toHaveLength(0);
    expect((await api.getInstance(inst.id)).status).toBe('RUNNING');

    // Last branch arrives — join fires and wakes the parent execution.
    await api.completeTask(tech[0].id, { techOpinion: '方案可行' });
    const director = await api.waitForTasks({ instanceId: inst.id, assignee: 'director' }, 10000);
    expect(director.length, 'director task must appear after the last review').toBeGreaterThan(0);

    await api.completeTask(director[0].id);
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });
});
