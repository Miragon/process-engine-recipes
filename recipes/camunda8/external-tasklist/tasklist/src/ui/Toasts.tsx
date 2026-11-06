import { X } from 'lucide-react';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { IconButton } from './Button';
import { Icon } from './Icon';
import { ToastContext, type ToastMessage } from './toast';
import { TONE_ICONS } from './tones';

interface Toast extends ToastMessage {
  id: number;
}

const MAX_TOASTS = 3;
let nextId = 1;

/** Toasts of the web app: at most three, the newest at the bottom; errors stay longer and are announced at once. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const show = useCallback((toast: ToastMessage) => {
    setToasts((current) => [...current, { ...toast, id: nextId++ }].slice(-MAX_TOASTS));
  }, []);
  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  return (
    <ToastContext value={show}>
      {children}
      <div className="toasts">
        {toasts.map((toast) => (
          <ToastCard key={toast.id} toast={toast} onDismiss={dismiss} />
        ))}
      </div>
    </ToastContext>
  );
}

function ToastCard({ toast, onDismiss }: { toast: Toast; onDismiss: (id: number) => void }) {
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused) return;
    const timer = window.setTimeout(
      () => {
        onDismiss(toast.id);
      },
      toast.tone === 'danger' ? 5000 : 2800,
    );
    return () => {
      window.clearTimeout(timer);
    };
  }, [paused, toast, onDismiss]);

  const pause = () => {
    setPaused(true);
  };
  const resume = () => {
    setPaused(false);
  };

  return (
    <div
      className={`toast toast--${toast.tone}`}
      role={toast.tone === 'danger' ? 'alert' : 'status'}
      aria-live={toast.tone === 'danger' ? 'assertive' : 'polite'}
      onMouseEnter={pause}
      onMouseLeave={resume}
      onFocus={pause}
      onBlur={resume}
    >
      <Icon of={TONE_ICONS[toast.tone]} className="toast__icon" />
      <span className="toast__message">{toast.message}</span>
      <IconButton
        label="Close"
        icon={X}
        onClick={() => {
          onDismiss(toast.id);
        }}
      />
    </div>
  );
}
