import { LoaderCircle, type LucideIcon } from 'lucide-react';
import type { ButtonHTMLAttributes, Ref } from 'react';
import { Icon } from './Icon';
import { classes } from './tones';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost';
  size?: 'md' | 'sm';
  icon?: LucideIcon;
  /** Shows a spinner and blocks the button while its action runs. */
  busy?: boolean;
  ref?: Ref<HTMLButtonElement>;
}

export function Button({ variant = 'secondary', size = 'md', icon, busy = false, type = 'button', className, disabled, children, ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={classes('btn', `btn--${variant}`, size === 'sm' && 'btn--sm', className)}
      disabled={disabled === true || busy}
      aria-busy={busy || undefined}
      {...props}
    >
      {busy ? <Icon of={LoaderCircle} className="spin" /> : icon && <Icon of={icon} />}
      {children}
    </button>
  );
}

interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  label: string;
  icon: LucideIcon;
  size?: number;
}

/** A button that shows only an icon; its label is the accessible name and the tooltip. */
export function IconButton({ label, icon, size = 16, className, type = 'button', ...props }: IconButtonProps) {
  return (
    <button type={type} className={classes('icon-btn', className)} aria-label={label} title={label} {...props}>
      <Icon of={icon} size={size} />
    </button>
  );
}
