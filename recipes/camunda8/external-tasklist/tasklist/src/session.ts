// The demo user the tasklist works as. The recipe has no sign-in: the choice is remembered in the browser and sent
// with every request as X-Demo-User. A real deployment takes the user from the identity provider's token instead.
import { useSyncExternalStore } from 'react';

const STORAGE_KEY = 'tasklist.demo-user';

export interface Session {
  /** The chosen demo user, or `null` before anybody was chosen. */
  userId: string | null;
  /** Why the tasklist asks for a user again, for example because the task manager did not accept the stored one. */
  notice: string | null;
}

let session: Session = { userId: readStoredUser(), notice: null };
const listeners = new Set<() => void>();

function change(next: Session) {
  session = next;
  listeners.forEach((notify) => {
    notify();
  });
}

export const currentUser = (): string | null => session.userId;

export function chooseUser(userId: string): void {
  storeUser(userId);
  change({ userId, notice: null });
}

/** The task manager answered 401: forget the user and ask for one again. */
export function rejectUser(notice: string): void {
  if (session.userId === null && session.notice === notice) return;
  storeUser(null);
  change({ userId: null, notice });
}

function subscribe(notify: () => void) {
  listeners.add(notify);
  return () => {
    listeners.delete(notify);
  };
}

export function useSession(): Session {
  return useSyncExternalStore(subscribe, () => session);
}

// Storage can be unavailable (private windows, blocked site data); the tasklist then asks again after a reload.
function readStoredUser(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function storeUser(userId: string | null): void {
  try {
    if (userId === null) window.localStorage.removeItem(STORAGE_KEY);
    else window.localStorage.setItem(STORAGE_KEY, userId);
  } catch {
    // Not remembered; the choice still holds until the page is reloaded.
  }
}
