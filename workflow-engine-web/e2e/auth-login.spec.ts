import { test, expect } from './fixtures';

/**
 * Browser login flow. These tests deliberately start from a clean session
 * (no localStorage userId) so the login gate is exercised. The shared fixtures
 * used by the rest of the suite keep their X-User-Id and skip this gate.
 *
 * The E2E browser reports an English locale, so the UI renders in English.
 */
test.describe('Browser login', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.removeItem('userId');
      localStorage.setItem('lang', 'en');
    });
  });

  test('login page shown to unauthenticated browser', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('Sign in to your workspace')).toBeVisible({ timeout: 8000 });
    await expect(page.locator('input[autocomplete="username"]')).toBeVisible();
    await expect(page.locator('input[autocomplete="current-password"]')).toBeVisible();
  });

  test('bad credentials show an error and stay on login', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('Sign in to your workspace')).toBeVisible({ timeout: 8000 });
    await page.locator('input[autocomplete="username"]').fill('admin');
    await page.locator('input[autocomplete="current-password"]').fill('wrong-password');
    await page.getByRole('button', { name: /Sign in/ }).click();
    await expect(page.getByText(/Invalid username/)).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('Sign in to your workspace')).toBeVisible();
  });

  test('valid login enters the app and shows the user', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('Sign in to your workspace')).toBeVisible({ timeout: 8000 });
    await page.locator('input[autocomplete="username"]').fill('admin');
    await page.locator('input[autocomplete="current-password"]').fill('admin123');
    await page.getByRole('button', { name: /Sign in/ }).click();
    // App shell appears with the designer tab and the current user's name.
    await expect(page.getByRole('button', { name: 'Designer' })).toBeVisible({ timeout: 8000 });
    await expect(page.getByText('系统管理员')).toBeVisible();
  });

  test('logout returns to login screen', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('Sign in to your workspace')).toBeVisible({ timeout: 8000 });
    await page.locator('input[autocomplete="username"]').fill('admin');
    await page.locator('input[autocomplete="current-password"]').fill('admin123');
    await page.getByRole('button', { name: /Sign in/ }).click();
    await expect(page.getByRole('button', { name: 'Designer' })).toBeVisible({ timeout: 8000 });
    await page.locator('button[title="Sign out"]').click();
    await expect(page.getByText('Sign in to your workspace')).toBeVisible({ timeout: 8000 });
  });
});
