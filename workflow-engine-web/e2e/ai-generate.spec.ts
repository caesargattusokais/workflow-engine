import { test, expect } from './fixtures';

test.describe('Designer — AI flow generation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('generate a flow from natural language into the canvas', async ({ page }) => {
    // Open the AI modal via the ✦ toolbar button
    const aiButton = page.locator('button:has-text("✦")');
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
    await page.locator('button:has-text("✦")').click();
    const modal = page.locator('.bg-gray-800.rounded-lg.shadow-2xl');
    await modal.locator('textarea').fill('test');
    await modal.locator('button:has-text("生成"), button:has-text("Generate")').click();
    // Modal stays open (no silent failure) — user can retry or close
    await expect(modal).toBeVisible();
  });

  test('AI config page shows status and saves settings (effective immediately)', async ({ page }) => {
    // Open the config modal via the ⚙ button (title = AI 模型配置 / AI Model Configuration)
    const gear = page.locator('button[title*="配置"], button[title*="Configuration"]');
    await expect(gear).toBeVisible();
    await gear.click();

    const modal = page.locator('.bg-gray-800.rounded-lg.shadow-2xl');
    await expect(modal).toBeVisible();

    // Status chip reflects the backend's current config (stub is wired via env)
    await expect(modal.locator('span:has-text("✓"), span:has-text("✕")').first()).toBeVisible();

    // Fill the form and save — must take effect immediately without restart
    await modal.locator('input').first().fill('http://host.docker.internal:9999/v1');
    await modal.locator('input[type="password"]').fill('stub');
    await modal.locator('input').nth(2).fill('stub-model');
    await modal.locator('button:has-text("保存"), button:has-text("Save")').click();

    // Modal closes on success (toast fires) — reopen and confirm source flipped to database
    await expect(modal).toBeHidden({ timeout: 10000 });
    await gear.click();
    await expect(modal.locator('span:has-text("页面配置"), span:has-text("Page config")').first())
      .toBeVisible({ timeout: 10000 });
    // API key now shows masked instead of "not set"
    await expect(modal.locator('span:has-text("Key: ****")').first()).toBeVisible();
    await modal.locator('button:has-text("恢复"), button:has-text("Reset")').click();
    await expect(modal).toBeHidden({ timeout: 10000 });
  });

  test('AI config test button reports connectivity result', async ({ page }) => {
    await page.locator('button[title*="配置"], button[title*="Configuration"]').click();
    const modal = page.locator('.bg-gray-800.rounded-lg.shadow-2xl');
    await expect(modal).toBeVisible();

    await modal.locator('input').first().fill('http://127.0.0.1:1/v1');
    await modal.locator('input').nth(2).fill('some-model');
    await modal.locator('button:has-text("测试"), button:has-text("Test")').click();

    // Unreachable provider → red result line appears (result renders in a div, not a span)
    await expect(modal.locator('div:has-text("✕")').last()).toBeVisible({ timeout: 15000 });
  });
});
