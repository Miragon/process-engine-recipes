import { expect, request, test } from '@playwright/test';
import { PAYMENTS, claim, complete, json, placeOrder, taskState, tasklistAs, waitForTask } from '../api/stack';
import { SCREENSHOT, openAs, row, taskDetail } from './desk';

test('finance claims Check payment and completes its JSON form after the application refused a wrong amount', async ({ page }) => {
  const alice = await tasklistAs('alice');
  const bob = await tasklistAs('bob');
  const orderId = await placeOrder(alice, 'Jana Novak');
  const checkOrder = await waitForTask(alice, { domain: 'order', q: orderId }, (t) => t.name === 'Check order');
  await claim(alice, checkOrder.userTaskKey);
  expect((await complete(alice, checkOrder.userTaskKey, { approved: true })).status()).toBe(204);
  const checkPayment = await waitForTask(bob, { domain: 'payment', q: orderId, state: 'OPEN' }, (t) => t.name === 'Check payment');
  const paymentId = checkPayment.businessId ?? '';

  await openAs(page, 'bob', `/?task=${checkPayment.userTaskKey}`);
  const detail = taskDetail(page);
  await expect(detail.getByRole('heading', { name: 'Check payment' })).toBeVisible();
  await expect(row(page, paymentId)).toHaveAttribute('aria-selected', 'true');
  await detail.getByRole('button', { name: 'Claim task' }).click();
  await expect(detail.getByText('Bob Baker (you)')).toBeVisible();

  // The payment application's form, rendered by the tasklist: read-only fields as facts, the decision as inputs.
  await expect(detail.getByText('Invoice amount')).toBeVisible();
  const payments = await request.newContext({ baseURL: PAYMENTS });
  const payment = await json<{ amount: number }>(await payments.get(`/api/v1/payments/${paymentId}`));
  await detail.getByRole('group', { name: 'Payment received' }).getByRole('radio', { name: 'Yes' }).check();
  const amount = detail.getByRole('spinbutton', { name: 'Amount received' });
  await amount.fill(String(payment.amount + 1));
  await detail.getByRole('button', { name: 'Complete task' }).click();

  // 422: the field error of the application lands at its control, the detail in a status box.
  await expect(amount).toHaveAttribute('aria-invalid', 'true');
  await expect(detail.getByText('The received amount must match', { exact: false }).first()).toBeVisible();
  await expect(detail.getByRole('alert').filter({ hasText: "Couldn't complete the task" })).toBeVisible();
  await expect(detail.getByText('1 field needs attention')).toBeVisible();
  expect((await taskState(bob, checkPayment.userTaskKey)).state).toBe('OPEN');

  if (process.env.UPDATE_SCREENSHOT) {
    // The picture shows the field with its error just above the docked action bar, without the toast of the claim.
    await expect(page.getByRole('status').filter({ hasText: 'You claimed' })).toHaveCount(0);
    await amount.evaluate((input) => {
      const pane = input.closest('.detail');
      const dock = pane?.querySelector('.actionbar-dock');
      const field = input.parentElement;
      if (!pane || !dock || !field) return;
      pane.scrollBy(0, Math.max(0, field.getBoundingClientRect().bottom - dock.getBoundingClientRect().top + 8));
    });
    await page.screenshot({ path: SCREENSHOT });
  }

  await amount.fill(String(payment.amount));
  await expect(amount).not.toHaveAttribute('aria-invalid', 'true');
  await detail.getByRole('button', { name: 'Complete task' }).click();

  await expect(detail.getByText('This task was completed')).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: 'Check payment completed.' })).toBeVisible();
  expect((await taskState(bob, checkPayment.userTaskKey)).state).toBe('COMPLETED');
  await expect(row(page, paymentId)).toHaveCount(0);
});
