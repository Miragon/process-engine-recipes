// The small marks the list and the detail share: domain chip, state and priority badges, avatars.
import { Check, CircleX, Clock, Flag, User } from 'lucide-react';
import { useApplications, useUsers } from '../api/queries';
import type { TaskState } from '../api/types';
import { initials, nameOf, priorityLevel } from '../format';
import { Icon } from '../ui/Icon';

/** The owning application, with a dot whose color comes from the palette in the order of the registry. */
export function DomainChip({ domain, label }: { domain: string; label: string }) {
  const { data: applications } = useApplications();
  const index = applications?.findIndex((application) => application.domain === domain) ?? -1;
  const tone = index < 0 ? 'other' : String((index % 3) + 1);
  return (
    <span className="chip">
      <span className={`dot dot--${tone}`} aria-hidden="true" />
      {label}
    </span>
  );
}

const STATES: Record<TaskState, { label: string; tone: string; icon: typeof Clock }> = {
  OPEN: { label: 'Open', tone: 'info', icon: Clock },
  COMPLETED: { label: 'Completed', tone: 'success', icon: Check },
  CANCELED: { label: 'Canceled', tone: 'danger', icon: CircleX },
};

export function StateBadge({ state }: { state: TaskState }) {
  const { label, tone, icon } = STATES[state];
  return (
    <span className={`badge badge--${tone}`}>
      <Icon of={icon} size={12} />
      {label}
    </span>
  );
}

/** Only a high priority stands out; normal and low are plain text where the priority is shown at all. */
export function PriorityBadge({ priority }: { priority: number }) {
  if (priorityLevel(priority) !== 'high') return null;
  return (
    <span className="badge badge--warning" title={`Priority ${String(priority)}`}>
      <Icon of={Flag} size={12} />
      High
    </span>
  );
}

export function Avatar({ userId }: { userId: string | null | undefined }) {
  const { data: users } = useUsers();
  if (!userId) {
    return (
      <span className="avatar avatar--empty" title="Unassigned">
        <Icon of={User} size={12} />
        <span className="visually-hidden">Unassigned</span>
      </span>
    );
  }
  const name = nameOf(userId, users);
  return (
    <span className="avatar" title={name}>
      <span aria-hidden="true">{initials(name)}</span>
      <span className="visually-hidden">{name}</span>
    </span>
  );
}
