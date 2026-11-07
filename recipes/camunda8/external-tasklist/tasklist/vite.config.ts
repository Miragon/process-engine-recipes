import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The form protocol is shared with the HTML forms of the process applications and lives in the contract module.
const protocolDirectory = fileURLToPath(new URL('../tasklist-contract/src/main/typescript', import.meta.url));

// In development the tasklist talks to the task manager of the running stack, as nginx does in the container.
// The proxy streams responses, so the server-sent events of /api/events arrive unbuffered.
const api = { '/api': { target: process.env.TASKMANAGER_URL ?? 'http://localhost:8090' } };

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@tasklist/form-protocol': `${protocolDirectory}/form-protocol.ts` },
  },
  server: {
    proxy: api,
    fs: { allow: ['.', protocolDirectory] },
  },
  preview: {
    proxy: api,
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    // The content security policy allows fonts and images only from the tasklist's own origin, so nothing is inlined.
    assetsInlineLimit: 0,
    modulePreload: { polyfill: false },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}', 'scripts/**/*.test.ts'],
    restoreMocks: true,
  },
});
