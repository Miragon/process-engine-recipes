import { useEffect, useState } from 'react';

/** The current time, updated every `interval` milliseconds, so relative times such as "5 min ago" stay right. */
export function useNow(interval = 30_000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => {
      setNow(new Date());
    }, interval);
    return () => {
      window.clearInterval(timer);
    };
  }, [interval]);
  return now;
}
