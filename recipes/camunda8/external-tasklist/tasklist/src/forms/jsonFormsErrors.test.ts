import { describe, expect, test } from 'vitest';
import { controlPath, fieldCount, toFormError, toInstancePath, translateError, valueAt, visibleErrors, withoutNulls, type FormError } from './jsonFormsErrors';

const required = (property: string, parentSchema: object = {}): FormError => ({
  instancePath: '',
  schemaPath: '#/required',
  keyword: 'required',
  params: { missingProperty: property },
  message: `must have required property '${property}'`,
  parentSchema,
});

describe('field errors of the applications in JSON Forms', () => {
  test('turn dotted field names into JSON pointers', () => {
    expect(toInstancePath('amountReceived')).toBe('/amountReceived');
    expect(toInstancePath('customer.email')).toBe('/customer/email');
    expect(toInstancePath('items[0].ready')).toBe('/items/0/ready');
    expect(toInstancePath('a/b~c')).toBe('/a~1b~0c');
    expect(toInstancePath('')).toBe('');
  });

  test('land at the control of their field', () => {
    const error = toFormError({ field: 'customer.email', message: 'Enter a valid email address.' });

    expect(error).toMatchObject({ instancePath: '/customer/email', keyword: 'application', message: 'Enter a valid email address.' });
    expect(controlPath(error)).toBe('customer.email');
    expect(controlPath(required('paymentReceived'))).toBe('paymentReceived');
  });

  test('take the place of schema errors of the same field', () => {
    const shown = visibleErrors(
      [{ field: 'paymentReceived', message: 'State whether the payment was received.' }],
      [required('paymentReceived'), { ...required('x'), keyword: 'maxLength', instancePath: '/rejectionReason', params: { limit: 500 } }],
    );

    expect(shown.map((error) => [controlPath(error), error.message])).toEqual([
      ['paymentReceived', 'State whether the payment was received.'],
      ['rejectionReason', "must have required property 'x'"],
    ]);
    expect(fieldCount(shown)).toBe(2);
  });

  test('read in plain English', () => {
    const translate = (error: FormError) => translateError(error, () => undefined as never);

    expect(translate(required('paymentReceived', { properties: { paymentReceived: { type: 'boolean' } } }))).toBe('Choose yes or no.');
    expect(translate(required('shippingDate', { properties: { shippingDate: { type: 'string' } } }))).toBe('Fill in this field.');
    expect(translate({ ...required('x'), keyword: 'minimum', params: { limit: 0 } })).toBe('Enter 0 or more.');
    expect(translate({ ...required('x'), keyword: 'maxLength', params: { limit: 500 } })).toBe('Use at most 500 characters.');
    expect(translate(toFormError({ field: 'comment', message: 'Give a reason.' }))).toBe('Give a reason.');
  });
});

describe('form data', () => {
  test('leaves out null values, which JSON Schema types would reject', () => {
    expect(withoutNulls({ orderId: 'ORD-1', shippingDate: null, items: [{ itemId: 'a', note: null }], nested: { keep: 0, drop: null } })).toEqual({
      orderId: 'ORD-1',
      items: [{ itemId: 'a' }],
      nested: { keep: 0 },
    });
  });

  test('reads the value of a dotted field', () => {
    const data = { customer: { email: 'anna@example.com' }, items: [{ ready: true }] };

    expect(valueAt(data, 'customer.email')).toBe('anna@example.com');
    expect(valueAt(data, 'items[0].ready')).toBe(true);
    expect(valueAt(data, 'customer.phone')).toBeUndefined();
  });
});
