// @vitest-environment node
import { readFile } from 'node:fs/promises';
import { expect, test } from 'vitest';
import { API_TYPES_FILE, generateApiTypes } from './api-types.ts';

test('the API types match the task manager OpenAPI document; refresh them with npm run generate:api', async () => {
  const committed = await readFile(API_TYPES_FILE, 'utf8');
  expect(committed).toBe(await generateApiTypes());
});
