// Live updates: the task manager pushes `task-changed` after every projection change over server-sent events.
// The events carry only keys and states; the tasklist loads again what it shows.
import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { refreshTask } from './queries';
import type { TaskChanged } from './types';

export const EVENTS_URL = '/api/events';

export function useLiveUpdates(onChange?: (change: TaskChanged) => void) {
  const client = useQueryClient();

  useEffect(() => {
    if (typeof EventSource === 'undefined') return;
    const source = new EventSource(EVENTS_URL);
    let lost = false;

    source.addEventListener('task-changed', (event: MessageEvent<string>) => {
      const change = parse(event.data);
      if (change === null) return;
      refreshTask(client, change.userTaskKey);
      onChange?.(change);
    });
    // The browser reconnects by itself; whatever changed in between is unknown, so everything but the forms, which
    // hold what the user entered, is loaded again.
    source.addEventListener('error', () => {
      lost = true;
    });
    source.addEventListener('open', () => {
      if (!lost) return;
      lost = false;
      void client.invalidateQueries({ predicate: (query) => !['form', 'start-form'].includes(String(query.queryKey[0])) });
    });

    return () => {
      source.close();
    };
  }, [client, onChange]);
}

function parse(data: string): TaskChanged | null {
  try {
    const change = JSON.parse(data) as Partial<TaskChanged>;
    return typeof change.userTaskKey === 'string' ? (change as TaskChanged) : null;
  } catch {
    return null;
  }
}
