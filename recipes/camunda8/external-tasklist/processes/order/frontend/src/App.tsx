import type { InitPayload, ResultPayload } from '@tasklist/form-protocol';
import { useEffect, useRef } from 'react';
import { CheckOrderForm } from './forms/CheckOrderForm';
import { parseCheckOrderData, parseStartOrderData, type CheckOrderData, type StartOrderData } from './forms/order';
import { StartOrderForm } from './forms/StartOrderForm';
import { reportHeight, useTasklist } from './tasklist';
import { Notice } from './ui/Notice';

/** One bundle serves all HTML forms of the order application and picks the form by its reference. */
export function App() {
  const { form, pending, result } = useTasklist();
  const frame = useRef<HTMLElement>(null);

  useEffect(() => (frame.current === null ? undefined : reportHeight(frame.current)), []);

  return (
    <main className="frame" ref={frame}>
      {form === null ? <p className="muted">Loading the form…</p> : <Form form={form} pending={pending} result={result} />}
    </main>
  );
}

function Form({ form, pending, result }: { form: InitPayload; pending: boolean; result: ResultPayload | null }) {
  const view = viewOf(form);
  const state = { readOnly: form.readOnly, pending, result };
  switch (view.formRef) {
    case 'start-order@1':
      return <StartOrderForm data={view.data} {...state} />;
    case 'check-order@1':
      return <CheckOrderForm data={view.data} {...state} />;
    case null:
      return <Notice tone="danger" title={view.problem} />;
  }
}

type View =
  | { formRef: 'start-order@1'; data: StartOrderData }
  | { formRef: 'check-order@1'; data: CheckOrderData }
  | { formRef: null; problem: string };

function viewOf(form: InitPayload): View {
  try {
    switch (form.formRef) {
      case 'start-order@1':
        return { formRef: form.formRef, data: parseStartOrderData(form.data) };
      case 'check-order@1':
        return { formRef: form.formRef, data: parseCheckOrderData(form.data) };
      default:
        return { formRef: null, problem: `The order application has no form ${form.formRef}.` };
    }
  } catch (error) {
    return { formRef: null, problem: error instanceof Error ? error.message : 'The form cannot be shown.' };
  }
}
