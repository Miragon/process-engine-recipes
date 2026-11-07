import type { ComponentType } from "react";
import type { OpenTask } from "../api/tasks";
import { RepairBikeForm } from "./repair-bike-form";
import { ChoosePickupSlotForm } from "./choose-pickup-slot-form";

type TaskForm = ComponentType<{ task: OpenTask }>;

const formsByTaskDefinitionKey: Record<string, TaskForm> = {
  repairBike: RepairBikeForm,
};

const formsByKey: Record<string, TaskForm> = {
  "miravelo:choose-pickup-slot": ChoosePickupSlotForm,
};

export function resolveForm(task: OpenTask): TaskForm | undefined {
  if (task.formKey) {
    return formsByKey[task.formKey];
  }
  return formsByTaskDefinitionKey[task.taskDefinitionKey];
}
