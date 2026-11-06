// Live updates: the task manager pushes `task-changed` after every projection change over server-sent events.
// The events carry only keys and states; the tasklist loads again what it shows.
import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { refreshTasks } from './queries';
import type { TaskChanged } from './types';

export const EVENTS_URL = '/api/events';

/** `EventSource.CLOSED`; jsdom has no EventSource to read it from. */
const CLOSED = 2;

export interface TaskEventsOptions {
  /** The keys of the tasks that changed, collected over a short time so a burst of events loads everything once. */
  onChanges: (userTaskKeys: Set<string>) => void;
  /** The stream was lost and is back; whatever changed in between is unknown. */
  onResync: () => void;
  createSource?: (url: string) => EventSource;
  batchMillis?: number;
}

/**
 * Subscribes to `task-changed`. The browser reconnects a dropped stream by itself, but gives up for good when the
 * reconnect is answered with anything but a stream, for example nginx's 502 while the task manager restarts; then
 * a new subscription follows after 1, 2, 4 … up to 30 seconds. Returns a function that ends the subscription.
 */
export function connectTaskEvents({ onChanges, onResync, createSource = (url) => new EventSource(url), batchMillis = 150 }: TaskEventsOptions) {
  let source: EventSource | null = null;
  let attempt = 0;
  let lost = false;
  let ended = false;
  let retryTimer: number | undefined;
  let batchTimer: number | undefined;
  const changed = new Set<string>();

  const flush = () => {
    batchTimer = undefined;
    const keys = new Set(changed);
    changed.clear();
    onChanges(keys);
  };

  const connect = () => {
    const current = createSource(EVENTS_URL);
    source = current;
    current.addEventListener('open', () => {
      attempt = 0;
      if (!lost) return;
      lost = false;
      onResync();
    });
    current.addEventListener('task-changed', (event: MessageEvent<string>) => {
      const key = keyOf(event.data);
      if (key === null) return;
      changed.add(key);
      batchTimer ??= window.setTimeout(flush, batchMillis);
    });
    current.addEventListener('error', () => {
      lost = true;
      if (current.readyState !== CLOSED || ended) return;
      current.close();
      const delay = Math.min(30_000, 1000 * 2 ** attempt);
      attempt++;
      retryTimer = window.setTimeout(connect, delay);
    });
  };

  connect();
  return () => {
    ended = true;
    window.clearTimeout(retryTimer);
    window.clearTimeout(batchTimer);
    source?.close();
  };
}

export function useLiveUpdates() {
  const client = useQueryClient();

  useEffect(() => {
    if (typeof EventSource === 'undefined') return;
    return connectTaskEvents({
      onChanges: (keys) => {
        refreshTasks(client, keys);
      },
      // Everything but the forms, which hold what the user entered, is loaded again.
      onResync: () => {
        void client.invalidateQueries({ predicate: (query) => !['form', 'start-form'].includes(String(query.queryKey[0])) });
      },
    });
  }, [client]);
}

function keyOf(data: string): string | null {
  try {
    const change = JSON.parse(data) as Partial<TaskChanged>;
    return typeof change.userTaskKey === 'string' ? change.userTaskKey : null;
  } catch {
    return null;
  }
}
