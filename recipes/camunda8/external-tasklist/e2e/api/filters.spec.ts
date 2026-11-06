import { expect, test } from '@playwright/test';
import { Task, claim, complete, json, placeOrder, tasklistAs, waitForTask } from './stack';

type Page = { items: Task[]; total: number };
type Facets = { domains: { value: string; label: string; count: number }[]; candidateGroups: { value: string }[] };

test('one tasklist filters the tasks of both applications', async () => {
  const alice = await tasklistAs('alice');
  // A name unique to this run, because the stack may still hold orders of earlier runs.
  const customer = `Finn Vogel ${Date.now().toString(36)}`;
  const first = await placeOrder(alice, customer);
  const second = await placeOrder(alice, 'Greta Lang');
  const third = await placeOrder(alice, 'Hanna Busch');
  const mine = await waitForTask(alice, { q: first }, (t) => t.name === 'Check order');
  await waitForTask(alice, { q: second }, (t) => t.name === 'Check order');
  await claim(alice, mine.userTaskKey);
  // An approved order leads to a task of the payment application in the same list.
  const approved = await waitForTask(alice, { q: third }, (t) => t.name === 'Check order');
  await claim(alice, approved.userTaskKey);
  expect((await complete(alice, approved.userTaskKey, { approved: true })).status()).toBe(204);
  const payment = await waitForTask(alice, { q: third, domain: 'payment', state: 'OPEN' });
  expect(payment.domainLabel).toBe('Payments');

  const orderTasks = await json<Page>(await alice.get('/api/tasks', { params: { domain: 'order' } }));
  expect(orderTasks.items.every((t) => t.domain === 'order')).toBe(true);
  const paymentTasks = await json<Page>(await alice.get('/api/tasks', { params: { domain: 'payment' } }));
  expect(paymentTasks.items.map((t) => t.userTaskKey)).toContain(payment.userTaskKey);
  expect(paymentTasks.items.every((t) => t.domain === 'payment')).toBe(true);

  const byGroup = await json<Page>(await alice.get('/api/tasks', { params: { candidateGroup: 'sales' } }));
  expect(byGroup.items.every((t) => t.candidateGroups.includes('sales'))).toBe(true);

  const assignedToMe = await json<Page>(await alice.get('/api/tasks', { params: { assignment: 'MINE' } }));
  expect(assignedToMe.items.map((t) => t.userTaskKey)).toContain(mine.userTaskKey);
  expect(assignedToMe.items.every((t) => t.assignee === 'alice')).toBe(true);

  const unassigned = await json<Page>(await alice.get('/api/tasks', { params: { assignment: 'UNASSIGNED', q: second } }));
  expect(unassigned.items).toHaveLength(1);

  const search = await json<Page>(await alice.get('/api/tasks', { params: { q: customer } }));
  expect(search.items.map((t) => t.userTaskKey)).toEqual([mine.userTaskKey]);

  const facets = await json<Facets>(await alice.get('/api/tasks/facets', { params: { state: 'ALL' } }));
  expect(facets.domains.map((d) => d.label)).toEqual(expect.arrayContaining(['Orders', 'Payments']));
  expect(facets.candidateGroups.map((g) => g.value)).toEqual(expect.arrayContaining(['sales', 'finance']));
});
