import { Inbox } from 'lucide-react';
import { useCallback } from 'react';
import { useLiveUpdates } from '../api/events';
import { useUsers } from '../api/queries';
import { capitalize } from '../format';
import { chooseUser, useSession } from '../session';
import { COMET } from '../ui/EmptyState';
import { Icon } from '../ui/Icon';
import { StatusBox } from '../ui/StatusBox';
import { FilterRail } from './FilterRail';
import { StartFormDetail } from './StartFormDetail';
import { TaskDetail } from './TaskDetail';
import { TaskList } from './TaskList';
import { Avatar } from './TaskMarks';
import { TopBar } from './TopBar';
import { useDeskLocation } from './useDeskLocation';

/** The Split desk: filter rail, task list and the open task side by side, below the top bar. */
export function Desk() {
  const { userId, notice } = useSession();
  const [location, navigate] = useDeskLocation();
  useLiveUpdates();

  const openTask = useCallback(
    (userTaskKey: string) => {
      navigate({ ...location, task: userTaskKey, start: null }, { replace: true });
    },
    [location, navigate],
  );

  return (
    <div className="app">
      <TopBar location={location} navigate={navigate} />
      {userId === null ? (
        <ChooseUser notice={notice} />
      ) : (
        <main className="panes">
          <FilterRail
            filters={location.filters}
            onChange={(filters) => {
              navigate({ ...location, filters });
            }}
          />
          <TaskList location={location} navigate={navigate} />
          <section className="detail" aria-label={location.start ? 'Start form' : 'Task'}>
            {location.start ? (
              <StartFormDetail
                key={`${userId}:${location.start.domain}/${location.start.id}`}
                start={location.start}
                onClose={() => {
                  navigate({ ...location, start: null });
                }}
                onOpenTask={openTask}
              />
            ) : location.task ? (
              <TaskDetail key={`${userId}:${location.task}`} taskKey={location.task} />
            ) : (
              <NothingOpen />
            )}
          </section>
        </main>
      )}
    </div>
  );
}

function NothingOpen() {
  return (
    <div className="nothing-open">
      <Icon of={Inbox} size={22} />
      <div>
        <h1 className="nothing-open__title">No task open</h1>
        <p className="muted">Pick a task from the list to see its details and its form.</p>
      </div>
    </div>
  );
}

/** The recipe has no sign-in: before anything else the user picks one of the task manager's demo users. */
function ChooseUser({ notice }: { notice: string | null }) {
  const users = useUsers();
  return (
    <main className="choose-user">
      <div className="empty-card empty-card--wide">
        <img src={COMET} alt="" width={64} height={64} />
        <h1 className="empty-card__title">Choose a demo user</h1>
        <p className="empty-card__help">The recipe has no sign-in. The tasklist sends the user you choose with every request.</p>
        {notice && <StatusBox tone="danger" title="The task manager did not accept the user">{notice}</StatusBox>}
        {users.isError ? (
          <StatusBox tone="danger" title="Couldn't load the demo users">
            {users.error.message}
          </StatusBox>
        ) : (
          <ul className="user-choices" aria-label="Demo users">
            {(users.data ?? []).map((user) => (
              <li key={user.id}>
                <button
                  type="button"
                  className="user-choice"
                  onClick={() => {
                    chooseUser(user.id);
                  }}
                >
                  <Avatar userId={user.id} />
                  <span>
                    <strong>{user.name}</strong>
                    <small>{user.groups.map(capitalize).join(', ')}</small>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
