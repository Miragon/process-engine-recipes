import { CircleAlert } from 'lucide-react';
import { useId, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { Icon } from './Icon';

interface FieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  hint?: string;
  error?: string;
  disabled?: boolean;
}

type InputProps = FieldProps & Pick<InputHTMLAttributes<HTMLInputElement>, 'type' | 'autoComplete' | 'maxLength'>;
type TextAreaProps = FieldProps & Pick<TextareaHTMLAttributes<HTMLTextAreaElement>, 'autoComplete' | 'maxLength' | 'rows'>;

export function TextField({ label, value, onChange, hint, error, disabled, ...input }: InputProps) {
  const id = useId();
  return (
    <FieldFrame id={id} label={label} hint={hint} error={error}>
      <input
        id={id}
        className="input"
        value={value}
        disabled={disabled}
        aria-invalid={error !== undefined}
        aria-describedby={describedBy(id, hint, error)}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        {...input}
      />
    </FieldFrame>
  );
}

export function TextAreaField({ label, value, onChange, hint, error, disabled, ...textarea }: TextAreaProps) {
  const id = useId();
  return (
    <FieldFrame id={id} label={label} hint={hint} error={error}>
      <textarea
        id={id}
        className="input"
        value={value}
        disabled={disabled}
        aria-invalid={error !== undefined}
        aria-describedby={describedBy(id, hint, error)}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        {...textarea}
      />
    </FieldFrame>
  );
}

interface FieldFrameProps {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}

function FieldFrame({ id, label, hint, error, children }: FieldFrameProps) {
  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
      </label>
      {children}
      {hint !== undefined && (
        <p className="field__hint" id={`${id}-hint`}>
          {hint}
        </p>
      )}
      <FieldError id={`${id}-error`} message={error} />
    </div>
  );
}

/** An error below a field or a group of fields, announced to screen readers. */
export function FieldError({ id, message }: { id?: string; message: string | undefined }) {
  if (message === undefined) return null;
  return (
    <p className="field__error" id={id} role="alert">
      <Icon of={CircleAlert} />
      {message}
    </p>
  );
}

function describedBy(id: string, hint: string | undefined, error: string | undefined): string | undefined {
  const ids = [hint === undefined ? null : `${id}-hint`, error === undefined ? null : `${id}-error`].filter(Boolean);
  return ids.length > 0 ? ids.join(' ') : undefined;
}
