import { expect, test } from '@playwright/test';
import { placeOrder, taskState, tasklistAs, waitForTask } from '../api/stack';
import { openAs, taskDetail } from './desk';

test('sales checks the order in the sandboxed form of the order application', async ({ page }) => {
  const alice = await tasklistAs('alice');
  const orderId = await placeOrder(alice, 'Karl Weiss');
  const task = await waitForTask(alice, { domain: 'order', q: orderId }, (t) => t.name === 'Check order');

  await openAs(page, 'alice', `/?task=${task.userTaskKey}`);
  const detail = taskDetail(page);
  await detail.getByRole('button', { name: 'Claim task' }).click();

  // The micro-frontend runs sandboxed without allow-same-origin, from the task manager on the tasklist's origin.
  const iframe = detail.locator('iframe');
  await expect(iframe).toHaveAttribute('sandbox', 'allow-scripts');
  await expect(iframe).toHaveAttribute('src', '/api/forms/order/check-order@1/frame');
  const form = iframe.contentFrame();
  await expect(form.getByRole('heading', { name: `Order ${orderId}` })).toBeVisible();
  await expect(form.getByRole('button', { name: 'Approve' })).toBeEnabled();

  // Its origin is opaque: it can read neither the tasklist's DOM nor its storage.
  const frame = await (await iframe.elementHandle())?.contentFrame();
  if (!frame) throw new Error('The form frame did not load.');
  expect(await frame.evaluate(() => window.origin)).toBe('null');
  const reach = await frame.evaluate(() => {
    const attempt = (read: () => unknown) => {
      try {
        read();
        return 'readable';
      } catch (error) {
        return (error as Error).name;
      }
    };
    return { parentDocument: attempt(() => window.parent.document.title), storage: attempt(() => window.localStorage.length) };
  });
  expect(reach).toEqual({ parentDocument: 'SecurityError', storage: 'SecurityError' });

  // A message that does not come from the frame is ignored, even with a valid envelope.
  const completions: string[] = [];
  page.on('request', (request) => {
    if (request.url().endsWith(`/api/tasks/${task.userTaskKey}/completion`)) completions.push(request.method());
  });
  await page.evaluate(() => {
    window.postMessage({ protocol: 'tasklist-form', version: 1, type: 'submit', payload: { data: { approved: true } } }, '*');
  });
  await page.waitForTimeout(1500);
  expect(completions).toEqual([]);
  expect((await taskState(alice, task.userTaskKey)).state).toBe('OPEN');

  // The frame's own submit goes through the tasklist to the task manager and the order application.
  await form.getByRole('button', { name: 'Approve' }).click();
  await expect(detail.getByText('This task was completed')).toBeVisible();
  expect(completions).toEqual(['POST']);
  expect((await taskState(alice, task.userTaskKey)).state).toBe('COMPLETED');
  // The completed task shows the form again, read-only.
  await expect(form.getByText('Sales approved the order.')).toBeVisible();
  await expect(detail.getByText('Read-only: the task was completed')).toBeVisible();
});
