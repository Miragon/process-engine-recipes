import { ChevronDown, Plus, Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useApplications, useStartForms, useUsers } from '../api/queries';
import type { StartFormEntry } from '../api/types';
import { capitalize } from '../format';
import { chooseUser, useSession } from '../session';
import { Button } from '../ui/Button';
import { COMET } from '../ui/EmptyState';
import { Icon } from '../ui/Icon';
import { Menu } from '../ui/Menu';
import { EMPTY_LOCATION, type DeskLocation } from './location';
import { Avatar } from './TaskMarks';
import type { Navigate } from './useDeskLocation';

interface TopBarProps {
  location: DeskLocation;
  navigate: Navigate;
}

export function TopBar({ location, navigate }: TopBarProps) {
  const { userId } = useSession();
  return (
    <header className="topbar">
      <a
        className="brand"
        href="/"
        onClick={(event) => {
          event.preventDefault();
          navigate(EMPTY_LOCATION);
        }}
      >
        <img src={COMET} alt="" width={28} height={28} />
        <span>Central tasklist</span>
      </a>
      <SearchField
        value={location.filters.q}
        disabled={userId === null}
        onSearch={(q) => {
          navigate({ ...location, filters: { ...location.filters, q } }, { replace: true });
        }}
      />
      <div className="topbar__actions">
        {userId !== null && (
          <StartAction
            onStart={(entry) => {
              navigate({ ...location, task: null, start: { domain: entry.domain, id: entry.id } });
            }}
          />
        )}
        <UserSwitcher />
      </div>
    </header>
  );
}

/** Searches as you type, after a short pause, and follows the query string when it changes elsewhere. */
function SearchField({ value, disabled, onSearch }: { value: string; disabled: boolean; onSearch: (q: string) => void }) {
  const { data: applications } = useApplications();
  const [text, setText] = useState(value);
  const [synced, setSynced] = useState(value);
  if (value !== synced) {
    setSynced(value);
    // The query string keeps the search trimmed; a space the user just typed stays in the field.
    if (text.trim() !== value) setText(value);
  }

  useEffect(() => {
    if (text.trim() === value.trim()) return;
    const timer = window.setTimeout(() => {
      onSearch(text);
    }, 250);
    return () => {
      window.clearTimeout(timer);
    };
  }, [text, value, onSearch]);

  const domains = applications?.map((application) => application.label.toLowerCase()) ?? [];
  return (
    <label className="search">
      <Icon of={Search} />
      <span className="visually-hidden">Search tasks</span>
      <input
        type="search"
        value={text}
        disabled={disabled}
        placeholder={['Search tasks', ...domains].join(', ')}
        onChange={(event) => {
          setText(event.target.value);
        }}
        onKeyDown={(event) => {
          if (event.key === 'Enter') onSearch(text);
        }}
      />
    </label>
  );
}

/** The start forms of all applications: one button for one form, a menu for several. */
function StartAction({ onStart }: { onStart: (entry: StartFormEntry) => void }) {
  const { data: forms = [] } = useStartForms();
  const [first] = forms;
  if (first === undefined) return null;
  if (forms.length === 1) {
    return (
      <Button
        variant="primary"
        icon={Plus}
        title={first.description ?? undefined}
        onClick={() => {
          onStart(first);
        }}
      >
        {first.label}
      </Button>
    );
  }
  return (
    <Menu
      label="Start"
      items={forms.map((entry) => ({
        key: `${entry.domain}/${entry.id}`,
        label: entry.label,
        description: entry.domainLabel,
        onSelect: () => {
          onStart(entry);
        },
      }))}
      trigger={(props) => (
        <button type="button" className="btn btn--primary" {...props}>
          <Icon of={Plus} />
          Start
          <Icon of={ChevronDown} size={14} />
        </button>
      )}
    />
  );
}

/** The demo users of the task manager; the choice is remembered in the browser. */
function UserSwitcher() {
  const { userId } = useSession();
  const { data: users = [] } = useUsers();
  const user = users.find((candidate) => candidate.id === userId);
  const name = user?.name ?? userId ?? 'Choose a user';
  const groups = user ? user.groups.map(capitalize).join(', ') : 'Demo users';
  return (
    <Menu
      label="Demo users"
      choice
      items={users.map((candidate) => ({
        key: candidate.id,
        label: candidate.name,
        description: candidate.groups.map(capitalize).join(', '),
        checked: candidate.id === userId,
        onSelect: () => {
          chooseUser(candidate.id);
        },
      }))}
      trigger={(props) => (
        <button type="button" className="user-switcher" aria-label={`Switch demo user, now ${name}`} {...props}>
          <Avatar userId={userId} />
          <span className="user-switcher__text">
            <strong>{name}</strong>
            <small>{groups}</small>
          </span>
          <Icon of={ChevronDown} size={14} />
        </button>
      )}
    />
  );
}
