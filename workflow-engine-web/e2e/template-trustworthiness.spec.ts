import { test, expect } from './fixtures';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { uniqueYamlId } from './fixtures';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const load = (f: string) => fs.readFileSync(path.join(__dirname, '../public/templates', f), 'utf-8');

type Api = {
  deploy(yaml: string): Promise<{ id: string }>;
  startInstance(id: string, vars?: Record<string, unknown>): Promise<{ id: string }>;
  getInstance(id: string): Promise<{ status: string }>;
  listTasks(p: Record<string, unknown>): Promise<any[]>;
  waitForTasks(p: Record<string, unknown>, timeout?: number): Promise<any[]>;
  completeTask(id: string, vars?: Record<string, unknown>): Promise<unknown>;
};

async function completeFirst(
  api: Api,
  instanceId: string,
  who: string | { assignee?: string; candidateGroup?: string },
  vars: Record<string, unknown> = {},
) {
  const filter = typeof who === 'string' ? { assignee: who } : who;
  const tasks = await api.waitForTasks({ instanceId, ...filter }, 10000);
  expect(tasks.length, `expected a PENDING task for ${JSON.stringify(filter)}`).toBeGreaterThan(0);
  await api.completeTask(tasks[0].id, vars);
  return tasks[0];
}

async function importGraph(page: any, yaml: string) {
  return page.evaluate(async (y: string) => {
    const { yamlToGraph } = await import(/* @vite-ignore */ '/src/designer/yamlToGraph.ts');
    return yamlToGraph(y);
  }, yaml);
}

/**
 * These tests pin the bundled templates to *runtime* truth. A template that
 * imports cleanly but suspends on a missing handler, routes to a ghost node,
 * or advertises a branch the engine never activates is a bug, not a doc issue.
 */
test.describe('Bundled template trustworthiness', () => {
  const ALL = [
    'leave-approval.yaml', 'expense-claim.yaml', 'purchase-order.yaml',
    'onboarding.yaml', 'contract-review.yaml', 'ai-expense-claim.yaml',
    'parallel-review.yaml', 'inclusive-review.yaml', 'http-integration.yaml',
    'ai-ticket-triage.yaml', 'group-task.yaml',
  ];

  test('every transition target is a declared node in all 11 templates', async ({ page }) => {
    await page.goto('/');
    for (const file of ALL) {
      const graph: any = await importGraph(page, load(file));
      const ids = new Set(graph.nodes.map((n: any) => n.id));
      for (const edge of graph.edges) {
        expect(ids.has(edge.target), `${file}: ${edge.source} -> ${edge.target} has no node`).toBe(true);
        expect(ids.has(edge.source), `${file}: ${edge.source} -> ${edge.target} has no source node`).toBe(true);
      }
    }
  });

  test('no template references a handlerClass absent from the server runtime', async ({ page }) => {
    await page.goto('/');
    for (const file of ALL) {
      const graph: any = await importGraph(page, load(file));
      for (const node of graph.nodes) {
        expect(node.data?.handlerClass, `${file}/${node.id} references an unregistered handler`).toBeUndefined();
      }
    }
  });

  test('boundaryTimer is mounted on the node that has a timeout edge', async ({ page }) => {
    await page.goto('/');
    for (const file of ALL) {
      const graph: any = await importGraph(page, load(file));
      const timed = new Set(graph.nodes.filter((n: any) => n.data?.boundaryTimer).map((n: any) => n.id));
      const timeoutSources = new Set(
        graph.edges.filter((e: any) => e.data?.edgeType === 'timeout').map((e: any) => e.source),
      );
      for (const id of timed) {
        expect(timeoutSources.has(id), `${file}/${id}: timer without timeout edge loops forever`).toBe(true);
      }
    }
  });

  test('expense-claim runs from a real amount form to END', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('expense-claim.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'p2-expense' });
    await completeFirst(api as unknown as Api, inst.id, 'p2-expense', { amount: 800 });
    await completeFirst(api as unknown as Api, inst.id, 'manager');
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });

  test('contract-review runs past the old ghost escalation node to END', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('contract-review.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'p2-contract' });
    await completeFirst(api as unknown as Api, inst.id, 'p2-contract', { amount: 50000 });
    await completeFirst(api as unknown as Api, inst.id, 'legal');
    await completeFirst(api as unknown as Api, inst.id, 'admin');
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });

  test('leave-approval runs from a real days form to END', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('leave-approval.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'p2-leave' });
    await completeFirst(api as unknown as Api, inst.id, 'p2-leave', { days: 2 });
    await completeFirst(api as unknown as Api, inst.id, 'manager');
    await completeFirst(api as unknown as Api, inst.id, 'hr');
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });

  test('purchase-order activates BOTH finance and legal tasks', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('purchase-order.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'p2-po' });
    await completeFirst(api as unknown as Api, inst.id, 'p2-po', { amount: 3000 });
    await completeFirst(api as unknown as Api, inst.id, 'manager');
    await completeFirst(api as unknown as Api, inst.id, 'finance');
    // The old two-in/two-out "parallel" gateway only produced finance; legal must exist.
    await completeFirst(api as unknown as Api, inst.id, 'legal');
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });

  test('group-task runs to END with candidate groups and no runaway timer', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('group-task.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'p2-group' });
    await completeFirst(api as unknown as Api, inst.id, 'p2-group', { title: '连接池耗尽', severity: '严重' });
    await completeFirst(api as unknown as Api, inst.id, { candidateGroup: 'ops' }, { diagnosis: '已扩容' });
    await completeFirst(api as unknown as Api, inst.id, { candidateGroup: 'qa' });
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });

  test('HTTP result edge really lands on ok, not the admin-named degraded node', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('http-integration.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'p2-http' });
    await completeFirst(api as unknown as Api, inst.id, 'p2-http', { systemId: 'order-service' });

    const adminTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'admin' }, 15000);
    const ok = adminTasks.find((t: any) => t.nodeId === 'ok');
    expect(ok, 'probe_result.enabled=true must route via result edge to node ok').toBeTruthy();
    expect(adminTasks.some((t: any) => t.nodeId === 'degraded')).toBe(false);
    await api.completeTask(ok.id);
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });

  test('AI triage routes stub output to a declared P0/P2 branch', async ({ api }) => {
    const def = await api.deploy(uniqueYamlId(load('ai-ticket-triage.yaml')));
    const inst = await api.startInstance(def.id, { applicant: 'p2-ai' });
    await completeFirst(api as unknown as Api, inst.id, 'p2-ai', { description: '支付接口故障已持续 7 小时' });

    const oncall = await api.waitForTasks({ instanceId: inst.id, assignee: 'oncall' }, 15000);
    expect(oncall.length).toBeGreaterThan(0);
    expect(oncall[0].nodeId).toBe('p0');
    await api.completeTask(oncall[0].id);
    expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
  });
});
