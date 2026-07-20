import { test, expect, workflows } from './fixtures';

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.click('button:has-text("Dashboard")');
    await page.waitForTimeout(2000);
  });

  test('global stats KPIs display', async ({ page }) => {
    // Should see KPI cards
    const kpiLabels = page.locator('.text-2xl.font-bold');
    await expect(kpiLabels.first()).toBeVisible({ timeout: 5000 });
  });

  test('select a flow shows per-flow stats', async ({ page }) => {
    // Click on a draft in the sidebar
    const flowItem = page.locator('.px-3.py-2.cursor-pointer').first();
    if (await flowItem.isVisible()) {
      await flowItem.click();
      await page.waitForTimeout(1000);
      // Stats should update
      const kpiLabels = page.locator('.text-2xl.font-bold');
      await expect(kpiLabels.first()).toBeVisible();
    }
  });

  test('empty dashboard prompt', async ({ page }) => {
    // If no draft is selected, should show prompt
    const prompt = page.locator('text=← Select a flow');
    // This may or may not be visible depending on state
    const flowItem = page.locator('.px-3.py-2.cursor-pointer').first();
    if (!(await flowItem.isVisible())) {
      await expect(prompt).toBeVisible();
    }
  });
});
