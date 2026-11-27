import { test, expect, workflows } from './fixtures';

/** Historic entries with node names, mirroring /instances/{id}/history. */
const fakeHistory = [
  { nodeId: 'start', action: 'enter', executor: 'system', timestamp: '2026-01-01T00:00:00Z' },
  { nodeId: 'fork', action: 'enter', executor: 'system', timestamp: '2026-01-01T00:00:01Z' },
  { nodeId: 'taskA', action: 'enter', executor: 'system', timestamp: '2026-01-01T00:00:02Z' },
  { nodeId: 'taskA', action: 'complete', executor: 'userA', comment: 'ok', timestamp: '2026-01-01T00:00:03Z' },
  { nodeId: 'taskB', action: 'enter', executor: 'system', timestamp: '2026-01-01T00:00:04Z' },
  { nodeId: 'taskB', action: 'complete', executor: 'userB', comment: 'ok', timestamp: '2026-01-01T00:00:05Z' },
  { nodeId: 'taskC', action: 'enter', executor: 'system', timestamp: '2026-01-01T00:00:06Z' },
  { nodeId: 'taskC', action: 'complete', executor: 'userC', comment: 'ok', timestamp: '2026-01-01T00:00:07Z' },
  { nodeId: 'taskD', action: 'enter', executor: 'system', timestamp: '2026-01-01T00:00:08Z' },
  { nodeId: 'taskD', action: 'complete', executor: 'userD', comment: 'ok', timestamp: '2026-01-01T00:00:09Z' },
  { nodeId: 'taskE', action: 'enter', executor: 'system', timestamp: '2026-01-01T00:00:10Z' },
  { nodeId: 'taskE', action: 'complete', executor: 'userE', comment: 'ok', timestamp: '2026-01-01T00:00:11Z' },
  { nodeId: 'join', action: 'enter', executor: 'system', timestamp: '2026-01-01T00:00:12Z' },
  { nodeId: 'end', action: 'enter', executor: 'system', timestamp: '2026-01-01T00:00:13Z' },
];

test.describe('Monitor — instance history shows every visited node', () => {
  test('enter/leave nodes and rows beyond the first 10 are all rendered', async ({ page, api }) => {
    // Guarantee at least one instance exists so the monitor list is non-empty,
    // regardless of whether the suite is run alone or after hundreds of tests.
    const def = await api.deploy(workflows.simpleLinear);
    const inst = await api.startInstance(def.id);

    // Intercept history so the assertion is deterministic and independent of
    // how many instances earlier tests have created in this environment.
    await page.route('**/api/instances/*/history', route =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(fakeHistory) }));

    await page.goto('/');
    await page.click('button:has-text("Monitor")');
    await page.waitForTimeout(1500);

    // Pick any instance in the list — the exact instance does not matter,
    // the intercepted history is what the sidebar will render.
    const firstInstance = page.locator('.p-1\\.5.rounded.cursor-pointer').first();
    await expect(firstInstance).toBeVisible({ timeout: 5000 });
    await firstInstance.click();
    await page.waitForTimeout(600);

    // Sidebar root: <div class="w-60 bg-gray-800 border-l ...">
    const sidebar = page.locator('div.w-60.border-l');
    const historyRows = sidebar.locator('div.ml-5.space-y-2 > div.pl-2\\.5.border-l');
    // Pre-fix: enter/leave filtered out (only 6 rows) and capped at the last 10.
    await expect(historyRows).toHaveCount(fakeHistory.length, { timeout: 5000 });
    // These nodes only have `enter`; pre-fix they were completely invisible.
    for (const nodeId of ['start', 'fork', 'join', 'end']) {
      await expect(historyRows.filter({ hasText: nodeId }).first()).toBeVisible({ timeout: 3000 });
    }
    // Row beyond index 10 must be present (taskA complete is at index 3;
    // start/enter is at the tail after reverse — verifies no 10-row truncation).
    await expect(historyRows.filter({ hasText: 'start' }).first()).toBeVisible();

    await api.terminateInstance(inst.id);
    await api.deleteInstance(inst.id);
    await api.deleteDefinition(def.id);
  });
});
