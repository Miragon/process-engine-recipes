// The task manager API (`/api`), the only backend of the tasklist. Every request names the demo user as X-Demo-User.
import type {
  Application,
  Completion,
  DemoUser,
  Facets,
  FieldError,
  FormData,
  FormView,
  HistoryEntry,
  StartFormEntry,
  StartResult,
  StateQuery,
  Task,
  TaskPage,
  TaskQuery,
  TaskUpdate,
} from './types';

export const USER_HEADER = 'X-Demo-User';

interface ProblemInit {
  status: number;
  code: string;
  detail: string;
  errors?: FieldError[];
  retryAfter?: number;
}

/**
 * An error answer of the task manager (`application/problem+json` with `code` and, for validation, `errors`),
 * or no answer at all (`status` 0).
 */
export class ApiProblem extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors: FieldError[];
  /** Seconds to wait before trying again, from `Retry-After`. */
  readonly retryAfter: number | undefined;

  constructor({ status, code, detail, errors = [], retryAfter }: ProblemInit) {
    super(detail);
    this.name = 'ApiProblem';
    this.status = status;
    this.code = code;
    this.errors = errors;
    this.retryAfter = retryAfter;
  }

  get detail(): string {
    return this.message;
  }
}

export function isProblem(error: unknown, status?: number): error is ApiProblem {
  return error instanceof ApiProblem && (status === undefined || error.status === status);
}

export interface ApiClientOptions {
  /** The demo user to send, read for every request; `null` sends none. */
  currentUser: () => string | null;
  fetch?: typeof fetch;
}

type Query = Record<string, string | number | boolean | null | undefined>;

interface RequestOptions {
  query?: Query;
  body?: unknown;
}

export type ApiClient = ReturnType<typeof createApiClient>;

export function createApiClient({ currentUser, fetch: send = (input, init) => fetch(input, init) }: ApiClientOptions) {
  async function request(method: string, path: string, { query, body }: RequestOptions = {}): Promise<Response> {
    const headers = new Headers({ Accept: 'application/json' });
    const user = currentUser();
    if (user !== null) headers.set(USER_HEADER, user);
    if (body !== undefined) headers.set('Content-Type', 'application/json');

    let response: Response;
    try {
      response = await send(path + queryString(query), {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new ApiProblem({
        status: 0,
        code: 'NETWORK',
        detail: "Couldn't reach the task manager. Check that the stack is running and try again.",
      });
    }
    if (!response.ok) throw await problemOf(response);
    return response;
  }

  async function json<T>(method: string, path: string, options?: RequestOptions): Promise<T> {
    const response = await request(method, path, options);
    return (await response.json()) as T;
  }

  const task = (key: string) => `/api/tasks/${segment(key)}`;
  const startForm = (domain: string, id: string) => `/api/start-forms/${segment(domain)}/${segment(id)}`;

  return {
    users: () => json<DemoUser[]>('GET', '/api/users'),
    applications: () => json<Application[]>('GET', '/api/applications'),
    startForms: () => json<StartFormEntry[]>('GET', '/api/start-forms'),

    tasks: (query: TaskQuery) => json<TaskPage>('GET', '/api/tasks', { query }),
    facets: (state: StateQuery) => json<Facets>('GET', '/api/tasks/facets', { query: { state } }),
    task: (key: string) => json<Task>('GET', task(key)),
    history: (key: string) => json<HistoryEntry[]>('GET', `${task(key)}/history`),
    form: (key: string) => json<FormView>('GET', `${task(key)}/form`),

    claim: (key: string) => json<Task>('POST', `${task(key)}/assignment`, { body: {} }),
    unclaim: (key: string) => json<Task>('DELETE', `${task(key)}/assignment`),
    update: (key: string, update: TaskUpdate) => json<Task>('PATCH', task(key), { body: update }),
    saveDraft: async (key: string, data: FormData): Promise<void> => {
      await request('POST', `${task(key)}/draft`, { body: { data } });
    },
    complete: async (key: string, data: FormData): Promise<Completion> => {
      const response = await request('POST', `${task(key)}/completion`, { body: { data } });
      return response.status === 202 ? 'accepted' : 'completed';
    },

    startForm: (domain: string, id: string) => json<FormView>('GET', `${startForm(domain, id)}/form`),
    submitStartForm: (domain: string, id: string, data: FormData) =>
      json<StartResult>('POST', `${startForm(domain, id)}/submission`, { body: { data } }),
  };
}

/** Where the task manager serves the HTML form bundle of an application for the sandboxed iframe. */
export function frameUrl(domain: string, formRef: string): string {
  return `/api/forms/${segment(domain)}/${segment(formRef)}/frame`;
}

/** A path segment; `@` stays readable in form references such as `check-order@1`. */
function segment(value: string): string {
  return encodeURIComponent(value).replaceAll('%40', '@');
}

function queryString(query: Query | undefined): string {
  if (query === undefined) return '';
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    params.set(name, String(value));
  }
  const search = params.toString();
  return search === '' ? '' : `?${search}`;
}

async function problemOf(response: Response): Promise<ApiProblem> {
  const body = await bodyOf(response);
  return new ApiProblem({
    status: response.status,
    code: typeof body.code === 'string' ? body.code : `HTTP_${String(response.status)}`,
    detail: detailOf(body) ?? fallbackDetail(response.status),
    errors: fieldErrorsOf(body.errors),
    retryAfter: retryAfterOf(response.headers.get('Retry-After')),
  });
}

async function bodyOf(response: Response): Promise<Record<string, unknown>> {
  try {
    const parsed: unknown = JSON.parse(await response.text());
    return typeof parsed === 'object' && parsed !== null ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

function detailOf(body: Record<string, unknown>): string | undefined {
  for (const value of [body.detail, body.title]) {
    if (typeof value === 'string' && value.trim() !== '') return value;
  }
  return undefined;
}

function fieldErrorsOf(errors: unknown): FieldError[] {
  if (!Array.isArray(errors)) return [];
  return errors.flatMap((error: unknown) => {
    if (typeof error !== 'object' || error === null) return [];
    const { field, message } = error as Record<string, unknown>;
    return typeof field === 'string' && typeof message === 'string' ? [{ field, message }] : [];
  });
}

/** `Retry-After` in seconds, given either as seconds or as an HTTP date. */
function retryAfterOf(header: string | null): number | undefined {
  if (header === null || header.trim() === '') return undefined;
  if (/^\d+$/.test(header.trim())) return Number(header.trim());
  const date = Date.parse(header);
  return Number.isNaN(date) ? undefined : Math.max(0, Math.ceil((date - Date.now()) / 1000));
}

function fallbackDetail(status: number): string {
  if (status === 401) return 'Choose one of the demo users.';
  if (status === 403) return 'You may not do this with the task.';
  if (status === 404) return 'The task manager does not know this task or form.';
  if (status === 409) return 'Somebody changed the task in the meantime. Reload it and try again.';
  if (status >= 500) return 'The task manager does not answer. Try again in a moment.';
  return `The task manager refused the request (${String(status)}).`;
}
