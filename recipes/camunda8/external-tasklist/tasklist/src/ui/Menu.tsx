import { Check } from 'lucide-react';
import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode, type RefObject } from 'react';
import { Icon } from './Icon';

export interface MenuItem {
  key: string;
  label: ReactNode;
  description?: ReactNode;
  /** The current choice of a choice menu; it shows a check. */
  checked?: boolean;
  onSelect: () => void;
}

export interface MenuTriggerProps {
  ref: RefObject<HTMLButtonElement | null>;
  'aria-haspopup': 'menu';
  'aria-expanded': boolean;
  'aria-controls': string | undefined;
  onClick: () => void;
  onKeyDown: (event: KeyboardEvent<HTMLButtonElement>) => void;
}

interface MenuProps {
  /** Accessible name of the menu. */
  label: string;
  items: MenuItem[];
  trigger: (props: MenuTriggerProps) => ReactNode;
  /** Edge of the trigger the menu lines up with. */
  align?: 'start' | 'end';
  /** The items are choices (`menuitemradio`) instead of commands. */
  choice?: boolean;
}

const ITEMS = '[role="menuitem"], [role="menuitemradio"]';

/** A dropdown menu: arrow keys, Home and End move, Escape and a click outside close it, focus returns to the trigger. */
export function Menu({ label, items, trigger, align = 'end', choice = false }: MenuProps) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const anchor = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const initial = Math.max(0, items.findIndex((item) => item.checked === true));

  const focusItem = (index: number) => {
    const elements = menuRef.current?.querySelectorAll<HTMLElement>(ITEMS);
    if (!elements || elements.length === 0) return;
    elements[(index + elements.length) % elements.length]?.focus();
  };

  useEffect(() => {
    if (!open) return;
    menuRef.current?.querySelectorAll<HTMLElement>(ITEMS)[initial]?.focus();
    const closeOutside = (event: PointerEvent) => {
      if (!anchor.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', closeOutside);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
    };
  }, [open, initial]);

  const close = () => {
    setOpen(false);
    triggerRef.current?.focus();
  };

  const onMenuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const elements = Array.from(menuRef.current?.querySelectorAll<HTMLElement>(ITEMS) ?? []);
    const current = elements.findIndex((element) => element === document.activeElement);
    const moves: Record<string, number> = { ArrowDown: current + 1, ArrowUp: current - 1, Home: 0, End: elements.length - 1 };
    const target = moves[event.key];
    if (target !== undefined) {
      event.preventDefault();
      focusItem(target);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      close();
    } else if (event.key === 'Tab') {
      setOpen(false);
    }
  };

  return (
    <div className="menu-anchor" ref={anchor}>
      {trigger({
        ref: triggerRef,
        'aria-haspopup': 'menu',
        'aria-expanded': open,
        'aria-controls': open ? id : undefined,
        onClick: () => {
          setOpen((wasOpen) => !wasOpen);
        },
        onKeyDown: (event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            setOpen(true);
          }
        },
      })}
      {open && (
        <div className={`menu menu--${align}`} role="menu" id={id} aria-label={label} ref={menuRef} onKeyDown={onMenuKeyDown}>
          {items.map((item) => (
            <button
              key={item.key}
              type="button"
              role={choice ? 'menuitemradio' : 'menuitem'}
              aria-checked={choice ? item.checked === true : undefined}
              tabIndex={-1}
              className="menu__item"
              onClick={() => {
                item.onSelect();
                close();
              }}
            >
              <span className="menu__text">
                <span className="menu__label">{item.label}</span>
                {item.description !== undefined && <span className="menu__description">{item.description}</span>}
              </span>
              {choice && item.checked === true && <Icon of={Check} />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
