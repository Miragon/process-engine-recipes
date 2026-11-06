import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { viteSingleFile } from 'vite-plugin-singlefile';
import { defineConfig } from 'vitest/config';

// The form protocol is shared with the tasklist and lives in the contract module.
const protocolDirectory = fileURLToPath(new URL('../../../tasklist-contract/src/main/typescript', import.meta.url));

// The tasklist loads the forms into a sandboxed frame whose content security policy allows no requests,
// so the build inlines everything (scripts, styles, the font) into one index.html inside the application jar.
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  resolve: {
    alias: { '@tasklist/form-protocol': `${protocolDirectory}/form-protocol.ts` },
  },
  server: {
    fs: { allow: ['.', protocolDirectory] },
  },
  build: {
    outDir: '../target/classes/tasklist/forms',
    emptyOutDir: true,
    modulePreload: { polyfill: false },
  },
  test: {
    environment: 'node',
  },
});
