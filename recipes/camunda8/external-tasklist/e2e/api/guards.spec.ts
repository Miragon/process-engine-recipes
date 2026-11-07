import { expect, request, test } from '@playwright/test';
import { ORDERS, PAYMENTS, TASKMANAGER, claim, complete, json, placeOrder, tasklistAs, waitForTask } from './stack';

test('only candidates claim, only the assignee completes, and the browser cannot reach the applications', async () => {
  const alice = await tasklistAs('alice');
  const bob = await tasklistAs('bob');
  const orderId = await placeOrder(alice, 'Dora Klein');
  const task = await waitForTask(alice, { domain: 'order', q: orderId }, (t) => t.name === 'Check order');

  expect((await bob.post(`/api/tasks/${task.userTaskKey}/assignment`, { data: {} })).status()).toBe(403);
  expect((await complete(alice, task.userTaskKey, { approved: true })).status()).toBe(403);

  const anonymous = await request.newContext({ baseURL: TASKMANAGER });
  expect((await anonymous.get(`/api/tasks/${task.userTaskKey}/form`)).status()).toBe(401);
  const stranger = await tasklistAs('mallory');
  expect((await stranger.get(`/api/tasks/${task.userTaskKey}/form`)).status()).toBe(401);

  // The provider API of an application only answers the task manager's token (#158).
  const orders = await request.newContext({ baseURL: ORDERS });
  const direct = await orders.get('/tasklist/v1/start-forms');
  expect(direct.status()).toBe(401);
  expect((await direct.json()).code).toBe('UNAUTHORIZED');
  const payments = await request.newContext({ baseURL: PAYMENTS, extraHTTPHeaders: { Authorization: 'Bearer guessed' } });
  expect((await payments.get('/tasklist/v1/start-forms')).status()).toBe(401);
  // Also when the path is spelled so that only the decoded form names the provider API.
  for (const path of ['/tasklist/v1;x/start-forms', '/%74asklist/v1/start-forms']) {
    expect((await orders.get(path)).status(), path).toBe(401);
  }
});

test('fields the form may not change are ignored', async () => {
  const alice = await tasklistAs('alice');
  const bob = await tasklistAs('bob');
  const orderId = await placeOrder(alice, 'Emil Roth');
  const checkOrder = await waitForTask(alice, { domain: 'order', q: orderId }, (t) => t.name === 'Check order');
  await claim(alice, checkOrder.userTaskKey);
  expect((await complete(alice, checkOrder.userTaskKey, { approved: true })).status()).toBe(204);

  const checkPayment = await waitForTask(bob, { domain: 'payment', q: orderId, state: 'OPEN' });
  await claim(bob, checkPayment.userTaskKey);
  const form = await json<{ data: { paymentId: string } }>(await bob.get(`/api/tasks/${checkPayment.userTaskKey}/form`));
  const payments = await request.newContext({ baseURL: PAYMENTS });
  const before = await json<{ amount: number; orderId: string }>(await payments.get(`/api/v1/payments/${form.data.paymentId}`));

  const forged = await complete(bob, checkPayment.userTaskKey, {
    paymentReceived: true,
    amountReceived: before.amount,
    amount: 0.01,
    orderId: 'ORD-0000',
    paymentId: 'PAY-0000',
  });
  expect(forged.status()).toBe(204);
  const after = await json<{ amount: number; orderId: string; status: string }>(await payments.get(`/api/v1/payments/${form.data.paymentId}`));
  expect(after).toMatchObject({ amount: before.amount, orderId: before.orderId, status: 'PAID' });
});
