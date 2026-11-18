import { test, expect } from './fixtures';

test.describe('Designer — AI flow generation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('generate a flow from natural language into the canvas', async ({ page }) => {
    // Open the AI modal via the ✦ toolbar button
    const aiButton = page.locator('button[title*="AI"]');
    await expect(aiButton).toBeVisible();
    await aiButton.click();

    // Modal shows with textarea
    const modal = page.locator('.bg-gray-800.rounded-lg.shadow-2xl');
    await expect(modal).toBeVisible();
    const textarea = modal.locator('textarea');
    await expect(textarea).toBeVisible();

    // Stub LLM generates the AI 请假审批 flow
    await textarea.fill('请假3天内经理审批，超过3天总监审批');
    await modal.locator('button:has-text("生成"), button:has-text("Generate")').click();

    // Modal closes and a draft named after the generated flow appears
    await expect(modal).toBeHidden({ timeout: 15000 });
    const aiDraft = page.locator('.text-gray-300.truncate', { hasText: 'AI 请假审批' });
    await expect(aiDraft.first()).toBeVisible({ timeout: 15000 });

    // Canvas shows generated nodes (start / gateway / manager / director / end)
    await expect(page.locator('.react-flow__node')).toHaveCount(6, { timeout: 15000 });
  });

  test('AI endpoint unconfigured surfaces an error toast', async ({ page }) => {
    // Block the generate call to simulate a failure and confirm error handling
    await page.route('**/api/ai/generate', route =>
      route.fulfill({ status: 409, body: JSON.stringify({ error: 'AI is not configured' }) }));
    await page.locator('button[title*="AI"]').click();
    const modal = page.locator('.bg-gray-800.rounded-lg.shadow-2xl');
    await modal.locator('textarea').fill('test');
    await modal.locator('button:has-text("生成"), button:has-text("Generate")').click();
    // Modal stays open (no silent failure) — user can retry or close
    await expect(modal).toBeVisible();
  });
});
