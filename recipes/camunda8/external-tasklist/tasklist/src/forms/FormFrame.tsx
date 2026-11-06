import type { InitPayload, ResultPayload } from '@tasklist/form-protocol';
import { Lock, RotateCw } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { frameUrl } from '../api/client';
import type { FormData } from '../api/types';
import { Button } from '../ui/Button';
import { Icon } from '../ui/Icon';
import { StatusBox } from '../ui/StatusBox';
import { connectFrame } from './frameHost';

/** How long the frame may take to say `ready` before the tasklist offers to load it again. */
const READY_TIMEOUT = 15_000;

interface FormFrameProps {
  domain: string;
  domainLabel: string;
  init: InitPayload;
  /** Accessible name of the frame. */
  title: string;
  onSubmit: (data: FormData) => Promise<ResultPayload>;
  onDraft: (data: FormData) => Promise<ResultPayload>;
}

/**
 * The HTML form of a process application in an iframe with `sandbox="allow-scripts"` only: an opaque origin that
 * cannot reach the tasklist's DOM, storage or API. Everything it shows arrives in `init`; it answers with `submit`.
 * The host re-creates the frame (React `key`) when the form turns read-only, so a new `ready` brings a new `init`.
 */
export function FormFrame({ domain, domainLabel, init, title, onSubmit, onDraft }: FormFrameProps) {
  const frame = useRef<HTMLIFrameElement>(null);
  const [height, setHeight] = useState<number | null>(null);
  const [initialized, setInitialized] = useState(false);
  const [stalled, setStalled] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const latest = useRef({ init, onSubmit, onDraft });

  useLayoutEffect(() => {
    latest.current = { init, onSubmit, onDraft };
  });

  useEffect(
    () =>
      connectFrame(window, {
        frame: () => frame.current?.contentWindow,
        init: () => latest.current.init,
        submit: (data) => latest.current.onSubmit(data),
        draft: (data) => latest.current.onDraft(data),
        resize: setHeight,
        initialized: () => {
          setInitialized(true);
          setStalled(false);
        },
      }),
    [],
  );

  // An application that is down answers the frame with an error page that never says `ready`.
  useEffect(() => {
    if (initialized) return;
    const timer = window.setTimeout(() => {
      setStalled(true);
    }, READY_TIMEOUT);
    return () => {
      window.clearTimeout(timer);
    };
  }, [initialized, attempt]);

  return (
    <div className="frame">
      <div className="frame__label">
        <Icon of={Lock} size={12} />
        <span>
          Form provided by <strong>{domainLabel}</strong> · {init.formRef}
        </span>
      </div>
      <div className="frame__body">
        {!initialized && (
          <div className="frame__loading">
            {stalled ? (
              <StatusBox
                tone="danger"
                title="The form did not load"
                actions={
                  <Button
                    size="sm"
                    icon={RotateCw}
                    onClick={() => {
                      setStalled(false);
                      setAttempt(attempt + 1);
                    }}
                  >
                    Retry
                  </Button>
                }
              >
                {`The ${domainLabel} application did not deliver its form. It may be down; try again in a moment.`}
              </StatusBox>
            ) : (
              <span role="status">Loading the form…</span>
            )}
          </div>
        )}
        <iframe
          key={attempt}
          ref={frame}
          className="frame__view"
          src={frameUrl(domain, init.formRef)}
          sandbox="allow-scripts"
          referrerPolicy="no-referrer"
          title={title}
          style={height === null ? undefined : { height }}
        />
      </div>
    </div>
  );
}
