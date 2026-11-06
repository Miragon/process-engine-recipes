import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { Command } from '../api/client';
import { api, refreshTask, storeTask } from '../api/queries';
import type { Task, TaskUpdate } from '../api/types';
import { useToast } from '../ui/toast';

/**
 * Claim, unclaim and update. Each returns after Camunda ran the listeners, so the answer is the task as it is now;
 * on 202 Camunda accepted the change but its listeners still run, and the task updates with the `task-changed` event.
 */
export function useTaskCommands(task: Task) {
  const client = useQueryClient();
  const toast = useToast();

  const answered = ({ task: updated, confirmed }: Command, message: string) => {
    if (confirmed) storeTask(client, updated);
    refreshTask(client, task);
    toast(confirmed ? { tone: 'success', message } : { tone: 'info', message: 'Camunda accepted the change. It shows as soon as its listeners have finished.' });
  };
  const failed = (error: Error) => {
    refreshTask(client, task);
    toast({ tone: 'danger', message: error.message });
  };

  const claim = useMutation({
    mutationFn: () => api.claim(task.userTaskKey),
    onSuccess: (command) => {
      answered(command, `You claimed ${task.name}.`);
    },
    onError: failed,
  });
  const unclaim = useMutation({
    mutationFn: () => api.unclaim(task.userTaskKey),
    onSuccess: (command) => {
      answered(command, `${task.name} is unassigned again.`);
    },
    onError: failed,
  });
  const update = useMutation({
    mutationFn: (change: TaskUpdate) => api.update(task.userTaskKey, change),
    onSuccess: (command, change) => {
      answered(command, change.priority !== undefined ? 'Priority changed.' : 'Due date changed.');
    },
    onError: failed,
  });

  return { claim, unclaim, update };
}
