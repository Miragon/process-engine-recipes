import { FORM_PROTOCOL, FORM_PROTOCOL_VERSION, type InitPayload } from '@tasklist/form-protocol';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { connectToTasklist, type FrameWindow, type HostHandlers, type TasklistHost } from './protocol';

const init: InitPayload = {
  formRef: 'check-order@1',
  mode: 'task',
  data: { order: { id: 'ORD-1001' } },
  readOnly: false,
  draftable: false,
};

describe('connectToTasklist', () => {
  const tasklist = { postMessage: vi.fn() };
  const stranger = { postMessage: vi.fn() };
  let receive: (event: MessageEvent) => void;
  let handlers: HostHandlers;
  let host: TasklistHost;

  /** A message as the browser delivers it to the frame. */
  const deliver = (source: unknown, data: unknown) => {
    receive({ source, data } as MessageEvent);
  };
  const fromTasklist = (type: string, payload: unknown) => ({
    protocol: FORM_PROTOCOL,
    version: FORM_PROTOCOL_VERSION,
    type,
    payload,
  });

  beforeEach(() => {
    tasklist.postMessage.mockClear();
    handlers = { onInit: vi.fn(), onResult: vi.fn() };
    const frame = {
      parent: tasklist,
      addEventListener: (_type: string, listener: (event: MessageEvent) => void) => {
        receive = listener;
      },
      removeEventListener: vi.fn(),
    } as unknown as FrameWindow;
    host = connectToTasklist(frame, handlers);
  });

  it('tells the tasklist that the form is ready', () => {
    expect(tasklist.postMessage).toHaveBeenCalledExactlyOnceWith(
      { protocol: 'tasklist-form', version: 1, type: 'ready' },
      '*',
    );
  });

  it('renders the form the tasklist sends', () => {
    deliver(tasklist, fromTasklist('init', init));

    expect(handlers.onInit).toHaveBeenCalledWith(init);
  });

  it('ignores messages from another window', () => {
    deliver(stranger, fromTasklist('init', init));

    expect(handlers.onInit).not.toHaveBeenCalled();
  });

  it('ignores messages of another protocol version', () => {
    deliver(tasklist, { ...fromTasklist('init', init), version: 2 });
    deliver(tasklist, { ...fromTasklist('init', init), protocol: 'other-form' });

    expect(handlers.onInit).not.toHaveBeenCalled();
  });

  it('submits the data in a versioned envelope', () => {
    deliver(tasklist, fromTasklist('init', init));

    expect(host.submit({ approved: true, comment: null })).toBe(true);
    expect(tasklist.postMessage).toHaveBeenLastCalledWith(
      {
        protocol: 'tasklist-form',
        version: 1,
        type: 'submit',
        payload: { data: { approved: true, comment: null } },
      },
      '*',
    );
  });

  it('submits nothing while the form is read-only', () => {
    expect(host.submit({ approved: true })).toBe(false);

    deliver(tasklist, fromTasklist('init', { ...init, readOnly: true }));
    expect(host.submit({ approved: true })).toBe(false);

    expect(tasklist.postMessage).toHaveBeenCalledOnce();
  });

  it('passes the result of a submission on', () => {
    const result = { ok: false, message: 'Give a reason.', errors: [{ field: 'comment', message: 'Give a reason.' }] };

    deliver(tasklist, fromTasklist('result', result));

    expect(handlers.onResult).toHaveBeenCalledWith(result);
  });

  it('reports a new height only when it changed', () => {
    host.resize(311.2);
    host.resize(311.8);
    host.resize(400);

    expect(tasklist.postMessage.mock.calls.slice(1).map(([message]) => message as unknown)).toEqual([
      { protocol: 'tasklist-form', version: 1, type: 'resize', payload: { height: 312 } },
      { protocol: 'tasklist-form', version: 1, type: 'resize', payload: { height: 400 } },
    ]);
  });
});
