import { afterEach, describe, expect, test, vi } from 'vitest';

const KEY = 'tasklist.demo-user';

async function freshSession() {
  vi.resetModules();
  return import('./session');
}

afterEach(() => {
  window.localStorage.clear();
  vi.restoreAllMocks();
});

describe('the demo user', () => {
  test('is remembered in the browser and read again on the next visit', async () => {
    const first = await freshSession();
    first.chooseUser('bob');
    expect(window.localStorage.getItem(KEY)).toBe('bob');

    const next = await freshSession();
    expect(next.currentUser()).toBe('bob');
  });

  test('is forgotten when the task manager does not accept it', async () => {
    window.localStorage.setItem(KEY, 'mallory');
    const session = await freshSession();
    expect(session.currentUser()).toBe('mallory');

    session.rejectUser('Choose one of the demo users and send it as X-Demo-User.');

    expect(session.currentUser()).toBeNull();
    expect(window.localStorage.getItem(KEY)).toBeNull();
  });

  test('still works when the browser blocks storage', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    const session = await freshSession();
    expect(session.currentUser()).toBeNull();

    session.chooseUser('alice');

    expect(session.currentUser()).toBe('alice');
  });
});
