import { expect, request, test } from '@playwright/test';
import { ORDERS, claim, placeOrder, tasklistAs, waitForTask } from '../api/stack';
import { filters, openAs, row, taskDetail, taskList } from './desk';

test('the desk filters the tasks of both applications by domain, assignment, group, search and state', async ({ page }) => {
  const alice = await tasklistAs('alice');
  // A name of this run only, so the search finds one task even on a stack that kept earlier runs (KEEP_STACK).
  const customer = `Hanna Berg ${Date.now().toString(36)}`;
  const mineId = await placeOrder(alice, customer);
  const otherId = await placeOrder(alice, 'Ivo Brandt');
  const mine = await waitForTask(alice, { q: mineId }, (t) => t.name === 'Check order');
  await waitForTask(alice, { q: otherId }, (t) => t.name === 'Check order');
  await claim(alice, mine.userTaskKey);

  await openAs(page, 'alice');
  const rail = filters(page);
  await expect(row(page, mineId)).toBeVisible();
  await expect(row(page, otherId)).toBeVisible();

  // Domain: the payment application has no task of these orders yet.
  await rail.getByRole('button', { name: /^Payments/ }).click();
  await expect(page).toHaveURL(/domain=payment/);
  await expect(row(page, mineId)).toHaveCount(0);
  for (const task of await taskList(page).getByRole('option').all()) await expect(task).toContainText('Payments');
  await rail.getByRole('button', { name: /^Orders/ }).click();
  await expect(page).toHaveURL(/domain=order/);
  await expect(row(page, mineId)).toBeVisible();

  // Assignment
  await rail.getByRole('button', { name: /^Mine/ }).click();
  await expect(page).toHaveURL(/assignment=mine/);
  await expect(row(page, mineId)).toBeVisible();
  await expect(row(page, otherId)).toHaveCount(0);
  await rail.getByRole('button', { name: /^Unassigned/ }).click();
  await expect(row(page, otherId)).toBeVisible();
  await expect(row(page, mineId)).toHaveCount(0);
  await rail.getByRole('button', { name: /^All tasks/ }).click();
  await expect(page).not.toHaveURL(/assignment=/);

  // Candidate group
  await rail.getByRole('button', { name: /^sales/ }).click();
  await expect(page).toHaveURL(/group=sales/);
  for (const task of await taskList(page).getByRole('option').all()) await expect(task).toContainText('sales');

  // Search in the top bar, by customer name and by business ID
  const search = page.getByRole('searchbox', { name: 'Search tasks' });
  await search.fill(customer);
  await expect(page).toHaveURL(/q=Hanna/);
  await expect(taskList(page).getByRole('option')).toHaveCount(1);
  await expect(row(page, mineId)).toBeVisible();

  // State: the customer cancels the other order, its task moves from open to canceled.
  const orders = await request.newContext({ baseURL: ORDERS });
  expect((await orders.post(`/api/v1/orders/${otherId}/cancellation`, { data: { reason: 'Ordered twice' } })).status()).toBe(204);
  await search.fill(otherId);
  await expect(page).toHaveURL(new RegExp(`q=${otherId}`));
  await expect(page.getByText('No tasks match these filters')).toBeVisible();
  await rail.getByRole('button', { name: /^Canceled/ }).click();
  await expect(page).toHaveURL(/state=canceled/);
  await expect(page.getByRole('heading', { name: /Canceled tasks/ })).toBeVisible();
  await expect(row(page, otherId)).toBeVisible();

  // Clear filters brings back the open tasks of everybody.
  await rail.getByRole('button', { name: /^Open/ }).click();
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await expect(page).not.toHaveURL(/state=|group=|domain=|q=/);
  await expect(row(page, mineId)).toBeVisible();
});

test('the list is a listbox: arrow keys move, Enter opens the task', async ({ page }) => {
  const alice = await tasklistAs('alice');
  const customer = `Jonas Keller ${Date.now().toString(36)}`;
  const olderId = await placeOrder(alice, customer);
  const newerId = await placeOrder(alice, customer);
  await waitForTask(alice, { q: olderId }, (t) => t.name === 'Check order');
  await waitForTask(alice, { q: newerId }, (t) => t.name === 'Check order');

  await openAs(page, 'alice', `/?q=${encodeURIComponent(customer)}`);
  await expect(taskList(page).getByRole('option')).toHaveCount(2);
  // Newest first: the newer order leads, Down moves the focus to the older one without opening it.
  await row(page, newerId).focus();
  await page.keyboard.press('ArrowDown');
  await expect(row(page, olderId)).toBeFocused();
  await expect(row(page, olderId)).toHaveAttribute('aria-selected', 'false');
  await page.keyboard.press('Enter');

  await expect(row(page, olderId)).toHaveAttribute('aria-selected', 'true');
  await expect(page).toHaveURL(/task=\d+/);
  await expect(taskDetail(page).getByRole('heading', { name: 'Check order' })).toBeVisible();
  await expect(taskDetail(page)).toContainText(olderId);
  await page.keyboard.press('ArrowUp');
  await expect(row(page, newerId)).toBeFocused();
});
