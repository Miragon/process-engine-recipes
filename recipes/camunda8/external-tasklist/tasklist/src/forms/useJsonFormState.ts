import { useMemo, useState } from 'react';
import type { ApiProblem } from '../api/client';
import type { FieldError, FormData } from '../api/types';
import { fieldCount, valueAt, visibleErrors, withoutNulls, type FormError } from './jsonFormsErrors';

const same = (left: unknown, right: unknown) => JSON.stringify(left) === JSON.stringify(right);

/**
 * What the user entered into a JSON form, and why the application refused it. A field error disappears as soon as
 * the user changes that field; the refusal itself stays until the user changes anything.
 */
export function useJsonFormState(initial: FormData) {
  const [data, setData] = useState(() => withoutNulls(initial));
  const [schemaErrors, setSchemaErrors] = useState<FormError[]>([]);
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([]);
  const [rejection, setRejection] = useState<ApiProblem | null>(null);

  const errors = useMemo(
    () => (rejection === null && fieldErrors.length === 0 ? [] : visibleErrors(fieldErrors, rejection === null ? [] : schemaErrors)),
    [fieldErrors, rejection, schemaErrors],
  );

  const change = (next: FormData, nextSchemaErrors: FormError[]) => {
    setSchemaErrors(nextSchemaErrors);
    if (next === data) return;
    setFieldErrors((current) => current.filter((error) => same(valueAt(next, error.field), valueAt(data, error.field))));
    setRejection(null);
    setData(next);
  };

  const reject = (problem: ApiProblem) => {
    setRejection(problem);
    setFieldErrors(problem.errors);
  };

  const accept = () => {
    setRejection(null);
    setFieldErrors([]);
  };

  return { data, errors, attention: fieldCount(errors), rejection, change, reject, accept };
}
