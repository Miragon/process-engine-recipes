// JSON Schema validation for JSON Forms that works under the tasklist's content security policy.
// Ajv, which JSON Forms uses by default, compiles every schema to a function with `new Function`; `script-src 'self'`
// forbids that. JSON Forms calls only `compile` (validation, combinators) and `validate` (rule conditions) on its Ajv
// instance, so this module offers both on top of @cfworker/json-schema, which interprets schemas instead.
import { Validator, type OutputUnit } from '@cfworker/json-schema';
import type { FormError } from './jsonFormsErrors';
import { fromPointer, toPointer } from './pointer';

export type ValidateFunction = ((data: unknown) => boolean) & { errors: FormError[] | null; schema: unknown };

export interface SchemaValidator {
  compile: (schema: unknown) => ValidateFunction;
  validate: (schema: unknown, data: unknown) => boolean;
}

/** Errors about a subschema that failed; Ajv reports only the failures inside it, and so does this validator. */
const ENCLOSING = new Set([
  '$ref',
  '$recursiveRef',
  'additionalItems',
  'allOf',
  'contains',
  'dependentSchemas',
  'else',
  'if',
  'items',
  'prefixItems',
  'properties',
  'patternProperties',
  'propertyNames',
  'then',
  'unevaluatedItems',
  'unevaluatedProperties',
]);

const LIMITS = new Set([
  'minimum',
  'maximum',
  'exclusiveMinimum',
  'exclusiveMaximum',
  'minLength',
  'maxLength',
  'minItems',
  'maxItems',
  'minProperties',
  'maxProperties',
  'multipleOf',
]);

function at(root: unknown, path: string[]): unknown {
  return path.reduce<unknown>((node, segment) => (typeof node === 'object' && node !== null ? (node as Record<string, unknown>)[segment] : undefined), root);
}

/** One failure in the shape of an Ajv error, which JSON Forms and the error translator read. */
function toFormError(unit: OutputUnit, root: unknown): FormError {
  const keywordPath = fromPointer(unit.keywordLocation);
  const parentSchema = at(root, keywordPath.slice(0, -1));
  const value = (parentSchema as Record<string, unknown> | undefined)?.[unit.keyword];
  const params: Record<string, unknown> = {};
  if (unit.keyword === 'required') params.missingProperty = /required property "(.*)"/.exec(unit.error)?.[1];
  else if (LIMITS.has(unit.keyword)) params.limit = value;
  else if (unit.keyword === 'format') params.format = value;
  else if (unit.keyword === 'type') params.type = value;
  else if (unit.keyword === 'const') params.allowedValue = value;
  else if (unit.keyword === 'enum') params.allowedValues = value;
  else if (unit.keyword === 'pattern') params.pattern = value;
  const instancePath = toPointer(fromPointer(unit.instanceLocation));
  return { instancePath, schemaPath: unit.keywordLocation, keyword: unit.keyword, params, message: unit.error, schema: value, parentSchema: parentSchema as object };
}

/**
 * Whether Ajv would accept a missing value (`undefined`), which the interpreter does not take. Rule conditions ask
 * this for fields that are not filled in yet: type, const and enum fail, keywords for other types pass.
 */
function acceptsMissing(schema: unknown): boolean {
  if (typeof schema === 'boolean') return schema;
  if (typeof schema !== 'object' || schema === null) return true;
  const { type, const: constant, enum: values, not, allOf, anyOf, oneOf } = schema as Record<string, unknown>;
  if (type !== undefined || constant !== undefined || values !== undefined) return false;
  if (not !== undefined && acceptsMissing(not)) return false;
  if (Array.isArray(allOf) && !allOf.every(acceptsMissing)) return false;
  if (Array.isArray(anyOf) && !anyOf.some(acceptsMissing)) return false;
  if (Array.isArray(oneOf) && oneOf.filter(acceptsMissing).length !== 1) return false;
  return true;
}

const MISSING: FormError = { instancePath: '', schemaPath: '#', keyword: 'type', params: {}, message: 'There is no value.' };

/** Validates like the Ajv instance JSON Forms creates (draft 7, all errors), without generating code. */
export function createSchemaValidator(): SchemaValidator {
  const compiled = new WeakMap<object, ValidateFunction>();

  const compile = (schema: unknown): ValidateFunction => {
    const key = typeof schema === 'object' && schema !== null ? schema : null;
    const known = key ? compiled.get(key) : undefined;
    if (known) return known;
    // The validator annotates the schema it gets; JSON Forms compares its schemas, so it gets a copy.
    const validator = new Validator(structuredClone(schema ?? true), '7', false);
    const validate = ((data: unknown) => {
      if (data === undefined) {
        const accepted = acceptsMissing(schema);
        validate.errors = accepted ? null : [MISSING];
        return accepted;
      }
      const result = validator.validate(data);
      validate.errors = result.valid ? null : result.errors.filter((unit) => !ENCLOSING.has(unit.keyword)).map((unit) => toFormError(unit, schema));
      return result.valid;
    }) as ValidateFunction;
    validate.errors = null;
    validate.schema = schema;
    if (key) compiled.set(key, validate);
    return validate;
  };

  return { compile, validate: (schema, data) => compile(schema)(data) };
}
