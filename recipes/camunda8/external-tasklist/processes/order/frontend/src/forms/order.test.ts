import { describe, expect, it } from 'vitest';
import {
  checkOrderSubmission,
  customerErrors,
  decisionErrors,
  formatMoney,
  itemErrors,
  newOrderSubmission,
  type CatalogItem,
} from './order';

const catalog: CatalogItem[] = [
  { id: 'city-helmet', name: 'City helmet', description: 'Helmet', price: 79.9 },
  { id: 'folding-lock', name: 'Folding lock', description: 'Lock', price: 50 },
  { id: 'inner-tube', name: 'Inner tube 28"', description: 'Tube', price: 7.5 },
];

describe('start-order@1', () => {
  it('submits the customer and the chosen items in catalog order', () => {
    const customer = { name: ' Alice Smith ', email: 'alice@example.com ', address: 'Hauptstraße 1\n80331 München' };

    expect(newOrderSubmission(catalog, customer, { 'folding-lock': 1, 'inner-tube': 0, 'city-helmet': 2 })).toEqual({
      customer: { name: 'Alice Smith', email: 'alice@example.com', address: 'Hauptstraße 1\n80331 München' },
      items: [
        { itemId: 'city-helmet', quantity: 2 },
        { itemId: 'folding-lock', quantity: 1 },
      ],
    });
  });

  it('needs an item and a complete customer before the review', () => {
    expect(itemErrors({ 'city-helmet': 0 })).toEqual([{ field: 'items', message: 'Choose at least one item.' }]);
    expect(customerErrors({ name: ' ', email: 'alice@example', address: '' }).map((error) => error.field)).toEqual([
      'customer.name',
      'customer.email',
      'customer.address',
    ]);
  });

  it('shows prices in euros', () => {
    expect(formatMoney(129.9, 'EUR')).toBe('€129.90');
  });
});

describe('check-order@1', () => {
  it('submits the decision with the comment or null', () => {
    expect(checkOrderSubmission(true, '  ')).toEqual({ approved: true, comment: null });
    expect(checkOrderSubmission(false, ' Out of stock ')).toEqual({ approved: false, comment: 'Out of stock' });
  });

  it('needs a reason to decline', () => {
    expect(decisionErrors(false, '')).toEqual([{ field: 'comment', message: 'Give a reason when you decline the order.' }]);
    expect(decisionErrors(true, '')).toEqual([]);
  });
});
