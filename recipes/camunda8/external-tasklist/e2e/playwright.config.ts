import { defineConfig, devices } from '@playwright/test';

// The specs share one running stack (./e2e.sh starts it) and create their own orders, one after another.
// `api` checks the task manager and the applications directly, `ui` the tasklist in Chromium through nginx.
export default defineConfig({
  timeout: 120_000,
  expect: { timeout: 30_000 },
  workers: 1,
  reporter: [['list']],
  projects: [
    { name: 'api', testDir: './api' },
    {
      name: 'ui',
      testDir: './ui',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: 'http://localhost:3000',
        viewport: { width: 1440, height: 900 },
        locale: 'en-US',
        timezoneId: 'Europe/Berlin',
        trace: 'retain-on-failure',
      },
    },
  ],
});
