// Completions that went out while the projection may still show the task open: Camunda took them (204) and the
// reload is on its way, or it accepted them (202) and its listeners still run.
import { useSyncExternalStore } from 'react';

const sent = new Set<string>();
const listeners = new Set<() => void>();

function changed() {
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The completion of the task went out: its forms stay read-only, also when the task is opened again. */
export function completionSent(userTaskKey: string) {
  sent.add(userTaskKey);
  changed();
}

/**
 * The task has closed: its forms follow the task again. A completion that a listener of the application denies after
 * a 202 leaves the task open, and its form read-only until the page is loaded again.
 */
export function completionSettled(userTaskKey: string) {
  if (sent.delete(userTaskKey)) changed();
}

export function useCompletionSent(userTaskKey: string): boolean {
  return useSyncExternalStore(subscribe, () => sent.has(userTaskKey));
}
