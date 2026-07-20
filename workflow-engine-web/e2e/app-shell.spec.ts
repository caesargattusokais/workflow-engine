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
    // Find language toggle buttons — both should exist
    const zhBtn = page.locator('button:has-text("中")').first();
    const enBtn = page.locator('button:has-text("EN")').first();
    // At least one should be visible
    await expect(zhBtn).toBeVisible();
    // Click the Chinese button to switch
    if (await zhBtn.isVisible()) {
      await zhBtn.click();
      await page.waitForTimeout(500);
      // Now EN should be the active one
      await expect(enBtn).toBeVisible();
    }
  });
});
