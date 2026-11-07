import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { connectTaskEvents } from './events';

/** Enough of an EventSource to play the task manager's stream. */
class FakeSource extends EventTarget {
  readonly url: string;
  readyState = 0;
  closed = false;

  constructor(url: string) {
    super();
    this.url = url;
  }

  open() {
    this.readyState = 1;
    this.dispatchEvent(new Event('open'));
  }

  taskChanged(userTaskKey: string, state = 'OPEN') {
    this.dispatchEvent(new MessageEvent('task-changed', { data: JSON.stringify({ userTaskKey, state, domain: 'order' }) }));
  }

  /** The connection dropped; the browser tries again by itself. */
  drop() {
    this.readyState = 0;
    this.dispatchEvent(new Event('error'));
  }

  /** The reconnect was answered with something else than a stream, for example 502; the browser gives up. */
  fail() {
    this.readyState = 2;
    this.dispatchEvent(new Event('error'));
  }

  close() {
    this.closed = true;
    this.readyState = 2;
  }
}

let sources: FakeSource[];
let onChanges: ReturnType<typeof vi.fn<(keys: Set<string>) => void>>;
let onResync: ReturnType<typeof vi.fn<() => void>>;
let disconnect: () => void;

beforeEach(() => {
  vi.useFakeTimers();
  sources = [];
  onChanges = vi.fn<(keys: Set<string>) => void>();
  onResync = vi.fn<() => void>();
  disconnect = connectTaskEvents({
    onChanges,
    onResync,
    createSource: (url) => {
      const source = new FakeSource(url);
      sources.push(source);
      return source as unknown as EventSource;
    },
  });
});

afterEach(() => {
  disconnect();
  vi.useRealTimers();
});

const latest = () => {
  const source = sources.at(-1);
  if (!source) throw new Error('no event source');
  return source;
};

describe('the live updates', () => {
  test('subscribe to the events of the task manager and collect a burst into one reload', () => {
    expect(latest().url).toBe('/api/events');
    latest().open();

    latest().taskChanged('1');
    latest().taskChanged('2');
    latest().taskChanged('1', 'CANCELED');
    latest().dispatchEvent(new MessageEvent('task-changed', { data: 'not json' }));
    expect(onChanges).not.toHaveBeenCalled();

    vi.advanceTimersByTime(200);
    expect(onChanges).toHaveBeenCalledTimes(1);
    expect([...(onChanges.mock.calls[0]?.[0] ?? [])]).toEqual(['1', '2']);
    expect(onResync).not.toHaveBeenCalled();
  });

  test('reload everything after the browser reconnected a dropped stream', () => {
    latest().open();
    latest().drop();
    latest().open();

    expect(sources).toHaveLength(1);
    expect(onResync).toHaveBeenCalledTimes(1);
  });

  test('subscribe again with growing pauses when the browser gave up, for example during a restart', () => {
    latest().open();
    latest().fail();
    expect(sources[0]?.closed).toBe(true);

    vi.advanceTimersByTime(999);
    expect(sources).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(sources).toHaveLength(2);

    latest().fail();
    vi.advanceTimersByTime(1999);
    expect(sources).toHaveLength(2);
    vi.advanceTimersByTime(1);
    expect(sources).toHaveLength(3);

    latest().open();
    expect(onResync).toHaveBeenCalledTimes(1);
    latest().fail();
    vi.advanceTimersByTime(1000);
    expect(sources).toHaveLength(4);
  });

  test('stop when the tasklist leaves', () => {
    latest().open();
    latest().fail();
    disconnect();

    vi.advanceTimersByTime(60_000);
    expect(sources).toHaveLength(1);
  });
});
