import { History } from 'lucide-react';
import { useHistory, useUsers } from '../api/queries';
import type { DemoUser, HistoryEntry, Task } from '../api/types';
import { clockTime, listOf, nameOf } from '../format';
import { Icon } from '../ui/Icon';
import { classes } from '../ui/tones';
import { useNow } from '../useNow';

const ATTRIBUTES: Record<string, string> = {
  priority: 'the priority',
  dueDate: 'the due date',
  followUpDate: 'the follow-up date',
  candidateGroupsList: 'the candidate groups',
  candidateGroups: 'the candidate groups',
  candidateUsersList: 'the candidate users',
  candidateUsers: 'the candidate users',
};

interface Line {
  title: string;
  detail: string;
  danger: boolean;
}

/** One lifecycle event of the projection in words. */
function describe(entry: HistoryEntry, task: Task, users: DemoUser[] | undefined): Line {
  const by = (userId: string | null | undefined) => (userId ? `by ${nameOf(userId, users)}` : '');
  const byReconciler = 'noticed by the reconciler';
  switch (entry.eventType) {
    case 'CREATING':
      return { title: 'Created', detail: `by the ${task.processName ?? task.processDefinitionId} process`, danger: false };
    case 'ASSIGNING':
      return entry.assignee ? { title: 'Claimed', detail: by(entry.assignee), danger: false } : { title: 'Unclaimed', detail: '', danger: false };
    case 'UPDATING': {
      const changed = entry.changedAttributes.map((attribute) => ATTRIBUTES[attribute] ?? attribute);
      return { title: 'Updated', detail: changed.length > 0 ? `changed ${listOf(changed)}` : '', danger: false };
    }
    case 'COMPLETING':
      return { title: 'Completed', detail: by(entry.assignee), danger: false };
    case 'CANCELING':
      return { title: 'Canceled', detail: 'by its process', danger: true };
    case 'COMPLETED':
      return { title: 'Completed', detail: byReconciler, danger: false };
    case 'CANCELED':
      return { title: 'Canceled', detail: byReconciler, danger: true };
    case 'DISCOVERED':
      return { title: 'Found', detail: byReconciler, danger: false };
    default:
      return { title: entry.eventType.charAt(0) + entry.eventType.slice(1).toLowerCase(), detail: '', danger: false };
  }
}

export function TaskHistory({ task }: { task: Task }) {
  const history = useHistory(task.userTaskKey);
  const { data: users } = useUsers();
  const now = useNow();

  return (
    <section className="history" aria-labelledby="history-title">
      <h2 className="section-title" id="history-title">
        <Icon of={History} size={14} />
        History
      </h2>
      {history.data === undefined ? (
        <p className="muted">{history.isError ? `Couldn't load the history. ${history.error.message}` : 'Loading…'}</p>
      ) : history.data.length === 0 ? (
        <p className="muted">No events yet.</p>
      ) : (
        <ol className="timeline">
          {history.data.map((entry, index) => {
            const line = describe(entry, task, users);
            return (
              <li key={`${entry.occurredAt}-${String(index)}`} className={classes(line.danger && 'is-danger')}>
                <span className="timeline__dot" aria-hidden="true" />
                <div>
                  <strong>{line.title}</strong> {line.detail && <span className="muted">{line.detail}</span>}
                </div>
                <time dateTime={entry.occurredAt}>{clockTime(entry.occurredAt, now)}</time>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
