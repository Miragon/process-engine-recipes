import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export interface OpenTask {
  id: string;
  name: string;
  taskDefinitionKey: string;
  formKey: string | null;
}

type TaskVariables = Record<string, unknown>;

const openTasksQueryKey = ["open-tasks"];

async function fetchOpenTasks(): Promise<OpenTask[]> {
  const response = await fetch("/api/tasks");
  if (!response.ok) {
    throw new Error(`Loading open tasks failed with status ${response.status}`);
  }
  return response.json();
}

async function completeTask(taskId: string, variables: TaskVariables): Promise<void> {
  const response = await fetch(`/api/tasks/${taskId}/complete`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(variables),
  });
  if (!response.ok) {
    throw new Error(`Completing task ${taskId} failed with status ${response.status}`);
  }
}

export function useOpenTasks() {
  return useQuery({ queryKey: openTasksQueryKey, queryFn: fetchOpenTasks, refetchInterval: 2000 });
}

export function useCompleteTask(taskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: TaskVariables) => completeTask(taskId, variables),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: openTasksQueryKey }),
  });
}
