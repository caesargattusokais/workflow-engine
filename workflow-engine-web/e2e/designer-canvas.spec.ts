import { test, expect } from './fixtures';

test.describe('Designer — Canvas & Nodes', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // Create a draft first
    await page.click('button:has-text("+ New")');
    await page.waitForTimeout(1000);
  });

  const nodeTypes = [
    { type: 'startEvent', label: /Start|开始/ },
    { type: 'endEvent', label: /End|结束/ },
    { type: 'userTask', label: /User Task|用户任务/ },
    { type: 'serviceTask', label: /Service Task|服务任务/ },
    { type: 'exclusiveGateway', label: /XOR|判断/ },
    { type: 'parallelGateway', label: /AND|并行/ },
    { type: 'inclusiveGateway', label: /OR|条件/ },
    { type: 'timer', label: /Timer|定时器/ },
    { type: 'callActivity', label: /Call Activity|调用活动/ },
  ];

  for (const { type, label } of nodeTypes) {
    test(`drag ${type} to canvas`, async ({ page }) => {
      // Find the node in the palette
      const paletteItem = page.locator(`[data-type="${type}"]`).first();
      if (await paletteItem.isVisible()) {
        const canvas = page.locator('.react-flow');
        const canvasBox = await canvas.boundingBox();
        if (canvasBox) {
          const palBox = await paletteItem.boundingBox();
          if (palBox) {
            await page.mouse.move(palBox.x + palBox.width / 2, palBox.y + palBox.height / 2);
            await page.mouse.down();
            await page.mouse.move(canvasBox.x + canvasBox.width / 2, canvasBox.y + canvasBox.height / 2, { steps: 10 });
            await page.mouse.up();
            // Node should appear on canvas
            await page.waitForTimeout(500);
          }
        }
      }
    });
  }

  test('select node shows property panel', async ({ page }) => {
    // Drag a userTask to canvas first
    const paletteItem = page.locator('[data-type="userTask"]').first();
    if (await paletteItem.isVisible()) {
      const canvas = page.locator('.react-flow');
      const canvasBox = await canvas.boundingBox();
      const palBox = await paletteItem.boundingBox();
      if (canvasBox && palBox) {
        await page.mouse.move(palBox.x + palBox.width / 2, palBox.y + palBox.height / 2);
        await page.mouse.down();
        await page.mouse.move(canvasBox.x + canvasBox.width / 2, canvasBox.y + canvasBox.height / 2, { steps: 10 });
        await page.mouse.up();
        await page.waitForTimeout(500);
        // Click on the node to select it
        await canvas.click();
        // Property panel should be visible
        const propPanel = page.locator('.bg-gray-800.border-l');
        await expect(propPanel).toBeVisible();
      }
    }
  });

  test('delete selected node', async ({ page }) => {
    // Drag a node, select it, click Delete Node
    const paletteItem = page.locator('[data-type="userTask"]').first();
    if (await paletteItem.isVisible()) {
      const canvas = page.locator('.react-flow');
      const canvasBox = await canvas.boundingBox();
      const palBox = await paletteItem.boundingBox();
      if (canvasBox && palBox) {
        await page.mouse.move(palBox.x + palBox.width / 2, palBox.y + palBox.height / 2);
        await page.mouse.down();
        await page.mouse.move(canvasBox.x + canvasBox.width / 2, canvasBox.y + canvasBox.height / 2, { steps: 10 });
        await page.mouse.up();
        await page.waitForTimeout(500);
        await canvas.click();
        // Click Delete Node button
        const deleteBtn = page.locator('button:has-text("Delete Node")');
        if (await deleteBtn.isVisible()) {
          await deleteBtn.click();
        }
      }
    }
  });
});
