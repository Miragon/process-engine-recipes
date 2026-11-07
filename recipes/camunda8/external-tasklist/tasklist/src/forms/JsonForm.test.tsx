import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';
import type { JsonFormsView } from '../api/types';
import { JsonForm } from './JsonForm';
import { toFormError } from './jsonFormsErrors';

afterEach(cleanup);

/** The form `check-payment@1` of the payment application, as the task manager hands it over. */
const CHECK_PAYMENT: JsonFormsView = {
  type: 'json-forms',
  formRef: 'check-payment@1',
  readOnly: false,
  draftable: false,
  data: { paymentId: 'PAY-1017', orderId: 'ORD-1039', invoiceAmount: '89.00 EUR' },
  schema: {
    type: 'object',
    properties: {
      paymentId: { type: 'string', title: 'Payment', readOnly: true },
      orderId: { type: 'string', title: 'Order', readOnly: true },
      invoiceAmount: { type: 'string', title: 'Invoice amount', readOnly: true },
      paymentReceived: { type: 'boolean', title: 'Payment received' },
      amountReceived: { type: 'number', title: 'Amount received', minimum: 0 },
      rejectionReason: { type: 'string', title: 'Reason for the rejection', maxLength: 500 },
    },
    required: ['paymentReceived'],
  },
  uiSchema: {
    type: 'VerticalLayout',
    elements: [
      {
        type: 'Group',
        label: 'Invoice',
        elements: [
          { type: 'HorizontalLayout', elements: [{ type: 'Control', scope: '#/properties/paymentId' }, { type: 'Control', scope: '#/properties/orderId' }] },
          { type: 'Control', scope: '#/properties/invoiceAmount' },
        ],
      },
      {
        type: 'Group',
        label: 'Check',
        elements: [
          { type: 'Control', scope: '#/properties/paymentReceived' },
          {
            type: 'Control',
            scope: '#/properties/amountReceived',
            rule: { effect: 'SHOW', condition: { scope: '#/properties/paymentReceived', schema: { const: true } } },
          },
          {
            type: 'Control',
            scope: '#/properties/rejectionReason',
            options: { multi: true },
            rule: { effect: 'SHOW', condition: { scope: '#/properties/paymentReceived', schema: { const: false } } },
          },
        ],
      },
    ],
  },
};

describe('a JSON form in the Miragon style', () => {
  test('shows read-only fields as facts and yes or no as a choice', () => {
    render(<JsonForm form={CHECK_PAYMENT} data={CHECK_PAYMENT.data} readOnly={false} errors={[]} onChange={vi.fn()} />);

    expect(screen.getByText('Invoice')).toBeDefined();
    expect(screen.getByText('PAY-1017')).toBeDefined();
    expect(screen.queryByRole('textbox', { name: 'Payment' })).toBeNull();
    const choice = screen.getByRole('group', { name: 'Payment received' });
    expect(choice.querySelectorAll('input[type="radio"]')).toHaveLength(2);
    // Shown only once the payment was received.
    expect(screen.queryByRole('spinbutton', { name: 'Amount received' })).toBeNull();
  });

  test('reports what the user enters', async () => {
    const onChange = vi.fn();
    render(<JsonForm form={CHECK_PAYMENT} data={CHECK_PAYMENT.data} readOnly={false} errors={[]} onChange={onChange} />);

    fireEvent.click(screen.getByRole('radio', { name: 'Yes' }));

    await waitFor(() => {
      expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ paymentReceived: true }), expect.any(Array));
    });
  });

  test('shows a field error of the application at its control and links it to the input', () => {
    const message = 'The received amount must match the invoice amount of 89.00 EUR';
    render(
      <JsonForm
        form={CHECK_PAYMENT}
        data={{ ...CHECK_PAYMENT.data, paymentReceived: true, amountReceived: 80 }}
        readOnly={false}
        errors={[toFormError({ field: 'amountReceived', message })]}
        onChange={vi.fn()}
      />,
    );

    const input = screen.getByLabelText('Amount received');
    expect(screen.getByText(message)).toBeDefined();
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(document.getElementById(input.getAttribute('aria-describedby') ?? '')?.textContent).toBe(message);
  });

  test('disables every input while read-only', () => {
    render(<JsonForm form={CHECK_PAYMENT} data={{ ...CHECK_PAYMENT.data, paymentReceived: false }} readOnly errors={[]} onChange={vi.fn()} />);

    expect(screen.getAllByRole('radio').every((radio) => (radio as HTMLInputElement).disabled)).toBe(true);
    expect(screen.getByLabelText<HTMLTextAreaElement>('Reason for the rejection').disabled).toBe(true);
  });
});
