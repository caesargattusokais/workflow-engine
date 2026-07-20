import { chromium, type FullConfig } from '@playwright/test';

export default async function globalSetup(config: FullConfig) {
  const { storageState } = config.projects[0].use;
  const browser = await chromium.launch();
  const page = await browser.newPage();

  // Set localStorage userId for auth
  await page.goto('http://localhost:3000');
  await page.evaluate(() => localStorage.setItem('userId', 'testuser'));
  await page.context().storageState({ path: storageState as string });

  await browser.close();
}
