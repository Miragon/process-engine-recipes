// A start form in the detail pane: the user starts a process of an application, then its first task opens.
import type { InitPayload, ResultPayload } from '@tasklist/form-protocol';
import { useMutation } from '@tanstack/react-query';
import { Play, RotateCw, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { isProblem } from '../api/client';
import { api, useFirstTask, useStartForm, useStartForms } from '../api/queries';
import type { FormData, JsonFormsView, StartResult } from '../api/types';
import { FormFrame } from '../forms/FormFrame';
import { JsonForm } from '../forms/LazyJsonForm';
import { useJsonFormState } from '../forms/useJsonFormState';
import { Button, IconButton } from '../ui/Button';
import { StatusBox } from '../ui/StatusBox';
import { useToast } from '../ui/toast';
import { ActionBar } from './ActionBar';
import type { StartFormRef } from './location';
import { DomainChip } from './TaskMarks';

/** How long the tasklist waits for the first task of a process it just started before it stops looking. */
const FIRST_TASK_TIMEOUT = 30_000;

interface StartFormDetailProps {
  start: StartFormRef;
  onClose: () => void;
  onOpenTask: (userTaskKey: string) => void;
}

export function StartFormDetail({ start, onClose, onOpenTask }: StartFormDetailProps) {
  const { data: entries } = useStartForms();
  const entry = entries?.find((candidate) => candidate.domain === start.domain && candidate.id === start.id);
  const form = useStartForm(start);
  const toast = useToast();
  const [placed, setPlaced] = useState<StartResult | null>(null);
  const [gaveUp, setGaveUp] = useState(false);
  const firstTask = useFirstTask(placed?.businessId ?? null, placed !== null && !gaveUp);

  const submission = useMutation({ mutationFn: (data: FormData) => api.submitStartForm(start.domain, start.id, data) });

  useEffect(() => {
    if (firstTask.data) onOpenTask(firstTask.data.userTaskKey);
  }, [firstTask.data, onOpenTask]);

  useEffect(() => {
    if (placed === null) return;
    const timer = window.setTimeout(() => {
      setGaveUp(true);
    }, FIRST_TASK_TIMEOUT);
    return () => {
      window.clearTimeout(timer);
    };
  }, [placed]);

  const started = (result: StartResult) => {
    setPlaced(result);
    toast({ tone: 'success', message: result.businessId ? `${result.businessId} placed.` : 'Process started.' });
  };

  const label = entry?.label ?? start.id;
  const domainLabel = entry?.domainLabel ?? start.domain;

  return (
    <>
      <div className="detail__head">
        <div>
          <p className="eyebrow">
            <DomainChip domain={start.domain} label={domainLabel} />
            <span className="muted">Start form{entry ? ` · ${entry.formRef}` : ''}</span>
          </p>
          <h1>{label}</h1>
          {entry?.description && <p className="muted">{entry.description}</p>}
        </div>
        <IconButton label="Close" icon={X} onClick={onClose} />
      </div>

      {placed && (
        <StatusBox tone={gaveUp ? 'info' : 'success'} title={placed.businessId ? `${placed.businessId} placed` : 'Process started'}>
          {gaveUp
            ? 'Its first task has not shown up yet. It appears in the list as soon as the process creates it.'
            : 'Its first task opens here as soon as the process creates it.'}
        </StatusBox>
      )}

      {form.isPending ? (
        <div className="card form-skeleton" aria-busy="true" aria-label="Loading the form">
          <span className="skeleton skeleton--title" />
          <span className="skeleton skeleton--field" />
          <span className="skeleton skeleton--field" />
        </div>
      ) : form.isError ? (
        <StatusBox
          tone="danger"
          title="Couldn't load the start form"
          actions={
            <Button size="sm" icon={RotateCw} onClick={() => void form.refetch()}>
              Retry
            </Button>
          }
        >
          {form.error.message}
        </StatusBox>
      ) : form.data.type === 'json-forms' ? (
        <JsonStartForm
          form={form.data}
          locked={placed !== null}
          busy={submission.isPending}
          onClose={onClose}
          onSubmit={(data, state) => {
            submission.mutate(data, {
              onSuccess: started,
              onError: (error) => {
                if (isProblem(error)) state.reject(error);
              },
            });
          }}
        />
      ) : (
        <>
          {/* The frame shows its own confirmation; once placed, the host ignores further submissions. */}
          <FormFrame
            domain={start.domain}
            domainLabel={domainLabel}
            init={{ formRef: form.data.formRef, mode: 'start', data: form.data.data, readOnly: form.data.readOnly || placed !== null, draftable: false } satisfies InitPayload}
            title={`${label}: form of ${domainLabel}`}
            onSubmit={async (data): Promise<ResultPayload> => {
              try {
                const result = await submission.mutateAsync(data);
                started(result);
                return { ok: true, message: result.businessId ? `${result.businessId} placed.` : 'Process started.' };
              } catch (error) {
                const problem = error instanceof Error ? error : new Error(String(error));
                return { ok: false, message: problem.message, errors: isProblem(problem) ? problem.errors : [] };
              }
            }}
            onDraft={() => Promise.resolve({ ok: false, message: 'Start forms keep no drafts.' })}
          />
          <ActionBar
            start={
              <Button variant="ghost" onClick={onClose}>
                {placed ? 'Close' : 'Cancel'}
              </Button>
            }
          />
        </>
      )}
    </>
  );
}

interface JsonStartFormProps {
  form: JsonFormsView;
  locked: boolean;
  busy: boolean;
  onClose: () => void;
  onSubmit: (data: FormData, state: ReturnType<typeof useJsonFormState>) => void;
}

function JsonStartForm({ form, locked, busy, onClose, onSubmit }: JsonStartFormProps) {
  const state = useJsonFormState(form.data);
  return (
    <>
      <div className="card form-card">
        <JsonForm form={form} data={state.data} readOnly={form.readOnly || locked || busy} errors={state.errors} onChange={state.change} />
      </div>
      <ActionBar
        notice={state.rejection && <StatusBox tone="danger" title="Couldn't start the process">{state.rejection.detail}</StatusBox>}
        start={
          <Button variant="ghost" onClick={onClose}>
            {locked ? 'Close' : 'Cancel'}
          </Button>
        }
        end={
          <Button
            variant="primary"
            icon={Play}
            busy={busy}
            disabled={locked}
            onClick={() => {
              onSubmit(state.data, state);
            }}
          >
            Start
          </Button>
        }
      />
    </>
  );
}
