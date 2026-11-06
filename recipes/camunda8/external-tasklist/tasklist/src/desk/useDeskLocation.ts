import { useCallback, useMemo, useSyncExternalStore } from 'react';
import { formatLocation, parseLocation, type DeskLocation } from './location';

const NAVIGATED = 'tasklist:navigate';

function subscribe(notify: () => void) {
  window.addEventListener('popstate', notify);
  window.addEventListener(NAVIGATED, notify);
  return () => {
    window.removeEventListener('popstate', notify);
    window.removeEventListener(NAVIGATED, notify);
  };
}

export type Navigate = (next: DeskLocation, options?: { replace?: boolean }) => void;

/** The desk's location in the query string; changes push a history entry unless `replace` is set. */
export function useDeskLocation(): [DeskLocation, Navigate] {
  const search = useSyncExternalStore(subscribe, () => window.location.search);
  const location = useMemo(() => parseLocation(search), [search]);

  const navigate = useCallback<Navigate>((next, { replace = false } = {}) => {
    const target = `${window.location.pathname}${formatLocation(next)}`;
    if (target === `${window.location.pathname}${window.location.search}`) return;
    if (replace) window.history.replaceState(null, '', target);
    else window.history.pushState(null, '', target);
    window.dispatchEvent(new Event(NAVIGATED));
  }, []);

  return [location, navigate];
}
