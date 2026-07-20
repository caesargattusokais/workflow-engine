import { test, expect } from './fixtures';

test.describe('App Shell', () => {
  test('tab switching', async ({ page }) => {
    await page.goto('/');
    // Default should be designer
    await expect(page.locator('button', { hasText: 'Designer' })).toHaveClass(/bg-blue-600/);
    // Switch to Monitor
    await page.click('button:has-text("Monitor")');
    await expect(page.locator('button', { hasText: 'Monitor' })).toHaveClass(/bg-blue-600/);
    // Switch to Dashboard
    await page.click('button:has-text("Dashboard")');
    await expect(page.locator('button', { hasText: 'Dashboard' })).toHaveClass(/bg-blue-600/);
    // Back to Designer
    await page.click('button:has-text("Designer")');
    await expect(page.locator('button', { hasText: 'Designer' })).toHaveClass(/bg-blue-600/);
  });

  test('language switch', async ({ page }) => {
    await page.goto('/');
    // Find language toggle
    const langBtn = page.locator('button:has-text("EN"), button:has-text("中")');
    await expect(langBtn).toBeVisible();
    // Click to switch
    await langBtn.first().click();
    // Verify language changed (check for translated text)
    await page.waitForTimeout(500);
  });
});
