// Form protocol v1 between the tasklist (host) and the HTML form of a process application (frame).
// Both sides import this file; PROTOCOL.md next to it describes the exchange.

export const FORM_PROTOCOL = 'tasklist-form' as const;
export const FORM_PROTOCOL_VERSION = 1 as const;

export type FormMode = 'task' | 'start';

export interface FieldError {
  field: string;
  message: string;
}

export interface InitPayload {
  formRef: string;
  mode: FormMode;
  data: Record<string, unknown>;
  readOnly: boolean;
  draftable: boolean;
}

export interface ResultPayload {
  ok: boolean;
  message?: string;
  errors?: FieldError[];
}

export type HostMessage =
  | { type: 'init'; payload: InitPayload }
  | { type: 'result'; payload: ResultPayload };

export type FrameMessage =
  | { type: 'ready' }
  | { type: 'submit'; payload: { data: Record<string, unknown> } }
  | { type: 'draft'; payload: { data: Record<string, unknown> } }
  | { type: 'resize'; payload: { height: number } };

export type Envelope<M> = M & {
  protocol: typeof FORM_PROTOCOL;
  version: typeof FORM_PROTOCOL_VERSION;
};

const HOST_TYPES: readonly string[] = ['init', 'result'];
const FRAME_TYPES: readonly string[] = ['ready', 'submit', 'draft', 'resize'];

export function envelope<M extends HostMessage | FrameMessage>(message: M): Envelope<M> {
  return { ...message, protocol: FORM_PROTOCOL, version: FORM_PROTOCOL_VERSION };
}

/**
 * Host side. Accepts a message only from the given frame. The frame is sandboxed without
 * `allow-same-origin`, so its origin is the opaque origin `"null"`.
 */
export function readFrameMessage(event: MessageEvent, frame: Window | null | undefined): FrameMessage | null {
  if (!frame || event.source !== frame || event.origin !== 'null') return null;
  return parse(event.data, FRAME_TYPES) as FrameMessage | null;
}

/** Frame side. Accepts a message only from the window that embeds the frame. */
export function readHostMessage(event: MessageEvent, parent: Window): HostMessage | null {
  if (event.source !== parent) return null;
  return parse(event.data, HOST_TYPES) as HostMessage | null;
}

function parse(data: unknown, types: readonly string[]): Record<string, unknown> | null {
  if (typeof data !== 'object' || data === null) return null;
  const message = data as Record<string, unknown>;
  if (message.protocol !== FORM_PROTOCOL || message.version !== FORM_PROTOCOL_VERSION) return null;
  if (typeof message.type !== 'string' || !types.includes(message.type)) return null;
  return message;
}
