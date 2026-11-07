import { expect, request, test } from '@playwright/test';
import { ORDERS, claim, placeOrder, tasklistAs, waitForTask } from '../api/stack';
import { openAs, taskDetail } from './desk';

test('a canceled order turns its open task read-only at once, without a reload', async ({ page }) => {
  const alice = await tasklistAs('alice');
  const orderId = await placeOrder(alice, 'Max Lorenz');
  const task = await waitForTask(alice, { domain: 'order', q: orderId }, (t) => t.name === 'Check order');
  await claim(alice, task.userTaskKey);

  await openAs(page, 'alice', `/?task=${task.userTaskKey}`);
  const detail = taskDetail(page);
  const form = detail.locator('iframe').contentFrame();
  await expect(form.getByRole('button', { name: 'Approve' })).toBeEnabled();
  let reloads = 0;
  page.on('framenavigated', (frame) => {
    if (frame === page.mainFrame()) reloads++;
  });

  // The customer cancels; the boundary event ends "Check order" and the task manager pushes task-changed.
  const orders = await request.newContext({ baseURL: ORDERS });
  expect((await orders.post(`/api/v1/orders/${orderId}/cancellation`, { data: { reason: 'Customer changed their mind' } })).status()).toBe(204);

  await expect(detail.getByRole('alert').filter({ hasText: 'This task was canceled by its process' })).toBeVisible();
  await expect(detail.getByText('Canceled', { exact: true }).first()).toBeVisible();
  await expect(detail.getByText('Read-only: the task was canceled')).toBeVisible();
  await expect(detail.getByRole('button', { name: 'Unclaim' })).toBeDisabled();
  // The form loads again read-only and shows the canceled order without its decision buttons.
  await expect(form.getByText('The customer canceled the order', { exact: false })).toBeVisible();
  await expect(form.getByRole('button', { name: 'Approve' })).toHaveCount(0);
  expect(reloads).toBe(0);
});
