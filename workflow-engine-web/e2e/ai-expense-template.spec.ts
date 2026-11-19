import { test, expect } from './fixtures';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { API_BASE, TEST_USER } from './fixtures';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * The bundled AI expense template must work end to end:
 *  - front end can import its YAML into a designer graph (aiTask fields survive)
 *  - the submit userTask carries a required form field (amountText)
 *  - back end rejects completion without the required field, and runs both
 *    amount branches through the AI node once it is supplied
 */
const TEMPLATE = fs.readFileSync(
  path.join(__dirname, '../public/templates/ai-expense-claim.yaml'), 'utf-8');

async function completeTaskRaw(taskId: string, variables: Record<string, unknown>) {
  return fetch(`${API_BASE}/tasks/${taskId}/complete`, {
    method: 'POST',
    headers: { 'X-User-Id': TEST_USER, 'Content-Type': 'application/json' },
    body: JSON.stringify({ variables, comment: '' }),
  });
}

test.describe('AI expense template', () => {
  test('imports into designer graph with aiTask and form fields intact', async ({ page }) => {
    await page.goto('/');
    const graph = await page.evaluate(async (yaml) => {
      const modPath = '/src/designer/yamlToGraph.ts';
      const { yamlToGraph } = await import(/* @vite-ignore */ modPath);
      return yamlToGraph(yaml);
    }, TEMPLATE);

    const ai = graph.nodes.find((n: any) => n.type === 'aiTask');
    expect(ai, 'template must contain an aiTask node').toBeTruthy();
    expect(ai.data.mode).toBe('extract');
    expect(ai.data.prompt).toContain('${amountText}');
    const apply = graph.nodes.find((n: any) => n.id === 'apply');
    expect(apply.data.formFields).toEqual([expect.objectContaining({
      key: 'amountText', required: true, type: 'textarea',
    })]);
    expect(graph.nodes.some((n: any) => n.type === 'exclusiveGateway')).toBe(true);

    // Round-trip: designer graph -> YAML must preserve the form field.
    const yamlOut: string = await page.evaluate(async (g) => {
      const { graphToYaml } = await import(/* @vite-ignore */ '/src/designer/graphToYaml.ts');
      return graphToYaml(g.nodes, g.edges, 'roundtrip', 'Roundtrip', 1);
    }, graph);
    const reYaml: any = await page.evaluate(async (y) => {
      const { yamlToGraph } = await import(/* @vite-ignore */ '/src/designer/yamlToGraph.ts');
      return yamlToGraph(y);
    }, yamlOut);
    const reApply = reYaml.nodes.find((n: any) => n.id === 'apply');
    expect(reApply.data.formFields).toEqual([expect.objectContaining({
      key: 'amountText', required: true, type: 'textarea',
    })]);
  });

  test('task API exposes form fields and server rejects empty submit', async ({ api }) => {
    const def = await api.deploy(TEMPLATE);
    const inst = await api.startInstance(def.id, { applicant: 'zhangsan' });
    const applyTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'zhangsan' }, 10000);
    expect(applyTasks.length).toBeGreaterThan(0);

    const fields = applyTasks[0].formFields;
    expect(fields).toEqual([
      expect.objectContaining({ key: 'amountText', label: '报销说明', required: true, type: 'textarea' }),
    ]);

    // Empty completion must be rejected by the server (400) and leave task pending.
    const res = await completeTaskRaw(applyTasks[0].id, {});
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toContain('报销说明');
    const stillPending = await api.listTasks({ instanceId: inst.id, status: 'PENDING' });
    expect(stillPending.some((t: any) => t.id === applyTasks[0].id)).toBe(true);
  });

  for (const { note, amount, assignee } of [
    { note: '差旅费一共 2300 元', amount: 2300, assignee: 'finance' },
    { note: '办公用品 300 元', amount: 300, assignee: 'manager' },
  ]) {
    test(`routes note "${note}" (amount ${amount}) to ${assignee}`, async ({ api }) => {
      const def = await api.deploy(TEMPLATE);
      const inst = await api.startInstance(def.id, { applicant: 'zhangsan' });

      // Human submit step first — the note is supplied as the form variable.
      const applyTasks = await api.waitForTasks({ instanceId: inst.id, assignee: 'zhangsan' }, 10000);
      expect(applyTasks.length).toBeGreaterThan(0);
      await api.completeTask(applyTasks[0].id, { amountText: note });

      // AI extracts the amount, gateway routes on extract_amount.
      const targetTasks = await api.waitForTasks({ instanceId: inst.id, assignee }, 10000);
      expect(targetTasks.length, `expected a task for ${assignee}`).toBeGreaterThan(0);

      const detail = await api.getInstance(inst.id);
      expect(Number(detail.variables?.['extract_amount'])).toBe(amount);
      expect(detail.variables?.['extract_category']).toBe('EXPENSE');

      await api.completeTask(targetTasks[0].id);
      expect((await api.getInstance(inst.id)).status).toBe('COMPLETED');
    });
  }
});
