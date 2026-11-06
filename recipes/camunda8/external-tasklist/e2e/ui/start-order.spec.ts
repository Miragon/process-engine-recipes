import { expect, test } from '@playwright/test';
import { taskState, tasklistAs } from '../api/stack';
import { openAs, row, startFormDetail, taskDetail } from './desk';

test('a new order starts from the start form of the order application and its first task opens', async ({ page }) => {
  await openAs(page, 'alice');
  await page.getByRole('button', { name: 'New order' }).click();
  await expect(page).toHaveURL(/start=order%2Fnew-order/);

  const detail = startFormDetail(page);
  await expect(detail.getByRole('heading', { name: 'New order' })).toBeVisible();
  const iframe = detail.locator('iframe');
  await expect(iframe).toHaveAttribute('sandbox', 'allow-scripts');
  const form = iframe.contentFrame();

  // The catalog arrives with the init message; the frame itself loads nothing.
  await form.getByRole('button', { name: /more$/ }).first().click();
  await form.getByRole('button', { name: 'Next' }).click();
  await form.getByLabel('Name', { exact: true }).fill('Lena Hoffmann');
  await form.getByLabel('Email', { exact: true }).fill('lena.hoffmann@example.com');
  await form.getByLabel('Delivery address', { exact: true }).fill('Lindwurmstr. 12, 80337 München');
  await form.getByRole('button', { name: 'Next' }).click();
  await form.getByRole('button', { name: 'Place order' }).click();

  const placed = page.getByRole('status').filter({ hasText: /ORD-\d+ placed/ }).first();
  await expect(placed).toBeVisible();
  const orderId = /ORD-\d+/.exec(await placed.innerText())?.[0] ?? '';

  // The order process creates "Check order"; the tasklist opens it as soon as the projection has it.
  const task = taskDetail(page);
  await expect(task.getByRole('heading', { name: 'Check order' })).toBeVisible();
  await expect(task).toContainText(orderId);
  await expect(page).toHaveURL(/task=\d+/);
  await expect(row(page, orderId)).toHaveAttribute('aria-selected', 'true');

  const key = /task=(\d+)/.exec(page.url())?.[1] ?? '';
  const alice = await tasklistAs('alice');
  expect(await taskState(alice, key)).toMatchObject({ name: 'Check order', businessId: orderId, state: 'OPEN' });
});
