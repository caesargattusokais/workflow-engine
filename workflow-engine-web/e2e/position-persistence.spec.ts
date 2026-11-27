import { test, expect } from './fixtures';

/**
 * Node drags reach the server through the same 10s debounced auto-save as any
 * other edit. Losing that write made dragged nodes snap back to their old
 * position when the designer was re-entered ("position revert"). These tests
 * pin every flush path:
 *  1) unmount — switching to the monitor tab and back must flush;
 *  2) reload — pagehide flush with a keepalive PUT survives navigation;
 *  3) switch window — between switchDraft() and getDraft() the canvas still
 *     shows the previous draft, and auto-save must stay disarmed so the old
 *     graph (old positions included) is never written into the new draft.
 */
test.describe('Designer node position persistence', () => {
  let nameSeq = 0;
  const draftName = (tag: string) => `pos-${tag}-${Date.now()}-${++nameSeq}`;

  // Draft list is paginated (10/page); start each test from a clean list.
  async function resetDrafts(api: any) {
    const r = await api.listDrafts(1, 100);
    for (const d of (r.items || r)) await api.deleteDraft(d.id);
  }

  async function makeDraft(api: any, name: string) {
    const d = await api.createDraft(name);
    await api.updateDraft(d.id, {
      nodes: [{ id: 'n1', type: 'userTask', position: { x: 60, y: 80 }, data: { name: 'Task 1' } }],
      edges: [],
    });
    return d;
  }

  async function dragExistingNode(page: any, dx: number, dy: number) {
    const node = page.locator('.react-flow__node').first();
    await expect(node).toBeVisible();
    const box = await node.boundingBox();
    expect(box).toBeTruthy();
    await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
    await page.mouse.down();
    await page.mouse.move(box!.x + box!.width / 2 + dx, box!.y + box!.height / 2 + dy, { steps: 6 });
    await page.mouse.up();
  }

  const parsePos = (style: string | null) => {
    const m = /translate\((-?[\d.]+)px,\s*(-?[\d.]+)px\)/.exec(style || '');
    return m ? { x: parseFloat(m![1]), y: parseFloat(m![2]) } : null;
  };

  const moved = async (api: any, id: string) => {
    const p = (await api.getDraft(id)).nodes[0]?.position;
    return !!p && (p.x !== 60 || p.y !== 80);
  };

  test('drag survives switching to the monitor tab and back (unmount flush)', async ({ page, api }) => {
    await resetDrafts(api);
    const d = await makeDraft(api, draftName('unmount'));
    await page.clock.install();
    await page.goto('/');
    await page.locator(`[data-draft-id="${d.id}"]`).click();
    await expect(page.locator('.react-flow__node')).toHaveCount(1);

    await dragExistingNode(page, 160, 90);
    // Leave the designer well before the 10s debounce. The unmount flush must
    // carry the drag to the server, otherwise the node snaps back below.
    await page.getByRole('button', { name: /^(Monitor|监控)$/ }).click();
    await expect.poll(() => moved(api, d.id), { timeout: 10000 }).toBe(true);
    const saved = (await api.getDraft(d.id)).nodes[0].position;

    await page.getByRole('button', { name: /^(Designer|设计器)$/ }).click();
    await page.locator(`[data-draft-id="${d.id}"]`).click();
    await expect(page.locator('.react-flow__node')).toHaveCount(1);
    const pos = parsePos(await page.locator('.react-flow__node').first().getAttribute('style'));
    expect(pos).toBeTruthy();
    expect(Math.abs(pos!.x - saved.x)).toBeLessThan(1);
    expect(Math.abs(pos!.y - saved.y)).toBeLessThan(1);
  });

  test('drag survives a page reload (pagehide keepalive flush)', async ({ page, api }) => {
    await resetDrafts(api);
    const d = await makeDraft(api, draftName('reload'));
    await page.goto('/');
    await page.locator(`[data-draft-id="${d.id}"]`).click();
    await expect(page.locator('.react-flow__node')).toHaveCount(1);

    await dragExistingNode(page, 140, 70);
    await page.reload();
    await expect(page.locator(`[data-draft-id="${d.id}"]`)).toBeVisible();
    // The keepalive PUT fired on pagehide must survive the navigation.
    await expect.poll(() => moved(api, d.id), { timeout: 10000 }).toBe(true);
  });

  test('switch window never writes the previous draft into the new draft', async ({ page, api }) => {
    await resetDrafts(api);
    const a = await makeDraft(api, draftName('stale-a'));
    const b = await api.createDraft(draftName('stale-b'));
    // Hold getDraft(B) open so the switch window (canvas still showing A) lasts.
    await page.route(`**/api/drafts/${b.id}*`, async route => {
      if (route.request().method() === 'GET') await new Promise(r => setTimeout(r, 1500));
      await route.continue();
    });
    await page.clock.install();
    await page.goto('/');
    await page.locator(`[data-draft-id="${a.id}"]`).click();
    await expect(page.locator('.react-flow__node')).toHaveCount(1);

    await page.locator(`[data-draft-id="${b.id}"]`).click();
    // Canvas still shows A's node while getDraft(B) is pending.
    await expect(page.locator('.react-flow__node')).toHaveCount(1);
    // Old code armed the auto-save here and would have written A's graph into B.
    await page.clock.fastForward(10_500);
    await page.waitForTimeout(1500);
    expect((await api.getDraft(b.id)).nodes.length, 'stale graph must not reach draft B').toBe(0);
    await expect(page.locator('.react-flow__node')).toHaveCount(0);
  });
});
