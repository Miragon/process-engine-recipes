import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, refreshTask, storeTask } from '../api/queries';
import type { Task, TaskUpdate } from '../api/types';
import { useToast } from '../ui/toast';

/** Claim, unclaim and update. Each returns after Camunda ran the listeners, so the answer is the task as it is now. */
export function useTaskCommands(task: Task) {
  const client = useQueryClient();
  const toast = useToast();

  const succeeded = (updated: Task, message: string) => {
    storeTask(client, updated);
    refreshTask(client, updated);
    toast({ tone: 'success', message });
  };
  const failed = (error: Error) => {
    refreshTask(client, task);
    toast({ tone: 'danger', message: error.message });
  };

  const claim = useMutation({
    mutationFn: () => api.claim(task.userTaskKey),
    onSuccess: (updated) => {
      succeeded(updated, `You claimed ${task.name}.`);
    },
    onError: failed,
  });
  const unclaim = useMutation({
    mutationFn: () => api.unclaim(task.userTaskKey),
    onSuccess: (updated) => {
      succeeded(updated, `${task.name} is unassigned again.`);
    },
    onError: failed,
  });
  const update = useMutation({
    mutationFn: (change: TaskUpdate) => api.update(task.userTaskKey, change),
    onSuccess: (updated, change) => {
      succeeded(updated, change.priority !== undefined ? 'Priority changed.' : 'Due date changed.');
    },
    onError: failed,
  });

  return { claim, unclaim, update };
}
