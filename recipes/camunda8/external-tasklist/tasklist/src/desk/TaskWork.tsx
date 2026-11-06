// The working area of a task: the claim card or the form, and the action bar below it.
import type { InitPayload, ResultPayload } from '@tasklist/form-protocol';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, CircleAlert, Inbox, LoaderCircle, Lock, RotateCw } from 'lucide-react';
import { useEffect, useRef, useState, type Ref } from 'react';
import { isProblem } from '../api/client';
import { api, refreshTask, useTaskForm, useUsers } from '../api/queries';
import type { Completion, FormData, HtmlFormView, JsonFormsView, Task } from '../api/types';
import { count, nameOf } from '../format';
import { FormFrame } from '../forms/FormFrame';
import { JsonForm } from '../forms/LazyJsonForm';
import { useJsonFormState } from '../forms/useJsonFormState';
import { useSession } from '../session';
import { Button } from '../ui/Button';
import { Icon } from '../ui/Icon';
import { StatusBox } from '../ui/StatusBox';
import { useToast } from '../ui/toast';
import { ActionBar } from './ActionBar';
import { completionSent, completionSettled, useCompletionSent } from './completions';
import { useTaskCommands } from './useTaskCommands';

/** Claim and unclaim from the work area; afterwards the focus moves to what takes the place of the button. */
interface Handover {
  claim: () => void;
  unclaim: () => void;
  claiming: boolean;
  unclaiming: boolean;
}

export function TaskWork({ task }: { task: Task }) {
  const { userId } = useSession();
  const [preview, setPreview] = useState(false);
  const commands = useTaskCommands(task);
  const sent = useCompletionSent(task.userTaskKey);
  const open = task.state === 'OPEN';
  const mine = task.assignee !== null && task.assignee !== undefined && task.assignee === userId;
  // Once the task is the user's, "Show form" has done its job: handing the task back brings the claim card back.
  if (mine && preview) setPreview(false);
  const showForm = !open || mine || preview;
  const form = useTaskForm(task, showForm);
  const formShown = showForm && form.data !== undefined;

  const work = useRef<HTMLDivElement>(null);
  const card = useRef<HTMLDivElement>(null);
  const focusNext = useRef<'form' | 'card' | null>(null);
  useEffect(() => {
    if (focusNext.current === 'form' && formShown) {
      focusNext.current = null;
      work.current?.focus();
    } else if (focusNext.current === 'card' && !showForm) {
      focusNext.current = null;
      card.current?.focus();
    }
  }, [formShown, showForm]);

  useEffect(() => {
    if (!open) completionSettled(task.userTaskKey);
  }, [open, task.userTaskKey]);

  const handover: Handover = {
    claim: () => {
      commands.claim.mutate(undefined, {
        onSuccess: () => {
          focusNext.current = 'form';
        },
      });
    },
    unclaim: () => {
      commands.unclaim.mutate(undefined, {
        onSuccess: () => {
          focusNext.current = 'card';
        },
      });
    },
    claiming: commands.claim.isPending,
    unclaiming: commands.unclaim.isPending,
  };

  if (!showForm) {
    return (
      <ClaimCard
        ref={card}
        task={task}
        handover={handover}
        onPreview={() => {
          setPreview(true);
        }}
      />
    );
  }
  if (form.isPending) return <FormPending failure={form.failureReason} />;
  if (form.isError) {
    return (
      <StatusBox
        tone="danger"
        title="Couldn't load the form"
        actions={
          <Button size="sm" icon={RotateCw} onClick={() => void form.refetch()}>
            Retry
          </Button>
        }
      >
        {form.error.message}
      </StatusBox>
    );
  }
  const readOnly = form.data.readOnly || !open || !mine || sent;
  return (
    <div className="work" ref={work} tabIndex={-1} role="group" aria-label={`Form of ${task.name}`}>
      {form.data.type === 'json-forms' ? (
        <JsonTaskForm key={form.data.formRef} task={task} form={form.data} readOnly={readOnly} handover={handover} />
      ) : (
        <HtmlTaskForm task={task} form={form.data} readOnly={readOnly} handover={handover} />
      )}
    </div>
  );
}

interface ClaimCardProps {
  task: Task;
  handover: Handover;
  onPreview: () => void;
  ref: Ref<HTMLDivElement>;
}

/** Shown instead of the form while the task is not the user's: claim it, or look at the form first. */
function ClaimCard({ task, handover, onPreview, ref }: ClaimCardProps) {
  const { data: users } = useUsers();
  const canClaim = task.actions.includes('CLAIM');
  const [title, help] = canClaim
    ? ['Claim the task to work on its form.', 'Only the assignee can complete it.']
    : task.assignee
      ? [`${nameOf(task.assignee, users)} works on this task.`, 'Only the assignee can complete it.']
      : [`Only members of ${task.candidateGroups.join(', ') || 'its candidate groups'} can work on this task.`, 'You can still look at its form.'];
  return (
    <div className="claim-card" ref={ref} tabIndex={-1}>
      <Icon of={Inbox} size={22} />
      <p>
        <strong>{title}</strong>
        <br />
        <span className="muted">{help}</span>
      </p>
      <div className="claim-card__actions">
        {canClaim && (
          <Button variant="primary" busy={handover.claiming} onClick={handover.claim}>
            Claim task
          </Button>
        )}
        <Button variant="ghost" onClick={onPreview}>
          Show form
        </Button>
      </div>
    </div>
  );
}

/** Loading, or waiting while the task manager still synchronizes the task with Camunda (503 with Retry-After). */
function FormPending({ failure }: { failure: Error | null }) {
  if (isProblem(failure, 503)) {
    return (
      <StatusBox tone="info" title="The form is not ready yet">
        {`${failure.code === 'SYNCING' ? 'The task manager is still synchronizing the task with Camunda.' : failure.detail} ` +
          `The tasklist asks again in ${String(failure.retryAfter ?? 2)} s.`}
      </StatusBox>
    );
  }
  return (
    <div className="card form-skeleton" aria-busy="true" aria-label="Loading the form">
      <span className="skeleton skeleton--title" />
      <span className="skeleton skeleton--field" />
      <span className="skeleton skeleton--field" />
      <span className="skeleton skeleton--field skeleton--short" />
    </div>
  );
}

/** Unclaim for the assignee, Claim while looking at the form of a task the user may claim. */
function TaskButtons({ task, handover }: { task: Task; handover: Handover }) {
  const { userId } = useSession();
  if (task.actions.includes('UNCLAIM')) {
    return (
      <Button busy={handover.unclaiming} onClick={handover.unclaim}>
        Unclaim
      </Button>
    );
  }
  if (task.actions.includes('CLAIM')) {
    return (
      <Button variant="primary" busy={handover.claiming} onClick={handover.claim}>
        Claim task
      </Button>
    );
  }
  if (task.state !== 'OPEN' && task.assignee === userId) return <Button disabled>Unclaim</Button>;
  return null;
}

const COMPLETING = { text: 'Camunda completes the task', icon: LoaderCircle };

function readOnlyHint(task: Task) {
  if (task.state === 'CANCELED') return { text: 'Read-only: the task was canceled', icon: Lock };
  if (task.state === 'COMPLETED') return { text: 'Read-only: the task was completed', icon: Lock };
  return { text: 'Read-only: claim the task to change it', icon: Lock };
}

/**
 * Completion and draft of the open task. Toasts and reloads hang on the mutations themselves, so they also happen
 * when the task-changed event of the completion arrives first and the form is already gone.
 */
function useCompletion(task: Task) {
  const client = useQueryClient();
  const toast = useToast();
  const refused = (error: Error) => {
    // A task that was completed or canceled meanwhile shows its new state.
    if (isProblem(error, 409) || isProblem(error, 403)) refreshTask(client, task);
  };
  const complete = useMutation({
    mutationFn: (data: FormData) => api.complete(task.userTaskKey, data),
    onSuccess: (outcome: Completion) => {
      // Until the task has closed nothing may be sent again, after a 202 also while Camunda's listeners still run.
      completionSent(task.userTaskKey);
      refreshTask(client, task);
      toast(
        outcome === 'completed'
          ? { tone: 'success', message: `${task.name} completed.` }
          : { tone: 'info', message: 'Camunda accepted the completion. The task closes once its listeners have finished.' },
      );
    },
    onError: refused,
  });
  const draft = useMutation({
    mutationFn: (data: FormData) => api.saveDraft(task.userTaskKey, data),
    onSuccess: () => {
      toast({ tone: 'success', message: 'Draft saved.' });
    },
    onError: refused,
  });
  return { complete, draft };
}

function JsonTaskForm({ task, form, readOnly, handover }: { task: Task; form: JsonFormsView; readOnly: boolean; handover: Handover }) {
  const state = useJsonFormState(form.data);
  const { complete, draft } = useCompletion(task);
  const [failed, setFailed] = useState<'complete' | 'draft'>('complete');
  const sent = useCompletionSent(task.userTaskKey);
  const sending = complete.isPending || draft.isPending;
  const editable = !readOnly && task.actions.includes('COMPLETE');

  const submit = () => {
    complete.mutate(state.data, {
      onSuccess: () => {
        state.accept();
      },
      onError: (error) => {
        setFailed('complete');
        if (isProblem(error)) state.reject(error);
      },
    });
  };
  const saveDraft = () => {
    draft.mutate(state.data, {
      onSuccess: () => {
        state.accept();
      },
      onError: (error) => {
        setFailed('draft');
        if (isProblem(error)) state.reject(error);
      },
    });
  };

  const hint =
    state.attention > 0
      ? { text: `${count(state.attention, 'field')} ${state.attention === 1 ? 'needs' : 'need'} attention`, icon: CircleAlert, tone: 'danger' as const }
      : sent
        ? COMPLETING
        : readOnly
          ? readOnlyHint(task)
          : undefined;
  const closed = task.state !== 'OPEN';

  return (
    <>
      <div className="card form-card">
        <JsonForm form={form} data={state.data} readOnly={readOnly || sending} errors={state.errors} onChange={state.change} />
      </div>
      <ActionBar
        notice={
          state.rejection && (
            <StatusBox tone="danger" title={failed === 'draft' ? "Couldn't save the draft" : "Couldn't complete the task"}>
              {state.rejection.detail}
            </StatusBox>
          )
        }
        start={<TaskButtons task={task} handover={handover} />}
        hint={hint}
        end={
          editable ? (
            <>
              {form.draftable && (
                <Button busy={draft.isPending} disabled={complete.isPending} onClick={saveDraft}>
                  Save draft
                </Button>
              )}
              <Button variant="primary" icon={Check} busy={complete.isPending} disabled={draft.isPending} onClick={submit}>
                Complete task
              </Button>
            </>
          ) : closed || sent ? (
            <Button variant="primary" icon={Check} disabled>
              Complete task
            </Button>
          ) : undefined
        }
      />
    </>
  );
}

function HtmlTaskForm({ task, form, readOnly, handover }: { task: Task; form: HtmlFormView; readOnly: boolean; handover: Handover }) {
  const { complete, draft } = useCompletion(task);
  const sent = useCompletionSent(task.userTaskKey);
  const init: InitPayload = { formRef: form.formRef, mode: 'task', data: form.data, readOnly, draftable: form.draftable && !readOnly };

  const answer = async (send: () => Promise<string>): Promise<ResultPayload> => {
    try {
      return { ok: true, message: await send() };
    } catch (error) {
      const problem = error instanceof Error ? error : new Error(String(error));
      return { ok: false, message: problem.message, errors: isProblem(problem) ? problem.errors : [] };
    }
  };
  const onSubmit = (data: FormData) =>
    answer(async () => ((await complete.mutateAsync(data)) === 'completed' ? 'Task completed.' : 'Camunda accepted the completion.'));
  const onDraft = (data: FormData) =>
    answer(async () => {
      await draft.mutateAsync(data);
      return 'Draft saved.';
    });

  const startButtons = <TaskButtons task={task} handover={handover} />;
  return (
    <>
      <FormFrame
        // A new frame for a form that turned read-only, so its next `init` says so.
        key={readOnly ? 'read-only' : 'editable'}
        domain={task.domain}
        domainLabel={task.domainLabel}
        init={init}
        title={`${task.name}: form of ${task.domainLabel}`}
        onSubmit={onSubmit}
        onDraft={onDraft}
      />
      <ActionBar start={startButtons} hint={sent ? COMPLETING : readOnly ? readOnlyHint(task) : { text: 'Complete the task in its form', icon: Check }} />
    </>
  );
}
