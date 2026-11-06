import { defineConfig } from '@playwright/test';

// The specs share one running stack (./e2e.sh starts it) and create their own orders, one after another.
export default defineConfig({
  timeout: 120_000,
  expect: { timeout: 30_000 },
  workers: 1,
  reporter: [['list']],
  projects: [
    { name: 'api', testDir: './api' },
  ],
});
