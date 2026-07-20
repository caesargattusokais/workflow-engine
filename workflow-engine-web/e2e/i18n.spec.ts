import { test, expect } from './fixtures';

test.describe('Internationalization', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('switch to English', async ({ page }) => {
    const enBtn = page.locator('button:has-text("EN")');
    if (await enBtn.isVisible()) {
      await enBtn.click();
      await page.waitForTimeout(500);
      // Check that some text is in English
      const designerBtn = page.locator('button:has-text("Designer")');
      await expect(designerBtn).toBeVisible();
    }
  });

  test('switch to Chinese', async ({ page }) => {
    const zhBtn = page.locator('button:has-text("中")');
    if (await zhBtn.isVisible()) {
      await zhBtn.click();
      await page.waitForTimeout(500);
      // Check that some text is in Chinese
      const designerBtn = page.locator('button:has-text("设计器")');
      await expect(designerBtn).toBeVisible();
    }
  });

  test('language preference persists on reload', async ({ page }) => {
    const enBtn = page.locator('button:has-text("EN")');
    if (await enBtn.isVisible()) {
      await enBtn.click();
      await page.waitForTimeout(500);
      await page.reload();
      await page.waitForTimeout(2000);
      // Should still be in English
      const designerBtn = page.locator('button:has-text("Designer")');
      await expect(designerBtn).toBeVisible();
    }
  });
});
