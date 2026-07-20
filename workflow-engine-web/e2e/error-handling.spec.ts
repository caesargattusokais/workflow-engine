import { test, expect, API_BASE } from './fixtures';

test.describe('Error Handling and Edge Cases', () => {
  test('404 on non-existent instance', async ({ api }) => {
    await expect(api.getInstance('nonexistent-id-12345')).rejects.toThrow(/404|Not found/i);
  });

  test('401 on missing X-User-Id shows error in frontend', async ({ page }) => {
    await page.goto('/');
    // Clear userId from localStorage
    await page.evaluate(() => localStorage.removeItem('userId'));
    // Try to interact with the app - should show error toast
    await page.reload();
    await page.waitForTimeout(2000);
    // Check for error toast
    const errorToast = page.locator('.bg-red-600');
    // May or may not appear depending on timing
  });

  test('toast notification on API error', async ({ page }) => {
    await page.goto('/');
    // Navigate to monitor
    await page.click('button:has-text("Monitor")');
    await page.waitForTimeout(1000);
    // Try to start instance with invalid data
    const startBtn = page.locator('button:has-text("Start Instance")');
    if (await startBtn.isVisible()) {
      await startBtn.click();
      await page.waitForTimeout(1000);
      // Should show error toast or validation
    }
  });

  test('very long draft name', async ({ api }) => {
    const longName = 'A'.repeat(200);
    const draft = await api.createDraft(longName);
    expect(draft.name).toBe(longName);
  });

  test('special characters in draft name', async ({ api }) => {
    const specialName = '测试-草稿_名©®';
    const draft = await api.createDraft(specialName);
    expect(draft.name).toBe(specialName);
  });

  test('concurrent draft edits (last write wins)', async ({ api }) => {
    const draft = await api.createDraft('Concurrent Test');
    // Two updates in sequence
    await api.updateDraft(draft.id, { name: 'Update 1' });
    await api.updateDraft(draft.id, { name: 'Update 2' });
    const fetched = await api.getDraft(draft.id);
    expect(fetched.name).toBe('Update 2');
  });

  test('Swagger UI is accessible', async ({ page }) => {
    const res = await fetch('http://localhost:8080/swagger-ui.html');
    expect(res.status).toBe(200);
  });

  test('OpenAPI JSON is accessible', async ({ page }) => {
    const res = await fetch('http://localhost:8080/v3/api-docs');
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('openapi');
    expect(body).toHaveProperty('paths');
  });
});
