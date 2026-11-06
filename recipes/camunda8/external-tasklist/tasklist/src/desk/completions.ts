// Completions on their way while the projection may still show the task open: the request is under way, Camunda
// took it (204) and the reload is coming, or it accepted it (202) and its listeners still run.
import { useEffect, useSyncExternalStore } from 'react';

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

/** The completion of the task goes out: its form and commands stay locked, also when the task is opened again. */
export function completionSent(userTaskKey: string) {
  sent.add(userTaskKey);
  changed();
}

/**
 * The completion was refused, or the task has closed: the task's form follows the task again. A completion that a
 * listener of the application denies after a 202 leaves the task open, and locked until the page is loaded again.
 */
export function completionSettled(userTaskKey: string) {
  if (sent.delete(userTaskKey)) changed();
}

/**
 * Whether a completion of the open task is on its way. It is forgotten once the task has closed, also when the
 * answer to the completion arrives only after the reload that shows the task closed.
 */
export function useCompleting(userTaskKey: string, open: boolean): boolean {
  const isSent = useSyncExternalStore(subscribe, () => sent.has(userTaskKey));
  useEffect(() => {
    if (isSent && !open) completionSettled(userTaskKey);
  }, [isSent, open, userTaskKey]);
  return isSent && open;
}
