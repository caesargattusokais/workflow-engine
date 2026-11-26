import { test, expect } from './fixtures';

/**
 * Edge shape (smoothstep / curve / straight / elbow) is a visual preference,
 * orthogonal to the semantic edgeType. It must round-trip through drafts and
 * be switchable both globally (toolbar) and per edge (context menu).
 */
test.describe('Edge shape switching', () => {
  test('four shapes map to distinct ReactFlow edge types and round-trip', async ({ page }) => {
    await page.goto('/');
    const result = await page.evaluate(async () => {
      const m = await import(/* @vite-ignore */ '/src/designer/edgeStyles.ts');
      const shapes = Object.keys(m.EDGE_SHAPES) as any[];
      const roundTrip = shapes.map(s => {
        const edge: any = { id: 'e', source: 'a', target: 'b', data: { edgeType: 'direct' } };
        const shaped = m.shapeEdge(edge, s);
        return {
          shape: s,
          type: shaped.type,
          stored: (shaped.data as any).edgeShape,
          readBack: m.getEdgeShape(shaped),
          icon: m.EDGE_SHAPES[s].icon,
        };
      });
      return { shapes, roundTrip, fallback: m.getEdgeShape({ data: { edgeType: 'result' } }) };
    });

    expect(result.shapes).toHaveLength(4);
    expect(new Set(result.shapes).size).toBe(4);
    for (const r of result.roundTrip) {
      expect(r.type, `${r.shape} ReactFlow type`).toBe(r.shape);
      expect(r.stored, `${r.shape} persisted shape`).toBe(r.shape);
      expect(r.readBack, `${r.shape} read-back`).toBe(r.shape);
      expect(r.icon, `${r.shape} icon path`).toBeTruthy();
    }
    expect(new Set(result.roundTrip.map(r => r.icon)).size).toBe(4);
    expect(result.fallback).toBe('smoothstep');
  });

  test('imported template edges default to smoothstep', async ({ page }) => {
    await page.goto('/');
    const shapes = await page.evaluate(async () => {
      const res = await fetch('/templates/http-integration.yaml', { cache: 'no-store' });
      const yaml = await res.text();
      const { yamlToGraph } = await import(/* @vite-ignore */ '/src/designer/yamlToGraph.ts');
      const { getEdgeShape } = await import(/* @vite-ignore */ '/src/designer/edgeStyles.ts');
      return yamlToGraph(yaml).edges.map((e: any) => ({ type: e.type, shape: getEdgeShape(e) }));
    });
    expect(shapes.length).toBeGreaterThan(0);
    for (const s of shapes) {
      expect(s.type).toBe('smoothstep');
      expect(s.shape).toBe('smoothstep');
    }
  });

  test('toolbar exposes all four shape buttons plus the legend hint', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Designer' })).toBeVisible({ timeout: 8000 });
    await page.getByRole('button', { name: 'Designer' }).click();
    for (const shape of ['smoothstep', 'default', 'straight', 'step']) {
      await expect(page.locator(`svg[aria-label="${shape}"]`).first()).toBeVisible();
    }
    await expect(page.locator('div.absolute.top-3.right-3').getByText('Switch shape at bottom-left')).toBeVisible();
  });

  test('global switch changes every rendered edge; per-edge switch changes only one', async ({ page, api }) => {
    const name = `Shape Test ${Date.now()}`;
    const draft = await api.importDraft(name,
      [
        { id: 'a', type: 'startEvent', position: { x: 80, y: 100 }, data: { name: 'Start' } },
        { id: 'b', type: 'userTask', position: { x: 320, y: 100 }, data: { name: 'Review', assignee: 'u' } },
        { id: 'c', type: 'endEvent', position: { x: 560, y: 100 }, data: { name: 'End' } },
      ],
      [
        { id: 'e1', source: 'a', target: 'b', data: { edgeType: 'direct' } },
        { id: 'e2', source: 'b', target: 'c', data: { edgeType: 'direct' } },
      ]);

    // The draft list is paginated (10/page) and many tests create drafts, so
    // locate the page holding ours instead of assuming it is on page 1.
    let targetPage = 1;
    for (let p = 1; p <= 30; p++) {
      const r: any = await api.listDrafts(p, 10);
      if ((r.items || r).some((d: any) => d.id === draft.id)) { targetPage = p; break; }
    }

    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Designer' })).toBeVisible({ timeout: 8000 });
    await page.getByRole('button', { name: 'Designer' }).click();
    for (let p = 1; p < targetPage; p++) {
      await page.getByRole('button', { name: /Load more/ }).click();
    }
    await page.getByText(name, { exact: false }).first().click();
    await expect(page.locator('.react-flow__edge')).toHaveCount(2, { timeout: 10000 });

    const classes = () => page.locator('.react-flow__edge').evaluateAll(els =>
      els.map(e => e.getAttribute('class') || ''));
    const initial = await classes();
    expect(initial.every(c => c.includes('react-flow__edge-smoothstep'))).toBe(true);

    // Global switch → both edges become straight
    await page.locator('svg[aria-label="straight"]').first().click();
    await expect.poll(async () => (await classes()).every(c => c.includes('react-flow__edge-straight'))).toBe(true);

    // Persisted in draft data so it survives reload
    await page.waitForTimeout(11000); // autosave debounce is 10s
    const saved = await api.getDraft(draft.id);
    expect(saved.edges.every((e: any) => e.data?.edgeShape === 'straight')).toBe(true);

    // Per-edge switch via context menu → only the right-clicked edge changes
    const firstEdge = page.locator('.react-flow__edge').first();
    await firstEdge.click({ button: 'right' });
    await expect(page.locator('svg[aria-label="shape-step"]')).toBeVisible();
    await page.locator('svg[aria-label="shape-step"]').click();
    await expect.poll(async () => {
      const cs = await classes();
      return cs.filter(c => c.includes('react-flow__edge-step')).length;
    }).toBe(1);
  });
});
