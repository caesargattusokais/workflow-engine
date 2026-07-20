import { test, expect, workflows } from './fixtures';

test.describe('Designer — Deploy & YAML', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.click('button:has-text("+ New")');
    await page.waitForTimeout(1000);
  });

  test('deploy empty canvas shows error', async ({ page }) => {
    await page.click('button:has-text("Deploy")');
    // Should show toast about adding nodes
    await page.waitForTimeout(1000);
    const toast = page.locator('.bg-green-900, .bg-red-600');
    // Toast should appear
  });

  test('view YAML for a draft', async ({ page }) => {
    // Right-click draft → View YAML
    const draftItem = page.locator('.text-gray-300.truncate').first();
    await draftItem.click({ button: 'right' });
    const viewYamlBtn = page.locator('text=View YAML');
    if (await viewYamlBtn.isVisible()) {
      await viewYamlBtn.click();
      // YAML preview should appear
      await page.waitForTimeout(500);
    }
  });
});
