import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Icon } from './Icon';
import { TONE_ICONS, type Tone } from './tones';

interface StatusBoxProps {
  tone: Tone;
  title: ReactNode;
  children?: ReactNode;
  actions?: ReactNode;
  icon?: LucideIcon;
}

/** A lasting state, such as a canceled task or a failed request: tinted area, 4px bar and icon in the status role. */
export function StatusBox({ tone, title, children, actions, icon }: StatusBoxProps) {
  return (
    <div className={`status status--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <Icon of={icon ?? TONE_ICONS[tone]} size={18} className="status__icon" />
      <div className="status__body">
        <strong className="status__title">{title}</strong>
        {children !== undefined && <div className="status__text">{children}</div>}
        {actions !== undefined && <div className="status__actions">{actions}</div>}
      </div>
    </div>
  );
}
