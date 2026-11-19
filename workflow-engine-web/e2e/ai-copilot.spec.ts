import { test, expect } from './fixtures';

/**
 * AI copilot — the /api/ai/diagnose endpoint explains an instance's state
 * using the configured LLM (the stub returns a deterministic structured JSON
 * diagnosis), and the monitor sidebar renders the diagnosis.
 *
 * The endpoint is read-only: it must never mutate the instance.
 */
test.describe('AI copilot — diagnose endpoint', () => {
  let defId: string;
  let instId: string;

  test.beforeAll(async ({ api }) => {
    const def = await api.deploy(`id: copilot-flow
name: Copilot Flow
version: 1
nodes:
  - id: start
    type: startEvent
  - id: review
    type: userTask
    name: Review
    assignee: reviewer
  - id: end
    type: endEvent
transitions:
  - from: start
    to: review
  - from: review
    to: end`);
    defId = def.id;
    const inst = await api.startInstance(defId, { applicant: 'zhangsan' });
    instId = inst.id;
    // Wait until the review task exists so the instance is mid-flight.
    await api.waitForTasks({ instanceId: instId, assignee: 'reviewer' }, 10000);
  });

  test('returns a structured diagnosis from the LLM', async ({ api }) => {
    const res = await fetch('http://localhost:8080/api/ai/diagnose', {
      method: 'POST',
      headers: { 'X-User-Id': 'testuser', 'Content-Type': 'application/json' },
      body: JSON.stringify({ instanceId: instId }),
    });
    expect(res.status).toBe(200);
    const d = await res.json();
    expect(d.summary).toBeTruthy();
    expect(d.severity).toBe('info');
    expect(Array.isArray(d.suggestions)).toBe(true);
    expect(typeof d.cause).toBe('string');
    // The instance must be untouched — diagnosis is read-only.
    const detail = await api.getInstance(instId);
    expect(detail.status).toBe('RUNNING');
  });

  test('rejects missing instanceId with 400', async () => {
    const res = await fetch('http://localhost:8080/api/ai/diagnose', {
      method: 'POST',
      headers: { 'X-User-Id': 'testuser', 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    });
    expect(res.status).toBe(400);
  });

  test('rejects unknown instance with 404', async () => {
    const res = await fetch('http://localhost:8080/api/ai/diagnose', {
      method: 'POST',
      headers: { 'X-User-Id': 'testuser', 'Content-Type': 'application/json' },
      body: JSON.stringify({ instanceId: 'no-such-instance' }),
    });
    expect(res.status).toBe(404);
  });

  test('rejects another tenant\'s instance with 404', async () => {
    // Create the instance under a different user, then diagnose as testuser.
    const other = await fetch('http://localhost:8080/api/instances', {
      method: 'POST',
      headers: { 'X-User-Id': 'otheruser', 'Content-Type': 'application/json' },
      body: JSON.stringify({ definitionId: defId, variables: {} }),
    });
    expect(other.status).toBe(200);
    const inst = await other.json();
    const res = await fetch('http://localhost:8080/api/ai/diagnose', {
      method: 'POST',
      headers: { 'X-User-Id': 'testuser', 'Content-Type': 'application/json' },
      body: JSON.stringify({ instanceId: inst.id }),
    });
    expect(res.status).toBe(404);
    await fetch(`http://localhost:8080/api/instances/${inst.id}`, {
      method: 'DELETE',
      headers: { 'X-User-Id': 'otheruser' },
    });
  });
});

test.describe('AI copilot — monitor sidebar UI', () => {
  test('renders the AI diagnosis after clicking the copilot button', async ({ page, api }) => {
    const def = await api.deploy(`id: copilot-ui-flow
name: Copilot UI Flow
version: 1
nodes:
  - id: start
    type: startEvent
  - id: review
    type: userTask
    name: Review
    assignee: ui-reviewer
  - id: end
    type: endEvent
transitions:
  - from: start
    to: review
  - from: review
    to: end`);
    const inst = await api.startInstance(def.id);
    await api.waitForTasks({ instanceId: inst.id, assignee: 'ui-reviewer' }, 10000);

    await page.goto('/');
    await page.click('button:has-text("Monitor")');
    await page.waitForTimeout(1500);

    // Select the newest instance of our definition.
    const instanceItem = page.locator('.p-1\\.5.rounded.cursor-pointer').first();
    await instanceItem.click();
    await page.waitForTimeout(800);

    // The copilot section exists with its button.
    const copilotBtn = page.locator('button:has-text("AI Copilot")').first();
    await expect(copilotBtn).toBeVisible();

    await copilotBtn.click();
    // Stub returns a deterministic structured diagnosis.
    await expect(page.locator('text=实例正在等待人工审批')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('text=催办或转派当前待办人')).toBeVisible();
  });
});
