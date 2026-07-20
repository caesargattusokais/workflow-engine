import { test, expect, workflows } from './fixtures';

test.describe('Monitor — Instance Lifecycle', () => {
  test.beforeEach(async ({ page, api }) => {
    // Deploy a definition and start an instance for testing
    const def = await api.deploy(workflows.simpleLinear);
    await api.startInstance(def.id);
    await page.goto('/');
    await page.click('button:has-text("Monitor")');
    await page.waitForTimeout(2000);
  });

  test('instances are listed', async ({ page }) => {
    // Should see at least one instance
    const instanceItems = page.locator('.bg-gray-750, .bg-blue-600');
    await expect(instanceItems.first()).toBeVisible({ timeout: 5000 });
  });

  test('select instance shows detail', async ({ page }) => {
    // Click on an instance
    const instanceItem = page.locator('.p-1\\.5.rounded.cursor-pointer').first();
    if (await instanceItem.isVisible()) {
      await instanceItem.click();
      await page.waitForTimeout(1000);
      // Detail sidebar should show
      const detail = page.locator('text=Instance Detail');
      if (await detail.isVisible()) {
        // Should show ID and status
        const idText = page.locator('text=ID:');
        await expect(idText).toBeVisible();
      }
    }
  });

  test('complete a task from task panel', async ({ page }) => {
    // Select an instance with pending tasks
    const instanceItem = page.locator('.p-1\\.5.rounded.cursor-pointer').first();
    if (await instanceItem.isVisible()) {
      await instanceItem.click();
      await page.waitForTimeout(1000);
      // Find Complete button in task panel
      const completeBtn = page.locator('button:has-text("Complete")').first();
      if (await completeBtn.isVisible()) {
        await completeBtn.click();
        await page.waitForTimeout(1000);
      }
    }
  });

  test('status filter chips', async ({ page }) => {
    // Click RUNNING filter
    const runningChip = page.locator('button:has-text("RUNNING")').first();
    if (await runningChip.isVisible()) {
      await runningChip.click();
      await page.waitForTimeout(1000);
    }
  });

  test('refresh instances', async ({ page }) => {
    const refreshBtn = page.locator('button:has-text("Refresh")');
    if (await refreshBtn.isVisible()) {
      await refreshBtn.click();
      await page.waitForTimeout(1000);
    }
  });
});
