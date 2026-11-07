// The frame's end of form protocol v1, described in tasklist-contract/src/main/typescript/PROTOCOL.md.
import {
  envelope,
  readHostMessage,
  type FrameMessage,
  type InitPayload,
  type ResultPayload,
} from '@tasklist/form-protocol';

export interface HostHandlers {
  onInit: (payload: InitPayload) => void;
  onResult: (payload: ResultPayload) => void;
}

/** What the form can tell the tasklist. */
export interface TasklistHost {
  /** Asks the tasklist to complete the task or start the process. Refused while the form is read-only. */
  submit: (data: Record<string, unknown>) => boolean;
  /** Tells the tasklist how tall the form is, so it can size the frame. */
  resize: (height: number) => void;
  disconnect: () => void;
}

export type FrameWindow = Pick<Window, 'parent' | 'addEventListener' | 'removeEventListener'>;

/**
 * Listens to the tasklist, the window that embeds the frame, and tells it that the form is ready for its data.
 * Messages from any other window or of another protocol version are ignored.
 */
export function connectToTasklist(frame: FrameWindow, handlers: HostHandlers): TasklistHost {
  const tasklist = frame.parent;
  // Read-only until the tasklist says otherwise, so nothing is submitted before the form knows its task.
  let readOnly = true;
  let reportedHeight = -1;

  const post = (message: FrameMessage) => {
    tasklist.postMessage(envelope(message), '*');
  };

  const receive = (event: MessageEvent) => {
    const message = readHostMessage(event, tasklist);
    if (message?.type === 'init') {
      readOnly = message.payload.readOnly;
      handlers.onInit(message.payload);
    } else if (message?.type === 'result') {
      handlers.onResult(message.payload);
    }
  };

  frame.addEventListener('message', receive);
  post({ type: 'ready' });

  return {
    submit(data) {
      if (readOnly) return false;
      post({ type: 'submit', payload: { data } });
      return true;
    },
    resize(height) {
      const rounded = Math.ceil(height);
      if (rounded === reportedHeight) return;
      reportedHeight = rounded;
      post({ type: 'resize', payload: { height: rounded } });
    },
    disconnect() {
      frame.removeEventListener('message', receive);
    },
  };
}

/** Reports the element's height to the tasklist whenever it changes. */
export function observeHeight(element: Element, host: TasklistHost): () => void {
  const observer = new ResizeObserver(() => {
    host.resize(element.getBoundingClientRect().height);
  });
  observer.observe(element);
  return () => {
    observer.disconnect();
  };
}
