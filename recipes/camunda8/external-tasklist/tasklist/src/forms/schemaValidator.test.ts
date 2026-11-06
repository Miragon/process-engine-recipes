import { afterEach, describe, expect, test, vi } from 'vitest';
import { controlPath } from './jsonFormsErrors';
import { createSchemaValidator } from './schemaValidator';

const SCHEMA = {
  type: 'object',
  properties: {
    paymentReceived: { type: 'boolean' },
    amountReceived: { type: 'number', minimum: 0 },
    rejectionReason: { type: 'string', maxLength: 5 },
    customer: { type: 'object', properties: { email: { type: 'string', format: 'email' } }, required: ['email'] },
    shippingDate: { type: 'string', format: 'date' },
  },
  required: ['paymentReceived'],
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('the schema validator for JSON Forms', () => {
  test('reports failures like Ajv, at the path of their control', () => {
    const validate = createSchemaValidator().compile(SCHEMA);

    const valid = validate({ amountReceived: -1, rejectionReason: 'far too long', customer: {}, shippingDate: '2026-13-45' });

    expect(valid).toBe(false);
    expect(validate.errors?.map((error) => [controlPath(error), error.keyword, error.params])).toEqual(
      expect.arrayContaining([
        ['paymentReceived', 'required', { missingProperty: 'paymentReceived' }],
        ['amountReceived', 'minimum', { limit: 0 }],
        ['rejectionReason', 'maxLength', { limit: 5 }],
        ['customer.email', 'required', { missingProperty: 'email' }],
        ['shippingDate', 'format', { format: 'date' }],
      ]),
    );
    expect(validate.errors?.some((error) => error.keyword === 'properties')).toBe(false);
    expect(validate.errors?.find((error) => error.keyword === 'required')?.parentSchema).toBe(SCHEMA);
  });

  test('accepts valid data and clears the errors', () => {
    const validate = createSchemaValidator().compile(SCHEMA);

    validate({});
    expect(validate({ paymentReceived: true, amountReceived: 89.9, customer: { email: 'anna@example.com' }, shippingDate: '2026-12-01' })).toBe(true);
    expect(validate.errors).toBeNull();
  });

  test('evaluates the schema conditions of UI schema rules', () => {
    const validator = createSchemaValidator();
    const condition = { const: true };

    expect(validator.validate(condition, true)).toBe(true);
    expect(validator.validate(condition, false)).toBe(false);
    expect(validator.validate(condition, undefined)).toBe(false);
    expect(validator.compile(condition)).toBe(validator.compile(condition));
  });

  test('leaves the schema of JSON Forms unchanged', () => {
    const schema = structuredClone(SCHEMA);

    createSchemaValidator().compile(schema)({});

    expect(schema).toEqual(SCHEMA);
  });

  test('works where the content security policy forbids generated code', () => {
    vi.stubGlobal('Function', () => {
      throw new EvalError("Refused to evaluate a string as JavaScript because 'unsafe-eval' is not allowed");
    });

    const validate = createSchemaValidator().compile(SCHEMA);

    expect(validate({ paymentReceived: false })).toBe(true);
  });
});
