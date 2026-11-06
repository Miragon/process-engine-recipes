import { ArrowUpDown, Calendar, RotateCw, Users } from 'lucide-react';
import { useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useStartForms, useTaskPage } from '../api/queries';
import type { Task } from '../api/types';
import { dueDay, relativeTime } from '../format';
import { useNow } from '../useNow';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { Icon } from '../ui/Icon';
import { Menu } from '../ui/Menu';
import { StatusBox } from '../ui/StatusBox';
import { classes } from '../ui/tones';
import {
  clearFilters,
  EMPTY_LOCATION,
  formatLocation,
  isFiltered,
  toTaskQuery,
  type DeskLocation,
  type Sort,
  type StateFilter,
} from './location';
import { Avatar, DomainChip, PriorityBadge } from './TaskMarks';
import type { Navigate } from './useDeskLocation';

const PAGE = 50;

const TITLES: Record<StateFilter, string> = { open: 'Open tasks', completed: 'Completed tasks', canceled: 'Canceled tasks' };

const SORTS: { value: Sort; label: string }[] = [
  { value: 'newest', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
  { value: 'priority', label: 'Highest priority first' },
  { value: 'due', label: 'Due soonest first' },
];

export function TaskList({ location, navigate }: { location: DeskLocation; navigate: Navigate }) {
  const { filters } = location;
  // Another filter starts again with the first page; opening a task keeps what is shown.
  const filterKey = formatLocation({ ...EMPTY_LOCATION, filters });
  const [limit, setLimit] = useState(PAGE);
  const [limitFor, setLimitFor] = useState(filterKey);
  if (limitFor !== filterKey) {
    setLimitFor(filterKey);
    setLimit(PAGE);
  }
  const query = useMemo(() => ({ ...toTaskQuery(filters), limit }), [filters, limit]);
  const page = useTaskPage(query);
  const { data: startForms = [] } = useStartForms();

  const open = (task: Task) => {
    navigate({ ...location, task: task.userTaskKey, start: null });
  };
  const sortLabel = SORTS.find((sort) => sort.value === filters.sort)?.label ?? 'Newest first';
  const [startForm] = startForms;
  const startAction =
    startForm === undefined
      ? null
      : {
          label: startForm.label,
          run: () => {
            navigate({ ...location, task: null, start: { domain: startForm.domain, id: startForm.id } });
          },
        };

  return (
    <section className="list" aria-labelledby="list-title">
      <div className="list__head">
        <h2 id="list-title">
          {TITLES[filters.state]} {page.data && <span className="muted">{page.data.total}</span>}
        </h2>
        <Menu
          label="Sort"
          choice
          items={SORTS.map((sort) => ({
            key: sort.value,
            label: sort.label,
            checked: sort.value === filters.sort,
            onSelect: () => {
              navigate({ ...location, filters: { ...filters, sort: sort.value } });
            },
          }))}
          trigger={(props) => (
            <button type="button" className="btn btn--ghost btn--sm" aria-label={`Sort: ${sortLabel}`} {...props}>
              <Icon of={ArrowUpDown} size={14} />
              {sortLabel}
            </button>
          )}
        />
      </div>

      {page.isPending ? (
        <ListSkeleton />
      ) : page.isError ? (
        <div className="list__state">
          <StatusBox
            tone="danger"
            title="Couldn't load the tasks"
            actions={
              <Button size="sm" icon={RotateCw} onClick={() => void page.refetch()}>
                Retry
              </Button>
            }
          >
            {page.error.message}
          </StatusBox>
        </div>
      ) : page.data.items.length === 0 ? (
        <div className="list__state">
          {isFiltered(filters) ? (
            <EmptyState
              title="No tasks match these filters"
              actions={[
                <Button
                  key="clear"
                  variant="primary"
                  onClick={() => {
                    navigate({ ...location, filters: clearFilters(filters) });
                  }}
                >
                  Clear filters
                </Button>,
                startAction ? (
                  <Button key="start" variant="ghost" onClick={startAction.run}>
                    {startAction.label}
                  </Button>
                ) : (
                  <Button key="reload" variant="ghost" onClick={() => void page.refetch()}>
                    Reload
                  </Button>
                ),
              ]}
            >
              Clear the filters to see every open task.
            </EmptyState>
          ) : (
            <EmptyState
              title="No open tasks"
              actions={[
                startAction ? (
                  <Button key="start" variant="primary" onClick={startAction.run}>
                    {startAction.label}
                  </Button>
                ) : (
                  <Button key="reload" variant="primary" onClick={() => void page.refetch()}>
                    Reload
                  </Button>
                ),
                <Button
                  key="completed"
                  variant="ghost"
                  onClick={() => {
                    navigate({ ...location, filters: { ...filters, state: 'completed' } });
                  }}
                >
                  Show completed tasks
                </Button>,
              ]}
            >
              New tasks show up here as soon as a process creates them.
            </EmptyState>
          )}
        </div>
      ) : (
        <>
          <TaskRows tasks={page.data.items} selected={location.task} onOpen={open} />
          {page.data.items.length < page.data.total && (
            <div className="list__more">
              <Button
                size="sm"
                variant="ghost"
                busy={page.isFetching}
                onClick={() => {
                  setLimit(limit + PAGE);
                }}
              >
                Show more
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  );
}

/** The rows as a listbox: Up and Down move, Home and End jump, Enter or Space opens the task. */
function TaskRows({ tasks, selected, onOpen }: { tasks: Task[]; selected: string | null; onOpen: (task: Task) => void }) {
  const now = useNow();
  const rows = useRef<Map<string, HTMLLIElement>>(new Map());
  const [active, setActive] = useState<string | null>(null);
  const focusable =
    [active, selected].find((key) => key !== null && tasks.some((task) => task.userTaskKey === key)) ?? tasks[0]?.userTaskKey;

  const move = (event: KeyboardEvent<HTMLUListElement>) => {
    const index = tasks.findIndex((task) => task.userTaskKey === focusable);
    const targets: Record<string, number> = { ArrowDown: index + 1, ArrowUp: index - 1, Home: 0, End: tasks.length - 1 };
    const target = targets[event.key];
    if (target !== undefined) {
      event.preventDefault();
      const task = tasks[Math.min(tasks.length - 1, Math.max(0, target))];
      if (task === undefined) return;
      setActive(task.userTaskKey);
      rows.current.get(task.userTaskKey)?.focus();
    } else if (event.key === 'Enter' || event.key === ' ') {
      const task = tasks[index];
      if (task === undefined) return;
      event.preventDefault();
      onOpen(task);
    }
  };

  return (
    <ul className="rows" role="listbox" aria-label="Tasks" onKeyDown={move}>
      {tasks.map((task) => (
        <li
          key={task.userTaskKey}
          ref={(element) => {
            if (element) rows.current.set(task.userTaskKey, element);
            else rows.current.delete(task.userTaskKey);
          }}
          role="option"
          aria-selected={task.userTaskKey === selected}
          aria-label={[task.name, task.subject].filter(Boolean).join(', ')}
          tabIndex={task.userTaskKey === focusable ? 0 : -1}
          className={classes('row', task.userTaskKey === selected && 'is-selected')}
          onFocus={() => {
            setActive(task.userTaskKey);
          }}
          onClick={() => {
            setActive(task.userTaskKey);
            onOpen(task);
          }}
        >
          <div className="row__main">
            <div className="row__title">
              <strong>{task.name}</strong>
              <PriorityBadge priority={task.priority} />
            </div>
            <div className="row__subject">{task.subject ?? task.businessId ?? task.processName ?? task.processDefinitionId}</div>
            <div className="row__meta">
              <DomainChip domain={task.domain} label={task.domainLabel} />
              {task.candidateGroups.length > 0 && (
                <span className="row__fact">
                  <Icon of={Users} size={12} />
                  {task.candidateGroups.join(', ')}
                </span>
              )}
              {task.dueDate && (
                <span className="row__fact">
                  <Icon of={Calendar} size={12} />
                  {dueDay(task.dueDate, now)}
                </span>
              )}
            </div>
          </div>
          <div className="row__side">
            <Avatar userId={task.assignee} />
            <time dateTime={task.closedAt ?? task.createdAt}>{whenOf(task, now)}</time>
          </div>
        </li>
      ))}
    </ul>
  );
}

/** When an open task was created, or when a closed one was completed or canceled. */
function whenOf(task: Task, now: Date): string {
  if (task.state === 'OPEN' || !task.closedAt) return relativeTime(task.createdAt, now);
  const when = relativeTime(task.closedAt, now);
  return `${task.state === 'COMPLETED' ? 'Completed' : 'Canceled'} ${when === 'Just now' ? 'just now' : when}`;
}

function ListSkeleton() {
  return (
    <ul className="rows" aria-busy="true" aria-label="Loading tasks">
      {[0, 1, 2, 3, 4, 5].map((index) => (
        <li key={index} className="row row--skeleton" aria-hidden="true">
          <div className="row__main">
            <span className="skeleton skeleton--title" />
            <span className="skeleton skeleton--line" />
            <span className="skeleton skeleton--meta" />
          </div>
          <div className="row__side">
            <span className="skeleton skeleton--avatar" />
          </div>
        </li>
      ))}
    </ul>
  );
}
