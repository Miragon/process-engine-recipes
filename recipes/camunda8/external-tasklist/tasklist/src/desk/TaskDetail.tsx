import { RotateCw } from 'lucide-react';
import { isProblem } from '../api/client';
import { useTask, useUsers } from '../api/queries';
import type { Task } from '../api/types';
import { atTime, nameOf } from '../format';
import { Button } from '../ui/Button';
import { StatusBox } from '../ui/StatusBox';
import { useNow } from '../useNow';
import { TaskFacts } from './TaskFacts';
import { TaskHistory } from './TaskHistory';
import { DomainChip, StateBadge } from './TaskMarks';
import { TaskWork } from './TaskWork';

/** The open task: header, its state, facts, the form with the action bar, and its history. */
export function TaskDetail({ taskKey }: { taskKey: string }) {
  const task = useTask(taskKey);
  const retry = (
    <Button size="sm" icon={RotateCw} onClick={() => void task.refetch()}>
      Retry
    </Button>
  );
  if (task.data === undefined) {
    if (!task.isError) return <DetailSkeleton />;
    return (
      <StatusBox tone="danger" title={isProblem(task.error, 404) ? "This task doesn't exist" : "Couldn't load the task"} actions={retry}>
        {task.error.message}
      </StatusBox>
    );
  }
  return (
    <>
      {/* A failed refresh keeps the task and what the user entered on screen. */}
      {task.isError && (
        <StatusBox tone="warning" title="Couldn't refresh the task" actions={retry}>
          {`${task.error.message} It shows its last known state.`}
        </StatusBox>
      )}
      <DetailHeader task={task.data} />
      <TaskStateBox task={task.data} />
      <TaskFacts task={task.data} />
      <TaskWork task={task.data} />
      <TaskHistory task={task.data} />
    </>
  );
}

function DetailHeader({ task }: { task: Task }) {
  const context = [task.processName, task.businessId].filter(Boolean).join(' · ');
  return (
    <div className="detail__head">
      <div>
        <p className="eyebrow">
          <DomainChip domain={task.domain} label={task.domainLabel} />
          {context && <span className="muted">{context}</span>}
        </p>
        <h1>{task.name}</h1>
        {task.subject && <p className="muted">{task.subject}</p>}
      </div>
      <StateBadge state={task.state} />
    </div>
  );
}

/** A closed task says so above everything else; the live update of a cancellation lands here at once. */
function TaskStateBox({ task }: { task: Task }) {
  const { data: users } = useUsers();
  const now = useNow();
  const when = task.closedAt ? ` ${atTime(task.closedAt, now)}` : '';

  if (task.state === 'CANCELED') {
    return (
      <StatusBox tone="danger" title="This task was canceled by its process">
        {task.closedBy === 'RECONCILER'
          ? 'Camunda no longer has this task, so the tasklist closed it. You can no longer work on it.'
          : `Its process ended the task${when}. You can no longer work on it.`}
      </StatusBox>
    );
  }
  if (task.state === 'COMPLETED') {
    return (
      <StatusBox tone="success" title="This task was completed">
        {task.assignee ? `${nameOf(task.assignee, users)} completed it${when}.` : `It was completed${when}.`}
      </StatusBox>
    );
  }
  return null;
}

function DetailSkeleton() {
  return (
    <div className="detail-skeleton" aria-busy="true" aria-label="Loading the task">
      <span className="skeleton skeleton--chip" />
      <span className="skeleton skeleton--heading" />
      <span className="skeleton skeleton--line" />
      <div className="card">
        <span className="skeleton skeleton--line" />
        <span className="skeleton skeleton--line skeleton--short" />
      </div>
    </div>
  );
}
