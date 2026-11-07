// The tasklist's end of form protocol v1 (tasklist-contract/src/main/typescript/PROTOCOL.md): it answers the HTML
// form of a process application that runs in a sandboxed iframe without `allow-same-origin`.
import {
  envelope,
  readFrameMessage,
  type HostMessage,
  type InitPayload,
  type ResultPayload,
} from '@tasklist/form-protocol';
import { isProblem } from '../api/client';
import type { FormData } from '../api/types';

export interface FrameHostOptions {
  /** The iframe's window. Messages from any other window are ignored. */
  frame: () => Window | null | undefined;
  /** What `init` carries. Read on every `ready`, so a reloaded frame gets the task's current state. */
  init: () => InitPayload;
  /** Completes the task or starts the process. Never called while the form is read-only. */
  submit: (data: FormData) => Promise<ResultPayload>;
  /** Saves a draft. Never called while the form is read-only or keeps no drafts. */
  draft: (data: FormData) => Promise<ResultPayload>;
  /** The frame's content height in pixels, already limited to a sensible range. */
  resize: (height: number) => void;
  /** The frame received its `init`. */
  initialized?: () => void;
}

export const FRAME_HEIGHT = { min: 120, max: 8000 } as const;

const FAILED: ResultPayload = { ok: false, message: "The tasklist couldn't send the form. Try again." };
const READ_ONLY: ResultPayload = { ok: false, message: 'The form is read-only now; nothing was sent.' };
const NO_DRAFTS: ResultPayload = { ok: false, message: 'This form keeps no drafts.' };

/** The answer to the frame when sending failed: the message and, for a refusal (422), the field errors. */
export function refusal(error: unknown): ResultPayload {
  const problem = error instanceof Error ? error : new Error(String(error));
  return { ok: false, message: problem.message, errors: isProblem(problem) ? problem.errors : [] };
}

/**
 * Listens on the tasklist's window for messages of the frame. Following the protocol it sends `init` once per
 * `ready` and answers each `submit` and `draft` with one `result`: `ok: false` without sending anything while the
 * form is read-only (or keeps no drafts), so a frame that has not heard of it yet does not wait forever. While an
 * earlier one is still being handled, the `result` of that one answers the frame. Returns a function that stops
 * listening.
 */
export function connectFrame(host: Pick<Window, 'addEventListener' | 'removeEventListener'>, options: FrameHostOptions) {
  let busy = false;

  const post = (frame: Window, message: HostMessage) => {
    // The frame has the opaque origin "null", which cannot be named as a target; frame-ancestors guards the frame.
    frame.postMessage(envelope(message), '*');
  };

  const handOver = (frame: Window, send: () => Promise<ResultPayload>) => {
    busy = true;
    send()
      .catch(() => FAILED)
      .then((result) => {
        busy = false;
        post(frame, { type: 'result', payload: result });
      })
      .catch(() => {
        busy = false;
      });
  };

  const receive = (event: MessageEvent) => {
    const frame = options.frame();
    const message = readFrameMessage(event, frame);
    if (message === null || !frame) return;

    switch (message.type) {
      case 'ready':
        post(frame, { type: 'init', payload: options.init() });
        options.initialized?.();
        break;
      case 'resize': {
        const height = Number((message.payload as { height?: unknown } | undefined)?.height);
        if (Number.isFinite(height)) options.resize(Math.min(FRAME_HEIGHT.max, Math.max(FRAME_HEIGHT.min, Math.ceil(height))));
        break;
      }
      case 'submit':
      case 'draft': {
        const data = dataOf(message.payload);
        if (data === null || busy) return;
        const { readOnly, draftable } = options.init();
        if (readOnly) post(frame, { type: 'result', payload: READ_ONLY });
        else if (message.type === 'draft' && !draftable) post(frame, { type: 'result', payload: NO_DRAFTS });
        else handOver(frame, () => (message.type === 'submit' ? options.submit(data) : options.draft(data)));
        break;
      }
    }
  };

  host.addEventListener('message', receive);
  return () => {
    host.removeEventListener('message', receive);
  };
}

function dataOf(payload: unknown): FormData | null {
  const data = (payload as { data?: unknown } | undefined)?.data;
  return typeof data === 'object' && data !== null && !Array.isArray(data) ? (data as FormData) : null;
}
