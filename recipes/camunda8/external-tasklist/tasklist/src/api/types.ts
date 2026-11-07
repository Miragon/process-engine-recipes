// Names for the types of the task manager API (generated into taskmanager.gen.ts from docs/api/taskmanager.json).
import type { FieldError } from '@tasklist/form-protocol';
import type { components, operations } from './taskmanager.gen';

type Schemas = components['schemas'];

export type Task = Schemas['TaskDto'];
export type TaskState = Task['state'];
export type TaskAction = Task['actions'][number];
export type TaskPage = Schemas['TaskPage'];
export type TaskUpdate = Schemas['TaskUpdate'];
export type Facet = Schemas['Facet'];
export type Facets = Schemas['Facets'];
export type HistoryEntry = Schemas['HistoryEntry'];
export type DemoUser = Schemas['DemoUserView'];
export type Application = Schemas['ApplicationDto'];
export type StartFormEntry = Schemas['StartFormEntry'];
export type StartResult = Schemas['StartResult'];
export type FormData = Record<string, unknown>;
export type { FieldError };

/** Query of `GET /api/tasks`; the user travels as `X-Demo-User`. */
export type TaskQuery = NonNullable<operations['list']['parameters']['query']>;
export type StateQuery = NonNullable<TaskQuery['state']>;

/** A form as `GET /api/tasks/{key}/form` answers it: `type` is `json-forms` or `html`, as in the contract's `FormView`. */
export type JsonFormsView = Schemas['JsonFormsView'];
export type HtmlFormView = Schemas['HtmlFormView'];
export type FormView = JsonFormsView | HtmlFormView;

/** Data of the server-sent event `task-changed` of `GET /api/events`, which the API document does not describe. */
export interface TaskChanged {
  userTaskKey: string;
  state: TaskState;
  domain: string;
}

/** Outcome of `POST /api/tasks/{key}/completion`: 204 completed, 202 accepted while Camunda's listeners still run. */
export type Completion = 'completed' | 'accepted';
