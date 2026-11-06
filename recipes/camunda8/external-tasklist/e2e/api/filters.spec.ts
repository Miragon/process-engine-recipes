import { expect, test } from '@playwright/test';
import { Task, claim, json, placeOrder, tasklistAs, waitForTask } from './stack';

type Page = { items: Task[]; total: number };
type Facets = { domains: { value: string; label: string; count: number }[]; candidateGroups: { value: string }[] };

test('one tasklist filters the tasks of both applications', async () => {
  const alice = await tasklistAs('alice');
  const first = await placeOrder(alice, 'Finn Vogel');
  const second = await placeOrder(alice, 'Greta Lang');
  const mine = await waitForTask(alice, { q: first }, (t) => t.name === 'Check order');
  await waitForTask(alice, { q: second }, (t) => t.name === 'Check order');
  await claim(alice, mine.userTaskKey);

  const byDomain = await json<Page>(await alice.get('/api/tasks', { params: { domain: 'order' } }));
  expect(byDomain.items.every((t) => t.domain === 'order')).toBe(true);

  const byGroup = await json<Page>(await alice.get('/api/tasks', { params: { candidateGroup: 'sales' } }));
  expect(byGroup.items.every((t) => t.candidateGroups.includes('sales'))).toBe(true);

  const assignedToMe = await json<Page>(await alice.get('/api/tasks', { params: { assignment: 'MINE' } }));
  expect(assignedToMe.items.map((t) => t.userTaskKey)).toContain(mine.userTaskKey);
  expect(assignedToMe.items.every((t) => t.assignee === 'alice')).toBe(true);

  const unassigned = await json<Page>(await alice.get('/api/tasks', { params: { assignment: 'UNASSIGNED', q: second } }));
  expect(unassigned.items).toHaveLength(1);

  const search = await json<Page>(await alice.get('/api/tasks', { params: { q: 'Finn Vogel' } }));
  expect(search.items.map((t) => t.userTaskKey)).toEqual([mine.userTaskKey]);

  const facets = await json<Facets>(await alice.get('/api/tasks/facets', { params: { state: 'ALL' } }));
  expect(facets.domains.map((d) => d.label)).toEqual(expect.arrayContaining(['Orders', 'Payments']));
  expect(facets.candidateGroups.map((g) => g.value)).toEqual(expect.arrayContaining(['sales', 'finance']));
});
