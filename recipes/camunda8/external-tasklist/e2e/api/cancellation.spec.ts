import { expect, request, test } from '@playwright/test';
import { CAMUNDA, ORDERS, claim, complete, json, placeOrder, taskState, tasklistAs, waitForTask } from './stack';

test('a boundary event cancels the open task and it can no longer be completed', async () => {
  const alice = await tasklistAs('alice');
  const orders = await request.newContext({ baseURL: ORDERS });
  const orderId = await placeOrder(alice, 'Ben Becker');
  const task = await waitForTask(alice, { domain: 'order', q: orderId }, (t) => t.name === 'Check order');
  await claim(alice, task.userTaskKey);

  // The customer cancels: the order application correlates OrderCanceled into its own process,
  // the interrupting boundary event on "Check order" fires and the canceling listener updates the projection.
  expect((await orders.post(`/api/v1/orders/${orderId}/cancellation`, { data: { reason: 'Customer changed their mind' } })).status()).toBe(204);

  await expect.poll(async () => (await taskState(alice, task.userTaskKey)).state).toBe('CANCELED');
  expect((await taskState(alice, task.userTaskKey)).closedBy).toBe('LISTENER');
  const history = await json<{ eventType: string }[]>(await alice.get(`/api/tasks/${task.userTaskKey}/history`));
  expect(history.map((e) => e.eventType)).toEqual(['CREATING', 'ASSIGNING', 'CANCELING']);

  const attempt = await complete(alice, task.userTaskKey, { approved: true });
  expect(attempt.status()).toBe(409);
  expect((await attempt.json()).code).toBe('TASK_NOT_ACTIVE');
  const form = await json<{ readOnly: boolean }>(await alice.get(`/api/tasks/${task.userTaskKey}/form`));
  expect(form.readOnly).toBe(true);

  expect((await orders.post(`/api/v1/orders/${orderId}/cancellation`, { data: { reason: 'again' } })).status()).toBe(204);
  await expect.poll(async () => (await json<{ status: string }>(await orders.get(`/api/v1/orders/${orderId}`))).status).toBe('CANCELED');
  expect((await alice.get('/api/tasks', { params: { state: 'CANCELED', q: orderId } })).ok()).toBe(true);
});

test('the reconciler closes a task that a process instance modification terminated', async () => {
  const alice = await tasklistAs('alice');
  const camunda = await request.newContext({ baseURL: CAMUNDA });
  const orderId = await placeOrder(alice, 'Clara Wolf');
  const task = await waitForTask(alice, { domain: 'order', q: orderId }, (t) => t.name === 'Check order');

  // Camunda's search is eventually consistent: wait until it knows the task, then terminate it by modification.
  let elementInstanceKey = '';
  await expect
    .poll(async () => {
      const response = await camunda.get(`/v2/user-tasks/${task.userTaskKey}`);
      if (!response.ok()) return false;
      elementInstanceKey = (await response.json()).elementInstanceKey;
      return true;
    })
    .toBe(true);
  const modification = await camunda.post(`/v2/process-instances/${task.processInstanceKey}/modification`, {
    data: { terminateInstructions: [{ elementInstanceKey }] },
  });
  expect(modification.status(), await modification.text()).toBe(204);

  // A modification skips the canceling listener; the reconciler (every 5 s in e2e) closes the task.
  await expect.poll(async () => (await taskState(alice, task.userTaskKey)).state, { timeout: 60_000 }).toBe('CANCELED');
  expect((await taskState(alice, task.userTaskKey)).closedBy).toBe('RECONCILER');
});
