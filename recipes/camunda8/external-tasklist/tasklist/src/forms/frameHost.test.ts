import { envelope, FORM_PROTOCOL, type InitPayload, type ResultPayload } from '@tasklist/form-protocol';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { connectFrame, FRAME_HEIGHT, type FrameHostOptions } from './frameHost';

const INIT: InitPayload = { formRef: 'check-order@1', mode: 'task', data: { order: { id: 'ORD-1042' } }, readOnly: false, draftable: true };

let iframe: HTMLIFrameElement;
let frame: Window;
let posted: unknown[];
let disconnect: () => void;

beforeEach(() => {
  iframe = document.createElement('iframe');
  document.body.append(iframe);
  if (iframe.contentWindow === null) throw new Error('jsdom gave the iframe no window');
  frame = iframe.contentWindow;
  posted = [];
  vi.spyOn(frame, 'postMessage').mockImplementation((message: unknown) => {
    posted.push(message);
  });
});

afterEach(() => {
  disconnect();
  iframe.remove();
});

function connect(overrides: Partial<FrameHostOptions> = {}) {
  const options: FrameHostOptions = {
    frame: () => frame,
    init: () => INIT,
    submit: vi.fn(() => Promise.resolve<ResultPayload>({ ok: true, message: 'Task completed.' })),
    draft: vi.fn(() => Promise.resolve<ResultPayload>({ ok: true, message: 'Draft saved.' })),
    resize: vi.fn(),
    ...overrides,
  };
  disconnect = connectFrame(window, options);
  return options;
}

/** A message as the sandboxed frame sends it: from its window, with the opaque origin "null". */
function fromFrame(data: unknown, { source = frame, origin = 'null' }: { source?: Window | null; origin?: string } = {}) {
  window.dispatchEvent(new MessageEvent('message', { data, source, origin }));
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('the frame host', () => {
  test('answers every ready with exactly one init', () => {
    connect();

    fromFrame(envelope({ type: 'ready' }));
    expect(posted).toEqual([{ type: 'init', payload: INIT, protocol: FORM_PROTOCOL, version: 1 }]);

    fromFrame(envelope({ type: 'resize', payload: { height: 400 } }));
    expect(posted).toHaveLength(1);

    // The frame loaded again, for example after the task was canceled.
    fromFrame(envelope({ type: 'ready' }));
    expect(posted).toHaveLength(2);
  });

  test('ignores messages of other windows, other origins and other protocol versions', () => {
    const options = connect();
    const other = document.createElement('iframe');
    document.body.append(other);

    fromFrame(envelope({ type: 'ready' }), { source: other.contentWindow });
    fromFrame(envelope({ type: 'ready' }), { source: window });
    fromFrame(envelope({ type: 'ready' }), { origin: window.location.origin });
    fromFrame(envelope({ type: 'ready' }), { origin: 'https://attacker.example' });
    fromFrame({ type: 'ready', protocol: FORM_PROTOCOL, version: 2 });
    fromFrame({ type: 'ready', protocol: 'other-protocol', version: 1 });
    fromFrame({ type: 'ready' });
    fromFrame(envelope({ type: 'init', payload: INIT }));
    fromFrame('ready');

    expect(posted).toEqual([]);
    expect(options.resize).not.toHaveBeenCalled();
    other.remove();
  });

  test('completes the task on submit and answers with the result', async () => {
    const submit = vi.fn(() =>
      Promise.resolve<ResultPayload>({ ok: false, message: 'Give a reason.', errors: [{ field: 'comment', message: 'Give a reason.' }] }),
    );
    connect({ submit });

    fromFrame(envelope({ type: 'submit', payload: { data: { approved: false } } }));
    await settle();

    expect(submit).toHaveBeenCalledWith({ approved: false });
    expect(posted).toEqual([
      envelope({
        type: 'result',
        payload: { ok: false, message: 'Give a reason.', errors: [{ field: 'comment', message: 'Give a reason.' }] },
      }),
    ]);
  });

  test('answers with a failed result when the submission throws', async () => {
    connect({ submit: () => Promise.reject(new Error('offline')) });

    fromFrame(envelope({ type: 'submit', payload: { data: {} } }));
    await settle();

    expect(posted).toEqual([envelope({ type: 'result', payload: { ok: false, message: "The tasklist couldn't send the form. Try again." } })]);
  });

  test('ignores submit and draft while the form is read-only', async () => {
    const options = connect({ init: () => ({ ...INIT, readOnly: true }) });

    fromFrame(envelope({ type: 'submit', payload: { data: { approved: true } } }));
    fromFrame(envelope({ type: 'draft', payload: { data: { approved: true } } }));
    await settle();

    expect(options.submit).not.toHaveBeenCalled();
    expect(options.draft).not.toHaveBeenCalled();
    expect(posted).toEqual([]);
  });

  test('saves drafts only for a form that keeps them', async () => {
    const options = connect({ init: () => ({ ...INIT, draftable: false }) });
    fromFrame(envelope({ type: 'draft', payload: { data: { items: [] } } }));
    await settle();
    expect(options.draft).not.toHaveBeenCalled();

    disconnect();
    const draftable = connect();
    fromFrame(envelope({ type: 'draft', payload: { data: { items: [] } } }));
    await settle();
    expect(draftable.draft).toHaveBeenCalledWith({ items: [] });
    expect(posted).toEqual([envelope({ type: 'result', payload: { ok: true, message: 'Draft saved.' } })]);
  });

  test('handles one submission at a time and ignores data that is not an object', async () => {
    let finish: (result: ResultPayload) => void = () => undefined;
    const submit = vi.fn(() => new Promise<ResultPayload>((resolve) => (finish = resolve)));
    connect({ submit });

    fromFrame(envelope({ type: 'submit', payload: { data: { approved: true } } }));
    fromFrame(envelope({ type: 'submit', payload: { data: { approved: true } } }));
    expect(submit).toHaveBeenCalledTimes(1);

    finish({ ok: true });
    await settle();
    fromFrame({ type: 'submit', payload: { data: ['not', 'an', 'object'] }, protocol: FORM_PROTOCOL, version: 1 });
    fromFrame({ type: 'submit', payload: {}, protocol: FORM_PROTOCOL, version: 1 });
    await settle();
    expect(submit).toHaveBeenCalledTimes(1);
    expect(posted).toEqual([envelope({ type: 'result', payload: { ok: true } })]);
  });

  test('sizes the frame to the reported height within limits', () => {
    const options = connect();

    fromFrame(envelope({ type: 'resize', payload: { height: 512.4 } }));
    fromFrame(envelope({ type: 'resize', payload: { height: 3 } }));
    fromFrame(envelope({ type: 'resize', payload: { height: 1e9 } }));
    fromFrame({ type: 'resize', payload: { height: 'tall' }, protocol: FORM_PROTOCOL, version: 1 });

    expect(vi.mocked(options.resize).mock.calls).toEqual([[513], [FRAME_HEIGHT.min], [FRAME_HEIGHT.max]]);
  });

  test('stops listening when disconnected', () => {
    connect();
    disconnect();

    fromFrame(envelope({ type: 'ready' }));

    expect(posted).toEqual([]);
  });
});
