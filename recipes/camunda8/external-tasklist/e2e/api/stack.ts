import { APIRequestContext, APIResponse, expect, request } from '@playwright/test';

/** Addresses of the stack from stack/docker-compose.yml, as the host sees them. */
export const TASKMANAGER = 'http://localhost:8090';
export const ORDERS = 'http://localhost:8081';
export const PAYMENTS = 'http://localhost:8082';
export const CAMUNDA = 'http://localhost:8080';

export type Task = {
  userTaskKey: string;
  name: string;
  domain: string;
  domainLabel: string;
  businessId: string | null;
  subject: string | null;
  formRef: string | null;
  state: 'OPEN' | 'COMPLETED' | 'CANCELED';
  assignee: string | null;
  candidateGroups: string[];
  closedBy: string | null;
  processInstanceKey: string;
  actions: string[];
};

/** The task manager API as one of the demo users sees it. */
export function tasklistAs(user: string): Promise<APIRequestContext> {
  return request.newContext({ baseURL: TASKMANAGER, extraHTTPHeaders: { 'X-Demo-User': user } });
}

export async function json<T>(response: APIResponse, status = 200): Promise<T> {
  expect(response.status(), await response.text()).toBe(status);
  return (await response.json()) as T;
}

/** Places an order through the order application's start form, the way the tasklist does. */
export async function placeOrder(tasklist: APIRequestContext, customer: string): Promise<string> {
  const form = await json<{ data: { items: { id: string }[] } }>(
    await tasklist.get('/api/start-forms/order/new-order/form'),
  );
  const items = form.data.items.slice(0, 2).map((item) => ({ itemId: item.id, quantity: 1 }));
  const started = await json<{ businessId: string }>(
    await tasklist.post('/api/start-forms/order/new-order/submission', {
      data: {
        data: {
          customer: { name: customer, email: `${customer.split(' ')[0].toLowerCase()}@example.com`, address: 'Lindwurmstr. 12, 80337 München' },
          items,
        },
      },
    }),
    201,
  );
  return started.businessId;
}

/** Waits until the projection shows a task matching the query, with its form reference, and returns it. */
export async function waitForTask(
  tasklist: APIRequestContext,
  query: Record<string, string>,
  matches: (task: Task) => boolean = () => true,
): Promise<Task> {
  let found: Task | undefined;
  await expect
    .poll(async () => {
      const page = await json<{ items: Task[] }>(await tasklist.get('/api/tasks', { params: { state: 'ALL', ...query } }));
      found = page.items.find((task) => task.formRef !== null && matches(task));
      return found !== undefined;
    }, { timeout: 60_000 })
    .toBe(true);
  return found!;
}

export async function taskState(tasklist: APIRequestContext, key: string): Promise<Task> {
  return json<Task>(await tasklist.get(`/api/tasks/${key}`));
}

export async function claim(tasklist: APIRequestContext, key: string): Promise<Task> {
  return json<Task>(await tasklist.post(`/api/tasks/${key}/assignment`, { data: {} }));
}

export function complete(tasklist: APIRequestContext, key: string, data: Record<string, unknown>): Promise<APIResponse> {
  return tasklist.post(`/api/tasks/${key}/completion`, { data: { data } });
}
