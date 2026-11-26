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
        // Pin the UI language: the app auto-detects from navigator.language,
        // and without this Chromium inherits the OS locale (zh-CN on dev
        // machines), breaking the English text selectors most specs rely on.
        locale: 'en',
        baseURL: 'http://localhost:3000',
        storageState: 'e2e/.auth.json',
      },
    },
  ],

  webServer: [
    {
      // Stub OpenAI-compatible LLM used by the AI designer specs. Set
      // STUB_REJECT_TEMPERATURE=1 to make it behave like an o-series / GPT-5
      // model that rejects `temperature`, exercising the client fallback.
      command: 'node ../docker/stub-llm.js',
      port: 9999,
      timeout: 15000,
      reuseExistingServer: !process.env.CI,
    },
    {
      // Deliberately the in-memory profile: the specs assert engine behaviour,
      // not persistence. Pointing them at a MySQL-backed server makes every
      // write wait on binlog fsync and the suite times out.
      command: 'java -jar ../workflow-engine-server/target/workflow-engine-server-1.0.0-SNAPSHOT.jar '
        + '--spring.profiles.active=memory,mock-ldap --server.port=8080 '
        // E2E API helpers use the legacy X-User-Id header to switch tenants.
        // Opt in explicitly here; production defaults to header-compat disabled.
        + '--auth.header-compat-enabled=true '
        + '--ai.base-url=http://127.0.0.1:9999/v1 --ai.api-key=stub --ai.model=stub-model',
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
