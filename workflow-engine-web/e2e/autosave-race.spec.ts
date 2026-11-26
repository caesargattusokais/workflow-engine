import { test, expect } from './fixtures';

/**
 * The designer auto-saves on a 10s debounce. Three races used to corrupt data:
 *  1) switching drafts cleared the timer without flushing — the edit vanished;
 *  2) a slow getDraft(A) resolving after getDraft(B) painted A's graph on B;
 *  3) two overlapping PUTs arrived out of order and the older graph won
 *     (the server has no optimistic lock).
 * Virtual clock keeps these tests fast without sleeping 10 real seconds.
 */
test.describe('Designer auto-save races', () => {
  let nameSeq = 0;
  const draftName = (tag: string) => `autosave-${tag}-${Date.now()}-${++nameSeq}`;

  // Draft list is paginated (10/page); start each test from a clean list so a
  // newly created draft is always visible without paging.
  async function resetDrafts(api: any) {
    const r = await api.listDrafts(1, 100);
    for (const d of (r.items || r)) await api.deleteDraft(d.id);
  }

  async function makeDraft(api: any, name: string, nodeCount = 0) {
    const d = await api.createDraft(name);
    if (nodeCount) {
      const nodes = Array.from({ length: nodeCount }, (_, i) => ({
        id: `n${i + 1}`, type: 'userTask', position: { x: 60 + i * 80, y: 80 },
        data: { name: `Task ${i + 1}` },
      }));
      await api.updateDraft(d.id, { nodes, edges: [] });
    }
    return d;
  }

  async function dragNode(page: any, type: string) {
    const paletteItem = page.locator(`[data-type="${type}"]`).first();
    await expect(paletteItem).toBeVisible();
    const canvas = page.locator('.react-flow');
    const canvasBox = await canvas.boundingBox();
    const palBox = await paletteItem.boundingBox();
    expect(canvasBox && palBox).toBeTruthy();
    await page.mouse.move(palBox!.x + palBox!.width / 2, palBox!.y + palBox!.height / 2);
    await page.mouse.down();
    await page.mouse.move(canvasBox!.x + canvasBox!.width / 2, canvasBox!.y + canvasBox!.height / 2, { steps: 8 });
    await page.mouse.up();
  }

  test('switching drafts flushes the pending debounced edit to the OLD draft', async ({ page, api }) => {
    await resetDrafts(api);
    const a = await makeDraft(api, draftName('switch-a'));
    const b = await makeDraft(api, draftName('switch-b'));
    await page.clock.install();
    await page.goto('/');
    await expect(page.locator(`[data-draft-id="${a.id}"]`)).toBeVisible();

    await page.locator(`[data-draft-id="${a.id}"]`).click();
    await expect(page.locator('.react-flow__node')).toHaveCount(0);
    await dragNode(page, 'userTask');
    await expect(page.locator('.react-flow__node')).toHaveCount(1);

    // Switch well before the 10s debounce. The edit must still reach A, not B.
    await page.locator(`[data-draft-id="${b.id}"]`).click();
    await expect.poll(async () => (await api.getDraft(a.id)).nodes.length).toBe(1);
    expect((await api.getDraft(b.id)).nodes.length).toBe(0);
  });

  test('debounce timer auto-saves to the active draft', async ({ page, api }) => {
    await resetDrafts(api);
    const d = await makeDraft(api, draftName('timer'));
    await page.clock.install();
    await page.goto('/');
    await page.locator(`[data-draft-id="${d.id}"]`).click();
    await expect(page.locator('.react-flow__node')).toHaveCount(0);

    await dragNode(page, 'startEvent');
    await expect(page.locator('.react-flow__node')).toHaveCount(1);
    expect((await api.getDraft(d.id)).nodes.length, 'nothing should be saved before debounce').toBe(0);

    await page.clock.fastForward(10_500);
    await expect.poll(async () => (await api.getDraft(d.id)).nodes.length).toBe(1);
  });

  test('a slow getDraft response cannot paint a stale graph on the new draft', async ({ page, api }) => {
    await resetDrafts(api);
    const a = await makeDraft(api, draftName('slow-a'), 1);
    const b = await makeDraft(api, draftName('slow-b'), 2);
    let stallA = true;
    await page.route(`**/api/drafts/${a.id}`, async route => {
      if (route.request().method() === 'GET' && stallA) {
        await new Promise(r => setTimeout(r, 1200));
      }
      await route.continue();
    });

    await page.goto('/');
    await expect(page.locator(`[data-draft-id="${b.id}"]`)).toBeVisible();
    await page.locator(`[data-draft-id="${a.id}"]`).click();
    await page.locator(`[data-draft-id="${b.id}"]`).click();
    stallA = false;

    // B was clicked last; its two nodes must win even though A's GET returns late.
    await expect(page.locator('.react-flow__node')).toHaveCount(2);
    await page.waitForTimeout(1500);
    await expect(page.locator('.react-flow__node')).toHaveCount(2);
  });

  test('serialised writes keep the newest graph when an older PUT is slow', async ({ page, api }) => {
    await resetDrafts(api);
    const d = await makeDraft(api, draftName('order'));
    await page.clock.install();
    await page.goto('/');
    await page.locator(`[data-draft-id="${d.id}"]`).click();

    let putCount = 0;
    let putDone = 0;
    await page.route(`**/api/drafts/${d.id}`, async route => {
      if (route.request().method() === 'PUT') {
        if (++putCount === 1) {
          await new Promise(r => setTimeout(r, 2500)); // first (stale) write is slow
        }
        await route.continue();
        putDone++;
      } else {
        await route.continue();
      }
    });

    await dragNode(page, 'startEvent');
    await page.clock.fastForward(10_500);
    await expect(page.locator('.react-flow__node')).toHaveCount(1);
    await dragNode(page, 'userTask');
    await page.clock.fastForward(10_500);
    await expect(page.locator('.react-flow__node')).toHaveCount(2);

    // Wait until BOTH writes have been forwarded before inspecting final state.
    await expect.poll(() => putDone).toBe(2);
    // The second PUT must queue behind the first; final server state has both nodes.
    expect((await api.getDraft(d.id)).nodes.length).toBe(2);
  });
});
