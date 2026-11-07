// The data the order application sends to its forms and the data the forms submit back.
import type { FieldError } from '@tasklist/form-protocol';

export interface CatalogItem {
  id: string;
  name: string;
  description: string;
  price: number;
}

/** `data` of the start form `start-order@1`. */
export interface StartOrderData {
  items: CatalogItem[];
  currency: string;
}

export interface Customer {
  name: string;
  email: string;
  address: string;
}

export type OrderStatus = 'CHECKING' | 'CANCELED' | 'DECLINED' | 'AWAITING_PAYMENT' | 'PREPARING' | 'SHIPPED' | 'NOT_PAID';

export interface OrderLine {
  name: string;
  quantity: number;
  price: number;
}

export interface OrderSummary {
  id: string;
  status: OrderStatus;
  customer: Customer;
  items: OrderLine[];
  total: number;
  currency: string;
  checkComment: string | null;
}

/** `data` of the task form `check-order@1`. */
export interface CheckOrderData {
  order: OrderSummary;
}

/** Pieces per catalog item id. */
export type Quantities = Partial<Record<string, number>>;

export const MAX_QUANTITY = 10;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** The rules the order application checks again on submission; here they give feedback before the last step. */
export function itemErrors(quantities: Quantities): FieldError[] {
  const chosen = Object.values(quantities).some((quantity) => (quantity ?? 0) > 0);
  return chosen ? [] : [{ field: 'items', message: 'Choose at least one item.' }];
}

/** Longest values the order application accepts. */
export const MAX_LENGTH = { name: 100, email: 254, address: 500 } as const;

export function customerErrors(customer: Customer): FieldError[] {
  const errors: FieldError[] = [];
  if (customer.name.trim() === '') {
    errors.push({ field: 'customer.name', message: "Enter the customer's name, up to 100 characters." });
  }
  if (!EMAIL.test(customer.email.trim())) {
    errors.push({ field: 'customer.email', message: 'Enter a valid email address.' });
  }
  if (customer.address.trim() === '') {
    errors.push({ field: 'customer.address', message: 'Enter the delivery address, up to 500 characters.' });
  }
  return errors;
}

/** The chosen items with their quantities, in catalog order. */
export function chosenLines(catalog: CatalogItem[], quantities: Quantities): (OrderLine & { itemId: string })[] {
  return catalog
    .map((item) => ({ itemId: item.id, name: item.name, price: item.price, quantity: quantities[item.id] ?? 0 }))
    .filter((line) => line.quantity > 0);
}

export function totalOf(lines: OrderLine[]): number {
  return lines.reduce((sum, line) => sum + line.price * line.quantity, 0);
}

/** What `start-order@1` submits: the same body as `POST /api/v1/orders` of the order application. */
export function newOrderSubmission(catalog: CatalogItem[], customer: Customer, quantities: Quantities) {
  return {
    customer: { name: customer.name.trim(), email: customer.email.trim(), address: customer.address.trim() },
    items: chosenLines(catalog, quantities).map(({ itemId, quantity }) => ({ itemId, quantity })),
  };
}

export function decisionErrors(approved: boolean, comment: string): FieldError[] {
  return !approved && comment.trim() === ''
    ? [{ field: 'comment', message: 'Give a reason when you decline the order.' }]
    : [];
}

/** What `check-order@1` submits. */
export function checkOrderSubmission(approved: boolean, comment: string) {
  return { approved, comment: comment.trim() === '' ? null : comment.trim() };
}

export function messagesFor(errors: FieldError[], field: string): string | undefined {
  const messages = errors.filter((error) => error.field === field).map((error) => error.message);
  return messages.length > 0 ? messages.join(' ') : undefined;
}

export function formatMoney(amount: number, currency: string): string {
  return new Intl.NumberFormat('en', { style: 'currency', currency }).format(amount);
}

export function parseStartOrderData(data: Record<string, unknown>): StartOrderData {
  if (!Array.isArray(data.items) || typeof data.currency !== 'string') throw new Error('The form received no catalog.');
  return data as unknown as StartOrderData;
}

export function parseCheckOrderData(data: Record<string, unknown>): CheckOrderData {
  const order = data.order as Partial<OrderSummary> | undefined;
  if (typeof order?.id !== 'string' || !Array.isArray(order.items)) throw new Error('The form received no order.');
  return data as unknown as CheckOrderData;
}
