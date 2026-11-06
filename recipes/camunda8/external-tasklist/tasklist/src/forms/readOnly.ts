import type { JsonSchema } from '@jsonforms/core';

/** A plain value the schema marks `readOnly`: the form shows it as a fact instead of a disabled input. */
export function isReadOnlySchema(schema: JsonSchema | undefined): boolean {
  if (schema === undefined) return false;
  return (schema as { readOnly?: unknown }).readOnly === true && schema.type !== 'object' && schema.type !== 'array';
}
