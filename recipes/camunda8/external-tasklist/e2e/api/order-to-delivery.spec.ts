import { expect, request, test } from '@playwright/test';
import { ORDERS, PAYMENTS, claim, complete, json, placeOrder, taskState, tasklistAs, waitForTask } from './stack';

test('an order runs through both applications in one tasklist', async () => {
  const alice = await tasklistAs('alice');
  const bob = await tasklistAs('bob');
  const carol = await tasklistAs('carol');

  const orderId = await placeOrder(alice, 'Anna Schmidt');

  // Sales checks the order in the order application's micro-frontend.
  const checkOrder = await waitForTask(alice, { domain: 'order', q: orderId }, (t) => t.name === 'Check order');
  expect(checkOrder).toMatchObject({ formRef: 'check-order@1', businessId: orderId, candidateGroups: ['sales'], state: 'OPEN' });
  expect(checkOrder.subject).toContain(orderId);
  expect((await claim(alice, checkOrder.userTaskKey)).assignee).toBe('alice');
  const orderForm = await json<{ type: string; readOnly: boolean; data: { order: { id: string } } }>(
    await alice.get(`/api/tasks/${checkOrder.userTaskKey}/form`),
  );
  expect(orderForm).toMatchObject({ type: 'html', readOnly: false });
  expect(orderForm.data.order.id).toBe(orderId);

  const declinedWithoutReason = await complete(alice, checkOrder.userTaskKey, { approved: false });
  expect(declinedWithoutReason.status()).toBe(422);
  expect((await declinedWithoutReason.json()).errors[0].field).toBe('comment');

  expect((await complete(alice, checkOrder.userTaskKey, { approved: true })).status()).toBe(204);
  // The completion returns after the global listener updated the projection.
  expect((await taskState(alice, checkOrder.userTaskKey)).state).toBe('COMPLETED');

  // Finance checks the payment in the payment application's JSON form.
  const checkPayment = await waitForTask(bob, { domain: 'payment', q: orderId, state: 'OPEN' }, (t) => t.name === 'Check payment');
  expect(checkPayment).toMatchObject({ formRef: 'check-payment@1', candidateGroups: ['finance'], domainLabel: 'Payments' });
  await claim(bob, checkPayment.userTaskKey);
  const paymentForm = await json<{ type: string; data: { paymentId: string } }>(
    await bob.get(`/api/tasks/${checkPayment.userTaskKey}/form`),
  );
  expect(paymentForm.type).toBe('json-forms');
  const payments = await request.newContext({ baseURL: PAYMENTS });
  const payment = await json<{ amount: number }>(await payments.get(`/api/v1/payments/${paymentForm.data.paymentId}`));

  const wrongAmount = await complete(bob, checkPayment.userTaskKey, { paymentReceived: true, amountReceived: payment.amount + 1 });
  expect(wrongAmount.status()).toBe(422);
  expect((await wrongAmount.json()).errors[0].field).toBe('amountReceived');
  expect((await complete(bob, checkPayment.userTaskKey, { paymentReceived: true, amountReceived: payment.amount })).status()).toBe(204);

  // The payment application reported the payment to the order application, which moves on to the delivery.
  const delivery = await waitForTask(carol, { domain: 'order', q: orderId, state: 'OPEN' }, (t) => t.name === 'Prepare delivery');
  expect(delivery.candidateGroups).toEqual(['warehouse']);
  await claim(carol, delivery.userTaskKey);
  const deliveryForm = await json<{ type: string; draftable: boolean; data: { items: { itemId: string }[] } }>(
    await carol.get(`/api/tasks/${delivery.userTaskKey}/form`),
  );
  expect(deliveryForm).toMatchObject({ type: 'json-forms', draftable: true });
  const someReady = deliveryForm.data.items.map((item, index) => ({ ...item, ready: index === 0 }));
  expect((await carol.post(`/api/tasks/${delivery.userTaskKey}/draft`, { data: { data: { items: someReady } } })).status()).toBe(204);
  expect((await complete(carol, delivery.userTaskKey, { items: someReady, shippingDate: '2026-12-01' })).status()).toBe(422);
  const allReady = deliveryForm.data.items.map((item) => ({ ...item, ready: true }));
  expect((await complete(carol, delivery.userTaskKey, { items: allReady, shippingDate: '2026-12-01' })).status()).toBe(204);

  const orders = await request.newContext({ baseURL: ORDERS });
  await expect
    .poll(async () => (await json<{ status: string }>(await orders.get(`/api/v1/orders/${orderId}`))).status)
    .toBe('SHIPPED');
});
