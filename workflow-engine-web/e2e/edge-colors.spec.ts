import { test, expect } from './fixtures';

/**
 * Edge colors must be derived from the semantic edgeType, no matter where the
 * edge came from (template import / loaded draft / hand drawn). Previously only
 * hand-drawn edges were colored, so imported templates rendered all-grey.
 */
test.describe('Edge color language', () => {
  test('imported template edges render distinct colors per edgeType', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Designer' })).toBeVisible({ timeout: 8000 });
    await page.getByRole('button', { name: 'Designer' }).click();

    // Import the HTTP-integration template directly through the same path the
    // template browser uses: yamlToGraph -> graph -> canvas.
    await page.evaluate(async () => {
      const res = await fetch('/templates/http-integration.yaml', { cache: 'no-store' });
      const yaml = await res.text();
      const { yamlToGraph } = await import(/* @vite-ignore */ '/src/designer/yamlToGraph.ts');
      const g = yamlToGraph(yaml);
      // Publish onto the running app through a custom event the Designer listens to is
      // overkill here; instead assert the graph already carries semantic types and let
      // the canvas normalization be checked by the unit-style assertions below.
      (window as any).__testGraph = g;
    });

    const result = await page.evaluate(async () => {
      const { getEdgeStyle, getEdgeMarker, styleEdgeForType } =
        await import(/* @vite-ignore */ '/src/designer/edgeStyles.ts');
      const g = (window as any).__testGraph;
      const byType: Record<string, string> = {};
      for (const e of g.edges) {
        const t = e.data?.edgeType || 'direct';
        byType[t] = getEdgeStyle(t).stroke;
        const styled = styleEdgeForType(e);
        if (styled.style?.stroke !== getEdgeStyle(t).stroke) throw new Error('style mismatch for ' + t);
        if ((styled.markerEnd as any)?.color !== getEdgeMarker(t).color) throw new Error('marker mismatch for ' + t);
      }
      return byType;
    });

    // The template must actually contain 3 semantic types (result/exception/timeout)
    // and each gets a unique color.
    expect(Object.keys(result)).toEqual(expect.arrayContaining(['result', 'exception', 'timeout']));
    const colors = Object.values(result);
    expect(new Set(colors).size).toBe(colors.length);
  });

  test('all six edge types map to six distinct strokes', async ({ page }) => {
    await page.goto('/');
    const strokes = await page.evaluate(async () => {
      const { EDGE_STYLES, getEdgeStyle } = await import(/* @vite-ignore */ '/src/designer/edgeStyles.ts');
      return Object.keys(EDGE_STYLES).map(k => getEdgeStyle(k).stroke);
    });
    expect(strokes).toHaveLength(6);
    expect(new Set(strokes).size).toBe(6);
  });

  test('styleEdgeForType colors bare imported edges (no style/marker)', async ({ page }) => {
    await page.goto('/');
    const result = await page.evaluate(async () => {
      const { styleEdgeForType, getEdgeStyle } = await import(/* @vite-ignore */ '/src/designer/edgeStyles.ts');
      // This is exactly what yamlToGraph / the server graph produce: data.edgeType but no style.
      const bare = [
        { id: 'e1', source: 'a', target: 'b', data: { edgeType: 'result' } },
        { id: 'e2', source: 'b', target: 'c', data: { edgeType: 'exception' } },
        { id: 'e3', source: 'c', target: 'd', data: { edgeType: 'direct' } },
      ];
      return bare.map(e => {
        const styled = styleEdgeForType(e as any);
        return {
          type: e.data.edgeType,
          stroke: (styled.style as any).stroke,
          marker: (styled.markerEnd as any).color,
          expected: getEdgeStyle(e.data.edgeType).stroke,
        };
      });
    });
    for (const r of result) {
      expect(r.stroke, `${r.type} stroke`).toBe(r.expected);
      expect(r.marker, `${r.type} marker`).toBe(r.expected);
    }
    expect(new Set(result.map(r => r.stroke)).size).toBe(3);
  });

  test('legend lists every edge type', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Designer' })).toBeVisible({ timeout: 8000 });
    await page.getByRole('button', { name: 'Designer' }).click();
    // Legend is rendered in the canvas; check a few type labels are present.
    const legend = page.locator('div').filter({ hasText: /^Edge types$/ }).first();
    await expect(legend).toBeVisible();
    for (const label of ['Direct', 'Condition', 'Default', 'Result', 'Exception', 'Timeout']) {
      await expect(page.locator('div.absolute.top-3.right-3').getByText(label, { exact: true })).toBeVisible();
    }
  });
});
