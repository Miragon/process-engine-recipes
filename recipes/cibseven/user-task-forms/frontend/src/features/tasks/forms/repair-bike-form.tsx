import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useCompleteTask, type OpenTask } from "../api/tasks";

const repairBikeSchema = z.object({
  workDone: z.string().trim().min(1, "Describe the work you did."),
  partsReplaced: z.string().trim(),
});

type RepairBikeValues = z.infer<typeof repairBikeSchema>;

export function RepairBikeForm({ task }: { task: OpenTask }) {
  const completeTask = useCompleteTask(task.id);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RepairBikeValues>({
    resolver: zodResolver(repairBikeSchema),
    defaultValues: { workDone: "", partsReplaced: "" },
  });

  const submit = handleSubmit((values) => completeTask.mutate(values));

  return (
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      <label className="field">
        Work done
        <textarea
          {...register("workDone")}
          rows={3}
          className="field-input"
          aria-invalid={errors.workDone ? true : undefined}
        />
        {errors.workDone ? (
          <span role="alert" className="field-error">
            {errors.workDone.message}
          </span>
        ) : null}
      </label>

      <label className="field">
        Parts replaced
        <input {...register("partsReplaced")} className="field-input" />
      </label>

      <button type="submit" className="button-primary" disabled={completeTask.isPending}>
        {completeTask.isPending ? "Saving…" : "Repair done"}
      </button>
    </form>
  );
}
