// Server state of the tasklist with TanStack Query. Keys name the demo user where the answer depends on it.
import {
  keepPreviousData,
  MutationCache,
  QueryCache,
  QueryClient,
  useQuery,
  type QueryClient as Client,
} from '@tanstack/react-query';
import { currentUser, rejectUser, useSession } from '../session';
import { toStateQuery, type StartFormRef, type StateFilter } from '../desk/location';
import { ApiProblem, createApiClient, isProblem } from './client';
import type { StartResult, Task, TaskQuery } from './types';

export const api = createApiClient({ currentUser });

/** Answers that will not change by asking again. */
const isFinal = (error: unknown) => isProblem(error) && error.status >= 400 && error.status < 500;

export function createQueryClient(): QueryClient {
  // A 401 means the task manager does not accept the demo user: ask for one again.
  const onError = (error: unknown) => {
    if (isProblem(error, 401)) rejectUser(error.detail);
  };
  return new QueryClient({
    queryCache: new QueryCache({ onError }),
    mutationCache: new MutationCache({ onError }),
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        retry: (failures, error) => !isFinal(error) && failures < 2,
      },
      mutations: { retry: false },
    },
  });
}

export const keys = {
  users: ['users'] as const,
  applications: ['applications'] as const,
  startForms: ['start-forms'] as const,
  tasks: (user: string | null, query: TaskQuery) => ['tasks', user, query] as const,
  facets: (user: string | null, state: StateFilter) => ['facets', user, state] as const,
  task: (key: string, user: string | null) => ['task', key, user] as const,
  history: (key: string) => ['history', key] as const,
  form: (key: string, user: string | null, assignee: string | null | undefined, state: string) =>
    ['form', key, user, assignee ?? null, state] as const,
  startForm: (ref: StartFormRef, user: string | null) => ['start-form', ref.domain, ref.id, user] as const,
  firstTask: (processInstanceKey: string) => ['first-task', processInstanceKey] as const,
};

const DIRECTORY = { staleTime: 5 * 60_000 };

export function useUsers() {
  return useQuery({ queryKey: keys.users, queryFn: api.users, ...DIRECTORY });
}

export function useApplications() {
  return useQuery({ queryKey: keys.applications, queryFn: api.applications, ...DIRECTORY });
}

export function useStartForms() {
  const { userId } = useSession();
  return useQuery({ queryKey: keys.startForms, queryFn: api.startForms, enabled: userId !== null, ...DIRECTORY });
}

export function useTaskPage(query: TaskQuery) {
  const { userId } = useSession();
  return useQuery({
    queryKey: keys.tasks(userId, query),
    queryFn: ({ signal }) => api.tasks(query, signal),
    enabled: userId !== null,
    placeholderData: keepPreviousData,
  });
}

export function useFacets(state: StateFilter) {
  const { userId } = useSession();
  return useQuery({
    queryKey: keys.facets(userId, state),
    queryFn: ({ signal }) => api.facets(toStateQuery(state), signal),
    placeholderData: keepPreviousData,
  });
}

export function useTask(key: string) {
  const { userId } = useSession();
  return useQuery({ queryKey: keys.task(key, userId), queryFn: ({ signal }) => api.task(key, signal) });
}

export function useHistory(key: string) {
  return useQuery({ queryKey: keys.history(key), queryFn: ({ signal }) => api.history(key, signal) });
}

/** The form a task is still being synchronized for (503 with Retry-After) is asked for again after the given time. */
const syncRetry = {
  retry: (failures: number, error: Error) => (isProblem(error, 503) ? failures < 20 : !isFinal(error) && failures < 2),
  retryDelay: (_failures: number, error: Error) => (error instanceof ApiProblem && error.retryAfter !== undefined ? error.retryAfter * 1000 : 2000),
};

/**
 * The form of a task. It is fetched again when the assignee or the state changes, because the application decides
 * with them whether the form is editable and what it shows. While it is shown it is never refetched in the
 * background, which would drop what the user entered; once it is no longer shown it is forgotten, so coming back
 * shows what the application keeps now, a saved draft included.
 */
export function useTaskForm(task: Task, enabled: boolean) {
  const { userId } = useSession();
  return useQuery({
    queryKey: keys.form(task.userTaskKey, userId, task.assignee, task.state),
    queryFn: () => api.form(task.userTaskKey),
    enabled,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    ...syncRetry,
  });
}

export function useStartForm(ref: StartFormRef) {
  const { userId } = useSession();
  return useQuery({
    queryKey: keys.startForm(ref, userId),
    queryFn: () => api.startForm(ref.domain, ref.id),
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    ...syncRetry,
  });
}

/**
 * Waits for the first task of a process instance the user just started. The task manager offers no filter by
 * process instance, so the newest open tasks, narrowed by the business ID when there is one, are searched for it.
 */
export function useFirstTask(started: StartResult | null, waiting: boolean) {
  return useQuery({
    queryKey: keys.firstTask(started?.processInstanceKey ?? ''),
    queryFn: async ({ signal }) => {
      const query = { q: started?.businessId ?? undefined, state: 'OPEN', sort: 'CREATED_AT', descending: true, limit: 50 } as const;
      const page = await api.tasks(query, signal);
      return page.items.find((task) => task.processInstanceKey === started?.processInstanceKey) ?? null;
    },
    enabled: started !== null && waiting,
    refetchInterval: (query) => (query.state.data ? false : 1000),
    staleTime: 0,
    gcTime: 0,
  });
}

/** Loads again the lists and their counts. */
function refreshLists(client: Client) {
  void client.invalidateQueries({ queryKey: ['tasks'] });
  void client.invalidateQueries({ queryKey: ['facets'] });
}

/** Loads again the lists, their counts and everything that shows one of the given tasks. */
export function refreshTasks(client: Client, userTaskKeys: Iterable<string>) {
  refreshLists(client);
  for (const key of userTaskKeys) {
    void client.invalidateQueries({ queryKey: ['task', key] });
    void client.invalidateQueries({ queryKey: keys.history(key) });
  }
}

/** After a command the projection is already up to date, so everything that shows the task is loaded again. */
export function refreshTask(client: Client, task: Task | string) {
  refreshTasks(client, [typeof task === 'string' ? task : task.userTaskKey]);
}

/** Puts a task the task manager returned into the cache, so the detail shows it without another request. */
export function storeTask(client: Client, task: Task) {
  client.setQueryData(keys.task(task.userTaskKey, currentUser()), task);
}
