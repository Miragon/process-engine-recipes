// The working area of a task: the claim card or the form, and the action bar below it.
import type { InitPayload, ResultPayload } from '@tasklist/form-protocol';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, CircleAlert, Inbox, Lock, RotateCw } from 'lucide-react';
import { useState } from 'react';
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
import { useTaskCommands } from './useTaskCommands';

type Commands = ReturnType<typeof useTaskCommands>;

export function TaskWork({ task }: { task: Task }) {
  const { userId } = useSession();
  const [preview, setPreview] = useState(false);
  const commands = useTaskCommands(task);
  const open = task.state === 'OPEN';
  const mine = task.assignee !== null && task.assignee !== undefined && task.assignee === userId;
  const showForm = !open || mine || preview;
  const form = useTaskForm(task, showForm);

  if (!showForm) {
    return (
      <ClaimCard
        task={task}
        commands={commands}
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
  const readOnly = form.data.readOnly || !open || !mine;
  return form.data.type === 'json-forms' ? (
    <JsonTaskForm key={form.data.formRef} task={task} form={form.data} readOnly={readOnly} commands={commands} />
  ) : (
    <HtmlTaskForm task={task} form={form.data} readOnly={readOnly} commands={commands} />
  );
}

/** Shown instead of the form while the task is not the user's: claim it, or look at the form first. */
function ClaimCard({ task, commands, onPreview }: { task: Task; commands: Commands; onPreview: () => void }) {
  const { data: users } = useUsers();
  const canClaim = task.actions.includes('CLAIM');
  const [title, help] = canClaim
    ? ['Claim the task to work on its form.', 'Only the assignee can complete it.']
    : task.assignee
      ? [`${nameOf(task.assignee, users)} works on this task.`, 'Only the assignee can complete it.']
      : [`Only members of ${task.candidateGroups.join(', ') || 'its candidate groups'} can work on this task.`, 'You can still look at its form.'];
  return (
    <div className="claim-card">
      <Icon of={Inbox} size={22} />
      <p>
        <strong>{title}</strong>
        <br />
        <span className="muted">{help}</span>
      </p>
      <div className="claim-card__actions">
        {canClaim && (
          <Button
            variant="primary"
            busy={commands.claim.isPending}
            onClick={() => {
              commands.claim.mutate();
            }}
          >
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
function TaskButtons({ task, commands }: { task: Task; commands: Commands }) {
  const { userId } = useSession();
  if (task.actions.includes('UNCLAIM')) {
    return (
      <Button
        busy={commands.unclaim.isPending}
        onClick={() => {
          commands.unclaim.mutate();
        }}
      >
        Unclaim
      </Button>
    );
  }
  if (task.actions.includes('CLAIM')) {
    return (
      <Button
        variant="primary"
        busy={commands.claim.isPending}
        onClick={() => {
          commands.claim.mutate();
        }}
      >
        Claim task
      </Button>
    );
  }
  if (task.state !== 'OPEN' && task.assignee === userId) return <Button disabled>Unclaim</Button>;
  return null;
}

function readOnlyHint(task: Task) {
  if (task.state === 'CANCELED') return { text: 'Read-only: the task was canceled', icon: Lock };
  if (task.state === 'COMPLETED') return { text: 'Read-only: the task was completed', icon: Lock };
  return { text: 'Read-only: claim the task to change it', icon: Lock };
}

function useCompletion(task: Task) {
  const client = useQueryClient();
  const toast = useToast();
  const complete = useMutation({ mutationFn: (data: FormData) => api.complete(task.userTaskKey, data) });
  const draft = useMutation({ mutationFn: (data: FormData) => api.saveDraft(task.userTaskKey, data) });

  const completed = (outcome: Completion) => {
    refreshTask(client, task);
    toast(
      outcome === 'completed'
        ? { tone: 'success', message: `${task.name} completed.` }
        : { tone: 'info', message: 'Camunda accepted the completion. The task closes once its listeners finished.' },
    );
  };
  const refused = (error: Error) => {
    // A task that was completed or canceled meanwhile shows its new state.
    if (isProblem(error, 409) || isProblem(error, 403)) refreshTask(client, task);
  };
  return { complete, draft, completed, refused, toast };
}

function JsonTaskForm({ task, form, readOnly, commands }: { task: Task; form: JsonFormsView; readOnly: boolean; commands: Commands }) {
  const state = useJsonFormState(form.data);
  const { complete, draft, completed, refused, toast } = useCompletion(task);
  const [failed, setFailed] = useState<'complete' | 'draft'>('complete');
  const editable = !readOnly && task.actions.includes('COMPLETE');

  const submit = () => {
    complete.mutate(state.data, {
      onSuccess: (outcome) => {
        state.accept();
        completed(outcome);
      },
      onError: (error) => {
        setFailed('complete');
        if (isProblem(error)) state.reject(error);
        refused(error);
      },
    });
  };
  const saveDraft = () => {
    draft.mutate(state.data, {
      onSuccess: () => {
        state.accept();
        toast({ tone: 'success', message: 'Draft saved.' });
      },
      onError: (error) => {
        setFailed('draft');
        if (isProblem(error)) state.reject(error);
        refused(error);
      },
    });
  };

  const hint =
    state.attention > 0
      ? { text: `${count(state.attention, 'field')} ${state.attention === 1 ? 'needs' : 'need'} attention`, icon: CircleAlert, tone: 'danger' as const }
      : readOnly
        ? readOnlyHint(task)
        : undefined;
  const closed = task.state !== 'OPEN';

  return (
    <>
      <div className="card form-card">
        <JsonForm form={form} data={state.data} readOnly={readOnly || complete.isPending} errors={state.errors} onChange={state.change} />
      </div>
      <ActionBar
        notice={
          state.rejection && (
            <StatusBox tone="danger" title={failed === 'draft' ? "Couldn't save the draft" : "Couldn't complete the task"}>
              {state.rejection.detail}
            </StatusBox>
          )
        }
        start={<TaskButtons task={task} commands={commands} />}
        hint={hint}
        end={
          editable ? (
            <>
              {form.draftable && (
                <Button busy={draft.isPending} disabled={complete.isPending} onClick={saveDraft}>
                  Save draft
                </Button>
              )}
              <Button variant="primary" icon={Check} busy={complete.isPending} onClick={submit}>
                Complete task
              </Button>
            </>
          ) : closed ? (
            <Button variant="primary" icon={Check} disabled>
              Complete task
            </Button>
          ) : undefined
        }
      />
    </>
  );
}

function HtmlTaskForm({ task, form, readOnly, commands }: { task: Task; form: HtmlFormView; readOnly: boolean; commands: Commands }) {
  const { complete, draft, completed, refused, toast } = useCompletion(task);
  const init: InitPayload = { formRef: form.formRef, mode: 'task', data: form.data, readOnly, draftable: form.draftable && !readOnly };

  const answer = async (send: () => Promise<string>): Promise<ResultPayload> => {
    try {
      return { ok: true, message: await send() };
    } catch (error) {
      const problem = error instanceof Error ? error : new Error(String(error));
      refused(problem);
      return { ok: false, message: problem.message, errors: isProblem(problem) ? problem.errors : [] };
    }
  };
  const onSubmit = (data: FormData) =>
    answer(async () => {
      const outcome = await complete.mutateAsync(data);
      completed(outcome);
      return outcome === 'completed' ? 'Task completed.' : 'Camunda accepted the completion.';
    });
  const onDraft = (data: FormData) =>
    answer(async () => {
      await draft.mutateAsync(data);
      toast({ tone: 'success', message: 'Draft saved.' });
      return 'Draft saved.';
    });

  const startButtons = <TaskButtons task={task} commands={commands} />;
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
      <ActionBar start={startButtons} hint={readOnly ? readOnlyHint(task) : { text: 'Complete the task in its form', icon: Check }} />
    </>
  );
}
