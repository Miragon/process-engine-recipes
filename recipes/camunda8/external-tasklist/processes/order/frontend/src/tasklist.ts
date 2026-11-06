// The connection to the tasklist as a small store the React components subscribe to.
import type { InitPayload, ResultPayload } from '@tasklist/form-protocol';
import { useSyncExternalStore } from 'react';
import { connectToTasklist, observeHeight } from './protocol';

export interface TasklistState {
  /** The form to show, once the tasklist sent it. */
  form: InitPayload | null;
  /** A submission waits for its result. */
  pending: boolean;
  /** The outcome of the last submission, until the user changes the form again. */
  result: ResultPayload | null;
}

let state: TasklistState = { form: null, pending: false, result: null };
const subscribers = new Set<() => void>();

function update(change: Partial<TasklistState>) {
  state = { ...state, ...change };
  subscribers.forEach((notify) => {
    notify();
  });
}

function subscribe(notify: () => void) {
  subscribers.add(notify);
  return () => {
    subscribers.delete(notify);
  };
}

// Connects as soon as the bundle runs, so the tasklist's answer cannot arrive before anybody listens.
const host = connectToTasklist(window, {
  onInit: (form) => {
    update({ form, pending: false, result: null });
  },
  onResult: (result) => {
    update({ pending: false, result });
  },
});

export function useTasklist(): TasklistState {
  return useSyncExternalStore(subscribe, () => state);
}

export function submit(data: Record<string, unknown>): void {
  if (host.submit(data)) update({ pending: true, result: null });
}

/** Forgets the last result once the user changes the form, so its errors do not linger. */
export function clearResult(): void {
  if (state.result !== null && !state.result.ok) update({ result: null });
}

export function reportHeight(element: Element): () => void {
  return observeHeight(element, host);
}
