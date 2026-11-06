// What the desk shows lives in the query string, so a view can be bookmarked, shared and reloaded:
// ?assignment=mine&domain=payment&state=open&group=finance&q=ORD-1042&sort=priority&task=2251799813685337
import type { StateQuery, TaskQuery } from '../api/types';

export const ASSIGNMENTS = ['all', 'mine', 'unassigned'] as const;
export const STATES = ['open', 'completed', 'canceled'] as const;
export const SORTS = ['newest', 'oldest', 'priority', 'due'] as const;

export type Assignment = (typeof ASSIGNMENTS)[number];
export type StateFilter = (typeof STATES)[number];
export type Sort = (typeof SORTS)[number];

export interface Filters {
  assignment: Assignment;
  state: StateFilter;
  /** Key of the owning application, as in the task header `domain`. */
  domain: string | null;
  group: string | null;
  /** Searches the task name, the subject and the business ID. */
  q: string;
  sort: Sort;
}

export interface StartFormRef {
  domain: string;
  id: string;
}

export interface DeskLocation {
  filters: Filters;
  /** Key of the task open in the detail pane. */
  task: string | null;
  /** The start form open in the detail pane instead of a task. */
  start: StartFormRef | null;
}

export const DEFAULT_FILTERS: Filters = {
  assignment: 'all',
  state: 'open',
  domain: null,
  group: null,
  q: '',
  sort: 'newest',
};

export const EMPTY_LOCATION: DeskLocation = { filters: DEFAULT_FILTERS, task: null, start: null };

const KEY = /^\d{1,20}$/;

export function parseLocation(search: string): DeskLocation {
  const params = new URLSearchParams(search);
  const text = (name: string) => params.get(name)?.trim() ?? '';
  const optional = (name: string) => (text(name) === '' ? null : text(name));
  const task = text('task');
  return {
    filters: {
      assignment: oneOf(ASSIGNMENTS, text('assignment'), DEFAULT_FILTERS.assignment),
      state: oneOf(STATES, text('state'), DEFAULT_FILTERS.state),
      domain: optional('domain'),
      group: optional('group'),
      q: text('q'),
      sort: oneOf(SORTS, text('sort'), DEFAULT_FILTERS.sort),
    },
    task: KEY.test(task) ? task : null,
    start: startFormOf(text('start')),
  };
}

/** The query string of a location; defaults are left out, so the plain desk has none. */
export function formatLocation({ filters, task, start }: DeskLocation): string {
  const params = new URLSearchParams();
  if (filters.assignment !== DEFAULT_FILTERS.assignment) params.set('assignment', filters.assignment);
  if (filters.domain !== null) params.set('domain', filters.domain);
  if (filters.state !== DEFAULT_FILTERS.state) params.set('state', filters.state);
  if (filters.group !== null) params.set('group', filters.group);
  if (filters.q.trim() !== '') params.set('q', filters.q.trim());
  if (filters.sort !== DEFAULT_FILTERS.sort) params.set('sort', filters.sort);
  if (start !== null) params.set('start', `${start.domain}/${start.id}`);
  else if (task !== null) params.set('task', task);
  const search = params.toString();
  return search === '' ? '' : `?${search}`;
}

/** The task manager's query for the filters: its enums are upper case, and the sort direction is explicit. */
export function toTaskQuery(filters: Filters): TaskQuery {
  return {
    state: toStateQuery(filters.state),
    assignment: filters.assignment === 'all' ? undefined : filters.assignment === 'mine' ? 'MINE' : 'UNASSIGNED',
    domain: filters.domain ?? undefined,
    candidateGroup: filters.group ?? undefined,
    q: filters.q.trim() === '' ? undefined : filters.q.trim(),
    ...SORT_QUERIES[filters.sort],
  };
}

export function toStateQuery(state: StateFilter): StateQuery {
  return state === 'open' ? 'OPEN' : state === 'completed' ? 'COMPLETED' : 'CANCELED';
}

/** Whether anything narrows the list; the sort order does not. */
export function isFiltered(filters: Filters): boolean {
  return (
    filters.assignment !== DEFAULT_FILTERS.assignment ||
    filters.state !== DEFAULT_FILTERS.state ||
    filters.domain !== null ||
    filters.group !== null ||
    filters.q.trim() !== ''
  );
}

/** Drops every filter but keeps the sort order. */
export function clearFilters(filters: Filters): Filters {
  return { ...DEFAULT_FILTERS, sort: filters.sort };
}

const SORT_QUERIES: Record<Sort, Pick<TaskQuery, 'sort' | 'descending'>> = {
  newest: { sort: 'CREATED_AT', descending: true },
  oldest: { sort: 'CREATED_AT', descending: false },
  priority: { sort: 'PRIORITY', descending: true },
  due: { sort: 'DUE_DATE', descending: false },
};

function oneOf<T extends string>(values: readonly T[], value: string, fallback: T): T {
  return (values as readonly string[]).includes(value) ? (value as T) : fallback;
}

function startFormOf(value: string): StartFormRef | null {
  const match = /^([^/]+)\/([^/]+)$/.exec(value);
  return match?.[1] !== undefined && match[2] !== undefined ? { domain: match[1], id: match[2] } : null;
}
