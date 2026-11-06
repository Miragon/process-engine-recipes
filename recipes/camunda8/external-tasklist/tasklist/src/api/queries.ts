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
import type { Task, TaskQuery } from './types';

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
  facets: (state: StateFilter) => ['facets', state] as const,
  counts: (user: string | null, state: StateFilter) => ['counts', user, state] as const,
  task: (key: string, user: string | null) => ['task', key, user] as const,
  history: (key: string) => ['history', key] as const,
  form: (key: string, user: string | null, assignee: string | null | undefined, state: string) =>
    ['form', key, user, assignee ?? null, state] as const,
  startForm: (ref: StartFormRef, user: string | null) => ['start-form', ref.domain, ref.id, user] as const,
  firstTask: (businessId: string) => ['first-task', businessId] as const,
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
    queryFn: () => api.tasks(query),
    enabled: userId !== null,
    placeholderData: keepPreviousData,
  });
}

export function useFacets(state: StateFilter) {
  return useQuery({ queryKey: keys.facets(state), queryFn: () => api.facets(toStateQuery(state)), placeholderData: keepPreviousData });
}

/** The facets have no counts per assignment, so the totals of two one-item pages stand in for them. */
export function useAssignmentCounts(state: StateFilter) {
  const { userId } = useSession();
  return useQuery({
    queryKey: keys.counts(userId, state),
    queryFn: async () => {
      const [mine, unassigned] = await Promise.all([
        api.tasks({ state: toStateQuery(state), assignment: 'MINE', limit: 1 }),
        api.tasks({ state: toStateQuery(state), assignment: 'UNASSIGNED', limit: 1 }),
      ]);
      return { mine: mine.total, unassigned: unassigned.total };
    },
    enabled: userId !== null,
    placeholderData: keepPreviousData,
  });
}

export function useTask(key: string) {
  const { userId } = useSession();
  return useQuery({ queryKey: keys.task(key, userId), queryFn: () => api.task(key) });
}

export function useHistory(key: string) {
  return useQuery({ queryKey: keys.history(key), queryFn: () => api.history(key) });
}

/** The form a task is still being synchronized for (503 with Retry-After) is asked for again after the given time. */
const syncRetry = {
  retry: (failures: number, error: Error) => (isProblem(error, 503) ? failures < 20 : !isFinal(error) && failures < 2),
  retryDelay: (_failures: number, error: Error) => (error instanceof ApiProblem && error.retryAfter !== undefined ? error.retryAfter * 1000 : 2000),
};

/**
 * The form of a task. It is fetched again when the assignee or the state changes, because the application decides
 * with them whether the form is editable and what it shows; it is never refetched in the background, which would
 * drop what the user entered.
 */
export function useTaskForm(task: Task, enabled: boolean) {
  const { userId } = useSession();
  return useQuery({
    queryKey: keys.form(task.userTaskKey, userId, task.assignee, task.state),
    queryFn: () => api.form(task.userTaskKey),
    enabled,
    staleTime: Infinity,
    gcTime: 60_000,
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

/** Waits for the first task of a process instance the user just started, found by its business ID. */
export function useFirstTask(businessId: string | null, waiting: boolean) {
  return useQuery({
    queryKey: keys.firstTask(businessId ?? ''),
    queryFn: async () => {
      const page = await api.tasks({ q: businessId ?? '', state: 'OPEN', sort: 'CREATED_AT', descending: false, limit: 20 });
      return page.items.find((task) => task.businessId === businessId) ?? null;
    },
    enabled: businessId !== null && waiting,
    refetchInterval: (query) => (query.state.data ? false : 1000),
    staleTime: 0,
    gcTime: 0,
  });
}

/** After a command the projection is already up to date, so everything that shows the task is loaded again. */
export function refreshTask(client: Client, task: Task | string) {
  const key = typeof task === 'string' ? task : task.userTaskKey;
  void client.invalidateQueries({ queryKey: ['tasks'] });
  void client.invalidateQueries({ queryKey: ['facets'] });
  void client.invalidateQueries({ queryKey: ['counts'] });
  void client.invalidateQueries({ queryKey: ['task', key] });
  void client.invalidateQueries({ queryKey: keys.history(key) });
}

/** Puts a task the task manager returned into the cache, so the detail shows it without another request. */
export function storeTask(client: Client, task: Task) {
  client.setQueryData(keys.task(task.userTaskKey, currentUser()), task);
}
