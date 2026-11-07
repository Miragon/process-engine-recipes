import { CircleAlert, CircleCheck, Info, TriangleAlert } from 'lucide-react';
import type { ReactNode } from 'react';
import { Icon } from './Icon';

export type Tone = 'info' | 'success' | 'warning' | 'danger';

const ICONS = { info: Info, success: CircleCheck, warning: TriangleAlert, danger: CircleAlert };

/** A status box: border, left bar and icon in the status role, the text in the normal text color. */
export function Notice({ tone, title, children }: { tone: Tone; title?: string; children?: ReactNode }) {
  return (
    <div className={`notice notice--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <span className="notice__icon">
        <Icon of={ICONS[tone]} />
      </span>
      <div className="notice__body">
        {title !== undefined && <p className="notice__title">{title}</p>}
        {children}
      </div>
    </div>
  );
}
