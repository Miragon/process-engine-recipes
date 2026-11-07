// Regenerates src/api/taskmanager.gen.ts after the task manager's OpenAPI document changed.
import { writeFile } from 'node:fs/promises';
import { API_TYPES_FILE, generateApiTypes } from './api-types.ts';

await writeFile(API_TYPES_FILE, await generateApiTypes());
console.info(`Wrote ${API_TYPES_FILE}`);
