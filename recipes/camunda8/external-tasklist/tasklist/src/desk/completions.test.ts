import { act, renderHook } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { completionSent, completionSettled, useCompletionSent } from './completions';

describe('completions', () => {
  test('keep a sent completion until the task has closed, for every view of the task', () => {
    const first = renderHook(() => useCompletionSent('42'));
    expect(first.result.current).toBe(false);

    act(() => {
      completionSent('42');
    });
    expect(first.result.current).toBe(true);
    first.unmount();

    // Opening the task again still finds the completion on its way.
    const again = renderHook(() => useCompletionSent('42'));
    expect(again.result.current).toBe(true);
    expect(renderHook(() => useCompletionSent('43')).result.current).toBe(false);

    act(() => {
      completionSettled('42');
    });
    expect(again.result.current).toBe(false);
  });
});
