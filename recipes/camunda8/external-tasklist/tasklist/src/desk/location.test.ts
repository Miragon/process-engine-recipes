import { describe, expect, test } from 'vitest';
import {
  clearFilters,
  DEFAULT_FILTERS,
  EMPTY_LOCATION,
  formatLocation,
  isFiltered,
  parseLocation,
  toTaskQuery,
  type DeskLocation,
} from './location';

describe('the desk location in the query string', () => {
  test('an empty query string is the open tasks of everybody, newest first', () => {
    expect(parseLocation('')).toEqual(EMPTY_LOCATION);
    expect(formatLocation(EMPTY_LOCATION)).toBe('');
  });

  test('reads every filter, the open task and the open start form', () => {
    const location = parseLocation('?assignment=mine&domain=payment&state=completed&group=finance&q=+ORD-1042+&sort=priority&task=2251799813685337');

    expect(location).toEqual({
      filters: { assignment: 'mine', domain: 'payment', state: 'completed', group: 'finance', q: 'ORD-1042', sort: 'priority' },
      task: '2251799813685337',
      start: null,
    });
    expect(parseLocation('?start=order/new-order').start).toEqual({ domain: 'order', id: 'new-order' });
  });

  test('falls back to the defaults for values it does not know', () => {
    const location = parseLocation('?assignment=everybody&state=deleted&sort=random&task=1;DROP&start=order&domain=');

    expect(location).toEqual(EMPTY_LOCATION);
  });

  test('writes only what differs from the defaults and survives a round trip', () => {
    const location: DeskLocation = {
      filters: { ...DEFAULT_FILTERS, assignment: 'unassigned', group: 'sales', q: 'Anna Schmidt' },
      task: '42',
      start: null,
    };

    const search = formatLocation(location);

    expect(search).toBe('?assignment=unassigned&group=sales&q=Anna+Schmidt&task=42');
    expect(parseLocation(search)).toEqual(location);
  });

  test('a start form takes the place of the open task', () => {
    const search = formatLocation({ filters: DEFAULT_FILTERS, task: '42', start: { domain: 'order', id: 'new-order' } });

    expect(search).toBe('?start=order%2Fnew-order');
    expect(parseLocation(search)).toEqual({ filters: DEFAULT_FILTERS, task: null, start: { domain: 'order', id: 'new-order' } });
  });

  test('maps the filters to the task manager query with its upper case enums', () => {
    expect(toTaskQuery(DEFAULT_FILTERS)).toEqual({ state: 'OPEN', sort: 'CREATED_AT', descending: true });
    expect(
      toTaskQuery({ assignment: 'mine', state: 'canceled', domain: 'order', group: 'sales', q: ' ORD-1042 ', sort: 'due' }),
    ).toEqual({
      state: 'CANCELED',
      assignment: 'MINE',
      domain: 'order',
      candidateGroup: 'sales',
      q: 'ORD-1042',
      sort: 'DUE_DATE',
      descending: false,
    });
    expect(toTaskQuery({ ...DEFAULT_FILTERS, assignment: 'unassigned', state: 'completed', sort: 'oldest' })).toMatchObject({
      assignment: 'UNASSIGNED',
      state: 'COMPLETED',
      sort: 'CREATED_AT',
      descending: false,
    });
  });

  test('knows whether filters narrow the list, and clears them but keeps the sort order', () => {
    expect(isFiltered({ ...DEFAULT_FILTERS, sort: 'priority' })).toBe(false);
    expect(isFiltered({ ...DEFAULT_FILTERS, q: 'Finn' })).toBe(true);
    expect(clearFilters({ assignment: 'mine', state: 'canceled', domain: 'order', group: 'sales', q: 'x', sort: 'due' })).toEqual({
      ...DEFAULT_FILTERS,
      sort: 'due',
    });
  });
});
