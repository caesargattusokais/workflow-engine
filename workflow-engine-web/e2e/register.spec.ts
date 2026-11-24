import { test, expect } from './fixtures';

/**
 * Browser self-registration flow. Starts from a clean session so the auth
 * gate is exercised. E2E browser locale is English.
 * Each test uses a unique username to avoid cross-test collisions in the
 * shared in-process user store.
 */
test.describe('Browser registration', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.removeItem('userId');
      localStorage.setItem('lang', 'en');
    });
  });

  test('login page offers a registration link', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('Sign in to your workspace')).toBeVisible({ timeout: 8000 });
    await expect(page.getByRole('button', { name: 'Sign up' })).toBeVisible();
  });

  test('password mismatch is rejected client-side', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Sign up' })).toBeVisible({ timeout: 8000 });
    await page.getByRole('button', { name: 'Sign up' }).click();
    await expect(page.getByText('Create your account')).toBeVisible();

    const uname = 'mismatch' + Date.now();
    await page.locator('input[autocomplete="username"]').fill(uname);
    await page.locator('input[autocomplete="new-password"]').first().fill('pass1234');
    await page.locator('input[autocomplete="new-password"]').last().fill('different');
    await page.getByRole('button', { name: /Create account/ }).click();
    await expect(page.getByText(/Passwords do not match/)).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('Create your account')).toBeVisible();
  });

  test('register signs in immediately and shows the new user', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Sign up' })).toBeVisible({ timeout: 8000 });
    await page.getByRole('button', { name: 'Sign up' }).click();
    await expect(page.getByText('Create your account')).toBeVisible();

    const uname = 'newuser' + Date.now();
    await page.locator('input[autocomplete="username"]').fill(uname);
    await page.getByPlaceholder('Your name').fill('Fresh User');
    await page.locator('input[autocomplete="new-password"]').first().fill('pass1234');
    await page.locator('input[autocomplete="new-password"]').last().fill('pass1234');
    await page.getByRole('button', { name: /Create account/ }).click();

    // App shell appears — registration auto-signed-in.
    await expect(page.getByRole('button', { name: 'Designer' })).toBeVisible({ timeout: 8000 });
    await expect(page.getByText('Fresh User')).toBeVisible();
  });

  test('duplicate username is rejected with a server error', async ({ page, request }) => {
    // API-level test for the duplicate path (browser registers via API too).
    const uname = 'dupe' + Date.now();
    const base = '/api';
    const first = await request.post(`${base}/auth/register`, {
      data: { username: uname, password: 'pass1234', displayName: 'First' }
    });
    expect(first.ok()).toBeTruthy();

    const second = await request.post(`${base}/auth/register`, {
      data: { username: uname.toUpperCase(), password: 'pass1234' }
    });
    expect(second.status()).toBe(409);
  });

  test('back to sign in returns to login page', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Sign up' })).toBeVisible({ timeout: 8000 });
    await page.getByRole('button', { name: 'Sign up' }).click();
    await expect(page.getByText('Create your account')).toBeVisible();
    await page.getByRole('button', { name: /Back to sign in/ }).click();
    await expect(page.getByText('Sign in to your workspace')).toBeVisible();
  });
});
