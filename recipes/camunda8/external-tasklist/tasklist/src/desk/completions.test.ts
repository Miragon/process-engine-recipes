import { act, renderHook } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { completionSent, completionSettled, useCompleting } from './completions';

describe('completions on their way', () => {
  test('lock the open task for every view of it until the completion is settled or the task has closed', () => {
    const first = renderHook(({ open }) => useCompleting('42', open), { initialProps: { open: true } });
    expect(first.result.current).toBe(false);

    act(() => {
      completionSent('42');
    });
    expect(first.result.current).toBe(true);
    first.unmount();

    // Opening the task again still finds the completion on its way; other tasks are not affected.
    const again = renderHook(({ open }) => useCompleting('42', open), { initialProps: { open: true } });
    expect(again.result.current).toBe(true);
    expect(renderHook(() => useCompleting('43', true)).result.current).toBe(false);

    again.rerender({ open: false });
    expect(again.result.current).toBe(false);
    expect(renderHook(() => useCompleting('42', true)).result.current).toBe(false);

    act(() => {
      completionSent('42');
      completionSettled('42');
    });
    expect(renderHook(() => useCompleting('42', true)).result.current).toBe(false);
  });

  test('forget a completion whose answer arrives after the reload that shows the task closed', () => {
    const closed = renderHook(() => useCompleting('7', false));

    act(() => {
      completionSent('7');
    });

    expect(closed.result.current).toBe(false);
    expect(renderHook(() => useCompleting('7', true)).result.current).toBe(false);
  });
});
