import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Icon } from '../ui/Icon';
import { classes } from '../ui/tones';

interface ActionBarProps {
  /** Actions on the task itself, such as Unclaim. */
  start?: ReactNode;
  /** A short state next to the main actions: "1 field needs attention", "Read-only: the task was canceled". */
  hint?: { text: string; icon: LucideIcon; tone?: 'danger' };
  /** The form's actions, the primary one last. */
  end?: ReactNode;
  /** Why the last action failed, shown right above the bar where the user clicked. */
  notice?: ReactNode;
}

/** Sticks to the bottom of the detail pane while the form is longer than the screen. */
export function ActionBar({ start, hint, end, notice }: ActionBarProps) {
  return (
    <div className="actionbar-dock">
      {notice}
      <footer className="actionbar">
        {start}
        <span className="actionbar__spacer" />
        {hint && (
          <span className={classes('actionbar__hint', hint.tone && `actionbar__hint--${hint.tone}`)}>
            <Icon of={hint.icon} size={14} />
            {hint.text}
          </span>
        )}
        {end}
      </footer>
    </div>
  );
}
