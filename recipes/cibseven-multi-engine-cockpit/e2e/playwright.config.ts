import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  timeout: 120_000,
  use: {
    baseURL: 'http://localhost:8080/webapp/',
    locale: 'en-US',
    viewport: { width: 1280, height: 720 },
    trace: 'retain-on-failure',
  },
});
