import { describe, expect, test } from 'vitest';
import { atTime, dueDateTime, dueDay, initials, listOf, priorityLabel, relativeTime, toLocalInput } from './format';

// Local times, so the expectations hold in every time zone the tests run in.
const NOW = new Date(2026, 10, 6, 18, 30);
const at = (day: number, hours: number, minutes = 0) => new Date(2026, 10, day, hours, minutes).toISOString();

describe('dates in the tasklist', () => {
  test('say how long ago a task was created', () => {
    expect(relativeTime(at(6, 18, 29), NOW)).toBe('1 min ago');
    expect(relativeTime(new Date(NOW.getTime() - 20_000).toISOString(), NOW)).toBe('Just now');
    expect(relativeTime(at(6, 18, 8), NOW)).toBe('22 min ago');
    expect(relativeTime(at(6, 15, 0), NOW)).toBe('3 h ago');
    expect(relativeTime(at(4, 18, 0), NOW)).toBe('2 d ago');
    expect(relativeTime(at(1, 9, 0), NOW)).toBe('5 d ago');
    expect(relativeTime(new Date(2026, 9, 25, 9).toISOString(), NOW)).toBe('Oct 25');
  });

  test('name the due day in the list and add the time in the facts', () => {
    expect(dueDay(at(6, 17), NOW)).toBe('Today');
    expect(dueDay(at(7, 9), NOW)).toBe('Tomorrow');
    expect(dueDay(at(8, 17), NOW)).toBe('Nov 8');
    expect(dueDay(new Date(2027, 0, 3).toISOString(), NOW)).toBe('Jan 3, 2027');
    expect(dueDateTime(at(6, 17), NOW)).toBe('Today, 17:00');
    expect(dueDateTime(at(8, 17), NOW)).toBe('Nov 8, 17:00');
    expect(atTime(at(6, 10, 9), NOW)).toBe('today at 10:09');
    expect(atTime(at(3, 10, 9), NOW)).toBe('on Nov 3 at 10:09');
  });

  test('fill the due date input in local time', () => {
    expect(toLocalInput(at(8, 17, 5))).toBe('2026-11-08T17:05');
  });
});

describe('people and priorities', () => {
  test('show initials, lists and priority levels', () => {
    expect(initials('Alice Adams')).toBe('AA');
    expect(initials('Dana  van Demo')).toBe('DD');
    expect(initials('bob')).toBe('B');
    expect(listOf(['the priority'])).toBe('the priority');
    expect(listOf(['the priority', 'the due date', 'the candidate groups'])).toBe('the priority, the due date and the candidate groups');
    expect([0, 25, 26, 50, 74, 75, 100].map(priorityLabel)).toEqual(['Low', 'Low', 'Normal', 'Normal', 'Normal', 'High', 'High']);
  });
});
