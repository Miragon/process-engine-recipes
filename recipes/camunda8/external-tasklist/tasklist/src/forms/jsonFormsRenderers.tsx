// The renderers that take the place of vanilla ones where the mockup or accessibility needs more: labelled controls
// with linked errors, read-only values as facts, yes or no as a segmented choice, and a table for object arrays that
// honours `disableAdd` and `disableRemove`. jsonFormsSetup.ts registers them on top of the vanilla set.
import {
  createDefaultValue,
  encode,
  Paths,
  Resolve,
  type ArrayControlProps,
  type ControlElement,
  type ControlProps,
  type JsonSchema,
} from '@jsonforms/core';
import { DispatchCell } from '@jsonforms/react';
import { CircleAlert, Plus, Trash2 } from 'lucide-react';
import { useLayoutEffect, useRef } from 'react';
import { Button, IconButton } from '../ui/Button';
import { Icon } from '../ui/Icon';
import { classes } from '../ui/tones';
import { isReadOnlySchema } from './readOnly';

/** The UI schema of one cell in a row of the items table. */
function cellOf(key: string): ControlElement {
  return { type: 'Control', scope: `#/properties/${encode(key)}`, label: false };
}

/** A read-only value as text: "Yes" and "No" for booleans, nothing filled in as "Not set". */
function ReadOnlyValue({ value }: { value: unknown }) {
  if (value === undefined || value === null || value === '') return <span className="jf-fact__empty">Not set</span>;
  if (typeof value === 'boolean') return <>{value ? 'Yes' : 'No'}</>;
  if (typeof value === 'object') return <>{JSON.stringify(value)}</>;
  if (typeof value === 'number') return <>{String(value)}</>;
  return <>{typeof value === 'string' ? value : JSON.stringify(value)}</>;
}

function FieldMessage({ id, errors, description }: { id: string; errors: string; description: string | undefined }) {
  if (errors !== '') {
    return (
      <p className="jf-message jf-message--error" id={`${id}-message`}>
        <Icon of={CircleAlert} size={14} />
        <span>{errors}</span>
      </p>
    );
  }
  if (description === undefined || description === '') return null;
  return (
    <p className="jf-message jf-message--description" id={`${id}-message`}>
      {description}
    </p>
  );
}

/**
 * A labelled control around a vanilla cell. The vanilla cells take no ARIA attributes, so the control links the
 * input to its message once it is rendered.
 */
export function InputControl({ id, label, errors, description, visible, enabled, uischema, schema, path }: ControlProps) {
  const wrapper = useRef<HTMLDivElement>(null);
  const invalid = errors !== '';
  const hasMessage = invalid || (description !== undefined && description !== '');

  useLayoutEffect(() => {
    const input = wrapper.current?.querySelector('input, textarea, select');
    if (!input) return;
    if (invalid) input.setAttribute('aria-invalid', 'true');
    else input.removeAttribute('aria-invalid');
    if (hasMessage) input.setAttribute('aria-describedby', `${id}-message`);
    else input.removeAttribute('aria-describedby');
  });

  return (
    <div ref={wrapper} className={classes('jf-control', invalid && 'has-error')} hidden={!visible} id={id}>
      <label htmlFor={`${id}-input`} className="jf-label">
        {label}
      </label>
      <DispatchCell uischema={uischema} schema={schema} path={path} id={`${id}-input`} enabled={enabled} />
      <FieldMessage id={id} errors={errors} description={description} />
    </div>
  );
}

/** A field the user cannot change, shown as a fact like the facts of the task. */
export function ReadOnlyControl({ id, label, data, visible, errors }: ControlProps) {
  const value: unknown = data;
  return (
    <div className={classes('jf-control', 'jf-fact', errors !== '' && 'has-error')} hidden={!visible} id={id}>
      <span className="jf-fact__label">{label}</span>
      <span className="jf-fact__value">
        <ReadOnlyValue value={value} />
      </span>
      <FieldMessage id={id} errors={errors} description={undefined} />
    </div>
  );
}

/** Yes or no as a segmented pair of radio buttons; nothing is chosen until the user answers. */
export function BooleanChoiceControl(props: ControlProps) {
  const { id, label, visible, enabled, errors, description, path } = props;
  const data: unknown = props.data;
  const invalid = errors !== '';
  const choices = [
    { value: true, label: 'Yes' },
    { value: false, label: 'No' },
  ];
  return (
    <fieldset
      className={classes('jf-control', 'jf-choice', invalid && 'has-error')}
      hidden={!visible}
      id={id}
      aria-describedby={invalid || description ? `${id}-message` : undefined}
    >
      <legend className="jf-label">{label}</legend>
      <div className={classes('segmented', !enabled && 'is-disabled')}>
        {choices.map((choice) => (
          <label key={choice.label} className={classes('segmented__option', data === choice.value && 'is-on')}>
            <input
              type="radio"
              className="jf-radio"
              name={`${id}-choice`}
              checked={data === choice.value}
              disabled={!enabled}
              aria-invalid={invalid || undefined}
              onChange={() => {
                props.handleChange(path, choice.value);
              }}
            />
            {choice.label}
          </label>
        ))}
      </div>
      <FieldMessage id={id} errors={errors} description={description} />
    </fieldset>
  );
}

/** An array of objects as a table: read-only columns as text, the others as vanilla cells. */
export function ItemsTableControl(props: ArrayControlProps) {
  const { id, label, path, schema, rootSchema, uischema, errors, visible, enabled } = props;
  const data: unknown = props.data;
  const options = (uischema.options ?? {}) as { disableAdd?: boolean; disableRemove?: boolean };
  const properties: Record<string, JsonSchema> = schema.properties ?? {};
  const columns = Object.entries(properties).filter(([, column]) => column.type !== 'array' && column.type !== 'object');
  const rows: unknown[] = Array.isArray(data) ? data : [];
  const canAdd = enabled && options.disableAdd !== true;
  const removeItems = props.removeItems?.bind(props);
  const canRemove = enabled && options.disableRemove !== true && removeItems !== undefined;
  const invalid = errors !== '';
  const title = (key: string, column: JsonSchema) => column.title ?? key;

  return (
    <div className={classes('jf-control', 'jf-table', invalid && 'has-error')} hidden={!visible} id={id}>
      <div className="jf-table__head">
        <span className="jf-label" id={`${id}-label`}>
          {label}
        </span>
        {canAdd && (
          <Button size="sm" icon={Plus} onClick={props.addItem(path, createDefaultValue(schema, rootSchema))}>
            Add
          </Button>
        )}
      </div>
      <table className="items-table" aria-labelledby={`${id}-label`} aria-describedby={invalid ? `${id}-message` : undefined}>
        <thead>
          <tr>
            {columns.map(([key, column]) => (
              <th key={key} className={column.type === 'number' || column.type === 'integer' ? 'num' : undefined}>
                {title(key, column)}
              </th>
            ))}
            {canRemove && (
              <th>
                <span className="visually-hidden">Remove</span>
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td className="jf-table__empty" colSpan={columns.length + (canRemove ? 1 : 0)}>
                No items
              </td>
            </tr>
          ) : (
            rows.map((row, index) => {
              const values = (typeof row === 'object' && row !== null ? row : {}) as Record<string, unknown>;
              const rowPath = Paths.compose(path, String(index));
              const firstText = columns.map(([key]) => values[key]).find((value) => typeof value === 'string');
              const rowName = typeof firstText === 'string' ? firstText : `Item ${String(index + 1)}`;
              return (
                <tr key={rowPath}>
                  {columns.map(([key, column]) => (
                    <td key={key} className={column.type === 'number' || column.type === 'integer' ? 'num' : undefined}>
                      {isReadOnlySchema(column) ? (
                        <ReadOnlyValue value={values[key]} />
                      ) : (
                        <label className="jf-cell">
                          <span className="visually-hidden">{`${title(key, column)}: ${rowName}`}</span>
                          <DispatchCell
                            schema={Resolve.schema(schema, `#/properties/${encode(key)}`, rootSchema)}
                            uischema={cellOf(key)}
                            path={Paths.compose(rowPath, key)}
                            id={`${id}-${String(index)}-${key}`}
                            enabled={enabled}
                          />
                        </label>
                      )}
                    </td>
                  ))}
                  {canRemove && (
                    <td className="num">
                      <IconButton label={`Remove ${rowName}`} icon={Trash2} onClick={removeItems(path, [index])} />
                    </td>
                  )}
                </tr>
              );
            })
          )}
        </tbody>
      </table>
      <FieldMessage id={id} errors={errors} description={undefined} />
    </div>
  );
}
