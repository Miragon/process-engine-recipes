import type { ReactNode } from 'react';

/** The official Miragon icon, the green comet on blue, used unchanged as app icon, favicon and empty-state mark. */
export const COMET = '/miragon-komet-blau.svg';

interface EmptyStateProps {
  title: string;
  children: ReactNode;
  /** Exactly two: the primary action first, then a ghost button. */
  actions: [ReactNode, ReactNode];
}

/** The Miragon empty state: a floating card with the comet, a title, one line of help and two actions. */
export function EmptyState({ title, children, actions }: EmptyStateProps) {
  return (
    <div className="empty-card">
      <img src={COMET} alt="" width={64} height={64} />
      <h3 className="empty-card__title">{title}</h3>
      <p className="empty-card__help">{children}</p>
      <div className="empty-card__actions">{actions}</div>
    </div>
  );
}
