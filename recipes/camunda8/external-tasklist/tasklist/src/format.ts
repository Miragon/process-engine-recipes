// How the tasklist writes dates, people and priorities. English, like the rest of the interface.
import type { DemoUser } from './api/types';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

const time = new Intl.DateTimeFormat('en-US', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const monthDay = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' });
const fullDate = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' });

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

function dayOffset(date: Date, now: Date): number {
  return Math.round((startOfDay(date) - startOfDay(now)) / DAY);
}

/** "Just now", "5 min ago", "3 h ago", "2 d ago", then the date: for when a task was created or closed. */
export function relativeTime(value: string, now: Date): string {
  const date = new Date(value);
  const elapsed = now.getTime() - date.getTime();
  if (elapsed < MINUTE) return 'Just now';
  if (elapsed < HOUR) return `${String(Math.floor(elapsed / MINUTE))} min ago`;
  if (elapsed < DAY) return `${String(Math.floor(elapsed / HOUR))} h ago`;
  if (elapsed < 7 * DAY) return `${String(Math.floor(elapsed / DAY))} d ago`;
  return monthDay.format(date);
}

/** "Today", "Tomorrow", "Yesterday" or "Nov 8": a due date in the list. */
export function dueDay(value: string, now: Date): string {
  const date = new Date(value);
  const offset = dayOffset(date, now);
  if (offset === 0) return 'Today';
  if (offset === 1) return 'Tomorrow';
  if (offset === -1) return 'Yesterday';
  return date.getFullYear() === now.getFullYear() ? monthDay.format(date) : fullDate.format(date);
}

/** "Today, 17:00", "Nov 8, 17:00" or "Jan 3, 2027, 17:00": a due date in the task's facts. */
export function dueDateTime(value: string, now: Date): string {
  const date = new Date(value);
  const offset = dayOffset(date, now);
  const named = offset === 0 ? 'Today' : offset === 1 ? 'Tomorrow' : offset === -1 ? 'Yesterday' : null;
  const day = named ?? (date.getFullYear() === now.getFullYear() ? monthDay.format(date) : fullDate.format(date));
  return `${day}, ${time.format(date)}`;
}

/** "Today 10:02" or "Nov 3, 10:02": when something happened. */
export function dateTime(value: string, now: Date): string {
  const date = new Date(value);
  const offset = dayOffset(date, now);
  if (offset === 0) return `Today ${time.format(date)}`;
  if (offset === -1) return `Yesterday ${time.format(date)}`;
  const day = date.getFullYear() === now.getFullYear() ? monthDay.format(date) : fullDate.format(date);
  return `${day}, ${time.format(date)}`;
}

/** "today at 10:09", "yesterday at 10:09" or "on Nov 3 at 10:09": when something happened, inside a sentence. */
export function atTime(value: string, now: Date): string {
  const date = new Date(value);
  const offset = dayOffset(date, now);
  const at = `at ${time.format(date)}`;
  if (offset === 0) return `today ${at}`;
  if (offset === -1) return `yesterday ${at}`;
  return `on ${date.getFullYear() === now.getFullYear() ? monthDay.format(date) : fullDate.format(date)} ${at}`;
}

/** Only the time on the day of `now`, otherwise the date as well: the timeline of a task. */
export function clockTime(value: string, now: Date): string {
  const date = new Date(value);
  return dayOffset(date, now) === 0 ? time.format(date) : `${monthDay.format(date)}, ${time.format(date)}`;
}

/** A date-time for `<input type="datetime-local">` in the browser's time zone. */
export function toLocalInput(value: string | null | undefined): string {
  const date = value ? new Date(value) : new Date(Date.now() + DAY);
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${String(date.getFullYear())}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export type PriorityLevel = 'low' | 'normal' | 'high';

/** Camunda priorities run from 0 to 100 with 50 as the default. */
export const PRIORITY_PRESETS: readonly { level: PriorityLevel; label: string; value: number }[] = [
  { level: 'low', label: 'Low', value: 25 },
  { level: 'normal', label: 'Normal', value: 50 },
  { level: 'high', label: 'High', value: 75 },
];

export function priorityLevel(priority: number): PriorityLevel {
  if (priority >= 75) return 'high';
  if (priority <= 25) return 'low';
  return 'normal';
}

export function priorityLabel(priority: number): string {
  return PRIORITY_PRESETS.find((preset) => preset.level === priorityLevel(priority))?.label ?? 'Normal';
}

/** Up to two initials of a name, "AA" for Alice Adams. */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const letters = parts.length > 1 ? [parts[0], parts[parts.length - 1]] : parts;
  return letters
    .map((part) => part?.[0] ?? '')
    .join('')
    .toUpperCase()
    .slice(0, 2);
}

/** The display name of a demo user, or the plain ID if the task manager does not know it. */
export function nameOf(userId: string, users: readonly DemoUser[] | undefined): string {
  return users?.find((user) => user.id === userId)?.name ?? userId;
}

export function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** "sales" or "sales, finance and warehouse". */
export function listOf(items: readonly string[]): string {
  if (items.length <= 1) return items.join('');
  return `${items.slice(0, -1).join(', ')} and ${items[items.length - 1] ?? ''}`;
}

/** "1 field", "2 fields". */
export function count(amount: number, singular: string, plural = `${singular}s`): string {
  return `${String(amount)} ${amount === 1 ? singular : plural}`;
}
