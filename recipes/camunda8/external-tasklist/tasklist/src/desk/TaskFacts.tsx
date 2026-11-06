import { Check, Pencil, X } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useUsers } from '../api/queries';
import type { Task } from '../api/types';
import { dateTime, dueDateTime, nameOf, PRIORITY_PRESETS, priorityLabel, priorityLevel, toLocalInput } from '../format';
import { useSession } from '../session';
import { IconButton } from '../ui/Button';
import { useNow } from '../useNow';
import { Avatar, PriorityBadge } from './TaskMarks';
import { useTaskCommands } from './useTaskCommands';

/** The key facts of a task. Candidates change the priority and the due date in place. */
export function TaskFacts({ task }: { task: Task }) {
  const { userId } = useSession();
  const { data: users } = useUsers();
  const now = useNow();
  const editable = task.actions.includes('UPDATE');

  return (
    <dl className="facts facts--card">
      <Fact label="Candidate group">{task.candidateGroups.length > 0 ? task.candidateGroups.join(', ') : <span className="muted">None</span>}</Fact>
      <Fact label="Assignee">
        {task.assignee ? (
          <>
            <Avatar userId={task.assignee} />
            {nameOf(task.assignee, users)}
            {task.assignee === userId && ' (you)'}
          </>
        ) : (
          <span className="muted">Unassigned</span>
        )}
      </Fact>
      <Fact label="Priority">
        <PriorityFact task={task} editable={editable} />
      </Fact>
      <Fact label="Due">
        <DueFact task={task} editable={editable} now={now} />
      </Fact>
      <Fact label="Created">{dateTime(task.createdAt, now)}</Fact>
      {task.closedAt && task.state !== 'OPEN' && (
        <Fact label={task.state === 'COMPLETED' ? 'Completed' : 'Canceled'}>{dateTime(task.closedAt, now)}</Fact>
      )}
    </dl>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="fact">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function PriorityFact({ task, editable }: { task: Task; editable: boolean }) {
  const { update } = useTaskCommands(task);
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(task.priority);
  const options = PRIORITY_PRESETS.some((preset) => preset.value === task.priority)
    ? PRIORITY_PRESETS
    : [...PRIORITY_PRESETS, { level: priorityLevel(task.priority), label: `Custom (${String(task.priority)})`, value: task.priority }];

  if (!editing) {
    return (
      <>
        {priorityLevel(task.priority) === 'high' ? <PriorityBadge priority={task.priority} /> : priorityLabel(task.priority)}
        {editable && (
          <IconButton
            className="fact__edit"
            label="Change priority"
            icon={Pencil}
            size={14}
            onClick={() => {
              setValue(task.priority);
              setEditing(true);
            }}
          />
        )}
      </>
    );
  }
  const save = () => {
    if (value !== task.priority) update.mutate({ priority: value });
    setEditing(false);
  };
  return (
    <span className="fact__editor">
      <select
        className="input input--compact"
        aria-label="Priority"
        value={value}
        autoFocus
        onChange={(event) => {
          setValue(Number(event.target.value));
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') setEditing(false);
          if (event.key === 'Enter') save();
        }}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <IconButton label="Save priority" icon={Check} size={14} onClick={save} />
      <IconButton
        label="Cancel"
        icon={X}
        size={14}
        onClick={() => {
          setEditing(false);
        }}
      />
    </span>
  );
}

function DueFact({ task, editable, now }: { task: Task; editable: boolean; now: Date }) {
  const { update } = useTaskCommands(task);
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');

  if (!editing) {
    return (
      <>
        {task.dueDate ? dueDateTime(task.dueDate, now) : <span className="muted">Not set</span>}
        {editable && (
          <IconButton
            className="fact__edit"
            label="Change due date"
            icon={Pencil}
            size={14}
            onClick={() => {
              setValue(toLocalInput(task.dueDate));
              setEditing(true);
            }}
          />
        )}
      </>
    );
  }
  const date = new Date(value);
  const valid = value !== '' && !Number.isNaN(date.getTime());
  const save = () => {
    if (!valid) return;
    update.mutate({ dueDate: date.toISOString() });
    setEditing(false);
  };
  return (
    <span className="fact__editor">
      <input
        type="datetime-local"
        className="input input--compact"
        aria-label="Due date"
        value={value}
        autoFocus
        onChange={(event) => {
          setValue(event.target.value);
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') setEditing(false);
          if (event.key === 'Enter') save();
        }}
      />
      <IconButton label="Save due date" icon={Check} size={14} disabled={!valid} onClick={save} />
      <IconButton
        label="Cancel"
        icon={X}
        size={14}
        onClick={() => {
          setEditing(false);
        }}
      />
    </span>
  );
}
