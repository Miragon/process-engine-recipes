import type { ResultPayload } from '@tasklist/form-protocol';

/** What every form gets: its data from the order application and the state of its last submission. */
export interface FormProps<D> {
  data: D;
  readOnly: boolean;
  pending: boolean;
  result: ResultPayload | null;
}
