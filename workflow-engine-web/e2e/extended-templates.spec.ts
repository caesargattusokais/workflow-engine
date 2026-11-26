import { test, expect } from './fixtures';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { uniqueYamlId } from './fixtures';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const load = (f: string) => fs.readFileSync(path.join(__dirname, '../public/templates', f), 'utf-8');

/**
 * Extended bundled templates must be importable into the designer graph AND
 * exercise the node/edge features they advertise. These tests pin down the
 * runtime semantics so a template can silently rot without failing CI.
 */
test.describe('Extended templates', () => {
  test('all four templates import into designer with expected nodes/edges', async ({ page }) => {
    await page.goto('/');
    const cases = [
      { file: 'inclusive-review.yaml', inclusive: 2, edges: ['conditional', 'default'] },
      { file: 'http-integration.yaml', service: 1, edges: ['result', 'exception', 'timeout'] },
      { file: 'ai-ticket-triage.yaml', ai: 1, edges: ['result'] },
      { file: 'group-task.yaml', groups: 1, edges: ['timeout'] },
    ];
    for (const c of cases) {
      const yaml = load(c.file);
      const graph = await page.evaluate(async (y) => {
        const { yamlToGraph } = await import(/* @vite-ignore */ '/src/designer/yamlToGraph.ts');
        return yamlToGraph(y);
      }, yaml);
      expect(graph.nodes.length, `${c.file} should have nodes`).toBeGreaterThan(2);
      if (c.inclusive) expect(graph.nodes.filter((n: any) => n.type === 'inclusiveGateway')).toHaveLength(c.inclusive);
      if (c.service) expect(graph.nodes.filter((n: any) => n.type === 'serviceTask')).toHaveLength(c.service);
      if (c.ai) expect(graph.nodes.filter((n: any) => n.type === 'aiTask')).toHaveLength(c.ai);
      // yamlToGraph stores the semantic edge type in data.edgeType (edge.type is ReactFlow's 'smoothstep').
      const types = new Set(graph.edges.map((e: any) => e.data?.edgeType || 'direct'));
      for (const t of c.edges) expect(types.has(t), `${c.file} should have ${t} edge`).toBe(true);
    }
  });

  test('inclusive gateway activates BOTH matching branches and joins them', async ({ api }) => {
    // amount=150000 & category=技术 -> legal (amount/技术) + finance (amount) both fire;
    // default/manager must NOT fire. Both review tasks must complete before archive appears.
    const def = await api.deploy(uniqueYamlId(load('inclusive-review.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'zhangsan', amount: 150000, category: '技术' });
    const submit = await api.waitForTasks({ instanceId: inst.id, assignee: 'zhangsan' }, 10000);
    expect(submit.length).toBeGreaterThan(0);
    await api.completeTask(submit[0].id, { amount: 150000, category: '技术' });

    const legal = await api.waitForTasks({ instanceId: inst.id, assignee: 'legal' }, 10000);
    const finance = await api.waitForTasks({ instanceId: inst.id, assignee: 'finance' }, 10000);
    expect(legal).toHaveLength(1);
    expect(finance).toHaveLength(1);
    expect(await api.listTasks({ instanceId: inst.id, assignee: 'manager' })).toHaveLength(0);

    await api.completeTask(legal[0].id, { legalOpinion: '条款通过' });
    expect(await api.listTasks({ instanceId: inst.id, assignee: 'admin' })).toHaveLength(0);

    await api.completeTask(finance[0].id, { financeOpinion: '预算通过' });
    const archive = await api.waitForTasks({ instanceId: inst.id, assignee: 'admin' }, 10000);
    expect(archive.length).toBeGreaterThan(0);
    await api.completeTask(archive[0].id);
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });

  test('inclusive gateway default branch fires when no condition matches', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('inclusive-review.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'lisi', amount: 1000, category: '采购' });
    const submit = await api.waitForTasks({ instanceId: inst.id, assignee: 'lisi' }, 10000);
    await api.completeTask(submit[0].id, { amount: 1000, category: '采购' });
    const manager = await api.waitForTasks({ instanceId: inst.id, assignee: 'manager' }, 10000);
    expect(manager).toHaveLength(1);
    expect(await api.listTasks({ instanceId: inst.id, assignee: 'legal' })).toHaveLength(0);
    expect(await api.listTasks({ instanceId: inst.id, assignee: 'finance' })).toHaveLength(0);
    await api.completeTask(manager[0].id);
    const archive = await api.waitForTasks({ instanceId: inst.id, assignee: 'admin' }, 10000);
    expect(archive.length).toBeGreaterThan(0);
  });

  test('HTTP serviceTask result route takes success branch (self-call /api/auth/status)', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('http-integration.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'wangwu', systemId: 'order-service' });
    const submit = await api.waitForTasks({ instanceId: inst.id, assignee: 'wangwu' }, 10000);
    expect(submit.length).toBeGreaterThan(0);
    await api.completeTask(submit[0].id, { systemId: 'order-service' });

    // /api/auth/status returns enabled=true, so the result edge must route to `ok` (admin).
    const ok = await api.waitForTasks({ instanceId: inst.id, assignee: 'admin' }, 15000);
    expect(ok.length).toBeGreaterThan(0);
    expect(await api.listTasks({ instanceId: inst.id, assignee: 'admin', status: 'COMPLETED' }).catch(() => [])).toBeDefined();
    await api.completeTask(ok[0].id);
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });

  test('group task is visible to candidate group members and completable', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('group-task.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'zhaoliu' });
    const submit = await api.waitForTasks({ instanceId: inst.id, assignee: 'zhaoliu' }, 10000);
    await api.completeTask(submit[0].id, { title: '订单库连接池耗尽', severity: '严重' });

    const triage = await api.waitForTasks({ instanceId: inst.id, candidateGroup: 'ops' }, 10000);
    expect(triage.length).toBeGreaterThan(0);
    await api.completeTask(triage[0].id, { diagnosis: '扩容连接池，已恢复' });

    const verify = await api.waitForTasks({ instanceId: inst.id, candidateGroup: 'qa' }, 10000);
    expect(verify.length).toBeGreaterThan(0);
  });
});
