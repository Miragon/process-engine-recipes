import { describe, expect, test, vi } from 'vitest';
import { ApiProblem, createApiClient, frameUrl } from './client';

function respond(status: number, body?: unknown, headers: Record<string, string> = {}) {
  const text = body === undefined ? null : typeof body === 'string' ? body : JSON.stringify(body);
  return new Response(text, { status, headers });
}

function clientAs(user: string | null, ...responses: Response[]) {
  const fetch = vi.fn<typeof globalThis.fetch>();
  responses.forEach((response) => fetch.mockResolvedValueOnce(response));
  const api = createApiClient({ currentUser: () => user, fetch });
  const request = (index = 0) => {
    const [url, init] = fetch.mock.calls[index] ?? [];
    return { url: typeof url === 'string' ? url : url instanceof URL ? url.href : (url?.url ?? ''), init, headers: new Headers(init?.headers) };
  };
  return { api, fetch, request };
}

describe('the task manager client', () => {
  test('names the demo user in every request', async () => {
    const { api, request } = clientAs('bob', respond(200, { items: [], total: 0, from: 0, limit: 50 }), respond(200, {}));

    await api.tasks({ state: 'OPEN' });
    await api.claim('2251799813685337');

    expect(request(0).headers.get('X-Demo-User')).toBe('bob');
    expect(request(1).headers.get('X-Demo-User')).toBe('bob');
    expect(request(1).init?.method).toBe('POST');
    expect(request(1).init?.body).toBe('{}');
    expect(request(1).headers.get('Content-Type')).toBe('application/json');
  });

  test('sends no user header before a demo user was chosen', async () => {
    const { api, request } = clientAs(null, respond(200, []));

    await api.users();

    expect(request().headers.has('X-Demo-User')).toBe(false);
  });

  test('leaves empty filters out of the query', async () => {
    const { api, request } = clientAs('alice', respond(200, { items: [], total: 0, from: 0, limit: 50 }));

    await api.tasks({ state: 'OPEN', assignment: 'MINE', domain: undefined, q: '', sort: 'PRIORITY', descending: true });

    expect(request().url).toBe('/api/tasks?state=OPEN&assignment=MINE&sort=PRIORITY&descending=true');
  });

  test('tells a confirmed claim from one whose listeners still run', async () => {
    const task = { userTaskKey: '42', assignee: 'bob' };
    const { api } = clientAs('bob', respond(200, task), respond(202, { ...task, assignee: null }));

    await expect(api.claim('42')).resolves.toEqual({ task, confirmed: true });
    await expect(api.claim('42')).resolves.toMatchObject({ confirmed: false });
  });

  test('aborts a request on the wire when its query is canceled', async () => {
    const controller = new AbortController();
    const { api, fetch } = clientAs('bob');
    fetch.mockImplementationOnce((_url, init) => {
      controller.abort();
      return Promise.reject(init?.signal?.reason as Error);
    });

    await expect(api.task('42', controller.signal)).rejects.not.toBeInstanceOf(ApiProblem);
    expect(fetch.mock.calls[0]?.[1]?.signal).toBe(controller.signal);
  });

  test('wraps submitted form data and tells completed from accepted', async () => {
    const { api, request } = clientAs('bob', respond(204), respond(202));

    await expect(api.complete('42', { paymentReceived: true })).resolves.toBe('completed');
    await expect(api.complete('42', { paymentReceived: true })).resolves.toBe('accepted');

    expect(request().url).toBe('/api/tasks/42/completion');
    expect(request().init?.body).toBe('{"data":{"paymentReceived":true}}');
  });

  test('turns a problem answer into an ApiProblem with its field errors', async () => {
    const { api } = clientAs(
      'bob',
      respond(
        422,
        {
          type: 'about:blank',
          title: 'Unprocessable Content',
          status: 422,
          detail: 'The received amount must match the invoice amount of 89.00 EUR',
          code: 'VALIDATION_FAILED',
          errors: [{ field: 'amountReceived', message: 'The received amount must match the invoice amount of 89.00 EUR' }, { field: 7 }],
        },
        { 'Content-Type': 'application/problem+json' },
      ),
    );

    const problem = await api.complete('42', { amountReceived: 80 }).catch((error: unknown) => error);

    expect(problem).toBeInstanceOf(ApiProblem);
    expect(problem).toMatchObject({
      status: 422,
      code: 'VALIDATION_FAILED',
      detail: 'The received amount must match the invoice amount of 89.00 EUR',
      errors: [{ field: 'amountReceived', message: 'The received amount must match the invoice amount of 89.00 EUR' }],
    });
  });

  test('reads Retry-After of a task that is still being synchronized', async () => {
    const { api } = clientAs(
      'bob',
      respond(503, { detail: 'The task is still being synchronized with Camunda. Try again in a moment.', code: 'SYNCING' }, { 'Retry-After': '2' }),
    );

    await expect(api.form('42')).rejects.toMatchObject({ status: 503, code: 'SYNCING', retryAfter: 2 });
  });

  test('explains answers without a problem body and failed connections', async () => {
    const { api, fetch } = clientAs('bob', respond(502, '<html>Bad Gateway</html>'));
    await expect(api.task('42')).rejects.toMatchObject({
      status: 502,
      code: 'HTTP_502',
      detail: 'The task manager does not answer. Try again in a moment.',
    });

    fetch.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(api.task('42')).rejects.toMatchObject({ status: 0, code: 'NETWORK' });
  });

  test('builds the paths of start forms and form frames', async () => {
    const { api, request } = clientAs('alice', respond(201, { processInstanceKey: '1', businessId: 'ORD-1042' }));

    await expect(api.submitStartForm('order', 'new-order', { items: [] })).resolves.toEqual({ processInstanceKey: '1', businessId: 'ORD-1042' });

    expect(request().url).toBe('/api/start-forms/order/new-order/submission');
    expect(frameUrl('order', 'check-order@1')).toBe('/api/forms/order/check-order@1/frame');
    expect(frameUrl('my domain', 'a/b')).toBe('/api/forms/my%20domain/a%2Fb/frame');
  });
});
