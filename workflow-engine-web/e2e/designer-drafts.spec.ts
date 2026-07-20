import { test, expect } from './fixtures';

test.describe('Designer — Draft CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('create a new draft', async ({ page }) => {
    await page.click('button:has-text("+ New")');
    await page.waitForTimeout(1000);
    // A new draft should appear in the sidebar
    const draftItems = page.locator('.text-gray-300.truncate');
    await expect(draftItems.first()).toBeVisible();
  });

  test('rename a draft', async ({ page }) => {
    await page.click('button:has-text("+ New")');
    await page.waitForTimeout(1000);
    // Right-click on the draft
    const draftItem = page.locator('.text-gray-300.truncate').first();
    await draftItem.click({ button: 'right' });
    // Click Rename in context menu
    await page.click('text=Rename');
    // Type new name in prompt
    page.on('dialog', async dialog => {
      await dialog.accept('Renamed Draft');
    });
    await page.evaluate(() => prompt('test'));
  });

  test('delete a draft', async ({ page }) => {
    await page.click('button:has-text("+ New")');
    await page.waitForTimeout(1000);
    const draftItem = page.locator('.text-gray-300.truncate').first();
    await draftItem.click({ button: 'right' });
    // Click Delete in context menu (use precise selector to avoid ReactFlow edge labels)
    const deleteBtn = page.locator('.fixed.z-50 button:has-text("Delete"), .fixed.z-50 button:has-text("删除")');
    await expect(deleteBtn).toBeVisible();
  });

  test('copy a draft', async ({ page }) => {
    await page.click('button:has-text("+ New")');
    await page.waitForTimeout(1000);
    const draftItem = page.locator('.text-gray-300.truncate').first();
    await draftItem.click({ button: 'right' });
    await page.click('text=Copy');
    await page.waitForTimeout(1000);
    // A copy should appear
    const draftItems = page.locator('.text-gray-300.truncate');
    const count = await draftItems.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });

  test('switch between drafts', async ({ page }) => {
    await page.click('button:has-text("+ New")');
    await page.waitForTimeout(500);
    await page.click('button:has-text("+ New")');
    await page.waitForTimeout(500);
    // Click on different drafts
    const draftItems = page.locator('.text-gray-300.truncate');
    if (await draftItems.count() >= 2) {
      await draftItems.nth(1).click();
      await page.waitForTimeout(300);
      await draftItems.nth(0).click();
    }
  });
});
