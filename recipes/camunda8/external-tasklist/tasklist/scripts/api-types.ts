// The TypeScript types of the task manager API, generated from its OpenAPI document with openapi-typescript.
// `npm run generate:api` writes them to src/api/taskmanager.gen.ts; a test fails when that file is out of date.
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

/** The OpenAPI document of the task manager, kept in sync with its code by the task manager's own snapshot test. */
export const API_DOCUMENT = new URL('../../docs/api/taskmanager.json', import.meta.url);

export const API_TYPES_FILE = fileURLToPath(new URL('../src/api/taskmanager.gen.ts', import.meta.url));

const HEADER = `// Generated from docs/api/taskmanager.json by \`npm run generate:api\` (openapi-typescript). Do not edit.

`;

export async function generateApiTypes(): Promise<string> {
  const ast = await openapiTS(API_DOCUMENT);
  return HEADER + astToString(ast);
}
