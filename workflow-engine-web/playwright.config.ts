import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? 'html' : 'list',
  timeout: 30000,
  expect: { timeout: 10000 },
  globalSetup: './e2e/global-setup',

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: 'http://localhost:3000',
        storageState: 'e2e/.auth.json',
      },
    },
  ],

  webServer: [
    {
      command: 'java -jar ../workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=memory,mock-ldap --server.port=8080',
      port: 8080,
      timeout: 30000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run dev',
      port: 3000,
      timeout: 15000,
      reuseExistingServer: !process.env.CI,
    },
  ],
});
