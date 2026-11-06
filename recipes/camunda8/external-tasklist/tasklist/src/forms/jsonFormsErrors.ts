// Field errors of a process application next to the controls of a JSON form, and plain English for schema errors.
import type { ErrorTranslator } from '@jsonforms/core';
import type { JsonFormsInitStateProps } from '@jsonforms/react';
import type { FieldError, FormData } from '../api/types';
import { fromPointer, toPointer } from './pointer';

export type FormError = NonNullable<JsonFormsInitStateProps['additionalErrors']>[number];

/** The segments of a field as the applications name it, with dots (`customer.email`, `items.0.ready`, `items[0]`). */
function fieldSegments(field: string): string[] {
  return field
    .replace(/\[(\d+)\]/g, '.$1')
    .split('.')
    .filter((segment) => segment !== '');
}

/** The JSON pointer JSON Forms matches for a field of the application. */
export function toInstancePath(field: string): string {
  return toPointer(fieldSegments(field));
}

/** A field error of the application as an error JSON Forms shows at the control of that field. */
export function toFormError({ field, message }: FieldError): FormError {
  return { instancePath: toInstancePath(field), schemaPath: '#/application', keyword: 'application', params: { field }, message };
}

/**
 * The dotted path of the control an error belongs to, as JSON Forms computes it (`getControlPath`), without loading
 * JSON Forms itself before a form is shown.
 */
export function controlPath(error: FormError): string {
  const params = error.params as Record<string, unknown>;
  const property = ['required', 'dependencies'].includes(error.keyword) ? params.missingProperty : undefined;
  const path = fromPointer(error.instancePath);
  return [...path, ...(typeof property === 'string' ? [property] : [])].join('.');
}

/**
 * What the form shows: the application's field errors, and the schema's errors for all other fields. They are shown
 * only after the application refused a submission, so the form does not greet the user with errors.
 */
export function visibleErrors(fieldErrors: FieldError[], schemaErrors: FormError[]): FormError[] {
  const application = fieldErrors.map(toFormError);
  const taken = new Set(application.map(controlPath));
  return [...application, ...schemaErrors.filter((error) => !taken.has(controlPath(error)))];
}

/** How many fields the shown errors concern. */
export function fieldCount(errors: FormError[]): number {
  return new Set(errors.map(controlPath)).size;
}

/** The value at a dotted path, for telling which field the user changed. */
export function valueAt(data: unknown, field: string): unknown {
  return fieldSegments(field).reduce<unknown>((value, segment) => (typeof value === 'object' && value !== null ? (value as Record<string, unknown>)[segment] : undefined), data);
}

/**
 * Leaves out `null` values: JSON Schema types reject them, while a missing value is simply not filled in yet.
 * The applications read a missing field as `null` again.
 */
export function withoutNulls(data: FormData): FormData {
  const clean = (value: unknown): unknown => {
    if (Array.isArray(value)) return value.map(clean);
    if (typeof value !== 'object' || value === null) return value;
    return Object.fromEntries(
      Object.entries(value)
        .filter(([, entry]) => entry !== null)
        .map(([key, entry]) => [key, clean(entry)]),
    );
  };
  return clean(data) as FormData;
}

/** Schema errors in the tone of the interface; messages of the application pass unchanged. */
export const translateError: ErrorTranslator = (error) => {
  const params = error.params as Record<string, unknown>;
  const limit = typeof params.limit === 'number' ? String(params.limit) : '';
  switch (error.keyword) {
    case 'application':
      return error.message ?? 'Check this field.';
    case 'required': {
      const property = typeof params.missingProperty === 'string' ? params.missingProperty : '';
      const schema = (error.parentSchema as { properties?: Record<string, { type?: unknown }> } | undefined)?.properties?.[property];
      return schema?.type === 'boolean' ? 'Choose yes or no.' : 'Fill in this field.';
    }
    case 'minimum':
      return `Enter ${limit} or more.`;
    case 'exclusiveMinimum':
      return `Enter more than ${limit}.`;
    case 'maximum':
      return `Enter ${limit} or less.`;
    case 'exclusiveMaximum':
      return `Enter less than ${limit}.`;
    case 'minLength':
      return limit === '1' ? 'Fill in this field.' : `Use at least ${limit} characters.`;
    case 'maxLength':
      return `Use at most ${limit} characters.`;
    case 'format':
      return params.format === 'date' ? 'Enter a date.' : `Enter a valid ${String(params.format)}.`;
    case 'enum':
    case 'const':
      return 'Choose one of the offered values.';
    case 'type':
      return 'Enter a valid value.';
    default:
      return error.message ?? 'Check this field.';
  }
};
